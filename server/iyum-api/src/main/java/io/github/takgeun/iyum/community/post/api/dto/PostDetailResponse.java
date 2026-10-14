package io.github.takgeun.iyum.community.post.api.dto;

import io.github.takgeun.iyum.community.post.application.PostDetailResult;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// Repository의 엔티티 → 서비스의 Result → 컨트롤러의 Response → JSON
@Schema(description = "게시글 상세 정보")
public record PostDetailResponse(
        Long id,
        PostType type,
        PostTopic topic,
        String title,
        String content,
        PostAuthorResponse author,
        Instant createdAt,
        Instant updatedAt
) {
    public static PostDetailResponse from(
            PostDetailResult result
    ) {
        return new PostDetailResponse(
                result.postId(),
                result.type(),
                result.topic(),
                result.title(),
                result.content(),
                PostAuthorResponse.from(result.author()),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
