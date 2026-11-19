package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.comment.infrastructure.PostCommentCount;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostLikeRepository;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.community.post.infrastructure.PostSearchRow;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Null;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 조회와 결과 변환을 하나의 읽기 전용 트랜잭션 안에서 실행하도록 설정
public class PostReadService {

    /**
     * 게시글 조회 -> 작성자 정보 읽기 -> 결과 객체 생성 -> 서비스 반환
     * 서비스 반환 전 결과 객체가 생성되니 이후 컨트롤러에서 결과 객체의 값만 읽으면 된다.
     */
    private static final int MAX_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostLikeReadService postLikeReadService;
    private final Clock clock;


    public Page<PostSummaryResult> getPosts(
            @Nullable PostType type,
            @Nullable PostTopic topic,
            int page,
            int size,
            @Nullable Long viewerId
    ) {
        return getPosts(
                type,
                topic,
                null,
                page,
                size,
                viewerId
        );
    }

    public Page<PostSummaryResult> getPosts(
            @Nullable PostType type,
            @Nullable PostTopic topic,
            @Nullable String keyword,
            int page,
            int size,
            @Nullable Long viewerId
    ) {
        validatePage(page, size);

        String normalizedKeyword = PostSearchKeyword.normalize(keyword);
        PageRequest pageable = PageRequest.of(page, size);

        Page<PostSearchRow> rows = postRepository.findVisiblePostSearchRows(
                type,
                topic,
                normalizedKeyword,
                pageable
        );

        // 현재 페이지에 게시글이 없으면 댓글 집계 쿼리 실행 X
        // 원래 Page의 전체 개수와 페이지 정보는 그대로 유지
        if (!rows.hasContent()) {
            return rows.map(
                    row -> PostSummaryResult.from(row.getPost(), 0L, 0L, false)
            );
        }

        // 현재 페이지에 포함된 게시글 ID만 추출한다.
        List<Long> postIds = rows.getContent()
                .stream()
                .map(row -> row.getPost().getId())
                .toList();

        // 게시글마다 조회하지 않고 한번에 집계한다.
        List<PostCommentCount> counts =
                commentRepository.countVisibleCommentsByPostIds(postIds);

        // 게시글 ID로 댓글 수를 찾을 수 있도록 Map으로 반환
        Map<Long, Long> commentCounts = new HashMap<>();

        for (PostCommentCount count : counts) {
            commentCounts.put(
                    count.getPostId(),
                    count.getCommentCount()
            );
        }

        Map<Long, PostLikeState> likeStates =
                postLikeReadService.getStates(postIds, viewerId);

        int keywordCodePointCount = normalizedKeyword == null
                ? 0
                : normalizedKeyword.codePointCount(0, normalizedKeyword.length());

        // 제목 일치 여부를 검사할 필요 없음.
        // 제목과 본문에 모두 검색어가 있으면 본문 발췌가 생성되고
        // 제목에만 있으면 DB가 반환한 본문 위치가 0이어서 발췌문은 null
        // 또한 이미 조회한 Post를 사용하므로 게시글별 상세 조회도 필요 없음
        return rows.map(row -> {
            Post post = row.getPost();

            PostLikeState likeState = likeStates.getOrDefault(
                    post.getId(),
                    PostLikeState.EMPTY
            );

            String matchedContentExcerpt = PostSearchExcerpt.fromBodyMatchPosition(
                    post.getContent(),
                    row.getBodyMatchPosition(),
                    keywordCodePointCount
            );

            return PostSummaryResult.from(
                    post,
                    commentCounts.getOrDefault(post.getId(), 0L),
                    likeState.likeCount(),
                    likeState.liked(),
                    matchedContentExcerpt
            );
        });
    }

    public PostDetailResult getPost(
            Long postId,
            @Nullable Long viewerId
    ) {
        if (postId == null || postId <= 0) {
            throw new PostReadException(
                    PostReadException.Code.POST_NOT_FOUND
            );
        }

        // 기존 findById()는 삭제된 게시글도 찾을 수 있음.
        // 따라서 삭제된 게시글은 찾으면 안되니 findVisiblePostById 활용
        Post post = postRepository.findVisiblePostById(postId)
                .orElseThrow(() ->
                        // findVisiblePostById가 비어있으면
                        // 삭제 여부 따로 공개 안하고 그냥 모두 POST_NOT_FOUND 처리
                        new PostReadException(
                                PostReadException.Code.POST_NOT_FOUND
                        )
                );

        PostLikeState likeState = postLikeReadService
                .getStates(List.of(post.getId()), viewerId)
                .getOrDefault(post.getId(), PostLikeState.EMPTY);

        return PostDetailResult.from(
                post,
                likeState.likeCount(),
                likeState.liked()
        );
    }

    // 게시글을 일정 개수씩 조회해서 댓글 수와 조회 위치까지 묶어 반환하는 서비스 메소드
    public PostFeedResult getFeed(
            @Nullable PostType type,
            @Nullable PostTopic topic,
            @Nullable Instant afterCreatedAt,
            @Nullable Long afterId,
            int size,    // 이번 요청에서 사용자에게 최대 몇 개의 게시글을 반환할 것인가?
            @Nullable Long viewerId
    ) {
        validateFeedSize(size);
        validateCursor(afterCreatedAt, afterId);

        // 커서 조건으로 조회 범위 정하고
        // 그 범위에서 size + 1개만 가져오기
        PageRequest limit = PageRequest.of(0, size + 1);

        List<Post> fetched;

        if (afterCreatedAt == null && afterId == null) {
            fetched = postRepository.findFirstVisiblePosts(
                    type,
                    topic,
                    limit
            );
        } else {
            fetched = postRepository.findVisiblePostsAfter(
                    type,
                    topic,
                    afterCreatedAt,
                    afterId,
                    limit
            );
        }

        boolean hasNext = fetched.size() > size;
        int returnedSize = Math.min(fetched.size(), size);

        // 다음 목록 확인용으로 가져온 추가 1개는 반환 대상에서 제외
        // 21개 게시글 중 20개 먼저 보여준다 가정할 때
        // 이번에 반환할 게시글은 0~19 인덱스 게시글 보여주기. (20 인덱스는 제외)
        List<Post> returnedPosts = fetched.subList(0, returnedSize);

        // 빈 목록이면 댓글 집계 쿼리도 실행하지 않는다.
        if (returnedPosts.isEmpty()) {
            return new PostFeedResult(
                    List.of(),
                    false,
                    null
            );
        }

        List<PostFeedItemResult> items =
                toFeedItems(returnedPosts, viewerId);

        PostCursor nextCursor = null;

        if (hasNext) {
            // 추가 확인용 게시글이 아니라
            // 실제 반환한 마지막 게시글로 커서를 만든다.
            PostFeedItemResult last = items.getLast();

            nextCursor = new PostCursor(
                    last.createdAt(),
                    last.postId()
            );
        }

        return new PostFeedResult(
                items,
                hasNext,
                nextCursor
        );
    }

    /**
     * 주간 인기 게시글 피드 조회
     */
    // 순위 선정과 응답용 좋아요 집계가 동일한 스냅샷을 읽는다.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<PostFeedItemResult> getWeeklyBestPosts(
            int size,
            @Nullable Long viewerId
    ) {
        validateFeedSize(size);

        Instant asOf = clock.instant();
        Instant fromInclusive = asOf.minus(Duration.ofDays(7));

        List<Long> rankedIds = postLikeRepository.findWeeklyBestPostIds(
                fromInclusive,
                asOf,
                // 주간베스트는 상위 size건을 가져오는 조회라서
                // 다음 페이지 확인용 한 건이 필요하지 않음.
                // 또한 findVisiblePostsInRankOrder() 가 빈 ID 목록을 바로 반환하도록 해서
                // 베스트 글이 0건이면 후속 게시글·댓글 좋아요 조회도 저절로 생략될 것.
                PageRequest.of(0, size)
        );

        List<Post> rankedPosts =
                postRepository.findVisiblePostsInRankOrder(rankedIds);

        return toFeedItems(rankedPosts, viewerId);
    }

    /**
     * 이미 조회한 게시글 목록에 댓글 수와 좋아요 정보를 붙여서
     * 화면에 전달할 결과 객체 목록으로 변환
     */
    private List<PostFeedItemResult> toFeedItems(
            // DB에서 이미 조회한 Post 객체 목록
            List<Post> posts,
            // 현재 목록을 보는 회원의 ID. 비로그인이면 null
            // 게시글 작성자의 ID가 아니라
            // 내가 이 게시글에 좋아요를 눌렀는지 확인하기 위한 ID
            @Nullable Long viewerId
    ) {

        if(posts.isEmpty()) {
            return List.of();
        }

        // 실제 반환할 게시글의 댓글 수만 집계된다.
        List<Long> postIds = posts.stream()
                .map(Post::getId)
                .toList();

        // 실제 반환할 게시글별 공개 댓글 수를 집계한다.
        List<PostCommentCount> counts =
                commentRepository.countVisibleCommentsByPostIds(postIds);

        // 댓글 수를 조회하고, 게시글 ID로 찾기 쉬운 Map으로 정리
        // 현재 반환할 게시글들의 공개 댓글 수를 일괄 조회한 후 Map에 담는다.
//         게시글 ID → 댓글 수
//        {
//            30L → 5L,
//            20L → 2L
//        }
        Map<Long, Long> commentCounts = new HashMap<>();
        for (PostCommentCount count : counts) {
            commentCounts.put(
                    count.getPostId(),
                    count.getCommentCount()
            );
        }

        // 좋아요 수와 조회자의 좋아요 여부 가져오기
        // 반환값은 게시글 ID로 좋아요 상태를 찾을 수 있는 Map
        Map<Long, PostLikeState> likeStates =
                postLikeReadService.getStates(postIds, viewerId);

        // 게시글 하나씩 결과 객체로 변환
        List<PostFeedItemResult> items = posts.stream()
                .map(post -> {
                    PostLikeState likeState = likeStates.getOrDefault(
                            post.getId(),
                            PostLikeState.EMPTY // 비로그인 시
                    );

                    return PostFeedItemResult.from(
                            post,
                            commentCounts.getOrDefault(post.getId(), 0L),
                            likeState.likeCount(),
                            likeState.liked()
                    );
                })
                .toList();

        // 피드의 게시글 항목 목록 반환
        // 반환 타입: List<PostFeedItemResult>
        return items;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new PostReadException(
                    PostReadException.Code.INVALID_PAGE_REQUEST
            );
        }

        // JPA가 처리할 수 있는 조회 시작 위치의 범위를 확인한다.
        // 파라미터 인자가 int 로 전달되므로 지나치게 큰 페이지 요청은 미리 거절하도록 설정
        // (long)은 곱셈 중 정수 범위 초과를 방지하기 위한 형 변환
        long offset = (long) page * size;

        if (offset > Integer.MAX_VALUE) {
            throw new PostReadException(
                    PostReadException.Code.INVALID_PAGE_REQUEST
            );
        }
    }

    private void validateFeedSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new PostReadException(
                    PostReadException.Code.INVALID_PAGE_REQUEST
            );
        }
    }

    private void validateCursor(
            @Nullable Instant afterCreatedAt,
            @Nullable Long afterId
    ) {
        // 둘 다 생략하면 첫 조회
        if (afterCreatedAt == null && afterId == null) {
            return;
        }

        // 후속 조회에서는 생성 시각과 양수 ID가 모두 필요
        if (afterCreatedAt == null || afterId == null || afterId <= 0) {
            throw new PostReadException(
                    PostReadException.Code.INVALID_CURSOR
            );
        }
    }
}
