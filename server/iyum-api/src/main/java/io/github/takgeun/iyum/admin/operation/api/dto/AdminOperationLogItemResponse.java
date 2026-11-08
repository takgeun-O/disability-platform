package io.github.takgeun.iyum.admin.operation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogItemResult;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogTargetType;
import jakarta.annotation.Nullable;

import java.time.Instant;

// 항목 응답 DTO
public record AdminOperationLogItemResponse(
        @Schema(description = "운영 기록 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "102")
        Long logId,
        @Schema(description = "해당 조치를 실행한 관리자 회원 ID. 콘텐츠 작성자와 다름", requiredMode = Schema.RequiredMode.REQUIRED, example = "7")
        Long actorMemberId,
        @Schema(description = "기록 대상 유형: POST 또는 COMMENT", requiredMode = Schema.RequiredMode.REQUIRED, example = "POST", implementation = String.class, allowableValues = {"POST", "COMMENT"})
        AdminOperationLogTargetType targetType,
        @Schema(description = "게시글 또는 댓글 ID. 댓글 ID는 부모 게시글 ID가 아님", requiredMode = Schema.RequiredMode.REQUIRED, example = "104")
        Long targetId,
        @Schema(description = "과거 조치. 현재 공개 상태가 아님", requiredMode = Schema.RequiredMode.REQUIRED, example = "HIDE")
        ModerationAction action,
        @Schema(description = "저장된 사유 유형. 기존 자유 입력 기록은 LEGACY", requiredMode = Schema.RequiredMode.REQUIRED, example = "SPAM_ADVERTISING")
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
        @Schema(description = "정규화된 상세 사유. 항상 존재하며 상세가 없거나 LEGACY이면 null", requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}, nullable = true)
        String reasonDetail,

        @Schema(description = "저장된 표시 문구. 조회 시 재계산하지 않음", requiredMode = Schema.RequiredMode.REQUIRED, example = "광고·홍보·스팸")
        String reason,
        @Schema(description = "과거 처리 시각. UTC ISO 8601", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-04T03:01:00Z")
        Instant createdAt,

        @Nullable
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "조회 시점의 대상 요약. 과거 스냅샷이 아님. 대상을 찾지 못하면 필드를 생략하지 않고 null", requiredMode = Schema.RequiredMode.REQUIRED, types = {"object", "null"}, schemaResolution = Schema.SchemaResolution.INLINE)
        AdminOperationLogCurrentTargetResponse currentTarget
) {

    public static AdminOperationLogItemResponse from(
            AdminOperationLogItemResult result
    ) {
        AdminOperationLogCurrentTargetResponse currentTarget =
                result.currentTarget() == null
                        ? null
                        : AdminOperationLogCurrentTargetResponse.from(
                        result.currentTarget()
                );

        return new AdminOperationLogItemResponse(
                result.logId(),
                result.actorMemberId(),
                result.targetType(),
                result.targetId(),
                result.action(),
                result.reasonType(),
                result.reasonDetail(),
                result.reason(),
                result.createdAt(),
                currentTarget
        );
    }
}
