package io.github.takgeun.iyum.community.post.application;

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

        // 페이지 안의 각 Post를 PostSummaryResult로 변환
        return posts.map(PostSummaryResult::from);
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
