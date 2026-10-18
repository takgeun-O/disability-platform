package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.comment.infrastructure.PostCommentCount;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Null;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Page<PostSummaryResult> getPosts(
            @Nullable PostType type,
            @Nullable PostTopic topic,
            int page,
            int size
    ) {
        validatePage(page, size);

        PageRequest pageable = PageRequest.of(page, size);

        Page<Post> posts = postRepository.findVisiblePosts(
                type,
                topic,
                pageable
        );

        // 현재 페이지에 게시글이 없으면 댓글 집계 쿼리 실행 X
        // 원래 Page의 전체 개수와 페이지 정보는 그대로 유지
        if(!posts.hasContent()) {
            return posts.map(
                    post -> PostSummaryResult.from(post, 0L)
            );
        }

        // 현재 페이지에 포함된 게시글 ID만 추출한다.
        List<Long> postIds = posts.getContent()
                .stream()
                .map(Post::getId)
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

        // 댓글이 없는 게시글은 집계 결과에 없으므로 0으로 채운다.
        return posts.map(post -> PostSummaryResult.from(
                post,
                commentCounts.getOrDefault(post.getId(), 0L)
        ));
    }

    public PostDetailResult getPost(Long postId) {
        if(postId == null || postId <= 0) {
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

        return PostDetailResult.from(post);
    }

    // 게시글을 일정 개수씩 조회해서 댓글 수와 조회 위치까지 묶어 반환하는 서비스 메소드
    public PostFeedResult getFeed(
            @Nullable PostType type,
            @Nullable PostTopic topic,
            @Nullable Instant afterCreatedAt,
            @Nullable Long afterId,
            int size    // 이번 요청에서 사용자에게 최대 몇 개의 게시글을 반환할 것인가?
    ) {
        validateFeedSize(size);
        validateCursor(afterCreatedAt, afterId);

        // 커서 조건으로 조회 범위 정하고
        // 그 범위에서 size + 1개만 가져오기
        PageRequest limit = PageRequest.of(0, size + 1);

        List<Post> fetched;

        if(afterCreatedAt == null && afterId == null) {
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
        if(returnedPosts.isEmpty()) {
            return new PostFeedResult(
                    List.of(),
                    false,
                    null
            );
        }

        // 실제 반환할 게시글의 댓글 수만 집계된다.
        List<Long> postIds = returnedPosts.stream()
                .map(Post::getId)
                .toList();

        List<PostCommentCount> counts =
                commentRepository.countVisibleCommentsByPostIds(postIds);

        Map<Long, Long> commentCounts = new HashMap<>();

        for (PostCommentCount count : counts) {
            commentCounts.put(
                    count.getPostId(),
                    count.getCommentCount()
            );
        }

        List<PostFeedItemResult> items = returnedPosts.stream()
                .map(post -> PostFeedItemResult.from(
                        post,
                        commentCounts.getOrDefault(post.getId(), 0L)
                ))
                .toList();

        PostCursor nextCursor = null;

        if(hasNext) {
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

    private void validatePage(int page, int size) {
        if(page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new PostReadException(
                    PostReadException.Code.INVALID_PAGE_REQUEST
            );
        }

        // JPA가 처리할 수 있는 조회 시작 위치의 범위를 확인한다.
        // 파라미터 인자가 int 로 전달되므로 지나치게 큰 페이지 요청은 미리 거절하도록 설정
        // (long)은 곱셈 중 정수 범위 초과를 방지하기 위한 형 변환
        long offset = (long) page * size;

        if(offset > Integer.MAX_VALUE) {
            throw new PostReadException(
                    PostReadException.Code.INVALID_PAGE_REQUEST
            );
        }
    }

    private void validateFeedSize(int size) {
        if(size < 1 || size > MAX_PAGE_SIZE) {
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
        if(afterCreatedAt == null && afterId == null) {
            return;
        }

        // 후속 조회에서는 생성 시각과 양수 ID가 모두 필요
        if(afterCreatedAt == null || afterId == null || afterId <= 0) {
            throw new PostReadException(
                    PostReadException.Code.INVALID_CURSOR
            );
        }
    }
}
