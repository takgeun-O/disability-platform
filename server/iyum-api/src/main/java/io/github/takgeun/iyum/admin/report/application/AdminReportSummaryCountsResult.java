package io.github.takgeun.iyum.admin.report.application;

/**
 * 신고 관리 화면의 요약 수치.
 * 목록 필터와 페이지 조건에 영향을 받지 않는다.
 *
 * 신고 관리의 요약 카드는 아래와 같이 3가지로 구성
 */
public record AdminReportSummaryCountsResult(

        // 처리 대기 신고
        // 접수 날짜와 관계없이 현재 RECEIVED인 신고
        long pendingReportCount,

        // 오늘 접수 신고
        // 오늘 접수된 모든 신고. (현재 신고 상태와 무관)
        long todayReportCount,

        // 오늘 종결 신고
        // 오늘 처리된 RESOLVED와 DISMISSED 신고
        long todayHandledReportCount
) {
}
