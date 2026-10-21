package io.github.takgeun.iyum.auth.passwordreset.application;

import lombok.Getter;

@Getter
public class PasswordResetException extends RuntimeException {

    public enum Code {
        PASSWORD_RESET_TOKEN_INVALID,
        PASSWORD_RESET_PASSWORD_INVALID,
        PASSWORD_RESET_PASSWORD_UNCHANGED
    }

    private final Code code;

    private PasswordResetException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public static PasswordResetException invalidToken() {
        return new PasswordResetException(
                Code.PASSWORD_RESET_TOKEN_INVALID,
                "유효하지 않거나 만료된 비밀번호 재설정 링크입니다."
        );
    }

    public static PasswordResetException invalidPassword(String message) {
        return new PasswordResetException(
                Code.PASSWORD_RESET_PASSWORD_INVALID,
                message
        );
    }

    public static PasswordResetException unchangedPassword() {
        return new PasswordResetException(
                Code.PASSWORD_RESET_PASSWORD_UNCHANGED,
                "현재 비밀번호와 다른 비밀번호를 입력해주세요."
        );
    }
}