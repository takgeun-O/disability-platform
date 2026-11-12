package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * 관리자 신고 상세 조회 결과.
 *
 * 스냅샷은 신고 접수 당시 내용이며,
 * currentTarget은 조회 시점의 현재 콘텐츠다.
 * 처리 정보는 신고에 저장된 값을 그대로 반환한다.
 */
public record AdminReportDetailResult(
        Long reportId,
        Long reporterMemberId,
        AdminReportTargetType targetType,
        Long targetId,
        ContentReportStatus status,
        ContentReportReasonType reasonType,
        @Nullable String reasonDetail,
        @Nullable String titleSnapshot,
        String contentSnapshot,
        Instant createdAt,
        @Nullable AdminReportCurrentTargetResult currentTarget,
        @Nullable Long handledByMemberId,
        @Nullable Instant handledAt,
        @Nullable String handlingReason
) {

    public static AdminReportDetailResult from(
            ContentReport report,
            @Nullable AdminReportCurrentTargetResult currentTarget
    ) {
        boolean postTarget = report.getPostId() != null;

        return new AdminReportDetailResult(
                report.getId(),
                report.getReporterMemberId(),
                postTarget
                        ? AdminReportTargetType.POST
                        : AdminReportTargetType.COMMENT,
                postTarget
                        ? report.getPostId()
                        : report.getCommentId(),
                report.getStatus(),
                report.getReasonType(),
                report.getReasonDetail(),
                report.getTitleSnapshot(),
                report.getContentSnapshot(),
                report.getCreatedAt(),
                currentTarget,
                report.getHandledByMemberId(),
                report.getHandledAt(),
                report.getHandlingReason()
        );
    }
}