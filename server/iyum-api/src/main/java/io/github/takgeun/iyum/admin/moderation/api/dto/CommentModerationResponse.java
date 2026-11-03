package io.github.takgeun.iyum.admin.moderation.api.dto;

import io.github.takgeun.iyum.admin.moderation.application.CommentModerationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.time.Instant;

public record CommentModerationResponse(
        Long postId,
        Long commentId,

        @Schema(
                description = """
                        댓글 자체의 숨김 여부.
                        부모 게시글의 숨김 상태를 포함한
                        최종 공개 여부를 의미하지 않습니다.
                        """
        )
        boolean hidden,

        @Nullable Instant hiddenAt,
        boolean changed
) {
    public static CommentModerationResponse from(
            CommentModerationResult result
    ) {
        return new CommentModerationResponse(
                result.postId(),
                result.commentId(),
                result.hidden(),
                result.hiddenAt(),
                result.changed()
        );
    }
}
