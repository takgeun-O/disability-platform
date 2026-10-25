package io.github.takgeun.iyum.community.post.application;

// 좋아요 조회 결과를 다루는 공통 객체
public record PostLikeState(
        long likeCount,
        // 현재 이 게시글을 보고 있는 사용자가 좋아요를 눌렀는가?
        // 프론트에서는 좋아요 버튼을 채워진 하트나 강조색으로 표시하고
        // 다시 누르면 취소할 상태임을 판단한다.
        // 좋아요 개수만으로는 내가 눌렀는지 알 수 없으니까 liked를 함께 전달
        boolean liked
) {

    // 좋아요 집계 결과가 없는 게시글은 EMPTY 사용
    public static final PostLikeState EMPTY =
            new PostLikeState(0L, false);
}
