package io.github.takgeun.iyum.member.api.dto;

import io.github.takgeun.iyum.member.application.MemberCommentItemResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// 댓글 하나를 담는 객체
@Schema(description = "내가 작성한 댓글과 원본 게시글 정보")
public record MemberCommentItemResponse(
        Long commentId,
        String content,
        Instant createdAt,
        Instant updatedAt,
        MemberCommentPostResponse post,

        @Schema(description = "댓글이 받은 좋아요 개수", example = "3")
        long likeCount,

        @Schema(
                description = "로그인한 회원이 해당 댓글에 좋아요를 눌렀는지 여부",
                example = "true"
        )
        boolean liked
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
                result.liked()
        );
    }
}
