package io.github.takgeun.iyum.admin.report.application;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 신고 본문 미리보기
 *
 * 기존 운영 기록과 동일하게 공백을 정리하고
 * 120 코드 포인트까지 표시
 *
 * 이 처리는 목룍 표시용이며, 저장된 스냅샷이나 상세 본문을 변경하지 않음.
 */
public final class AdminReportContentPreview {

    public static final int MAX_CODE_POINTS = 120;

    private static final Pattern WHITESPACE = Pattern.compile(
            "\\s+",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    private AdminReportContentPreview() {
    }

    public static String from(String content) {
        Objects.requireNonNull(content, "content must not be null");

        String normalized = WHITESPACE
                .matcher(content)
                .replaceAll(" ")
                .strip();

        int codePointCount = normalized.codePointCount(
                0,
                normalized.length()
        );

        if(codePointCount <= MAX_CODE_POINTS) {
            return normalized;
        }

        int endIndex = normalized.offsetByCodePoints(
                0,
                MAX_CODE_POINTS
        );

        return normalized.substring(0, endIndex) + "…";
    }
}
