package io.github.takgeun.iyum.admin.content.api.dto;

import io.github.takgeun.iyum.admin.content.application.AdminPostDetailResult;
import io.github.takgeun.iyum.admin.content.application.AdminPostStatus;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import jakarta.annotation.Nullable;

import java.time.Instant;

// 상세 응답 DTO
public record AdminPostDetailResponse(
        Long postId,
        PostType type,
        PostTopic topic,
        String title,
        String content,
        AdminPostAuthorResponse author,
        AdminPostStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant hiddenAt,
        @Nullable Instant deletedAt
) {

    public static AdminPostDetailResponse from(
            AdminPostDetailResult result
    ) {
        return new AdminPostDetailResponse(
                result.postId(),
                result.type(),
                result.topic(),
                result.title(),
                result.content(),
                AdminPostAuthorResponse.from(result.author()),
                result.status(),
                result.createdAt(),
                result.updatedAt(),
                result.hiddenAt(),
                result.deletedAt()
        );
    }
}