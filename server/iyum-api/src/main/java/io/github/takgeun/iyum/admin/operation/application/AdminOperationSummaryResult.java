package io.github.takgeun.iyum.admin.operation.application;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 운영 기록 요약 조회 결과.
 */
public record AdminOperationSummaryResult(

        // 집계 날짜를 계산할 기준 시각.
        // 모든 집계가 동일한 DB 스냅샷임을 보장하지 않는다.
        Instant queriedAt,

        // queriedAt을 Asia/Seoul 기준으로 변환한 날짜.
        LocalDate baseDate,

        // 전체·오늘·오늘 숨김·오늘 숨김 해제 기록 건수.
        AdminOperationSummaryCountsResult counts
) {
}