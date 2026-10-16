package io.github.takgeun.iyum.community.post.application;

import lombok.Getter;

@Getter
public class PostDeleteException extends RuntimeException {

    private final Code code;

    public PostDeleteException(Code code) {
        super(code.message);
        this.code = code;
    }

    public enum Code {
        MEMBER_NOT_FOUND(
                "회원 정보를 확인할 수 없습니다."
        ),
        MEMBER_NOT_ACTIVE(
                "활성 상태의 회원만 게시글을 삭제할 수 있습니다."
        ),
        POST_NOT_FOUND(
                "게시글을 찾을 수 없습니다."
        ),
        NOT_POST_AUTHOR(
                "본인이 작성한 게시글만 삭제할 수 있습니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}
