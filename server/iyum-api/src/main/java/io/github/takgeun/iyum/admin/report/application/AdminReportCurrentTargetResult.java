package io.github.takgeun.iyum.admin.report.application;

import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * 조회 시점의 신고 대상 콘텐츠.
 * 신고 접수 당시 스냅샷이 아니다.
 * <p>
 * 게시글:
 * - targetId는 게시글 ID
 * - parentPostId는 null
 * - title은 현재 게시글 제목
 * <p>
 * 댓글:
 * - targetId는 댓글 ID
 * - parentPostId는 부모 게시글 ID
 * - title은 null
 * <p>
 * hiddenAt과 deletedAt은 대상 자체의 상태다.
 * 댓글의 경우 부모 게시글 상태를 의미하지 않는다.
 */
public record AdminReportCurrentTargetResult(
        Long targetId,
        @Nullable Long parentPostId,
        AdminReportTargetAuthorResult author,
        @Nullable String title,
        String content, // 줄바꿈을 유지한 현재 본문 전체
        @Nullable Instant hiddenAt,
        @Nullable Instant deletedAt
) {
}
