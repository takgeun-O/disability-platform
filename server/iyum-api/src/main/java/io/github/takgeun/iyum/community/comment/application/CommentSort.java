package io.github.takgeun.iyum.community.comment.application;

/**
 * 게시글별 댓글 목록의 정렬 기준.
 * 정렬 조건을 생략한 요청에는 OLDEST를 적용한다.
 */
public enum CommentSort {

    // 작성 시각 오름차순 -> 댓글 ID 오름차순
    OLDEST,

    // 작성 시각 내림차순 -> 댓글 ID 내림차순
    LATEST,

    // 좋아요 수 내림차순 -> 작성 시각 오름차순 -> 댓글 ID 오름차순
    POPULAR
}
