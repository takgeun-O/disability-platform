package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import jakarta.annotation.Nullable;

import java.time.Instant;

public record AdminCommentItemResult(
        Long commentId,
        String content,
        AdminCommentAuthorResult author,
        AdminCommentStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant hiddenAt,
        @Nullable Instant deletedAt
) {

    public static AdminCommentItemResult from(Comment comment) {
        return new AdminCommentItemResult(
                comment.getId(),
                comment.getContent(),
                AdminCommentAuthorResult.from(comment.getAuthor()),
                AdminCommentStatus.from(comment),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.getHiddenAt(),
                comment.getDeletedAt()
        );
    }
}
