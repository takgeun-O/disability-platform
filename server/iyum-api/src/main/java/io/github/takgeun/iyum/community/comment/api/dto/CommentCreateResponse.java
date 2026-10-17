package io.github.takgeun.iyum.community.comment.api.dto;

import io.github.takgeun.iyum.community.comment.application.CommentCreateResult;
import io.swagger.v3.oas.annotations.media.Schema;

// 댓글 작성 응답 DTO
@Schema(description = "댓글 작성 결과")
public record CommentCreateResponse(

        @Schema(
                description = "생성된 댓글 ID",
                example = "123"
        )
        Long commentId
) {

    public static CommentCreateResponse from(
            CommentCreateResult result
    ) {
        return new CommentCreateResponse(result.commentId());
    }
}
