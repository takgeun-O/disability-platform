package io.github.takgeun.iyum.community.comment.api.dto;

import io.github.takgeun.iyum.community.comment.api.validation.ValidCommentCreateRequest;
import io.github.takgeun.iyum.community.comment.application.CommentCreateCommand;
import io.github.takgeun.iyum.community.comment.domain.CommentInputPolicy;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "댓글 작성 요청. 작성자는 로그인 세션에서 결정합니다."
)
@ValidCommentCreateRequest
public record CommentCreateRequest(
        @Schema(
                description = """
                        공백만 있는 내용은 허용하지 않습니다.
                        유니코드 코드 포인트 기준 최대 2,000자이며,
                        앞뒤 공백과 줄바꿈을 보존합니다.
                        """,
                example = "저도 처음에는 적응하는 데 시간이 걸렸어요.",
                minLength = 1,
                maxLength = CommentInputPolicy.CONTENT_MAX_LENGTH,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String content
        // 작성자 ID는 로그인 세션이서,
        // 게시글 ID는 URL에서 받으므로 요청 DTO에 넣지 않는다.
) {

    public CommentCreateCommand toCommand() {
        return new CommentCreateCommand(content);
    }
}
