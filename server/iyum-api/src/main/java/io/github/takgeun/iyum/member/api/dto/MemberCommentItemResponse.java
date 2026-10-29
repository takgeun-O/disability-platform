package io.github.takgeun.iyum.member.api.dto;

import io.github.takgeun.iyum.member.application.MemberCommentItemResult;
import io.github.takgeun.iyum.member.application.MemberContentVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.time.Instant;

// 댓글 하나를 담는 객체
@Schema(description = "내 댓글 항목과 표시 상태")
public record MemberCommentItemResponse(
        Long commentId,
        @Nullable String content,
        Instant createdAt,
        @Nullable Instant updatedAt,
        MemberCommentPostResponse post,

        @Schema(description = "댓글이 받은 좋아요 개수. 숨김 항목은 null")
        @Nullable Long likeCount,

        @Schema(description = "로그인 회원의 좋아요 여부. 숨김 항목은 null")
        @Nullable Boolean liked,

        @Schema(
                description = "댓글 표시 상태",
                allowableValues = {
                        "VISIBLE",
                        "HIDDEN",
                        "PARENT_HIDDEN"
                }
        )
        MemberContentVisibility visibility
) {

    public static MemberCommentItemResponse from(
            MemberCommentItemResult result
    ) {
        return new MemberCommentItemResponse(
                result.commentId(),
                result.content(),
                result.createdAt(),
                result.updatedAt(),
                MemberCommentPostResponse.from(result.post()),
                result.likeCount(),
                result.liked(),
                result.visibility()
        );
    }
}
