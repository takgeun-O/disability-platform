package io.github.takgeun.iyum.admin.operation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogItemResult;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Spring의 Page 자체를 JSON으로 노출하지 않고,
 * 프론트에서 사용할 응답 필드를 명시한다.
 */
// 목록 응답 DTO
public record AdminOperationLogListResponse(
        @Schema(description = "서버 정렬 순서의 운영 기록. 빈 목록 가능", requiredMode = Schema.RequiredMode.REQUIRED)
        List<AdminOperationLogItemResponse> items,
        @Schema(description = "0부터 시작하는 요청 페이지 번호", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
        int page,
        @Schema(description = "페이지 크기: 1~50, 기본 20", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
        int size,
        @Schema(description = "전체 조건에 맞는 운영 기록 개수. 요약 누락과 무관", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
        long totalElements,
        @Schema(description = "전체 페이지 수. 빈 결과는 0", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        int totalPages,
        @Schema(description = "다음 페이지 존재 여부", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
        boolean hasNext,
        @Schema(description = "이전 페이지 존재 여부", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
        boolean hasPrevious
) {

    public static AdminOperationLogListResponse from(
            Page<AdminOperationLogItemResult> result
    ) {
        List<AdminOperationLogItemResponse> items =
                result.getContent()
                        .stream()
                        .map(AdminOperationLogItemResponse::from)
                        .toList();

        return new AdminOperationLogListResponse(
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
