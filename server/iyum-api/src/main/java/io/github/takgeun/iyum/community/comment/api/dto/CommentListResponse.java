package io.github.takgeun.iyum.community.comment.api.dto;

import io.github.takgeun.iyum.community.comment.application.CommentListResult;
import jakarta.annotation.Nullable;

import java.util.List;

// 댓글 목록 응답
public record CommentListResponse(
        List<CommentResponse> items,
        boolean hasNext,
        @Nullable CommentCursorResponse nextCursor
) {

    public CommentListResponse {
        items = List.copyOf(items);
    }

    public static CommentListResponse from(
            CommentListResult result
    ) {
        var cursor = result.nextCursor();

        return new CommentListResponse(
                result.items().stream()
                        .map(CommentResponse::from)
                        .toList(),
                result.hasNext(),
                cursor == null
                        ? null
                        : CommentCursorResponse.from(cursor)
        );
    }
}
