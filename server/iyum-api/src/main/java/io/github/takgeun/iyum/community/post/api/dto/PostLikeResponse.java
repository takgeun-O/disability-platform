package io.github.takgeun.iyum.community.post.api.dto;

import io.github.takgeun.iyum.community.post.application.PostLikeResult;

public record PostLikeResponse(
        Long postId,
        boolean liked,
        long likeCount
) {

    public static PostLikeResponse from(PostLikeResult result) {
        return new PostLikeResponse(
                result.postId(),
                result.liked(),
                result.likeCount()
        );
    }
}
