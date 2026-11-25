package io.github.takgeun.iyum.community.comment.application;

import jakarta.annotation.Nullable;

/**
 * 베스트 카드 전용 결과 타입
 */
public record BestCommentResult(
        CommentResult root,
        // bestReply가 null이면 기존처럼 원댓글 한 건만 표시하는 카드
        @Nullable CommentResult bestReply
) {
}
