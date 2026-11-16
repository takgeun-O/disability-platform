package io.github.takgeun.iyum.community.report.domain;

import java.util.regex.Pattern;

/**
 * 관리자가 신고를 처리하면서 입력한 사유를 정리하고 검증하는 정책
 */
public final class ContentReportHandlingPolicy {

    public static final int HANDLING_REASON_MAX_LENGTH = 500;

    // \A: 문자열의 맨 처음
    // \s+: 공백 문자(\s)가 하나 이상(+) 연속됨
    // |: 또는
    // \z: 문자열의 맨 끝
    // -> 전체 의미 : 문자열 맨 앞의 연속된 공백 또는 맨 뒤의 연속된 공백
    private static final Pattern EDGE_WHITESPACE = Pattern.compile(
            "\\A\\s+|\\s+\\z",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    private ContentReportHandlingPolicy() {
    }

    public static String normalizeHandlingReason(String reason) {
        if(reason == null) {
            throw new IllegalArgumentException(
                    "신고 처리 사유는 필수입니다."
            );
        }

        // matcher(reason)으로 입력값에서 정규식에 일치하는 부분을 찾고
        // replaceAll("")로 찾은 부분을 모두 빈 문자열로 바꾸는 것. (즉 제거)
        String normalized = EDGE_WHITESPACE.matcher(reason)
                .replaceAll("");

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "신고 처리 사유는 필수입니다."
            );
        }

        int length = normalized.codePointCount(
                0,
                normalized.length()
        );

        if (length > HANDLING_REASON_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "신고 처리 사유는 500자 이하여야 합니다."
            );
        }

        return normalized;
    }
}
