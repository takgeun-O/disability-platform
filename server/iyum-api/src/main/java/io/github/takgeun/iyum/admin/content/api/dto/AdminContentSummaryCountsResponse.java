package io.github.takgeun.iyum.admin.content.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.content.application.AdminContentSummaryCountsResult;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminContentSummaryCountsResponse(
        @Schema(
                description = """
                        한국 날짜 기준 오늘 작성된 게시글 수.
                        createdAt 기준이며 현재 숨김·삭제 여부와 무관하다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long todayPostCount,

        @Schema(
                description = """
                        한국 날짜 기준 오늘 작성된 댓글 수.
                        createdAt 기준이며 댓글과 부모 게시글의
                        현재 숨김·삭제 여부와 무관하다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long todayCommentCount,

        @Schema(
                description = """
                        전체 기간 기준 현재 숨겨진 게시글 수.
                        hiddenAt이 있고 deletedAt이 없는 게시글만 포함한다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long hiddenPostCount,

        @Schema(
                description = """
                        전체 기간 기준 현재 숨겨진 댓글 수.
                        댓글 자체의 hiddenAt이 있고 deletedAt이 없는 댓글만 포함한다.
                        부모 게시글의 숨김·삭제 상태는 반영하지 않는다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long hiddenCommentCount
) {

    public static AdminContentSummaryCountsResponse from(
            AdminContentSummaryCountsResult result
    ) {
        return new AdminContentSummaryCountsResponse(
                result.todayPostCount(),
                result.todayCommentCount(),
                result.hiddenPostCount(),
                result.hiddenCommentCount()
        );
    }
}
