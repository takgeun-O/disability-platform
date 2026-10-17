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
}
