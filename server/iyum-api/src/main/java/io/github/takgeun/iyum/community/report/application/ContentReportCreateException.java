package io.github.takgeun.iyum.community.report.application;

import lombok.Getter;

@Getter
public class ContentReportCreateException extends RuntimeException {

    private final Code code;

    public ContentReportCreateException(Code code) {
        super(code.message);
        this.code = code;
    }

    public enum Code {

        AUTHENTICATION_REQUIRED(
                "유효한 로그인 정보가 필요합니다."
        ),

        INVALID_REASON(
                "신고 사유를 확인해 주세요. "
                        + "상세 설명은 500자 이하여야 하며, "
                        + "기타 사유는 상세 설명이 필요합니다."
        ),

        POST_NOT_FOUND(
                "신고할 수 있는 게시글을 찾을 수 없습니다."
        ),

        COMMENT_NOT_FOUND(
                "신고할 수 있는 댓글을 찾을 수 없습니다."
        ),

        SELF_REPORT_NOT_ALLOWED(
                "자신이 작성한 콘텐츠는 신고할 수 없습니다."
        ),

        REPORT_ALREADY_EXISTS(
                "이미 신고한 콘텐츠입니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}