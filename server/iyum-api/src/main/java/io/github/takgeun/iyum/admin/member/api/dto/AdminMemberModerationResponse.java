package io.github.takgeun.iyum.admin.member.api.dto;

import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationResult;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** { "memberId": 20, "status": "SUSPENDED", "changed": true } */
public record AdminMemberModerationResponse(
        @Schema(
                        description = "조치 대상 회원 ID",
                        type = "integer",
                        types = {"integer"},
                        format = "int64",
                        minimum = "1",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "20")
                Long memberId,
        @Schema(
                        description = "이번 요청 처리 후의 회원 상태",
                        allowableValues = {"ACTIVE", "SUSPENDED"},
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "SUSPENDED")
                MemberStatus status,
        @Schema(
                        description =
                                """
                                이번 요청에서 실제 상태가 변경되었는지 여부.
                                이미 요청한 상태이면 false이며
                                인증 버전과 조치 이력을 추가 변경하지 않습니다.
                                """,
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "true")
                boolean changed) {

    public static AdminMemberModerationResponse from(AdminMemberModerationResult result) {
        return new AdminMemberModerationResponse(
                result.memberId(), result.status(), result.changed());
    }
}
