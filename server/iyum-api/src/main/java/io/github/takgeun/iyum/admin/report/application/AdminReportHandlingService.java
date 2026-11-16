package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportAlreadyHandledException;
import io.github.takgeun.iyum.community.report.domain.ContentReportHandlingPolicy;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.community.report.infrastructure.ContentReportRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminReportHandlingService {

    private final MemberRepository memberRepository;
    private final ContentReportRepository reportRepository;
    private final Clock clock;

    public AdminReportHandlingResult resolve(
            @Nullable Long actorMemberId,
            long credentialVersion,
            @Nullable Long reportId,
            @Nullable String handlingReason
    ) {
        return handle(
                actorMemberId,
                credentialVersion,
                reportId,
                handlingReason,
                ContentReportStatus.RESOLVED
        );
    }

    public AdminReportHandlingResult dismiss(
            @Nullable Long actorMemberId,
            long credentialVersion,
            @Nullable Long reportId,
            @Nullable String handlingReason
    ) {
        return handle(
                actorMemberId,
                credentialVersion,
                reportId,
                handlingReason,
                ContentReportStatus.DISMISSED
        );
    }

    private AdminReportHandlingResult handle(
            @Nullable Long actorMemberId,
            long credentialVersion,
            @Nullable Long reportId,
            @Nullable String handlingReason,
            ContentReportStatus nextStatus
    ) {
        // 신고 존재 여부를 확인하기 전에 요청자의 자격부터 검증한다.
        // 잠금 순서는 요청 관리자 -> 신고로 유지한다.
        validateAdminForUpdate(actorMemberId, credentialVersion);

        ContentReport report = findReportForUpdate(reportId);

        if (report.getStatus() != ContentReportStatus.RECEIVED) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.REPORT_ALREADY_HANDLED
            );
        }

        String normalizedReason = normalizeHandlingReason(
                handlingReason
        );

        // 잠금 대기가 끝난 뒤 실제 처리 시각을 얻는다.
        Instant handledAt = clock.instant();

        try {
            switch (nextStatus) {
                case RESOLVED -> report.resolve(
                        actorMemberId,
                        normalizedReason,
                        handledAt
                );
                case DISMISSED -> report.dismiss(
                        actorMemberId,
                        normalizedReason,
                        handledAt
                );
                default -> throw new IllegalStateException(
                        "지원하지 않는 신고 처리 상태: " + nextStatus
                );
            }
        } catch (ContentReportAlreadyHandledException exception) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.REPORT_ALREADY_HANDLED
            );
        }

        // 조회한 엔티티는 영속 상태이므로 변경 감지로 저장
        // 게시글·댓글·회원 상태는 변경하지 않는다.
        return AdminReportHandlingResult.from(report);
    }

    private void validateAdminForUpdate(
            @Nullable Long actorMemberId,
            long credentialVersion
    ) {
        if (actorMemberId == null
                || actorMemberId <= 0
                || credentialVersion < 0) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED
            );
        }

        Member actor = memberRepository
                .findByIdForUpdate(actorMemberId)
                .orElseThrow(() ->
                        new AdminReportHandlingException(
                                AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED
                        )
                );

        if (actor.getStatus() != MemberStatus.ACTIVE
                || actor.getCredentialVersion() != credentialVersion) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED
            );
        }

        if(actor.getRole() != MemberRole.ADMIN) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.ADMIN_REQUIRED
            );
        }
    }

    private ContentReport findReportForUpdate(
            @Nullable Long reportId
    ) {
        if (reportId == null || reportId <= 0) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.REPORT_NOT_FOUND
            );
        }

        return reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() ->
                        new AdminReportHandlingException(
                                AdminReportHandlingException.Code.REPORT_NOT_FOUND
                        )
                );
    }

    private String normalizeHandlingReason(
            @Nullable String handlingReason
    ) {
        try {
            return ContentReportHandlingPolicy
                    .normalizeHandlingReason(handlingReason);
        } catch (IllegalArgumentException exception) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.INVALID_HANDLING_REASON
            );
        }
    }
}
