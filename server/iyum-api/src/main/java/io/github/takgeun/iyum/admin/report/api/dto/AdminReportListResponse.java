package io.github.takgeun.iyum.admin.report.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.takgeun.iyum.admin.report.application.AdminReportItemResult;
import org.springframework.data.domain.Page;

import java.util.List;

public record AdminReportListResponse(
        @Schema(
                description = "신고 목록. 대상별 집계가 아님",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<AdminReportItemResponse> items,
        @Schema(
                description = "0부터 시작하는 현재 페이지",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int32"
        )
        int page,
        @Schema(
                description = "페이지 크기",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int32"
        )
        int size,
        @Schema(
                description = "동일 검색 조건의 전체 신고 수. 범위 밖에서도 유지",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64"
        )
        long totalElements,
        @Schema(
                description = "전체 페이지 수",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int32"
        )
        int totalPages,
        @Schema(
                description = "다음 페이지 존재 여부",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean hasNext,
        @Schema(
                description = "이전 페이지 존재 여부",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean hasPrevious
) {

    public static AdminReportListResponse from(
            Page<AdminReportItemResult> result
    ) {
        List<AdminReportItemResponse> items = result.getContent()
                .stream()
                .map(AdminReportItemResponse::from)
                .toList();

        return new AdminReportListResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                result.hasPrevious()
        );
    }
}
