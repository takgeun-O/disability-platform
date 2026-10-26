package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;

import java.time.Instant;

public record CommentResult(
        Long commentId,
        String content,
        CommentAuthorResult author,
        Instant createdAt,
        Instant updatedAt,
        long likeCount,
        boolean liked
) {

    public static CommentResult from(
            Comment comment,
            CommentLikeState likeState
    ) {
        return new CommentResult(
                comment.getId(),
                comment.getContent(),
                CommentAuthorResult.from(comment.getAuthor()),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                likeState.likeCount(),
                likeState.liked()
        );
    }
}
