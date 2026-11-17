package io.github.takgeun.iyum.admin.member.application;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 회원 관리 요약 조회 결과.
 */
public record AdminMemberSummaryResult(

        // 집계 날짜를 계산할 기준 시각.
        // 모든 집계가 동일한 DB 스냅샷임을 보장하지 않는다.
        Instant queriedAt,

        // queriedAt을 Asia/Seoul 기준으로 변환한 날짜.
        LocalDate baseDate,

        // 현재 회원·오늘 가입·인증 대기·이용 정지 수치.
        AdminMemberSummaryCountsResult counts
) {
}