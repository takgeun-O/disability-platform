package io.github.takgeun.iyum.admin.member.api.dto;

import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationLogResult;
import io.github.takgeun.iyum.admin.member.domain.MemberModerationAction;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// 이력 항목 응답 DTO
@Schema(description = "회원 이용 정지·해제 이력")
public record AdminMemberModerationLogResponse(
        @Schema(
                        description = "운영 조치 이력 ID",
                        type = "integer",
                        types = {"integer"},
                        format = "int64",
                        example = "101",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long logId,
        @Schema(
                        description = "조치를 실행한 관리자 회원 ID",
                        type = "integer",
                        types = {"integer"},
                        format = "int64",
                        example = "40",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long actorMemberId,
        @Schema(
                        description = "조치 대상 회원 ID",
                        type = "integer",
                        types = {"integer"},
                        format = "int64",
                        example = "20",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long targetMemberId,
        @Schema(
                        description = "SUSPEND: 이용 정지, LIFT_SUSPENSION: 정지 해제",
                        allowableValues = {"SUSPEND", "LIFT_SUSPENSION"},
                        example = "SUSPEND",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                MemberModerationAction action,
        @Schema(
                        description = "조치 직전 회원 상태. 현재 상태가 아닌 저장된 이력 값",
                        allowableValues = {"ACTIVE", "SUSPENDED"},
                        example = "ACTIVE",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                MemberStatus beforeStatus,
        @Schema(
                        description = "조치 직후 회원 상태. 현재 상태가 아닌 저장된 이력 값",
                        allowableValues = {"ACTIVE", "SUSPENDED"},
                        example = "SUSPENDED",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                MemberStatus afterStatus,
        @Schema(
                        description = "조치 당시 저장된 처리 사유",
                        example = "반복적인 이용 규칙 위반",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String reason,
        @Schema(
                        description = "처리 시각. UTC ISO 8601",
                        type = "string",
                        format = "date-time",
                        example = "2026-11-12T00:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Instant createdAt) {

    public static AdminMemberModerationLogResponse from(AdminMemberModerationLogResult result) {
        return new AdminMemberModerationLogResponse(
                result.logId(),
                result.actorMemberId(),
                result.targetMemberId(),
                result.action(),
                result.beforeStatus(),
                result.afterStatus(),
                result.reason(),
                result.createdAt());
    }
}
