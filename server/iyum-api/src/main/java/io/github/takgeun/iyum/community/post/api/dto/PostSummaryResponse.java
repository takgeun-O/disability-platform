package io.github.takgeun.iyum.community.post.api.dto;

import io.github.takgeun.iyum.community.post.application.PostSummaryResult;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "게시글 목록 항목")
public record PostSummaryResponse(
        Long id,
        PostType type,
        PostTopic topic,
        String title,
        PostAuthorResponse author,
        Instant createdAt,

        @Schema(
                description = "논리 삭제되지 않은 댓글 수",
                example = "3",
                minimum = "0"
        )
        long commentCount
) {

    public static PostSummaryResponse from(
            PostSummaryResult result
    ) {
        return new PostSummaryResponse(
                result.postId(),
                result.type(),
                result.topic(),
                result.title(),
                PostAuthorResponse.from(result.author()),
                result.createdAt(),
                result.commentCount()
        );
    }
}
