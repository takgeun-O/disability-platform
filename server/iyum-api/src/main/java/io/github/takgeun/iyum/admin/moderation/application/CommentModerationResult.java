package io.github.takgeun.iyum.admin.moderation.application;

import java.time.Instant;

public record CommentModerationResult(
        Long postId,
        Long commentId,
        // hidden은 댓글 자체의 숨김 여부임.
        // 부모 게시글 상태까지 합친 최종 공개 여부를 의미하지 않음.
        boolean hidden,
        Instant hiddenAt,
        boolean changed
) {
}
