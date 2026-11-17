package io.github.takgeun.iyum.admin.operation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationSummaryCountsResult;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminOperationSummaryCountsResponse(

        @Schema(
                description = "전체 기간의 게시글·댓글 숨김·숨김 해제 기록 건수. 대상 수가 아닌 저장된 기록 수.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long totalOperationLogCount,

        @Schema(
                description = "한국 날짜 기준 오늘 생성된 콘텐츠 운영 기록 건수. createdAt 반개구간 기준.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long todayOperationLogCount,

        @Schema(
                description = "오늘 생성된 action=HIDE 기록 건수. 현재 숨김 콘텐츠 수가 아니다.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long todayHideCount,

        @Schema(
                description = "오늘 생성된 action=RESTORE 기록 건수. 현재 콘텐츠 상태와 무관하다.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long todayRestoreCount
) {

    public static AdminOperationSummaryCountsResponse from(
            AdminOperationSummaryCountsResult result
    ) {
        return new AdminOperationSummaryCountsResponse(
                result.totalOperationLogCount(),
                result.todayOperationLogCount(),
                result.todayHideCount(),
                result.todayRestoreCount()
        );
    }
}
