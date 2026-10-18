package io.github.takgeun.iyum.community.post.api.dto;

import io.github.takgeun.iyum.community.post.application.PostCursor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "다음 게시글 피드 조회에 사용할 커서")
public record PostCursorResponse(

        @Schema(
                description = """
                        마지막으로 반환한 게시글의 생성 시각.
                        다음 요청의 afterCreatedAt에 문자열 그대로 전달합니다.
                        """,
                example = "2026-10-18T02:00:00.123456Z"
        )
        Instant createdAt,

        @Schema(
                description = """
                        마지막으로 반환한 게시글 ID.
                        다음 요청의 afterId에 전달합니다.
                        """,
                example = "104"
        )
        Long postId
) {

    public static PostCursorResponse from(PostCursor cursor) {
        return new PostCursorResponse(
                cursor.createdAt(),
                cursor.postId()
        );
    }
}
