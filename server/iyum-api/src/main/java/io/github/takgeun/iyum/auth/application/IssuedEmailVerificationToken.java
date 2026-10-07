package io.github.takgeun.iyum.auth.application;

import java.time.Instant;

/**
 * 서버 내부에서 사용하는 발급 결과 (회원가입 API의 응답 DTO로 반환하면 안됨)
 */
public record IssuedEmailVerificationToken(
        Long memberId,
        String rawToken,
        Instant expiresAt
) {

    // record는 기본 toString()에 모든 필드를 포함하므로 오버라이드 처리.
    @Override
    public String toString() {
        return "IssuedEmailVerificationToken["
                + "memberId=" + memberId
                + ", rawToken=[REDACTED]"
                + ", expiresAt=" + expiresAt
                + "]";
    }
}
