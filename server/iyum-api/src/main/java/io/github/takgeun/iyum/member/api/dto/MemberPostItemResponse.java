package io.github.takgeun.iyum.member.api.dto;

import io.github.takgeun.iyum.community.post.api.dto.PostAuthorResponse;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.application.MemberContentVisibility;
import io.github.takgeun.iyum.member.application.MemberPostItemResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.time.Instant;

@Schema(
        description = """
                내 게시글 항목.
                숨김 항목은 ID, 작성 시각, 표시 상태를 제공하며
                내용과 집계값은 null입니다.
                """
)
public record MemberPostItemResponse(
        Long id,
        @Nullable PostType type,
        @Nullable PostTopic topic,
        @Nullable String title,
        @Nullable String contentPreview,
        @Nullable PostAuthorResponse author,
        Instant createdAt,
        @Nullable Long commentCount,
        @Nullable Long likeCount,
        @Nullable Boolean liked,

        @Schema(
                description = "게시글 표시 상태",
                allowableValues = {"VISIBLE", "HIDDEN"}
        )
        MemberContentVisibility visibility
) {

    public static MemberPostItemResponse from(
            MemberPostItemResult result
    ) {
        return new MemberPostItemResponse(
                result.postId(),
                result.type(),
                result.topic(),
                result.title(),
                result.contentPreview(),
                // 숨김 항목에서는 author도 null이므로,
                // PostAuthorResponse.from() 호출 전에 아래와 같이 확인이 필요함
                result.author() == null
                        ? null
                        : PostAuthorResponse.from(result.author()),
                result.createdAt(),
                result.commentCount(),
                result.likeCount(),
                result.liked(),
                result.visibility()
        );
    }
}
