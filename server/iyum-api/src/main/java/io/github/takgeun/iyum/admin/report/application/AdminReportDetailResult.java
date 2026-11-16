package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportHandlingOutcome;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * 관리자 신고 상세 조회 결과.
 * <p>
 * 스냅샷은 신고 접수 당시 내용이며,
 * currentTarget은 조회 시점의 현재 콘텐츠다.
 * <p>
 * 처리 정보와 처리 결과는 신고에 저장된 값을 반환한다.
 * 현재 콘텐츠 상태로 처리 결과를 다시 계산하지 않는다.
 * 즉, 신고를 처리했을 당시의 결과는 이력으로 유지하고,
 * 게시글이 나중에 바뀌더라도 그 결과를 바꾸지 않는다.
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
        @Nullable AdminReportCurrentTargetResult currentTarget, // 지금 게시글·댓글이 어떤 상태인가?
        @Nullable Long handledByMemberId,
        @Nullable Instant handledAt,
        @Nullable String handlingReason,
        @Nullable ContentReportHandlingOutcome handlingOutcome  // 그 신고를 처리했을 때 어떤 결과가 발생했는가?
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
                report.getHandlingReason(),
                report.getHandlingOutcome() // 콘텐츠의 hiddenAt을 확인해서 결과를 만들지 않고, 신고에 저장된 값을 사용한다.
        );
    }
}