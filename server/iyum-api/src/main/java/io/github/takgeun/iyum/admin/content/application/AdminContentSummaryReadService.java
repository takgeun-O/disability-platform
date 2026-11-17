package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.admin.content.infrastructure.AdminContentSummaryReadRepository;
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
public class AdminContentSummaryReadService {

    private static final ZoneId SUMMARY_ZONE =
            ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final AdminContentSummaryReadRepository summaryReadRepository;
    private final Clock clock;

    /**
     * actorMemberId와 credentialVersion은
     * 로그인한 MemberPrincipal에서 전달받는다.
     */
    public AdminContentSummaryResult getSummary(
            @Nullable Long actorMemberId,
            long credentialVersion
    ) {
        validateAdmin(
                actorMemberId,
                credentialVersion
        );

        // 기준 시각은 한번만 읽는다.
        // 처리 도중 자정이 지나는 케이스에서도 응답 날짜와 집계 범위가 일치하도록 하기 위함.
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

        AdminContentSummaryCountsResult counts =
                summaryReadRepository.findCounts(
                        fromInclusive,
                        toExclusive
                );

        return new AdminContentSummaryResult(
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
            throw new AdminContentReadException(
                    AdminContentReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        // 여기서는 관리자 권한을 확인한 뒤 통계를 조회할 뿐
        // 회원 상태를 변경하지 않기 때문에 잠금조회하지 않음.
        // 즉, 회원의 상태를 조회부터 작업 완료까지 고정해야 할 요구가 없으니 일반 조회로도 충분
        // 잠금 조회가 필요한 경우는 비밀번호 변경과 같은 케이스임.
        Member actor = memberRepository.findById(actorMemberId)
                .orElseThrow(() ->
                        new AdminContentReadException(
                                AdminContentReadException.Code.AUTHENTICATION_REQUIRED
                        )
                );

        if (actor.getStatus() != MemberStatus.ACTIVE
                || actor.getCredentialVersion() != credentialVersion) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        if (actor.getRole() != MemberRole.ADMIN) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.ADMIN_REQUIRED
            );
        }
    }
}
