package io.github.takgeun.iyum.community.post.application;

import lombok.Getter;

@Getter
public class PostCreateException extends RuntimeException {

    private final Code code;

    public PostCreateException(Code code) {
        super(code.message);
        this.code = code;
    }

    public enum Code {

        // 작성자 ID가 없거나 해당 회원이 DB에 없음
        AUTHOR_NOT_FOUND(
                "작성자 정보를 확인할 수 없습니다."
        ),

        // 회원이 있지만 현재 상태가 ACTIVE가 아님
        AUTHOR_NOT_ACTIVE(
                "활성 상태의 회원만 게시글을 작성할 수 있습니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}
