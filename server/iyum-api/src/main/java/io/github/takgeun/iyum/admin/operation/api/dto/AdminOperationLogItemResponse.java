package io.github.takgeun.iyum.admin.operation.api.dto;

import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogItemResult;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogTargetType;

import java.time.Instant;

// 항목 응답 DTO
public record AdminOperationLogItemResponse(
        Long logId,
        Long actorMemberId,
        AdminOperationLogTargetType targetType,
        Long targetId,
        ModerationAction action,
        String reason,
        Instant createdAt
) {

    public static AdminOperationLogItemResponse from(
            AdminOperationLogItemResult result
    ) {
        return new AdminOperationLogItemResponse(
                result.logId(),
                result.actorMemberId(),
                result.targetType(),
                result.targetId(),
                result.action(),
                result.reason(),
                result.createdAt()
        );
    }
}
