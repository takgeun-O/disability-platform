package io.github.takgeun.iyum.admin.member.api.dto;

import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationLogResult;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

// 페이지 응답 DTO
@Schema(description = "회원 이용 정지·해제 이력 페이지")
public record AdminMemberModerationLogListResponse(
        @Schema(
                        description = "처리 시각 내림차순, 동일 시각이면 이력 ID 내림차순",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<AdminMemberModerationLogResponse> items,
        @Schema(
                        description = "0부터 시작하는 페이지 번호",
                        type = "integer",
                        types = {"integer"},
                        format = "int32",
                        minimum = "0",
                        example = "0",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int page,
        @Schema(
                        description = "페이지 크기",
                        type = "integer",
                        types = {"integer"},
                        format = "int32",
                        minimum = "1",
                        maximum = "50",
                        example = "20",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int size,
        @Schema(
                        description = "해당 회원의 전체 이력 개수",
                        type = "integer",
                        types = {"integer"},
                        format = "int64",
                        minimum = "0",
                        example = "2",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long totalElements,
        @Schema(
                        description = "전체 페이지 수",
                        type = "integer",
                        types = {"integer"},
                        format = "int32",
                        minimum = "0",
                        example = "1",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int totalPages,
        @Schema(description = "다음 페이지 존재 여부", requiredMode = Schema.RequiredMode.REQUIRED)
                boolean hasNext,
        @Schema(description = "이전 페이지 존재 여부", requiredMode = Schema.RequiredMode.REQUIRED)
                boolean hasPrevious) {

    public static AdminMemberModerationLogListResponse from(
            Page<AdminMemberModerationLogResult> result) {
        List<AdminMemberModerationLogResponse> items =
                result.getContent().stream().map(AdminMemberModerationLogResponse::from).toList();

        return new AdminMemberModerationLogListResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                result.hasPrevious());
    }
}
