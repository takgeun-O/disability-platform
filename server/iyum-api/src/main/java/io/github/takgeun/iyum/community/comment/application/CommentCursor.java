package io.github.takgeun.iyum.community.comment.application;

import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * 다음 댓글 조회의 출발점.
 *
 * likeCount는 POPULAR 정렬에서만 사용한다.
 */
public record CommentCursor(
        Instant createdAt,
        Long commentId,
        CommentSort sort,
        @Nullable Long likeCount
) {
    /**
     * 기존 등록순 호출과의 호환성을 유지한다.
     */
    public CommentCursor(
            Instant createdAt,
            Long commentId
    ) {
        this(
                createdAt,
                commentId,
                CommentSort.OLDEST,
                null
        );
    }
}
