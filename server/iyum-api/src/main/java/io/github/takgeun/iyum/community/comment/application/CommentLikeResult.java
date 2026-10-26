package io.github.takgeun.iyum.community.comment.application;

public record CommentLikeResult(
        Long postId,
        Long commentId,
        boolean liked,  // 좋아요 요청 처리 후 상태
        long likeCount  // 좋아요 요청 처리 후 상태
) {
}
