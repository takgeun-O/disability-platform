package io.github.takgeun.iyum.admin.report.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.report.application.AdminReportSummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 전체 응답 DTO
 */
// 프론트측에서 응답에 어떤 필드가 들어오는지 일관되게 유지하기 위함
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminReportSummaryResponse(
        @Schema(
                description = """
                        조회 기준 시각. UTC ISO 8601.
                        집계 날짜 계산의 기준이며,
                        모든 조회가 동일한 DB 스냅샷임을 보장하지 않는다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "string",
                types = {"string"},
                format = "date-time"
        )
        Instant queriedAt,

        @Schema(
                description = """
                        queriedAt의 Asia/Seoul 기준 날짜.
                        오늘 접수와 오늘 종결 집계의 기준 날짜.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "string",
                types = {"string"},
                format = "date"
        )
        LocalDate baseDate,

        @Schema(
                description = """
                        목록 필터와 무관한 전체 신고 요약 수치.
                        집계 대상이 없으면 각 값은 0이다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        AdminReportSummaryCountsResponse counts
) {

    public static AdminReportSummaryResponse from(
            AdminReportSummaryResult result
    ) {
        return new AdminReportSummaryResponse(
                result.queriedAt(),
                result.baseDate(),
                AdminReportSummaryCountsResponse.from(
                        result.counts()
                )
        );
    }
}