package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentLikeRepository;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentPopularityRow;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentReplyCount;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 한 게시글의 댓글을 등록순·최신순·인기순으로 조회하고
 * 좋아요 정보와 다음 페이지 커서까지 묶어서 반환하는 서비스
 * <p>
 * REPEATABLE_READ
 * 여러 조회의 기준을 맞추기 위한 설정.
 * 인기순 응답 하나를 만드는 동안에 여러 조회가 실행된다.
 * - 인기순 집계 조회
 * - 댓글 본문 조회
 * - 현재 회원의 좋아요 상태 조회
 * PostgreSQL에서 이 작업들이 하나의 REPEATABLE_READ 트랜잭션으로 실행되면
 * 같은 DB 스냅샷을 기준으로 조회할 수 있게 된다.
 * 예를 들어 인기순 집계 직후 다른 사용자가 댓글을 삭제하더라도
 * 이 삭제 요청의 뒤이은 본문 조회는 같은 스냅샷을 기준으로 읽을 수 있다.
 * 즉, "순위에는 있는데 본문은 사라지는" 불일치를 줄이기 위한 목적
 */
@Service
@RequiredArgsConstructor
@Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ
)
public class CommentReadService {

    private static final int BEST_COMMENT_LIMIT = 3;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;

    private final CommentLikeReadService commentLikeReadService;

    /**
     * 기존 비로그인 등록순 호출 유지
     */
    public CommentListResult getComments(
            Long postId,
            @Nullable Instant afterCreatedAt,
            @Nullable Long afterId,
            int size
    ) {
        return getComments(
                postId,
                afterCreatedAt,
                afterId,
                size,
                null
        );
    }

    /**
     * 기존 등록순 호출을 새 조회 조건으로 변환
     */
    public CommentListResult getComments(
            Long postId,    // 댓글을 조회할 게시글
            @Nullable Instant afterCreatedAt,   // 직전에 받은 마지막 댓글의 생성 시각
            @Nullable Long afterId,     // 직전에 받은 마지막 댓글의 ID
            int size,    // 이번에 받을 댓글 개수
            @Nullable Long viewerId
    ) {
        CommentCursor cursor = null;

        // 두 값 중 하나만 전달된 잘못된 요청도 커서로 만들어 검증하도록 하기 위함
        // &&로 바꾸면 하나만 전달된 요청을 첫 페이지 요청처럼 취급할 수 있음
        if (afterCreatedAt != null || afterId != null) {
            cursor = new CommentCursor(
                    afterCreatedAt,
                    afterId
            );
        }

        CommentReadQuery query = new CommentReadQuery(
                CommentSort.OLDEST,
                cursor,
                size
        );

        return getComments(postId, query, viewerId);
    }

    /**
     * 정렬 조건을 사용하는 새로운 조회 진입점.
     * 공통 진입점에서 게시글을 확인하고 조회 개수를 정한다.
     * <p>
     * 예시 요청
     * getComments(
     * 50L,
     * new CommentReadQuery(CommentSort.LATEST, null, 2),
     * 7L
     * );
     */
    public CommentListResult getComments(
            Long postId,
            CommentReadQuery query,
            @Nullable Long viewerId
    ) {
        // 조회 조건이 있는지 확인한다.
        Objects.requireNonNull(query, "댓글 조회 조건은 필수입니다.");

        // postId가 유효하고, 공개 조회 가능한 게시글인지 확인
        validatePost(postId);

        // query.size() + 1 만큼 페이지 요청 (추가 1개는 다음 페이지가 있는지 여부 판단용)
        PageRequest limit = PageRequest.of(
                0,
                query.size() + 1
        );

        // 정렬 조건이 인기순이면 해당 분기로 이동
        /**
         * 왜 인기순은 별도 처리할까?
         * 인기순은 좋아요 개수를 집계해서 순위를 정하는 작업과
         * 댓글 본문·작성자 정보를 가져오는 작업을 나누어 처리한다.
         */
        if (query.sort() == CommentSort.POPULAR) {
            return getPopularComments(
                    postId,
                    query,
                    viewerId,
                    limit
            );
        }

        // 정렬 조건이 인기순이 아닌 경우 해당 분기로 이동
        // 최신순, 등록순
        return getTimeOrderedComments(
                postId,
                query,
                viewerId,
                limit
        );
    }

    /**
     * 등록순·최신순 응답을 만든다.
     */
    private CommentListResult getTimeOrderedComments(
            Long postId,
            CommentReadQuery query,
            @Nullable Long viewerId,
            PageRequest limit
    ) {
        // findTimeOrderedComments()가 정렬과 커서를 보고
        // Repository 메서드를 선택한다.
        List<Comment> comments = findTimeOrderedComments(
                postId,
                query,
                limit
        );

        // 다음 페이지 여부 계산
        boolean hasNext = comments.size() > query.size();
        int returnedSize = Math.min(
                comments.size(),
                query.size()
        );

        // 실제 응답할 댓글은 요청한 query.size() 개수로 자른다.
        List<Comment> returnedComments =
                comments.subList(0, returnedSize);

        // 응답할 댓글에 좋아요 정보를 붙인다.
        // 먼저 응답 대상의 ID를 추출
        List<Long> commentIds = returnedComments.stream()
                .map(Comment::getId)
                .toList();

        Map<Long, Long> replyCounts = getReplyCounts(postId, commentIds);

        // 직전에서 추출한 ID를 조회 서비스에 한번에 넘긴다.
        Map<Long, CommentLikeState> likeStates =
                commentLikeReadService.getStates(
                        commentIds,
                        viewerId
                );

        // 댓글 본문·작성자 등의 정보와 좋아요 상태를 합쳐서 CommentResult를 만든다.
        List<CommentResult> items = returnedComments.stream()
                .map(comment -> CommentResult.from(
                        comment,
                        likeStates.getOrDefault(    // 해당 댓글의 집계가 없으면 기본값인 좋아요 0개, 즉, 내가 누르지 않았음을 의미
                                comment.getId(),
                                CommentLikeState.EMPTY
                        ),
                        replyCounts.getOrDefault(comment.getId(), 0L)
                ))
                .toList();

        CommentCursor nextCursor = null;

        if (hasNext) {
            // 다음 페이지 커서는 실제 반환한 마지막 댓글로 만들어야 한다.
            CommentResult last = items.getLast();

            nextCursor = new CommentCursor(
                    last.createdAt(),
                    last.commentId(),
                    query.sort(),
                    null
            );
        }

        return new CommentListResult(
                items,
                hasNext,
                nextCursor
        );
    }

    /**
     * 정렬 종류와 커서 유무에 따라 시간순 쿼리를 선택한다.
     */
    private List<Comment> findTimeOrderedComments(
            Long postId,
            CommentReadQuery query,
            PageRequest limit
    ) {
        CommentCursor cursor = query.cursor();

        return switch (query.sort()) {
            case OLDEST -> cursor == null
                    ? commentRepository.findFirstVisibleComments(
                    postId,
                    limit
            )
                    : commentRepository.findVisibleCommentsAfter(
                    postId,
                    cursor.createdAt(),
                    cursor.commentId(),
                    limit
            );

            case LATEST -> cursor == null
                    ? commentRepository.findFirstVisibleCommentsLatest(
                    postId,
                    limit
            )
                    : commentRepository.findVisibleCommentsLatestAfter(
                    postId,
                    cursor.createdAt(),
                    cursor.commentId(),
                    limit
            );

            case POPULAR -> throw new IllegalStateException(
                    "인기순은 별도 조회 경로를 사용해야 합니다."
            );
        };
    }

    /**
     * 인기순 결과에 본문·작성자·현재 회원의 liked를 연결한다.
     */
    private CommentListResult getPopularComments(
            Long postId,
            CommentReadQuery query,
            @Nullable Long viewerId,
            PageRequest limit   // 여기에 전달된 limit은 query.size() + 1개의 댓글 정보가 있음
    ) {
        CommentCursor cursor = query.cursor();

        // 1. 첫 요청에서 만약 2개를 원하면, 인기순 집계 쿼리로 최대 3개를 가져온다.
        // rows: [103번·8개, 101번·5개, 102번·3개]
        // CommentPopularityRow: 댓글ID·작성 시각·좋아요 수를 제공하는 집계 결과
        List<CommentPopularityRow> rows = cursor == null    // 첫 조회냐 아니냐
                ? commentRepository.findFirstVisibleCommentsPopular(
                postId,
                limit
        )
                : commentRepository.findVisibleCommentsPopularAfter(
                postId,
                cursor.likeCount(),
                cursor.createdAt(),
                cursor.commentId(),
                limit
        );

        boolean hasNext = rows.size() > query.size();
        int returnedSize = Math.min(
                rows.size(),
                query.size()
        );

        List<CommentPopularityRow> returnedRows =
                rows.subList(0, returnedSize);

        // 빈 IN 조회와 불필요한 좋아요 조회를 피한다.
        if (returnedRows.isEmpty()) {
            return new CommentListResult(
                    List.of(),
                    false,
                    null
            );
        }

        List<Long> commentIds = returnedRows.stream()
                .map(CommentPopularityRow::getCommentId)
                .toList();

        Map<Long, Long> replyCounts = getReplyCounts(postId, commentIds);

        Map<Long, Comment> commentsById =
                commentRepository.findPopularListCommentsByIds(
                                postId,
                                commentIds
                        )
                        .stream()
                        .collect(Collectors.toMap(  // ID로 찾기 쉽도록 Map으로 바꾼다.
                                Comment::getId,
                                Function.identity()
                        ));

        Map<Long, CommentLikeState> likeStates =
                commentLikeReadService.getStates(
                        commentIds,
                        viewerId
                );

        // 본문 조회 결과의 순서가 아니라
        // 처음 받은 인기순 결과(returnedRows)의 순서대로 응답을 만든다.
        // 각 행의 ID로 본문을 찾아서 붙이므로 본문 조회 결과의 순서가 바뀌어도 응답의 순서는 유지된다.
        List<CommentResult> items = returnedRows.stream()
                .map(row -> {
                    Comment comment =
                            commentsById.get(row.getCommentId());

                    // 같은 스냅샷과 동일한 공개 조건을 사용했다면 있어야 하는 본문이 없을 때
                    // 조용히 댓글을 빼지 않고 조회 로직의 불일치를 드러내는 장치
                    // 다만 첫 페이지 요청과 다음 페이지 요청까지 같은 스냅샷으로 유지되는 것은 아님
                    // 요청 사이에 좋아요 수가 바뀌면 인기순 위치도 바뀔 수 있기 때문에
                    // 이 코드만으로는 페이지 간 중복·누락까지 완전히 막지는 못함.
                    if (comment == null) {
                        throw new IllegalStateException(
                                "인기순 댓글의 본문 조회 결과가 일치하지 않습니다."
                        );
                    }

                    CommentLikeState viewerState =
                            likeStates.getOrDefault(
                                    row.getCommentId(),
                                    CommentLikeState.EMPTY
                            );

                    // 응답 개수와 커서는 정렬에 사용한 집계값을 사용한다.
                    // 이렇게 하면 정렬에 사용한 좋아요 수와 화면에 표시하는 좋아요 수의 기준이 일치한다.
                    CommentLikeState state = new CommentLikeState(
                            row.getLikeCount(),
                            viewerState.liked()
                    );

                    return CommentResult.from(
                            comment,
                            state,
                            replyCounts.getOrDefault(row.getCommentId(), 0L)
                    );
                })
                .toList();

        CommentCursor nextCursor = null;

        if (hasNext) {
            CommentPopularityRow last = returnedRows.getLast();

            nextCursor = new CommentCursor(
                    last.getCreatedAt(),
                    last.getCommentId(),
                    CommentSort.POPULAR,
                    last.getLikeCount()
            );
        }

        return new CommentListResult(
                items,
                hasNext,
                nextCursor
        );
    }

    public List<CommentResult> getBestComments(
            Long postId,
            @Nullable Long viewerId
    ) {
        validatePost(postId);

        List<Long> rankedIds = commentLikeRepository.findBestCommentIds(
                postId,
                PageRequest.of(0, BEST_COMMENT_LIMIT)
        );

        if (rankedIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Comment> commentsById = commentRepository.findVisibleCommentsByIds(postId, rankedIds)
                .stream()
                .collect(Collectors.toMap(
                        Comment::getId,
                        Function.identity()
                ));

        Map<Long, Long> replyCounts = getReplyCounts(postId, rankedIds);

        Map<Long, CommentLikeState> likeStates =
                commentLikeReadService.getStates(rankedIds, viewerId);

        // rankedIds 순서로 조립한다.
        // 예를 들어 순위 조회 결과가 [42, 17] 이면
        // 본문 조회가 [17, 42]로 반환되어도 응답은 [42, 17]이어야 한다.
        // IN 조회의 반환 순서에 의존하면 이 부분이 깨질 수 있음.
        return rankedIds.stream()
                .map(commentId -> {
                    Comment comment = commentsById.get(commentId);
                    CommentLikeState likeState = likeStates.get(commentId);

                    // 일반 댓글은 좋아요 0개가 가능하지만 베스트로 선정된 댓글은
                    // 같은 스냅샷에서 이미 좋아요가 10개 이상임.
                    // 비로그인 사용자도 전체 좋아요 수는 그대로 받고 liked만 false가 되어야 한다.
                    if (comment == null || likeState == null) {
                        throw new IllegalStateException(
                                "베스트 댓글의 본문 또는 좋아요 집계가 없습니다. commentId="
                                        + commentId
                        );
                    }

                    return CommentResult.from(
                            comment,
                            likeState,
                            replyCounts.getOrDefault(commentId, 0L)
                    );
                })
                .toList();
    }

    private void validatePost(Long postId) {
        if (postId == null || postId <= 0) {
            throw new CommentReadException(
                    CommentReadException.Code.POST_NOT_FOUND
            );
        }

        postRepository.findVisiblePostById(postId)
                .orElseThrow(() ->
                        new CommentReadException(
                                CommentReadException.Code.POST_NOT_FOUND
                        )
                );
    }

    public CommentListResult getReplies(
            Long postId,
            Long parentId,
            @Nullable CommentCursor cursor,
            int size,
            @Nullable Long viewerId
    ) {
        CommentReadQuery query = new CommentReadQuery(
                CommentSort.OLDEST,
                cursor,
                size
        );

        validatePost(postId);

        PageRequest limit = PageRequest.of(0, query.size() + 1);

        List<Comment> replies;

        if(cursor == null) {
            replies = commentRepository.findFirstVisibleReplies(
                    postId,
                    parentId,
                    limit
            );
        } else {
            replies = commentRepository.findVisibleRepliesAfter(
                    postId,
                    parentId,
                    cursor.createdAt(),
                    cursor.commentId(),
                    limit
            );
        }

        if(replies.isEmpty()) {
            return new CommentListResult(
                    List.of(),
                    false,
                    null
            );
        }

        boolean hasNext = replies.size() > query.size();
        List<Comment> returnedReplies = replies.subList(
                0,
                Math.min(replies.size(), query.size())
        );

        List<Long> replyIds = returnedReplies.stream()
                .map(Comment::getId)
                .toList();

        Map<Long, CommentLikeState> likeStates =
                commentLikeReadService.getStates(replyIds, viewerId);

        List<CommentResult> items = returnedReplies.stream()
                .map(reply -> CommentResult.from(
                        reply,
                        likeStates.getOrDefault(
                                reply.getId(),
                                CommentLikeState.EMPTY
                        ),
                        0L
                ))
                .toList();

        CommentCursor nextCursor = null;

        if(hasNext) {
            CommentResult last = items.getLast();
            nextCursor = new CommentCursor(
                    last.createdAt(),
                    last.commentId()
            );
        }

        return new CommentListResult(items, hasNext, nextCursor);
    }

    /**
     * 한 페이지의 답글 수를 한 번에 조회
     */
    private Map<Long, Long> getReplyCounts(
            Long postId,
            List<Long> parentIds
    ) {
        if (parentIds.isEmpty()) {
            return Map.of();
        }

        return commentRepository.countVisibleRepliesByParentIds(postId, parentIds)
                .stream()
                .collect(Collectors.toMap(
                        CommentReplyCount::getParentId,
                        CommentReplyCount::getReplyCount
                ));
    }
}
