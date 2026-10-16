package io.github.takgeun.iyum.community.post.application;

import lombok.Getter;

// 수정 실패를 표현할 예외 만들기
// 이 예외를 HTTP 에러와 기존 JSON 오류 형식으로 연결하는 작업은 컨트롤러에서 진행
@Getter
public class PostUpdateException extends RuntimeException {

    private final Code code;

    public PostUpdateException(Code code) {
        super(code.message);
        this.code = code;
    }

    public enum Code {

        MEMBER_NOT_FOUND(
                "회원 정보를 확인할 수 없습니다."
        ),

        MEMBER_NOT_ACTIVE(
                "활성 상태의 회원만 게시글을 수정할 수 있습니다."
        ),

        POST_NOT_FOUND(
                "게시글을 찾을 수 없습니다."
        ),

        NOT_POST_AUTHOR(
                "본인이 작성한 게시글만 수정할 수 있습니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}
