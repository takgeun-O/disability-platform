package io.github.takgeun.iyum.admin.operation.application;

import lombok.Getter;

@Getter
public class AdminOperationLogReadException extends RuntimeException {

    private final Code code;

    public AdminOperationLogReadException(Code code) {
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

        INVALID_PAGE_REQUEST(
                "페이지는 0 이상이고 조회 개수는 1 이상 50 이하여야 하며, "
                        + "조회 시작 위치는 지원 범위 이내여야 합니다."
        ),

        INVALID_DATE_RANGE(
                "조회 날짜 범위가 올바르지 않습니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}