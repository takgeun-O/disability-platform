package io.github.takgeun.iyum.admin.report.api.dto;

import io.github.takgeun.iyum.admin.report.application.AdminReportHandlingResult;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "관리자 신고 처리 결과")
public record AdminReportHandlingResponse(

        @Schema(
                description = "신고 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                example = "100"
        )
        Long reportId,

        @Schema(
                description = "처리 후 신고 상태",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"RESOLVED", "DISMISSED"},
                example = "RESOLVED"
        )
        ContentReportStatus status,

        @Schema(
                description = "처리한 관리자 회원 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                example = "40"
        )
        Long handledByMemberId,

        @Schema(
                description = "처리 시각. UTC ISO 8601",
                requiredMode = Schema.RequiredMode.REQUIRED,
                format = "date-time",
                example = "2026-11-13T01:00:00Z"
        )
        Instant handledAt,

        @Schema(
                description = "정규화하여 저장한 처리 사유",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "검토 결과 필요한 운영 조치를 완료했습니다."
        )
        String handlingReason

) {

    public static AdminReportHandlingResponse from(
            AdminReportHandlingResult result
    ) {
        return new AdminReportHandlingResponse(
                result.reportId(),
                result.status(),
                result.handledByMemberId(),
                result.handledAt(),
                result.handlingReason()
        );
    }
}