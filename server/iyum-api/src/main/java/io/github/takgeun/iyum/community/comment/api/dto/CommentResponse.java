package io.github.takgeun.iyum.community.comment.api.dto;

import io.github.takgeun.iyum.community.comment.application.CommentResult;
import jakarta.annotation.Nullable;

import java.time.Instant;

// 개별 댓글 응답
public record CommentResponse(
        Long commentId,
        String content,
        @Nullable CommentAuthorResponse author,
        Instant createdAt,
        Instant updatedAt,
        // 값이 null일 때 updatedAt으로 대체하지 말 것.
        // 대체 시 프론트 쪽에서 숨김·복원 때문에 변경된 시각이
        // 다시 본문 수정 시각으로 노출될 가능성이 있음.
        @Nullable Instant contentUpdatedAt,
        long likeCount,
        boolean liked,
        boolean deleted,
        long replyCount
) {

    public static CommentResponse from(
            CommentResult result
    ) {
        return new CommentResponse(
                result.commentId(),
                result.content(),
                result.author() == null
                        ? null
                        : CommentAuthorResponse.from(result.author()),
                result.createdAt(),
                result.updatedAt(),
                result.contentUpdatedAt(),
                result.likeCount(),
                result.liked(),
                result.deleted(),
                result.replyCount()
        );
    }
}
