package io.github.takgeun.iyum.admin.operation.application;

import java.util.Objects;
import java.util.regex.Pattern;

public final class AdminOperationLogContentPreview {

    public static final int MAX_CODE_POINTS = 120;

    private static final Pattern WHITESPACE = Pattern.compile(
            "\\s+", // \\s: 공백, 줄바꿈, 탭 같은 공백 문자, +: 하나 이상의 연속된 구간
            Pattern.UNICODE_CHARACTER_CLASS     // 유니코드 기준의 공백 문자도 인식
    );

    private AdminOperationLogContentPreview() {
    }

    public static String from(String content) {
        Objects.requireNonNull(content, "content must not be null");

        // 본문의 줄바꿈·탭·연속 공백을 공백 한 칸으로 통일하고
        // 앞뒤 공백을 제거해서 한 줄짜리 미리보기를 만드는 과정
        String normalized = WHITESPACE
                .matcher(content)
                .replaceAll(" ")        // 공백 구간을 모두 한 칸으로 변경
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
