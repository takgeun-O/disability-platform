package io.github.takgeun.iyum.admin.content.api.dto;

import io.github.takgeun.iyum.admin.content.application.AdminPostAuthorResult;

// 작성자 응답 DTO
public record AdminPostAuthorResponse(
        Long memberId,
        String nickname,
        boolean withdrawn
) {

    public static AdminPostAuthorResponse from(
            AdminPostAuthorResult result
    ) {
        return new AdminPostAuthorResponse(
                result.memberId(),
                result.nickname(),
                result.withdrawn()
        );
    }
}
