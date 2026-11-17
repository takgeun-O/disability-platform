package io.github.takgeun.iyum.admin.member.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.member.application.AdminMemberSummaryCountsResult;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminMemberSummaryCountsResponse(

        @Schema(
                description = """
                        전체 기간 기준 현재 회원 수.
                        WITHDRAWN, DELETED 상태를 제외한다.
                        인증 대기·활성·이용 제한·이용 정지 상태를 포함한다.
                        관리자 계정도 포함한다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long currentMemberCount,

        @Schema(
                description = """
                        한국 날짜 기준 오늘 생성된 계정 수.
                        createdAt 기준이며 현재 인증·정지·탈퇴 상태와 무관하다.
                        관리자 계정도 포함한다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long todayMemberCount,

        @Schema(
                description = """
                        전체 기간 기준 현재 PENDING 상태인 계정 수.
                        가입 날짜와 무관하며 관리자 계정도 포함한다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long pendingMemberCount,

        @Schema(
                description = """
                        전체 기간 기준 현재 SUSPENDED 상태인 계정 수.
                        가입 날짜와 무관하며 역할 조건을 적용하지 않는다.
                        과거 정지 이력이나 조치 횟수를 의미하지 않는다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64",
                minimum = "0"
        )
        long suspendedMemberCount
) {

    public static AdminMemberSummaryCountsResponse from(
            AdminMemberSummaryCountsResult result
    ) {
        return new AdminMemberSummaryCountsResponse(
                result.currentMemberCount(),
                result.todayMemberCount(),
                result.pendingMemberCount(),
                result.suspendedMemberCount()
        );
    }
}
