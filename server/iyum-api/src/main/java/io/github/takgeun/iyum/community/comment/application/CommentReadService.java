package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.*;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
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

    /**
     * 어떤 댓글 덕분에 베스트가 됐는지와
     * 그 댓글을 화면에서 어떻게 보여줄지 연결하는 메서드
     * 대댓글이 좋아요를 많이 받으면 그 대댓글 덕분에 베스트에 올라가지만,
     * 화면에서는 맥락을 알 수 있도록 원댓글과 대댓글을 함께 보여줘야 한다.
     * 그래서 순위를 정한 댓글 ID와 화면에 표시할 원댓글 ID가 다를 수 있다.
     *
     * 예시)
     * 100번 댓글 - 좋아요 3개 - 원댓글
     * 101번 댓글 - 좋아요 18개 - 100번의 대댓글
     * 200번 댓글 - 좋아요 15개 - 원댓글. 대댓글 없음
     * 이 때 메서드가 만들어야 하는 결과는 아래와 같다.
     * 베스트 순위 - 순위가 결정된 이유 - 화면에 표시할 내용
     * 1위 - 101번 대댓글이 18개 - 100번 원댓글 + 101번 대댓글
     * 2위 - 200번 원댓글이 15개 - 200번 원댓글만
     */
    public List<BestCommentResult> getBestComments(
            Long postId,
            @Nullable Long viewerId
    ) {
        // 대상 게시글 확인
        validatePost(postId);

        // rankedIds: 어떤 댓글이 순위를 따냈나?
        // findBestCommentIds(): 한 원댓글 묶음에서 대표를 하나씩 뽑고, 대표들을 좋아요 순으로 정렬
        // PageRequest.of(0, BEST_COMMENT_LIMIT): 그 중 상위 개수만 가져온다.
        // rankedIds = [101, 200]
        List<Long> rankedIds = commentLikeRepository.findBestCommentIds(
                postId,
                PageRequest.of(0, BEST_COMMENT_LIMIT)
        );

        // 후보가 하나도 없으면 여기서 빈 리스트를 반환하고 종료한다.
        if (rankedIds.isEmpty()) {
            return List.of();
        }

        /**
         * rootIds : 각 순위에서 어떤 원댓글을 보여줘야 하나?
         * rankedIds에는 숫자만 있어서 101이 누구의 대댓글인지 알 수 없다.
         * 그래서 findVisibleCommentsByIds()로 댓글 객체를 가져와
         * rankedCommentsById에 담는다.
         */
        Map<Long, Comment> rankedCommentsById = commentRepository
                .findVisibleCommentsByIds(postId, rankedIds)
                .stream()
                .collect(Collectors.toMap(
                        Comment::getId,
                        Function.identity() // Comment 객체 자체를 넣는다는 의미
                ));


        // 보여줄 원댓글 골라내는 과정
        List<Long> rootIds = rankedIds.stream()
                .map(id -> Objects.requireNonNull(
                        rankedCommentsById.get(id),
                        "베스트 후보 댓글을 찾을 수 없습니다. commentId=" + id
                ))
                // 101은 대댓글이므로 부모 ID인 100을 선택한다.
                // 200은 원댓글이므로 자기 ID인 200을 선택한다.
                // 따라서 rankedIds = [101, 200] -> rootIds = [100, 200]이 된다.
                // 원래 순서대로 rankedIds를 순회했기 때문에 100번 원댓글의 좋아요가 3개여도
                // 첫 번째 자리가 유지된다.
                // 이후 rootsById에는 화면에 표시할 원댓글 객체들을 담는다.
                .map(comment -> comment.getParent() == null
                        ? comment.getId()
                        : comment.getParent().getId())
                .toList();

        // 화면에 표시할 원댓글 객체 담기
        // findPopularListCommentsByIds()를 쓴 이유는
        // 공개 대댓글이 남은 삭제 원댓글도 가져올 수 있기 때문
        Map<Long, Comment> rootsById = commentRepository
                .findPopularListCommentsByIds(postId, rootIds)
                .stream()
                .collect(Collectors.toMap(Comment::getId, Function.identity()));

        // bestReplyIdByRootId: 각 원댓글 옆에 어떤 대댓글을 붙이나?
        // 선정된 원댓글마다 좋아요가 10개 이상인 대댓글 중 가장 높은 것 하나를 찾는다.
        // bestReplyIdByRootId = {100 -> 101}
        // 200번 댓글은 해당하는 대댓글이 없으니 맵에 항목이 없음.
        Map<Long, Long> bestReplyIdByRootId = commentLikeRepository
                .findBestRepliesByParentIds(postId, rootIds)
                .stream()
                .collect(Collectors.toMap(
                        BestReplyIds::getParentId,
                        BestReplyIds::getReplyId
                ));

        // replyIds = [101]
        List<Long> replyIds = List.copyOf(bestReplyIdByRootId.values());

        // repliesById: 이렇게 찾은 대댓글의 실제 Comment 객체를 ID로 꺼내기 위한 맵
        // 왜 대댓글을 다시 찾을까?
        // 반대 상황을 생각해보자.
        // 100번 원댓글이 25개, 101번 대댓글이 18개 좋아요 개수라면
        // 첫 번째 쿼리는 원댓글 100만 대표로 뽑는다.
        // 그래도 화면에는 좋아요 18개인 대댓글 101을 함께 보여줘야 한다.
        // 이 상황의 대댓글을 찾아주는 것이 바로 여기 쿼리임.
        Map<Long, Comment> repliesById = replyIds.isEmpty()
                ? Map.of()
                : commentRepository.findVisibleCommentsByIds(postId, replyIds)
                .stream()
                .collect(Collectors.toMap(
                        Comment::getId,
                        Function.identity()
                ));

        List<Long> displayedIds = new ArrayList<>(rootIds);
        displayedIds.addAll(replyIds);

        // 표시할 댓글이 정해졌으니 필요한 정보를 모아서 조회한다.
        // likeStates: 화면에 표시할 원댓글과 대댓글 각각의 좋아요 수, viewerId 회원의 좋아요 여부
        // 예시 기준 조회할 ID [100, 200, 101]
        Map<Long, CommentLikeState> likeStates =
                commentLikeReadService.getStates(displayedIds, viewerId);

        // replyCounts: 각 원댓글에 달린 대댓글 수
        // 예시 기준 조회할 ID [100, 200] (101은 원댓글이 아님)
        Map<Long, Long> replyCounts = getReplyCounts(postId, rootIds);

        List<BestCommentResult> results = new ArrayList<>();

        // 준비한 데이터를 카드로 조립한다.
        // 예시 기준으로 원댓글 [100, 200] 순서로 순회한다.

        // rootId = 100 -> rootResult : 원댓글 100 -> bestReply: 대댓글 101
        // -> BestCommentResult(원댓글100, 대댓글101) 생성
        // rootId = 200 -> rootResult : 원댓글 200 -> bestReply: 대댓글 null
        // -> BestCommentResult(원댓글100, null) 생성
        for (Long rootId : rootIds) {
            Comment root = Objects.requireNonNull(
                    rootsById.get(rootId),
                    "베스트 카드의 원댓글을 찾을 수 없습니다. rootId=" + rootId
            );

            CommentResult rootResult = CommentResult.from(
                    root,
                    // 원댓글 좋아요에는 아래 getOrDefault(rootId, CommentLikeState.EMPTY) 가 필요
                    // 대댓글 덕분에 선정된 원댓글은 실제로 좋아요가 0개일 수 있음.
                    // 반면 베스트 대댓글은 같은 읽기 트랜잭션에서 이미 좋아요 10개 이상으로 선정됐으므로
                    // 집계가 있어야 한다.
                    likeStates.getOrDefault(rootId, CommentLikeState.EMPTY),
                    replyCounts.getOrDefault(rootId, 0L)
            );

            CommentResult bestReply = null;
            Long replyId = bestReplyIdByRootId.get(rootId);

            if(replyId != null) {
                Comment reply = Objects.requireNonNull(
                        repliesById.get(replyId),
                        "베스트 대댓글을 찾을 수 없습니다. replyId=" + replyId
                );
                CommentLikeState replyLikeState = Objects.requireNonNull(
                        likeStates.get(replyId),
                        "베스트 대댓글의 좋아요 집계가 없습니다. replyId=" + replyId
                );

                bestReply = CommentResult.from(reply, replyLikeState, 0L);
            }

            results.add(new BestCommentResult(rootResult, bestReply));
        }

        return results;
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

        if (cursor == null) {
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

        if (replies.isEmpty()) {
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

        if (hasNext) {
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
