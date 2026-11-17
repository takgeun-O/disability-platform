package io.github.takgeun.iyum.admin.dashboard.application;

/**
 * 관리자 대시보드 요약 수치.
 *
 * 오늘 집계는 한국 시간 기준 생성 시각으로 계산하며,
 * 생성 이후의 상태 변경 여부와 무관하게 포함한다.
 */
public record AdminDashboardCountsResult(

        // 현재 RECEIVED인 신고 수
        long pendingReportCount,

        // 오늘 접수된 신고 수
        long todayReportCount,

        // 오늘 작성된 게시글 수
        long todayPostCount,

        // 오늘 생성된 회원 계정 수
        long todayMemberCount
) {
}
