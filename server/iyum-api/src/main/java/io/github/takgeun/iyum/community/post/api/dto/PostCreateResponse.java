package io.github.takgeun.iyum.community.post.api.dto;

import io.github.takgeun.iyum.community.post.application.PostCreateResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "게시글 작성 성공 응답")
public record PostCreateResponse(

        // 프론트엔드는 이 ID로 상세 화면에 이동할 수 있게 된다.
        @Schema(
                description = "생성된 게시글 ID",
                example = "123",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Long id
) {

        public static PostCreateResponse from(
                PostCreateResult result
        ) {
                return new PostCreateResponse(result.postId());
        }
}
