package io.github.takgeun.iyum.community.comment.application;

import jakarta.annotation.Nullable;

/**
 * 서비스가 받을 정렬·커서·조회 개수를 하나로 묶기
 * <p>
 * 게시글별 댓글 목록의 조회 조건
 * cursor가 null이면 첫 조회다.
 */
public record CommentReadQuery(
        CommentSort sort,
        @Nullable CommentCursor cursor,
        int size
) {

    private static final int MAX_PAGE_SIZE = 50;

    public CommentReadQuery {
        // 정렬 조건 생략 시 기존 등록순을 유지한다.
        if (sort == null) {
            sort = CommentSort.OLDEST;
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new CommentReadException(
                    CommentReadException.Code.INVALID_PAGE_REQUEST
            );
        }

        if (cursor != null) {
            validateCursor(sort, cursor);
        }
    }

    private static void validateCursor(
            CommentSort sort,
            CommentCursor cursor
    ) {
        if (cursor.createdAt() == null
                || cursor.commentId() == null
                || cursor.commentId() <= 0
                || cursor.sort() != sort) {
            throw invalidCursor();
        }

        if (sort == CommentSort.POPULAR) {
            // 인기순은 좋아요 0개도 유효한 커서다.
            if (cursor.likeCount() == null
                    || cursor.likeCount() < 0) {
                throw invalidCursor();
            }
        } else {
            // 등록순·최신순 커서에는 좋아요 수를 넣지 않는다.
            if(cursor.likeCount() != null) {
                throw invalidCursor();
            }
        }
    }

    private static CommentReadException invalidCursor() {
        return new CommentReadException(
                CommentReadException.Code.INVALID_CURSOR
        );
    }
}
