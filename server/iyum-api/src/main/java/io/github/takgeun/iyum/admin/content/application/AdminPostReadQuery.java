package io.github.takgeun.iyum.admin.content.application;

import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * 관리자 게시글 목록 조회 조건.
 * <p>
 * 내부에서는 ALL 대신 실제 조회할 상태 집합을 보관한다.
 * ex) 전체 조회 -> VISIBLE, HIDDEN, DELETED
 * <p>
 * 이 객체를 만든 이유는 컨트롤러에서 들어온 값을 조회 쿼리에 넘기기 전에
 * 기본값·검색어·페이지 범위를 한곳에서 정리하기 위함
 */
public record AdminPostReadQuery(
        int page,
        int size,
        @Nullable String keyword,
        Set<AdminPostStatus> statuses
) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    public static final int MAX_KEYWORD_CODE_POINTS = 100;

    public AdminPostReadQuery {
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

        // 기본 생성자를 직접 호출하더라도 상태 검증을 거치게 한다.
        // 원본 집합과 분리된 불변 집합으로 보관한다.
        statuses = AdminPostStatus.normalizeStatuses(statuses);

        if (keyword != null) {
            keyword = keyword.strip();

            if(keyword.isEmpty()) {
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
     * 기존 단일 상태 생성자.
     * null 또는 ALL은 실제 상태 세 개로 변환한다.
     */
    public AdminPostReadQuery(
            @Nullable AdminPostStatus status,
            @Nullable String keyword,
            int page,
            int size
    ) {
        this(
                page,
                size,
                keyword,
                AdminPostStatus.fromSingleStatus(status)
        );
    }

    /**
     * 명시적인 다중 상태 조회 조건을 생성한다.
     * null, 빈 목록, null 원소, ALL은 허용하지 않는다.
     */
    public static AdminPostReadQuery ofStatuses(
            Collection<AdminPostStatus> statuses,
            @Nullable String keyword,
            int page,
            int size
    ) {
        return new AdminPostReadQuery(
                page,
                size,
                keyword,
                AdminPostStatus.normalizeStatuses(statuses)
        );
    }
}
