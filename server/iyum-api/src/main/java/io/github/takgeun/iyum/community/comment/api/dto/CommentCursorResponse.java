package io.github.takgeun.iyum.community.comment.api.dto;

import io.github.takgeun.iyum.community.comment.application.CommentCursor;

import java.time.Instant;

// 다음 조회용 커서 응답
// 같은 시각에 작성된 댓글도 구분하도록 작성 시각과 댓글 ID 전달
public record CommentCursorResponse(
        Instant createdAt,
        Long commentId
) {

    public static CommentCursorResponse from(
            CommentCursor cursor
    ) {
        return new CommentCursorResponse(
                cursor.createdAt(),
                cursor.commentId()
        );
    }
}
