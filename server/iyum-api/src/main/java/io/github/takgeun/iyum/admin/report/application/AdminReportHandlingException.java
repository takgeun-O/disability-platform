package io.github.takgeun.iyum.admin.report.application;

import lombok.Getter;

@Getter
public class AdminReportHandlingException extends RuntimeException {

    private final Code code;

    public AdminReportHandlingException(Code code) {
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

        REPORT_NOT_FOUND(
                "신고 내역을 찾을 수 없습니다."
        ),

        REPORT_ALREADY_HANDLED(
                "이미 처리된 신고입니다."
        ),

        INVALID_HANDLING_REASON(
                "처리 사유를 1자 이상 500자 이하로 입력해 주세요."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}