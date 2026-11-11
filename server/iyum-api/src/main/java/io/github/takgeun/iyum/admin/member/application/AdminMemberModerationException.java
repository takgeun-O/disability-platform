package io.github.takgeun.iyum.admin.member.application;

import lombok.Getter;

@Getter
public class AdminMemberModerationException extends RuntimeException {

    private final Code code;

    public AdminMemberModerationException(Code code) {
        super(code.message);
        this.code = code;
    }

    public enum Code {

        AUTHENTICATION_REQUIRED(
                "유효한 로그인 정보가 필요합니다."
        ),

        ADMIN_REQUIRED(
                "관리자 권한이 필요합니다."
        ),

        INVALID_REASON(
                "처리 사유는 필수이며, 앞뒤 공백을 제거한 뒤 500자 이하여야 합니다."
        ),

        MEMBER_NOT_FOUND(
                "회원을 찾을 수 없습니다."
        ),

        SELF_MODERATION_NOT_ALLOWED(
                "자기 자신에게 회원 운영 조치를 할 수 없습니다."
        ),

        ADMIN_TARGET_NOT_ALLOWED(
                "관리자 계정은 이용 정지 또는 정지 해제할 수 없습니다."
        ),

        MEMBER_STATUS_NOT_ALLOWED(
                "현재 회원 상태에서는 이용 정지 또는 정지 해제할 수 없습니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}