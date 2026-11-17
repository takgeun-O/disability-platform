package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.admin.report.infrastructure.AdminReportSummaryReadRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportSummaryReadService {

    private static final ZoneId SUMMARY_ZONE =
            ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final AdminReportSummaryReadRepository summaryReadRepository;
    private final Clock clock;

    /**
     * actorMemberId와 credentialVersion은
     * 로그인한 MemberPrincipal에서 전달 받는다.
     */
    public AdminReportSummaryResult getSummary(
            @Nullable Long actorMemberId,
            long credentialVersion
    ) {
        validateAdmin(
                actorMemberId,
                credentialVersion
        );

        // instant() 메서드는 딱 한번만 호출해야 한다.
        // 자정을 지나는 순간이 있을 경우 응답의 기준 날짜와 집계 범위가
        // 맞지 않을 수 있기 때문
        Instant queriedAt = clock.instant();

        LocalDate baseDate = queriedAt
                .atZone(SUMMARY_ZONE)
                .toLocalDate();

        Instant fromInclusive = baseDate
                .atStartOfDay(SUMMARY_ZONE)
                .toInstant();

        Instant toExclusive = baseDate
                .plusDays(1)
                .atStartOfDay(SUMMARY_ZONE)
                .toInstant();

        AdminReportSummaryCountsResult counts =
                summaryReadRepository.findCounts(
                        fromInclusive,
                        toExclusive
                );

        return new AdminReportSummaryResult(
                queriedAt,
                baseDate,
                counts
        );
    }

    private void validateAdmin(
            @Nullable Long actorMemberId,
            long credentialVersion
    ) {
        if (actorMemberId == null
                || actorMemberId <= 0
                || credentialVersion < 0) {
            throw new AdminReportReadException(
                    AdminReportReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        Member actor = memberRepository.findById(actorMemberId)
                .orElseThrow(() ->
                        new AdminReportReadException(
                                AdminReportReadException.Code.AUTHENTICATION_REQUIRED
                        )
                );

        if (actor.getStatus() != MemberStatus.ACTIVE
                || actor.getCredentialVersion() != credentialVersion) {
            throw new AdminReportReadException(
                    AdminReportReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        if (actor.getRole() != MemberRole.ADMIN) {
            throw new AdminReportReadException(
                    AdminReportReadException.Code.ADMIN_REQUIRED
            );
        }
    }
}
