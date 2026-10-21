package io.github.takgeun.iyum.auth.passwordreset.application;

import java.time.Instant;

// 변경 완료 이벤트
// 메일 작성에 필요한 회원 ID·수신 이메일·변경 시각만 전달
public record PasswordResetCompleted(
        Long memberId,
        String email,
        Instant changedAt
) {

    @Override
    public String toString() {
        return "PasswordResetCompleted[REDACTED]";
    }
}
