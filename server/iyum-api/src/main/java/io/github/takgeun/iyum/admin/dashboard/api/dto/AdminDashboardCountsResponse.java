package io.github.takgeun.iyum.admin.dashboard.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardCountsResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 요약 수치 응답 DTO
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminDashboardCountsResponse(
        @Schema(
                description = "현재 RECEIVED 상태인 전체 신고 수. 접수 날짜와 무관",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long pendingReportCount,

        @Schema(
                description = "한국 날짜 기준 오늘 접수된 신고 수. 현재 처리 상태와 무관",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long todayReportCount,

        @Schema(
                description = "한국 날짜 기준 오늘 작성된 게시글 수. 숨김·삭제 여부와 무관",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long todayPostCount,

        @Schema(
                description = "한국 날짜 기준 오늘 생성된 회원 계정 수. 인증·정지·탈퇴 상태와 무관",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long todayMemberCount
) {

    public static AdminDashboardCountsResponse from(
            AdminDashboardCountsResult result
    ) {
        return new AdminDashboardCountsResponse(
                result.pendingReportCount(),
                result.todayReportCount(),
                result.todayPostCount(),
                result.todayMemberCount()
        );
    }
}