package io.github.takgeun.iyum.admin.member.application;

/**
 * 회원 관리 화면의 요약 수치
 * 목록 검색·상태·역할·정렬·페이지 조건과 무관하다.
 * 관리자 계정을 포함한 전체 계정을 기준으로 집계한다.
 */
public record AdminMemberSummaryCountsResult(

        // 전체 기간 기준 현재 회원 수.
        // WITHDRAWN, DELETED 상태는 제외한다.
        // PENDING, ACTIVE, RESTRICTED, SUSPENDED 상태는 포함한다.
        long currentMemberCount,

        // 한국 날짜 기준 오늘 생성된 계정 수.
        // 현재 인증·정지·탈퇴 상태와 무관하다.
        long todayMemberCount,

        // 전체 기간 기준 현재 PENDING 상태인 계정 수.
        long pendingMemberCount,

        // 전체 기간 기준 현재 SUSPENDED 상태인 계정 수.
        long suspendedMemberCount
) {
}
