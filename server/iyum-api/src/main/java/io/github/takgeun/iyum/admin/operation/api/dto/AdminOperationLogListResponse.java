package io.github.takgeun.iyum.admin.operation.api.dto;

import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogItemResult;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Spring의 Page 자체를 JSON으로 노출하지 않고,
 * 프론트에서 사용할 응답 필드를 명시한다.
 */
// 목록 응답 DTO
public record AdminOperationLogListResponse(
        List<AdminOperationLogItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
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
