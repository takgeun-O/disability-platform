package io.github.takgeun.iyum.admin.report.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.takgeun.iyum.admin.report.application.AdminReportTargetAuthorResult;

/**
 * 신고 대상 콘텐츠의 현재 작성자 표시 정보.
 * 신고자나 처리 관리자 정보가 아니다.
 */
public record AdminReportTargetAuthorResponse(
        @Schema(
                description = "대상 콘텐츠 작성자 회원 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64"
        )
        Long memberId,
        @Schema(
                description = "현재 닉네임. DELETED 회원은 탈퇴한 회원",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String nickname,
        @Schema(
                description = "작성자 상태가 DELETED인 경우 true",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean withdrawn
) {

    public static AdminReportTargetAuthorResponse from(
            AdminReportTargetAuthorResult result
    ) {
        return new AdminReportTargetAuthorResponse(
                result.memberId(),
                result.nickname(),
                result.withdrawn()
        );
    }
}
