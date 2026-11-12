package io.github.takgeun.iyum.community.report.application;

import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;

import java.time.Instant;

// 신고 접수 결과
public record ContentReportCreateResult(
        // 신고자의 인증 버전이나 원문 스냅샷을 접수 응답에 포함할 필요 없음
        Long reportId,
        ContentReportStatus status,
        Instant createdAt
) {
}
