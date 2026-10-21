package io.github.takgeun.iyum.auth.passwordreset.application;

import java.time.Instant;

public record PasswordResetMailRequested(
        Long memberId,
        String email,
        String rawToken,
        Instant expiresAt
) {

    @Override
    public String toString() {
        return "PasswordResetMailRequested[REDACTED]";
    }
}
