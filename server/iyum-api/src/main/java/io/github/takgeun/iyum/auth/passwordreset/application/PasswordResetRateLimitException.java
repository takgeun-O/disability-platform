package io.github.takgeun.iyum.auth.passwordreset.application;

public class PasswordResetRateLimitException extends RuntimeException {

    public PasswordResetRateLimitException() {
        super("비밀번호 재설정 요청이 많습니다. 잠시 후 다시 시도해주세요.");
    }
}
