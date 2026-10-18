package io.github.takgeun.iyum.community.post.application;

import java.time.Instant;

public record PostCursor(
        // 실제 반환한 마지막 게시글의 생성 시각과 ID
        Instant createdAt,
        Long postId
) {
}
