package io.github.takgeun.iyum.admin.member.application;

import lombok.Getter;

@Getter
public class AdminMemberReadException extends RuntimeException {

    private final Code code;

    public AdminMemberReadException(Code code) {
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
                "회원 조회 조건을 올바르게 입력해 주세요."
        ),

        INVALID_PAGE_REQUEST(
                "페이지는 0 이상이고 조회 개수는 1 이상 50 이하여야 하며, "
                        + "조회 시작 위치는 지원 범위 이내여야 합니다."
        ),

        INVALID_SEARCH_KEYWORD(
                "검색 유형에 맞는 유효한 검색어를 입력해 주세요."
        ),

        MEMBER_NOT_FOUND(
                "회원을 찾을 수 없습니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}