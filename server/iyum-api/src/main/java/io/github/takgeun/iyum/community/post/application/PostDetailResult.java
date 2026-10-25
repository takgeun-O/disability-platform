package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;

import java.time.Instant;

// 이 결과 객체에는 Post나 Member 같은 JPA 엔티티가 들어있지 않음.
// 따라서 서비스 호출이 끝난 뒤에도 영속성 컨텍스트 없이 값을 읽을 수 있다.

/**
 * 게시글 상세 조회 결과.
 *
 * 서비스의 읽기 전용 트랜잭션 안에서 Post 엔티티를 이 객체로 변환한다.
 * 작성자 정보도 PostAuthorResult로 변환하여 JPA 엔티티를 포함하지 않는다.
 *
 * 따라서 변환이 완료된 뒤에는 영속성 컨텍스트 없이 값을 읽을 수 있다.
 * 이는 open-in-view=false 환경에서 컨트롤러가 응답을 만들 때
 * 지연 로딩에 의존하지 않도록 하기 위한 설계다.
 *
 * 또한 응답에 필요한 정보만 전달하고,
 * 엔티티 접근과 HTTP 응답 처리의 역할을 분리한다.
 */
public record PostDetailResult(
        Long postId,
        PostType type,
        PostTopic topic,
        String title,
        String content,
        PostAuthorResult author,
        Instant createdAt,
        Instant updatedAt,
        long likeCount,
        boolean liked
) {

    public static PostDetailResult from(
            Post post,
            long likeCount,
            boolean liked
    ) {
        return new PostDetailResult(
                post.getId(),
                post.getType(),
                post.getTopic(),
                post.getTitle(),
                post.getContent(),
                PostAuthorResult.from(post.getAuthor()),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                likeCount,
                liked
        );
    }
}
