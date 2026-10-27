package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;

import java.time.Instant;

// 내 댓글 한 개를 담을 결과 객체
public record MemberCommentItemResult(
        Long commentId,
        String content,
        Instant createdAt,
        Instant updatedAt,
        MemberCommentPostResult post,
        long likeCount,
        boolean liked
) {

    public static MemberCommentItemResult from(
            Comment comment,
            long likeCount,
            boolean liked
    ) {
        return new MemberCommentItemResult(
                comment.getId(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                MemberCommentPostResult.from(comment.getPost()),
                likeCount,
                liked
        );
    }
}
