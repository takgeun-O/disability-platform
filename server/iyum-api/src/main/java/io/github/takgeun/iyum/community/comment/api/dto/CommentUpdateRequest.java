package io.github.takgeun.iyum.community.comment.api.dto;

import io.github.takgeun.iyum.community.comment.api.validation.ValidCommentUpdateRequest;
import io.github.takgeun.iyum.community.comment.application.CommentUpdateCommand;
import io.github.takgeun.iyum.community.comment.domain.CommentInputPolicy;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "댓글 수정 요청. 본인이 작성한 댓글만 수정할 수 있습니다."
)
@ValidCommentUpdateRequest
public record CommentUpdateRequest(
        @Schema(
                description = """
                        공백만 있는 내용은 허용하지 않습니다.
                        유니코드 코드 포인트 기준 최대 2,000자이며,
                        앞뒤 공백과 줄바꿈을 보존합니다.
                        """,
                example = "사용 경험을 조금 더 자세하게 수정했어요.",
                minLength = 1,
                maxLength = CommentInputPolicy.CONTENT_MAX_LENGTH,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String content
) {

    public CommentUpdateCommand toCommand() {
        return new CommentUpdateCommand(content);
    }
}
