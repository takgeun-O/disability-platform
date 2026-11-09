package io.github.takgeun.iyum.admin.content.api.dto;

import io.github.takgeun.iyum.admin.content.application.AdminCommentAuthorResult;
import io.github.takgeun.iyum.admin.content.application.AdminCommentItemResult;
import io.github.takgeun.iyum.admin.content.application.AdminCommentStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;

public record AdminCommentItemResponse(
        Long commentId,
        String content,
        AdminCommentAuthorResponse author,
        AdminCommentStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant contentUpdatedAt,
        @Nullable Instant hiddenAt,
        @Nullable Instant deletedAt
) {

    public static AdminCommentItemResponse from(
            AdminCommentItemResult result
    ) {
        return new AdminCommentItemResponse(
                result.commentId(),
                result.content(),   // 서비스에서 전달한 전체 본문 반환 (내용 확인 후 숨김·복원 여부 판단을 위해)
                AdminCommentAuthorResponse.from(result.author()),
                result.status(),
                result.createdAt(),
                result.updatedAt(),
                result.contentUpdatedAt(),
                result.hiddenAt(),
                result.deletedAt()
        );
    }
}
