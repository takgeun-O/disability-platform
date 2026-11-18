package io.github.takgeun.iyum.community.post.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.community.post.application.PostSummaryResult;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

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
        long commentCount,

        @Schema(
                description = "게시글 좋아요 수",
                example = "5",
                minimum = "0"
        )
        long likeCount,

        @Schema(
                description = "현재 로그인한 회원의 좋아요 여부. 비로그인은 false",
                example = "true"
        )
        boolean liked,

        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(
                description = """
                본문에서 검색어가 처음 일치한 부분과 앞뒤 최대 40코드포인트씩의 원문.
                생략된 쪽에는 말줄임표(…)를 붙인다.
                검색어가 없거나 본문에 일치하는 부분이 없으면 null이다.
                """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"}
        )
        @Nullable String matchedContentExcerpt
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
                result.commentCount(),
                result.likeCount(),
                result.liked(),
                result.matchedContentExcerpt()
        );
    }
}
