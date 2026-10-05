package io.github.takgeun.iyum.auth.application;

import lombok.Getter;

/**
 * 이메일 중복과 닉네임 중복을 구분할 수 있도록 예외에 코드 포함시키기
 */
@Getter
public class SignupConflictException extends RuntimeException {

    private final Code code;

    public SignupConflictException(Code code) {
        this(code, null);
    }

    public SignupConflictException(Code code, Throwable cause) {
        super(code.message, cause);
        this.code = code;
    }

    public enum Code {
        EMAIL_ALREADY_EXISTS("이미 가입된 이메일입니다."),
        NICKNAME_ALREADY_EXISTS("이미 사용 중인 닉네임입니다.");

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}
