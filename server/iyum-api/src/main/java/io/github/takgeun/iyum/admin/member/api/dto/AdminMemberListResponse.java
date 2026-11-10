package io.github.takgeun.iyum.admin.member.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.takgeun.iyum.admin.member.application.AdminMemberItemResult;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 목록 전체 응답 DTO
 *
 * 서비스가 반환한 Page에서 회원 목록과 페이지 정보를 꺼내서
 * API 응답 DTO에 옮겨담는다.
 */
@Schema(description = "서버 정렬 적용 후의 회원 페이지")
public record AdminMemberListResponse(
        @Schema(description = "현재 페이지 회원 목록. 없으면 빈 배열", requiredMode = Schema.RequiredMode.REQUIRED)
        List<AdminMemberItemResponse> items,
        @Schema(description = "0부터 시작하는 요청 페이지 번호", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
        int page,
        @Schema(description = "요청 페이지 크기 (1~50)", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
        int size,
        @Schema(description = "전체 조건에 맞는 회원 수. 범위 밖 페이지에서도 유지", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        long totalElements,
        @Schema(description = "전체 페이지 수. 결과가 없으면 0", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        int totalPages,
        @Schema(description = "다음 페이지 존재 여부", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
        boolean hasNext,
        @Schema(description = "이전 페이지 여부. 요청 page가 0보다 크면 true", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
        boolean hasPrevious
) {

    public static AdminMemberListResponse from(
            // 컨트롤러가 result를 받는 시점에 result에는
            // 현재 페이지의 회원 데이터와 페이지 정보가 함께 들어있음.
            // result.getContent();       // 현재 페이지의 회원 20명
            // result.getNumber();        // 1: 현재 페이지 번호
            // result.getSize();          // 20: 요청한 페이지 크기
            // result.getTotalElements(); // 45: 조건에 맞는 전체 회원 수
            // result.getTotalPages();    // 3: 전체 페이지 수
            // result.hasNext();          // true: 세 번째 페이지가 있음
            // result.hasPrevious();      // true: 첫 번째 페이지가 있음
            Page<AdminMemberItemResult> result
    ) {
        List<AdminMemberItemResponse> items = result.getContent()
                .stream()
                .map(AdminMemberItemResponse::from)
                .toList();

        return new AdminMemberListResponse(
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
