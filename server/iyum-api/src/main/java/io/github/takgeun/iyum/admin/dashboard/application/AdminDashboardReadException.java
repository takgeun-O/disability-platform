package io.github.takgeun.iyum.admin.dashboard.application;

import lombok.Getter;

@Getter
public class AdminDashboardReadException extends RuntimeException {

    private final Code code;

    public AdminDashboardReadException(Code code) {
        super(code.message);
        this.code = code;
    }

    public enum Code {

        AUTHENTICATION_REQUIRED(
                "유효한 로그인 정보가 필요합니다."
        ),

        ADMIN_REQUIRED(
                "관리자 권한이 필요합니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}