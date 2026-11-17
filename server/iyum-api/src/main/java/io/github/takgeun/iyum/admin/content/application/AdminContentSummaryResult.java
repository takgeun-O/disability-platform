package io.github.takgeun.iyum.admin.content.application;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 게시글·댓글 관리 요약 조회 결과
 *
 * var result = new AdminContentSummaryResult(
 *         queriedAt,
 *         baseDate,
 *         counts
 * );
 * --> AdminContentSummaryCountsResult에서 만든 counts에
 * 어느 시각과 날짜를 기준으로 조회했는지 붙여서 전달
 */
public record AdminContentSummaryResult(

        // 집계 날짜를 계산할 기준 시각
        // 모든 집계가 동일한 DB 스냅샷임을 보장하지 않는다.
        Instant queriedAt,

        // 기준 시각을 Asia/Seoul 기준으로 변환한 날짜
        LocalDate baseDate,

        // 오늘 작성·현재 숨김 수치
        AdminContentSummaryCountsResult counts
) {
}
