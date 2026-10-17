package io.github.takgeun.iyum.community.comment.domain;

public final class CommentInputPolicy {

    public static final int CONTENT_MAX_LENGTH = 2_000;

    private CommentInputPolicy() {
    }

    public static String validateContent(String content) {
        // null, 빈 문자열, 공백·줄바꿈만 있는 내용은 거부
        // strip()을 사용하지 않으므로 정상 내용의 앞뒤 공백과 줄바꿈 보존
        if(content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "댓글 내용은 필수입니다."
            );
        }

        // 길이에는 공백·줄바꿈도 포함
        if(characterCount(content) > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "댓글 내용은 2,000자 이하여야 합니다."
            );
        }

        return content;
    }

    private static int characterCount(String value) {
        // codePointCount: 이모지처럼 자바에서 length()에서 2로 계산되는 문자도 1로 계산함
        return value.codePointCount(0, value.length());
    }
}
