package io.github.takgeun.iyum.admin.dashboard.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 전체 응답 DTO
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminDashboardResponse(
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
                description = "queriedAt의 Asia/Seoul 기준 날짜. 오늘 집계의 기준 날짜",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "string",
                types = {"string"},
                format = "date"
        )
        LocalDate baseDate,

        @Schema(
                description = "대시보드 요약 수치. 집계 대상이 없으면 각 값은 0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        AdminDashboardCountsResponse counts,

        @Schema(
                description = """
                        RECEIVED 신고 중 접수 시각과 신고 ID 오름차순으로 최대 5건.
                        대상 콘텐츠의 숨김·삭제 여부와 무관하다.
                        동일 대상의 여러 신고도 각각 포함한다.
                        처리 대기 신고가 없으면 빈 배열.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<AdminDashboardPendingReportResponse> pendingReports
) {

    public AdminDashboardResponse {
        pendingReports = List.copyOf(pendingReports);
    }

    public static AdminDashboardResponse from(
            AdminDashboardResult result
    ) {
        List<AdminDashboardPendingReportResponse> pendingReports =
                result.pendingReports()
                        .stream()
                        .map(AdminDashboardPendingReportResponse::from)
                        .toList();

        return new AdminDashboardResponse(
                result.queriedAt(),
                result.baseDate(),
                AdminDashboardCountsResponse.from(result.counts()),
                pendingReports
        );
    }
}