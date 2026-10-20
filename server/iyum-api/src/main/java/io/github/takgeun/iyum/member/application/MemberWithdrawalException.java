package io.github.takgeun.iyum.member.application;

import lombok.Getter;

@Getter
public class MemberWithdrawalException extends RuntimeException {

    private final Code code;

    public MemberWithdrawalException(Code code) {
        super(code.message);
        this.code = code;
    }

    public enum Code {

        MEMBER_NOT_FOUND(
                "회원 정보를 확인할 수 없습니다."
        ),

        MEMBER_NOT_ACTIVE(
                "활성 상태의 회원만 탈퇴할 수 있습니다."
        ),

        INVALID_PASSWORD(
                "현재 비밀번호를 올바르게 입력해 주세요."
        ),

        PASSWORD_MISMATCH(
                "현재 비밀번호가 일치하지 않습니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}