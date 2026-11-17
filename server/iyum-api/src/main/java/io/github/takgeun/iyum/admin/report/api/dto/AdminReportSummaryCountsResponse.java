package io.github.takgeun.iyum.admin.report.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.report.application.AdminReportSummaryCountsResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 집계 수치 응답 DTO
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminReportSummaryCountsResponse(
        @Schema(
                description = """
                        현재 RECEIVED 상태인 전체 신고 수.
                        접수 날짜와 무관하다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long pendingReportCount,

        @Schema(
                description = """
                        한국 날짜 기준 오늘 접수된 신고 수.
                        createdAt 기준이며 현재 신고 상태와 무관하다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long todayReportCount,

        @Schema(
                description = """
                        한국 날짜 기준 오늘 종결된 신고 수.
                        handledAt 기준으로 RESOLVED와 DISMISSED를 포함한다.
                        접수 날짜와 무관하다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long todayHandledReportCount
) {

    public static AdminReportSummaryCountsResponse from(
            AdminReportSummaryCountsResult result
    ) {
        return new AdminReportSummaryCountsResponse(
                result.pendingReportCount(),
                result.todayReportCount(),
                result.todayHandledReportCount()
        );
    }
}
