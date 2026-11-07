package io.github.takgeun.iyum.admin.content.application;

import lombok.Getter;

@Getter
public class AdminContentReadException extends RuntimeException {

    private final Code code;

    public AdminContentReadException(Code code) {
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
                "조회할 상태를 올바르게 선택해 주세요."
        ),

        INVALID_PAGE_REQUEST(
                "페이지는 0 이상이고 조회 개수는 1 이상 50 이하여야 하며, "
                        + "조회 시작 위치는 지원 범위 이내여야 합니다."
        ),

        INVALID_SEARCH_KEYWORD(
                "검색어는 앞뒤 공백을 제거한 뒤 100자 이하여야 합니다."
        ),

        POST_NOT_FOUND(
                "게시글을 찾을 수 없습니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}