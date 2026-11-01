package io.github.takgeun.iyum.admin.content.api.dto;

import io.github.takgeun.iyum.admin.content.application.AdminCommentAuthorResult;

public record AdminCommentAuthorResponse(
        Long memberId,
        String nickname,
        boolean withdrawn
) {

    public static AdminCommentAuthorResponse from(
            AdminCommentAuthorResult result
    ) {
        return new AdminCommentAuthorResponse(
                result.memberId(),
                result.nickname(),
                result.withdrawn()
        );
    }
}
