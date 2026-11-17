package io.github.takgeun.iyum.admin.dashboard.application;

import io.github.takgeun.iyum.admin.report.application.AdminReportTargetType;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;

import java.time.Instant;

/**
 * 관리자 대시보드에 표시할 처리 대기 신고 한 건.
 */
public record AdminDashboardPendingReportResult(
        Long reportId,
        AdminReportTargetType targetType,
        Long targetId,
        ContentReportReasonType reasonType,
        Instant createdAt
) {

    // RECEIVED만 조회하고 오래된 순으로 5건을 제한하는 책임은 리파지토리에서 담당
    public static AdminDashboardPendingReportResult from(
            ContentReport report
    ) {
        boolean postTarget = report.getPostId() != null;

        return new AdminDashboardPendingReportResult(
                report.getId(),
                postTarget
                        ? AdminReportTargetType.POST
                        : AdminReportTargetType.COMMENT,
                postTarget
                        ? report.getPostId()
                        : report.getCommentId(),
                report.getReasonType(),
                report.getCreatedAt()
        );
    }
}
