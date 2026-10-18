package io.github.takgeun.iyum.community.post.application;

import java.util.regex.Pattern;

// 본문 미리보기
final class PostContentPreview {

    private static final int MAX_CODE_POINTS = 200;

    // 줄바꿈·탭·Unicode 공백을 포함한 연속 공백을 찾는다.
//    입력: "첫 번째 줄\n\n두 번째 줄"
//    출력: "첫 번째 줄 두 번째 줄"
    private static final Pattern WHITESPACE =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private PostContentPreview() {}

    static String from(String content) {
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

        // 말 줄임표는 프론트에서 처리할 것
        return normalized.substring(0, endIndex).stripTrailing();
    }
}
