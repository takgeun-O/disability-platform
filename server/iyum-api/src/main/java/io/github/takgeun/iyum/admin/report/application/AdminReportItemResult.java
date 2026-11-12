package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * 관리자 신고 목록의 한 항목.
 * <p>
 * titleSnapshot과 contentSnapshotPreview는 신고 접수 당시 내용이다.
 * currentAuthor는 조회 시점의 콘텐츠 작성자임.
 */
public record AdminReportItemResult(
        Long reportId,
        AdminReportTargetType targetType,
        Long targetId,
        ContentReportStatus status,
        ContentReportReasonType reasonType,
        @Nullable String titleSnapshot,
        String contentSnapshotPreview,
        Instant createdAt,
        @Nullable AdminReportTargetAuthorResult currentAuthor
) {

    public static AdminReportItemResult from(
            ContentReport report,
            @Nullable AdminReportCurrentTargetResult currentTarget
    ) {
        boolean postTarget = report.getPostId() != null;

        return new AdminReportItemResult(
                report.getId(),
                postTarget
                        ? AdminReportTargetType.POST
                        : AdminReportTargetType.COMMENT,
                postTarget
                        ? report.getPostId()
                        : report.getCommentId(),
                report.getStatus(),
                report.getReasonType(),
                report.getTitleSnapshot(),
                AdminReportContentPreview.from(
                        report.getContentSnapshot()
                ),
                report.getCreatedAt(),
                currentTarget == null
                        ? null
                        : currentTarget.author()
        );
    }
}
