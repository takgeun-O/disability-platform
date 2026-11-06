package io.github.takgeun.iyum.admin.operation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
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
        ModerationReasonType reasonType,

        // 상세가 없어도 필드를 생략하지 않고 null로 응답한다.
        // @JsonInclude: Java 객체를 JSON 응답으로 바꿀 때, 해당 필드를
        // 포함할지 결정하는 애노테이션
        // reasonDetail값이 null이어도 JSON에서 이 필드를 생략하지 말라.
        // 이렇게 구현한 의도는 프론트상에서 후처리할 수도 있지만,
        // 핵심 목적은 응답 구조를 일정하게 유지하고,
        // "상세 사유가 없다"는 것을 명확히 전달하는 것
        // 참고로 프론트에서는 reasonDetail값이 null이면 해당 영역에 값을 표시하지 않게 하는 등 활용이 가능하다.
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String reasonDetail,

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
                result.reasonType(),
                result.reasonDetail(),
                result.reason(),
                result.createdAt()
        );
    }
}
