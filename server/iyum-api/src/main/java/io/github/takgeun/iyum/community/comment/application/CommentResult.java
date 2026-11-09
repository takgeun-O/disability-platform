package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import jakarta.annotation.Nullable;

import java.time.Instant;

public record CommentResult(
        Long commentId,
        String content,
        CommentAuthorResult author,
        Instant createdAt,
        Instant updatedAt,
        // 값이 null일 때 updatedAt으로 대체하지 말 것.
        // 대체 시 프론트 쪽에서 숨김·복원 때문에 변경된 시각이
        // 다시 본문 수정 시각으로 노출될 가능성이 있음.
        @Nullable Instant contentUpdatedAt,
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
                comment.getContentUpdatedAt(),
                likeState.likeCount(),
                likeState.liked()
        );
    }
}
