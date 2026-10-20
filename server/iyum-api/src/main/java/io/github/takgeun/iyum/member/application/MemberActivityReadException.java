package io.github.takgeun.iyum.member.application;

import lombok.Getter;

@Getter
public class MemberActivityReadException extends RuntimeException {

    private final Code code;

    public MemberActivityReadException(Code code) {
        super(code.message);
        this.code = code;
    }

    public enum Code {

        MEMBER_NOT_FOUND(
                "회원 정보를 확인할 수 없습니다."
        ),

        MEMBER_NOT_ACTIVE(
                "활성 상태의 회원만 내 활동을 조회할 수 있습니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}
