package io.github.takgeun.iyum.member.api.dto;

import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.application.MemberCommentPostResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@Schema(description = "내 댓글의 원본 게시글 정보. 숨김 항목은 postId만 제공")
public record MemberCommentPostResponse(
        Long postId,
        @Nullable String title,
        @Nullable PostType type,
        @Nullable PostTopic topic
) {
    public static MemberCommentPostResponse from(
            MemberCommentPostResult result
    ) {
        return new MemberCommentPostResponse(
                result.postId(),
                result.title(),
                result.type(),
                result.topic()
        );
    }
}
