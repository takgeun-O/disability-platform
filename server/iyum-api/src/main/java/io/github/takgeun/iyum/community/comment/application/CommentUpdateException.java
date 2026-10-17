package io.github.takgeun.iyum.community.comment.application;

import lombok.Getter;

// 수정 과정에서 사용할 예외
@Getter
public class CommentUpdateException extends RuntimeException {

    private final Code code;

    public CommentUpdateException(Code code) {
        super(code.message);
        this.code = code;
    }

    public enum Code {

        MEMBER_NOT_FOUND(
                "로그인한 회원 정보를 확인할 수 없습니다."
        ),

        MEMBER_NOT_ACTIVE(
                "현재 댓글을 수정할 수 없는 회원 상태입니다."
        ),

        POST_NOT_FOUND(
                "게시글을 찾을 수 없습니다."
        ),

        COMMENT_NOT_FOUND(
                "댓글을 찾을 수 없습니다."
        ),

        NOT_COMMENT_AUTHOR(
                "본인이 작성한 댓글만 수정할 수 있습니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}