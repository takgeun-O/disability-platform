package io.github.takgeun.iyum.community.post.application;

public record PostLikeResult(
        Long postId,
        boolean liked,  // 처리 후 요청 회원의 좋아요 여부
        long likeCount  // 처리 후 좋아요 갯수 기준임 (즉, 좋아요 있는 상태에서 등록 요청해도 true. 멱등 처리)
) {
}
