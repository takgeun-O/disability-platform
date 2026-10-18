package io.github.takgeun.iyum.community.post.application;

import lombok.Getter;

@Getter
public class PostReadException extends RuntimeException {

    private final Code code;

    public PostReadException(Code code) {
        super(code.message);
        this.code = code;
    }

    public enum Code {

        INVALID_PAGE_REQUEST(
                "게시글 목록의 페이지 조건을 확인해 주세요."
        ),

        INVALID_CURSOR(
                "게시글 목록의 커서 조건을 확인해 주세요."
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
