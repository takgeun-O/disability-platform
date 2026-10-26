package io.github.takgeun.iyum.community.comment.api.dto;

import io.github.takgeun.iyum.community.comment.application.CommentResult;

import java.time.Instant;

// 개별 댓글 응답
public record CommentResponse(
        Long commentId,
        String content,
        CommentAuthorResponse author,
        Instant createdAt,
        Instant updatedAt,
        long likeCount,
        boolean liked
) {

    public static CommentResponse from(
            CommentResult result
    ) {
        return new CommentResponse(
                result.commentId(),
                result.content(),
                CommentAuthorResponse.from(result.author()),
                result.createdAt(),
                result.updatedAt(),
                result.likeCount(),
                result.liked()
        );
    }
}
