package io.github.takgeun.iyum.community.comment.application;

import lombok.Getter;

@Getter
public class CommentLikeException extends RuntimeException {

    private final Code code;

    public CommentLikeException(Code code) {
        super(code.message);
        this.code = code;
    }

    public enum Code {
        MEMBER_NOT_FOUND(
                "회원 정보를 확인할 수 없습니다."
        ),
        MEMBER_NOT_ACTIVE(
                "활성 상태의 회원만 좋아요를 등록하거나 취소할 수 있습니다."
        ),
        POST_NOT_FOUND(
                "게시글을 찾을 수 없습니다."
        ),
        COMMENT_NOT_FOUND(
                "댓글을 찾을 수 없습니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}