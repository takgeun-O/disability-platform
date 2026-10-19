package io.github.takgeun.iyum.member.api.dto;

import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.application.MemberCommentPostResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 댓글이 달린 원본 게시글 정보")
public record MemberCommentPostResponse(
        Long postId,
        String title,
        PostType type,
        PostTopic topic
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
