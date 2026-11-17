package io.github.takgeun.iyum.admin.dashboard.application;

import io.github.takgeun.iyum.admin.dashboard.infrastructure.AdminDashboardReadRepository;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardReadService {

    private static final ZoneId DASHBOARD_ZONE =
            ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final AdminDashboardReadRepository dashboardReadRepository;
    private final Clock clock;

    /**
     * actorMemberId와 credentialVersion은
     * 로그인한 MemberPrincipal에서 전달 받기
     */
    public AdminDashboardResult getDashboard(
            @Nullable Long actorMemberId,
            long credentialVersion
    ) {
        // 유효한 actorMemberId인지
        // ACTIVE인지, ADMIN인지 모두 체크
        validateAdmin(actorMemberId, credentialVersion);

        // 하나의 기준 시각으로 응답 시각과 집계 날짜를 정한다.
        // 이렇게 시간을 한 번만 가져오는 이유
        // baseDate, 시작 시각, 종료 시각을 각각 현재 시간을 따로 계산해서 가져오면
        // 자정을 지나는 순간 응답의 날짜와 집계의 기간이 어긋날 수 있음.
        Instant queriedAt = clock.instant();

        LocalDate baseDate = queriedAt
                .atZone(DASHBOARD_ZONE)
                .toLocalDate();

        Instant fromInclusive = baseDate
                .atStartOfDay(DASHBOARD_ZONE)
                .toInstant();

        Instant toExclusive = baseDate
                .plusDays(1)
                .atStartOfDay(DASHBOARD_ZONE)
                .toInstant();

        AdminDashboardCountsResult counts = dashboardReadRepository.findCounts(
                fromInclusive,
                toExclusive
        );

        List<AdminDashboardPendingReportResult> pendingReports =
                dashboardReadRepository.findPendingReports();

        return new AdminDashboardResult(
                queriedAt,
                baseDate,
                counts,
                pendingReports
        );
    }

    private void validateAdmin(
            @Nullable Long actorMemberId,
            long credentialVersion
    ) {
        if (actorMemberId == null
                || actorMemberId <= 0
                || credentialVersion < 0) {
            throw new AdminDashboardReadException(
                    AdminDashboardReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        Member actor = memberRepository.findById(actorMemberId)
                .orElseThrow(() ->
                        new AdminDashboardReadException(
                                AdminDashboardReadException.Code.AUTHENTICATION_REQUIRED
                        )
                );

        if (actor.getStatus() != MemberStatus.ACTIVE
                || actor.getCredentialVersion() != credentialVersion) {
            throw new AdminDashboardReadException(
                    AdminDashboardReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        if(actor.getRole() != MemberRole.ADMIN) {
            throw new AdminDashboardReadException(
                    AdminDashboardReadException.Code.ADMIN_REQUIRED
            );
        }
    }
}
