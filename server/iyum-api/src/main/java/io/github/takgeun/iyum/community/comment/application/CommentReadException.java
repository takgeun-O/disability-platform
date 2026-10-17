package io.github.takgeun.iyum.community.comment.application;

import lombok.Getter;

@Getter
public class CommentReadException extends RuntimeException {

    private final Code code;

    public CommentReadException(Code code) {
        super(code.message);
        this.code = code;
    }


    public enum Code {
        INVALID_PAGE_REQUEST(
                "댓글 조회 개수는 1개 이상 50개 이하여야 합니다."
        ),

        INVALID_CURSOR(
                "댓글 조회 커서를 확인해 주세요."
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
