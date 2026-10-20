package io.github.takgeun.iyum.member.application;

// 회원 활용 요약 결과 객체
public record MemberActivitySummaryResult(
        // 활성이 없는 건 null이 아니라 0을 반환할 것
        long postCount,
        long commentCount
) {
}
