package io.github.takgeun.iyum.admin.member.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.member.application.AdminMemberSummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminMemberSummaryResponse(

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
                        오늘 가입 수 집계의 기준 날짜.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "string",
                types = {"string"},
                format = "date"
        )
        LocalDate baseDate,

        @Schema(
                description = """
                        목록 검색·필터·정렬·페이지 조건과 무관한 회원 요약 수치.
                        관리자 계정을 포함하며 집계 대상이 없으면 해당 값은 0이다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        AdminMemberSummaryCountsResponse counts
) {

    public static AdminMemberSummaryResponse from(
            AdminMemberSummaryResult result
    ) {
        return new AdminMemberSummaryResponse(
                result.queriedAt(),
                result.baseDate(),
                AdminMemberSummaryCountsResponse.from(
                        result.counts()
                )
        );
    }
}
