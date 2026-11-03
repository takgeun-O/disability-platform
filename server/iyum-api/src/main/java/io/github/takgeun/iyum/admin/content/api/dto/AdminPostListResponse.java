package io.github.takgeun.iyum.admin.content.api.dto;

import io.github.takgeun.iyum.admin.content.application.AdminPostItemResult;
import org.springframework.data.domain.Page;

import java.util.List;

// 목록 전체 응답 DTO
// 서비스 내부에서는 Page를 사용하지만 HTTP 응답에는 미리 정한 필드만 제공하기로 함.
// 프론트는 items와 페이지 정보만 사용하면 된다.
// 검색 결과가 없으면 정상적인 200 응답으로 빈 items와 totalElements: 0 반환
public record AdminPostListResponse(
        List<AdminPostItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {

    public static AdminPostListResponse from(
            Page<AdminPostItemResult> result
    ) {
        List<AdminPostItemResponse> items = result.getContent()
                .stream()
                .map(AdminPostItemResponse::from)
                .toList();

        return new AdminPostListResponse(
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
