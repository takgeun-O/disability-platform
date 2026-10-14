package io.github.takgeun.iyum.community.post.domain;

public final class PostInputPolicy {

    public static final int TITLE_MAX_LENGTH = 120;
    public static final int CONTENT_MAX_LENGTH = 10_000;

    // 외부에서 PostInputPolicy 생성 금지
    // 이렇게 안하면 자바에서는 생성자가 하나도 없는 클래스가 보이면 기본 생성자를 자동으로 만들어줌. (그것도 public 하게)
    // 외부에서는 static으로 선언한 필드와 메소드만 활용할 것.
    private PostInputPolicy() {
    }

    public static String normalizeTitle(String title) {
        if(title == null) {
            throw new IllegalArgumentException(
                    "제목은 필수입니다."
            );
        }

        String normalized = title.strip();

        if(normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "제목은 필수입니다."
            );
        }

        if(characterCount(normalized) > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "제목은 120자 이하여야 합니다."
            );
        }

        return normalized;
    }

    public static String validateContent(String content) {
        if(content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "본문은 필수입니다."
            );
        }

        if(characterCount(content) > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "본문은 10,000자 이하여야 합니다."
            );
        }

        return content;
    }

    private static int characterCount(String value) {
        return value.codePointCount(0, value.length());
    }
}
