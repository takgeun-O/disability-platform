package io.github.takgeun.iyum.admin.report.application;

import lombok.Getter;

@Getter
public class AdminReportReadException extends RuntimeException {

    private final Code code;

    public AdminReportReadException(Code code) {
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

        INVALID_REQUEST_PARAMETER(
                "요청 파라미터의 형식과 값을 확인해 주세요."
        ),

        INVALID_PAGE_REQUEST(
                "페이지 번호와 크기를 확인해 주세요."
        ),

        INVALID_DATE_RANGE(
                "조회 날짜 범위를 확인해 주세요."
        ),

        REPORT_NOT_FOUND(
                "신고 내역을 찾을 수 없습니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}