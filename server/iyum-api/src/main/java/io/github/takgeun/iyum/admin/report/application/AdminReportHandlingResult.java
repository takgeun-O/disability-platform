package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;

import java.time.Instant;

public record AdminReportHandlingResult(
        Long reportId,
        ContentReportStatus status,
        Long handledByMemberId,
        Instant handledAt,
        String handlingReason
) {

    public static AdminReportHandlingResult from(
            ContentReport report
    ) {
        return new AdminReportHandlingResult(
                report.getId(),
                report.getStatus(),
                report.getHandledByMemberId(),
                report.getHandledAt(),
                report.getHandlingReason()
        );
    }
}