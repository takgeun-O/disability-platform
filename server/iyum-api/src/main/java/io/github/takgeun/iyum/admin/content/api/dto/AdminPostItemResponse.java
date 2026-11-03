package io.github.takgeun.iyum.admin.content.api.dto;

import io.github.takgeun.iyum.admin.content.application.AdminPostItemResult;
import io.github.takgeun.iyum.admin.content.application.AdminPostStatus;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import jakarta.annotation.Nullable;

import java.time.Instant;

// 목록 항목 응답 DTO
public record AdminPostItemResponse(
        Long postId,
        PostType type,
        PostTopic topic,
        String title,
        String contentPreview,
        AdminPostAuthorResponse author,
        AdminPostStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant hiddenAt,
        @Nullable Instant deletedAt
) {

    public static AdminPostItemResponse from(
            AdminPostItemResult result
    ) {
        return new AdminPostItemResponse(
                result.postId(),
                result.type(),
                result.topic(),
                result.title(),
                result.contentPreview(),
                AdminPostAuthorResponse.from(result.author()),
                result.status(),
                result.createdAt(),
                result.updatedAt(),
                result.hiddenAt(),
                result.deletedAt()
        );
    }
}
