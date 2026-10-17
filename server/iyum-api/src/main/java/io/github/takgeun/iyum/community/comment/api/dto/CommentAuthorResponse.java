package io.github.takgeun.iyum.community.comment.api.dto;

import io.github.takgeun.iyum.community.comment.application.CommentAuthorResult;

// 댓글 작성자 응답
public record CommentAuthorResponse(
        Long memberId,
        String nickname
) {

    public static CommentAuthorResponse from(
            CommentAuthorResult result
    ) {
        return new CommentAuthorResponse(
                result.memberId(),
                result.nickname()
        );
    }
}
