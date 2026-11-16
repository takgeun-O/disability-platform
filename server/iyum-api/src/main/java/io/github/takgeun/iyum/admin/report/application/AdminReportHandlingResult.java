package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportHandlingOutcome;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;

import java.time.Instant;

public record AdminReportHandlingResult(
        Long reportId,
        ContentReportStatus status,
        Long handledByMemberId,
        Instant handledAt,
        String handlingReason,
        // 결과는 현재 콘텐츠 상태로 추측하지 않고 신고 엔티티에 저장된 값을 반환해야 한다.
        ContentReportHandlingOutcome handlingOutcome
) {

    public static AdminReportHandlingResult from(
            ContentReport report
    ) {
        return new AdminReportHandlingResult(
                report.getId(),
                report.getStatus(),
                report.getHandledByMemberId(),
                report.getHandledAt(),
                report.getHandlingReason(),
                report.getHandlingOutcome()
        );
    }
}