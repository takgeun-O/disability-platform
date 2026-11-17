package io.github.takgeun.iyum.admin.content.application;

/**
 * 게시글·댓글 관리 화면의 요약
 * 목록 필터와 페이지 조건에 영향을 받지 않는다.
 *
 * var counts = new AdminContentSummaryCountsResult(
 *         10,
 *         25,
 *         3,
 *         7
 * );
 * --> 오늘 게시글 10개, 댓글 25개 작성 // 현재 숨겨진 게시글은 3개, 댓글 7개라는 의미
 * 즉 집계 숫자 묶음이라고 이해하면 됨.
 */
public record AdminContentSummaryCountsResult(

        // 한국 날짜 기준 오늘 작성된 게시글
        // 현재 숨김·삭제 여부와 무관하다.
        long todayPostCount,

        // 한국 날짜 기준 오늘 작성된 댓글
        // 댓글 및 부모 게시글의 현재 숨김·삭제 여부와 무관하다.
        long todayCommentCount,

        // 전체 기간 기준 현재 숨겨진 게시글
        // 삭제된 게시글은 제외한다.
        long hiddenPostCount,

        // 전체 기간 기준 현재 숨겨진 댓글
        // 삭제된 댓글은 제외하며, 부모 게시글의 상태는 반영하지 않는다.
        long hiddenCommentCount
) {
}
