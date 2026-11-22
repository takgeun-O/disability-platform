package io.github.takgeun.iyum.community.comment.api.dto;

import io.github.takgeun.iyum.community.comment.application.CommentResult;

import java.util.List;

public record BestCommentListResponse(
        // 개발 댓글 목록
        List<CommentResponse> items
) {

    public static BestCommentListResponse from(
            List<CommentResult> results
    ) {
        return new BestCommentListResponse(
                results.stream()
                        .map(CommentResponse::from)
                        .toList()
        );
    }
}
