package io.github.takgeun.iyum.admin.member.application;

import io.github.takgeun.iyum.admin.member.infrastructure.AdminMemberSummaryReadRepository;
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
public class AdminMemberSummaryReadService {

    private static final ZoneId SUMMARY_ZONE =
            ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final AdminMemberSummaryReadRepository summaryReadRepository;
    private final Clock clock;

    /**
     * actorMemberId와 credentialVersion은
     * 로그인한 MemberPrincipal에서 전달받는다.
     */
    public AdminMemberSummaryResult getSummary(
            @Nullable Long actorMemberId,
            long credentialVersion
    ) {
        validateAdmin(
                actorMemberId,
                credentialVersion
        );

        // 기준 시각을 한 번만 읽는다.
        // 처리 도중 자정이 지나 응답 날짜와 집계 범위가 일치하도록!
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

        AdminMemberSummaryCountsResult counts = summaryReadRepository.findCounts(
                fromInclusive,
                toExclusive
        );

        return new AdminMemberSummaryResult(
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
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        // 조회 전용 기능이므로 기존 회원 조회 서비스처럼
        // 잠금 없이 현재 관리자 자격 확인
        Member actor = memberRepository.findById(actorMemberId)
                .orElseThrow(() ->
                        new AdminMemberReadException(
                                AdminMemberReadException.Code.AUTHENTICATION_REQUIRED
                        )
                );

        if (actor.getStatus() != MemberStatus.ACTIVE
                || actor.getCredentialVersion() != credentialVersion) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        if(actor.getRole() != MemberRole.ADMIN) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.ADMIN_REQUIRED
            );
        }
    }
}
