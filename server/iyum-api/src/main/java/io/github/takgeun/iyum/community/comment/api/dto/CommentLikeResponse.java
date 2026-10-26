package io.github.takgeun.iyum.community.comment.api.dto;

import io.github.takgeun.iyum.community.comment.application.CommentLikeResult;

public record CommentLikeResponse(
        Long postId,
        Long commentId,
        boolean liked,
        long likeCount
) {

    public static CommentLikeResponse from(CommentLikeResult result) {
        return new CommentLikeResponse(
                result.postId(),
                result.commentId(),
                result.liked(),
                result.likeCount()
        );
    }
}
