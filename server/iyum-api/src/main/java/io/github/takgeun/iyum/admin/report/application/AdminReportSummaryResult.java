package io.github.takgeun.iyum.admin.report.application;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 신고 관리 요약 조회 결과
 */
public record AdminReportSummaryResult(

        // 집계 날짜를 계산할 기준 시각
        Instant queriedAt,

        // 기준 시각을 한국 시간으로 변환한 날짜
        LocalDate baseDate,

        // 처리 대기·오늘 접수·오늘 종결 수치
        AdminReportSummaryCountsResult counts
) {
}
