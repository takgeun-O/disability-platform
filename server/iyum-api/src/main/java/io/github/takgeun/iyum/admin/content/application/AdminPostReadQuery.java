package io.github.takgeun.iyum.admin.content.application;

import jakarta.annotation.Nullable;

/**
 * 관리자 게시글 목록 조회 조건.
 * <p>
 * status가 null이면 ALL을 적용한다.
 * keyword가 null이거나 공백뿐이면 검색 조건을 적용하지 않는다.
 * <p>
 * 이 객체를 만든 이유는 컨트롤러에서 들어온 값을 조회 쿼리에 넘기기 전에
 * 기본값·검색어·페이지 범위를 한곳에서 정리하기 위함
 */
public record AdminPostReadQuery(
        AdminPostStatus status,
        @Nullable String keyword,
        int page,
        int size
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

        if (status == null) {
            status = AdminPostStatus.ALL;
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
