package io.github.takgeun.iyum.admin.content.application;

import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * 관리자 댓글 목록 조회 조건.
 * 부모 게시글이 아닌 댓글 자체의 상태를 저장한다.
 */
public record AdminCommentReadQuery(
        int page,
        int size,
        @Nullable String keyword,
        Set<AdminCommentStatus> statuses
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

        statuses = AdminCommentStatus.normalizeStatuses(statuses);

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

    /**
     * 기존 단일 상태 생성자를 유지한다.
     */
    public AdminCommentReadQuery(
            @Nullable AdminCommentStatus status,
            @Nullable String keyword,
            int page,
            int size
    ) {
        this(
                page,
                size,
                keyword,
                AdminCommentStatus.fromSingleStatus(status)
        );
    }

    /**
     * 명시적인 다중 상태 조회 조건을 생성한다.
     */
    public static AdminCommentReadQuery ofStatuses(
            Collection<AdminCommentStatus> statuses,
            @Nullable String keyword,
            int page,
            int size
    ) {
        return new AdminCommentReadQuery(
                page,
                size,
                keyword,
                AdminCommentStatus.normalizeStatuses(statuses)
        );
    }
}
