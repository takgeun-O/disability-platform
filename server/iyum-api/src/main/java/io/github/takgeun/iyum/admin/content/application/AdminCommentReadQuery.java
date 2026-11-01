package io.github.takgeun.iyum.admin.content.application;

import jakarta.annotation.Nullable;

public record AdminCommentReadQuery(
        AdminCommentStatus status,
        @Nullable String keyword,
        int page,
        int size
) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    public static final int MAX_KEYWORD_CODE_POINTS = 100;

    public AdminCommentReadQuery {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.INVALID_PAGE_REQUEST
            );
        }

        long offset = (long) page * size;

        if (offset > Integer.MAX_VALUE) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.INVALID_PAGE_REQUEST
            );
        }

        if (status == null) {
            status = AdminCommentStatus.ALL;
        }

        if (keyword != null) {
            keyword = keyword.strip();

            if (keyword.isEmpty()) {
                keyword = null;
            } else if (
                    keyword.codePointCount(0, keyword.length())
                            > MAX_KEYWORD_CODE_POINTS
            ) {
                throw new AdminContentReadException(
                        AdminContentReadException.Code.INVALID_SEARCH_KEYWORD
                );
            }
        }
    }
}
