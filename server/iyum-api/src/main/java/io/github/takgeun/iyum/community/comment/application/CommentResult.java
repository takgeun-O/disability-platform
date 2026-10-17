package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;

import java.time.Instant;

public record CommentResult(
        Long commentId,
        String content,
        CommentAuthorResult author,
        Instant createdAt,
        Instant updatedAt
) {

    public static CommentResult from(Comment comment) {
        return new CommentResult(
                comment.getId(),
                comment.getContent(),
                CommentAuthorResult.from(comment.getAuthor()),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
