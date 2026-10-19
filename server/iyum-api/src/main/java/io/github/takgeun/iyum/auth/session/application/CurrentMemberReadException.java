package io.github.takgeun.iyum.auth.session.application;

import lombok.Getter;

@Getter
public class CurrentMemberReadException extends RuntimeException {

    private final Code code;

    public CurrentMemberReadException(Code code) {
        super(code.message);
        this.code = code;
    }

    public enum Code {

        MEMBER_NOT_FOUND(
                "회원 정보를 확인할 수 없습니다."
        ),

        MEMBER_NOT_ACTIVE(
                "현재 활성 상태가 아닌 회원입니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}
