package io.github.takgeun.iyum.admin.operation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogTargetAuthorResult;

// 작성자 응답 DTO
// 작성자 식별과 표시에 필요한 필드만 포함
public record AdminOperationLogTargetAuthorResponse(
        @Schema(description = "콘텐츠 작성자 회원 ID. 탈퇴해도 유지", requiredMode = Schema.RequiredMode.REQUIRED, example = "42")
        Long memberId,
        @Schema(description = "현재 작성자 표시 이름. 탈퇴 시 탈퇴한 회원", requiredMode = Schema.RequiredMode.REQUIRED, example = "이음회원")
        String nickname,
        @Schema(description = "작성자 탈퇴 여부", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
        boolean withdrawn
) {

    public static AdminOperationLogTargetAuthorResponse from(
            AdminOperationLogTargetAuthorResult result
    ) {
        return new AdminOperationLogTargetAuthorResponse(
                result.memberId(),
                result.nickname(),
                result.withdrawn()
        );
    }
}
