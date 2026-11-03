package io.github.takgeun.iyum.admin.moderation.application;

import lombok.Getter;

@Getter
public class ContentModerationException extends RuntimeException {

    private final Code code;

    public ContentModerationException(Code code) {
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

        INVALID_REASON(
                "처리 사유는 앞뒤 공백을 제거한 뒤 1자 이상 500자 이하로 입력해 주세요."
        ),

        POST_NOT_FOUND(
                "게시글을 찾을 수 없습니다."
        ),

        POST_DELETED(
                "삭제된 게시글은 숨김 또는 복원할 수 없습니다."
        ),

        COMMENT_NOT_FOUND(
                "댓글을 찾을 수 없습니다."
        ),

        COMMENT_DELETED(
            "삭제된 댓글은 숨김 또는 복원할 수 없습니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}