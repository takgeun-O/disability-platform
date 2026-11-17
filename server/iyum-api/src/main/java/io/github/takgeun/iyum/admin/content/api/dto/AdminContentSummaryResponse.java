package io.github.takgeun.iyum.admin.content.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.content.application.AdminContentSummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 전체 응답
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminContentSummaryResponse(
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
                        오늘 작성 게시글·댓글 집계의 기준 날짜.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "string",
                types = {"string"},
                format = "date"
        )
        LocalDate baseDate,

        @Schema(
                description = """
                        목록 필터와 무관한 전체 게시글·댓글 요약 수치.
                        집계 대상이 없으면 각 값은 0이다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        AdminContentSummaryCountsResponse counts
) {

    public static AdminContentSummaryResponse from(
            AdminContentSummaryResult result
    ) {
        return new AdminContentSummaryResponse(
                result.queriedAt(),
                result.baseDate(),
                AdminContentSummaryCountsResponse.from(
                        result.counts()
                )
        );
    }
}