package io.github.takgeun.iyum.community.post.application;

import jakarta.annotation.Nullable;

public final class PostSearchKeyword {

    private static final int MAX_CODE_POINTS = 100;

    private PostSearchKeyword() {
    }

    @Nullable
    public static String normalize(@Nullable String keyword) {
        if(keyword == null) {
            return null;
        }

        String normalized = keyword.strip();

        if(normalized.isEmpty()) {
            return null;
        }

        if(normalized.codePointCount(0, normalized.length()) > MAX_CODE_POINTS) {
            throw new PostReadException(
                    PostReadException.Code.INVALID_SEARCH_KEYWORD
            );
        }

        return normalized;

        // DB에서 %를 문자 그대로 찾는 처리는 저장소 단계에서 처리
    }
}
