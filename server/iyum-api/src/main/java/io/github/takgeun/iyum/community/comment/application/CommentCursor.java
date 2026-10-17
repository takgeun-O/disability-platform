package io.github.takgeun.iyum.community.comment.application;

import java.time.Instant;

// 다음 조회의 출발점
public record CommentCursor(
        Instant createdAt,
        Long commentId
) {
}
