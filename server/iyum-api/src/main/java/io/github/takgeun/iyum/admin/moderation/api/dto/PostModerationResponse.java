package io.github.takgeun.iyum.admin.moderation.api.dto;

import io.github.takgeun.iyum.admin.moderation.application.PostModerationResult;
import jakarta.annotation.Nullable;

import java.time.Instant;

public record PostModerationResponse(
        Long postId,
        boolean hidden,
        @Nullable Instant hiddenAt,
        boolean changed
) {

    public static PostModerationResponse from(
            PostModerationResult result
    ) {
        return new PostModerationResponse(
                result.postId(),
                result.hidden(),
                result.hiddenAt(),
                result.changed()
        );
    }
}
