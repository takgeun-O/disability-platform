package io.github.takgeun.iyum.community.comment.application;

public record CommentLikeState(
        long likeCount,
        boolean liked
) {

    public static final CommentLikeState EMPTY =
            new CommentLikeState(0L, false);
}
