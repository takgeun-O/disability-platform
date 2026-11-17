package io.github.takgeun.iyum.admin.dashboard.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardPendingReportResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetType;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 처리 대기 신고 항목 응답 DTO
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminDashboardPendingReportResponse(
        @Schema(
                description = "신고 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64"
        )
        Long reportId,

        @Schema(
                description = "신고 대상 종류",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"POST", "COMMENT"}
        )
        AdminReportTargetType targetType,

        @Schema(
                description = "게시글 ID 또는 댓글 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64"
        )
        Long targetId,

        @Schema(
                description = "신고 접수 사유 유형",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        ContentReportReasonType reasonType,

        @Schema(
                description = "신고 접수 시각. UTC ISO 8601",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "string",
                types = {"string"},
                format = "date-time"
        )
        Instant createdAt
) {

    public static AdminDashboardPendingReportResponse from(
            AdminDashboardPendingReportResult result
    ) {
        return new AdminDashboardPendingReportResponse(
                result.reportId(),
                result.targetType(),
                result.targetId(),
                result.reasonType(),
                result.createdAt()
        );
    }
}