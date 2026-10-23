package io.github.takgeun.iyum.member.application;

import java.time.Instant;

// 마이페이지 비밀번호 변경 완료 이벤트 추가
public record MemberPasswordChanged(
        Long memberId,
        String email,
        Instant changedAt
) {

    @Override
    public String toString() {
        return "MemberPasswordChanged[REDACTED]";
    }
}
