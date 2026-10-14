package io.github.takgeun.iyum.community.post.api.dto;

import io.github.takgeun.iyum.community.post.application.PostAuthorResult;
import io.swagger.v3.oas.annotations.media.Schema;

// 작성자 응답
@Schema(description = "게시글 작성자 공개 정보")
public record PostAuthorResponse(
        Long memberId,
        String nickname
) {

    public static PostAuthorResponse from(
            PostAuthorResult result
    ) {
        return new PostAuthorResponse(
                result.memberId(),
                result.nickname()
        );
    }
}
