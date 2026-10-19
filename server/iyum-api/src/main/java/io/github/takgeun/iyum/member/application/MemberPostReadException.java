package io.github.takgeun.iyum.member.application;

import lombok.Getter;

@Getter
public class MemberPostReadException extends RuntimeException {

    private final Code code;

    public MemberPostReadException(Code code) {
        super(code.message);
        this.code = code;
    }

    public enum Code {
        MEMBER_NOT_FOUND(
                "회원 정보를 확인할 수 없습니다."
        ),
        MEMBER_NOT_ACTIVE(
                "활성 상태의 회원만 내 게시글을 조회할 수 있습니다."
        ),
        INVALID_PAGE_REQUEST(
                "조회 개수는 1개 이상 50개 이하여야 합니다."
        ),
        INVALID_CURSOR(
                "커서의 생성 시각과 양수 게시글 ID를 함께 전달해 주세요."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}