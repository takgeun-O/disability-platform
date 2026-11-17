package io.github.takgeun.iyum.admin.operation.application;

import io.github.takgeun.iyum.admin.operation.infrastructure.AdminOperationSummaryReadRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 관리자 검증 후 집계를 실행하는지
 * 기준 시각을 한 번만 읽는지
 * 한국 자정·월말·연말·윤년의 날짜 범위가 정확한지
 * 인증·권한 검증에 실패하면 집계 조회를 실행하지 않는지
 */
@ExtendWith(MockitoExtension.class)
class AdminOperationSummaryReadServiceTest {

    private static final Long ACTOR_ID = 40L;
    private static final long VERSION = 3L;

    private static final Instant NOW =
            Instant.parse("2026-11-18T03:00:00Z");

    private static final Instant START =
            Instant.parse("2026-11-17T15:00:00Z");

    private static final Instant END =
            Instant.parse("2026-11-18T15:00:00Z");

    @Mock
    MemberRepository memberRepository;

    @Mock
    AdminOperationSummaryReadRepository summaryReadRepository;

    @Mock
    Clock clock;

    @InjectMocks
    AdminOperationSummaryReadService service;

    @ParameterizedTest
    @ValueSource(longs = {0L, VERSION})
    void 관리자_검증_후_한_기준_시각으로_운영_기록_요약을_조회한다(
            long credentialVersion
    ) {
        allowAdmin(credentialVersion);

        // 시계를 다시 읽으면 다음 날로 바뀌도록 준비한다.
        when(clock.instant())
                .thenReturn(NOW, END);

        AdminOperationSummaryCountsResult counts =
                new AdminOperationSummaryCountsResult(
                        20L,
                        7L,
                        3L,
                        5L
                );

        when(summaryReadRepository.findCounts(
                START,
                END
        )).thenReturn(counts);

        AdminOperationSummaryResult result = service.getSummary(
                ACTOR_ID,
                credentialVersion
        );

        assertThat(result.queriedAt()).isEqualTo(NOW);
        assertThat(result.baseDate())
                .isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(result.counts()).isEqualTo(counts);

        var order = inOrder(
                memberRepository,
                clock,
                summaryReadRepository
        );

        order.verify(memberRepository)
                .findById(ACTOR_ID);

        order.verify(clock)
                .instant();

        order.verify(summaryReadRepository)
                .findCounts(
                        START,
                        END
                );

        verifyNoMoreInteractions(
                memberRepository,
                clock,
                summaryReadRepository
        );
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-17T14:59:59.999999Z, 2026-11-17, "
                    + "2026-11-16T15:00:00Z, 2026-11-17T15:00:00Z",

            "2026-11-17T15:00:00Z, 2026-11-18, "
                    + "2026-11-17T15:00:00Z, 2026-11-18T15:00:00Z",

            "2026-11-30T15:00:00Z, 2026-12-01, "
                    + "2026-11-30T15:00:00Z, 2026-12-01T15:00:00Z",

            "2026-12-31T15:00:00Z, 2027-01-01, "
                    + "2026-12-31T15:00:00Z, 2027-01-01T15:00:00Z",

            "2028-02-28T15:00:00Z, 2028-02-29, "
                    + "2028-02-28T15:00:00Z, 2028-02-29T15:00:00Z"
    })
    void 한국_자정과_월말_연말_윤년의_집계_기간을_계산한다(
            String nowText,
            String dateText,
            String startText,
            String endText
    ) {
        allowAdmin(VERSION);

        Instant now = Instant.parse(nowText);
        LocalDate expectedDate = LocalDate.parse(dateText);
        Instant expectedStart = Instant.parse(startText);
        Instant expectedEnd = Instant.parse(endText);

        AdminOperationSummaryCountsResult counts =
                new AdminOperationSummaryCountsResult(
                        0L,
                        0L,
                        0L,
                        0L
                );

        when(clock.instant())
                .thenReturn(now);

        when(summaryReadRepository.findCounts(
                expectedStart,
                expectedEnd
        )).thenReturn(counts);

        AdminOperationSummaryResult result = service.getSummary(
                ACTOR_ID,
                VERSION
        );

        assertThat(result.queriedAt()).isEqualTo(now);
        assertThat(result.baseDate()).isEqualTo(expectedDate);
        assertThat(result.counts()).isEqualTo(counts);

        verify(summaryReadRepository).findCounts(
                expectedStart,
                expectedEnd
        );

        verify(clock).instant();
        verifyNoMoreInteractions(clock);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 잘못된_회원_ID는_회원과_집계_조회_전에_거부한다(
            Long actorMemberId
    ) {
        assertRejected(
                actorMemberId,
                VERSION,
                AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 음수_인증_버전은_회원과_집계_조회_전에_거부한다() {
        assertRejected(
                ACTOR_ID,
                -1L,
                AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 존재하지_않는_회원은_인증_실패로_거부한다() {
        when(memberRepository.findById(ACTOR_ID))
                .thenReturn(Optional.empty());

        assertRejected(
                ACTOR_ID,
                VERSION,
                AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
        );

        verify(memberRepository).findById(ACTOR_ID);
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = "ACTIVE",
            mode = EnumSource.Mode.EXCLUDE
    )
    void 활성_상태가_아닌_회원은_관리자여도_거부한다(
            MemberStatus status
    ) {
        Member actor = actor(
                status,
                MemberRole.ADMIN,
                VERSION
        );

        when(memberRepository.findById(ACTOR_ID))
                .thenReturn(Optional.of(actor));

        assertRejected(
                ACTOR_ID,
                VERSION,
                AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
        );

        verify(memberRepository).findById(ACTOR_ID);
    }

    @ParameterizedTest
    @EnumSource(MemberRole.class)
    void 인증_버전이_다르면_역할보다_인증_실패를_우선한다(
            MemberRole role
    ) {
        Member actor = actor(
                MemberStatus.ACTIVE,
                role,
                VERSION + 1
        );

        when(memberRepository.findById(ACTOR_ID))
                .thenReturn(Optional.of(actor));

        assertRejected(
                ACTOR_ID,
                VERSION,
                AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
        );

        verify(memberRepository).findById(ACTOR_ID);
    }

    @Test
    void 유효한_로그인이라도_일반_회원은_관리자_권한_오류로_거부한다() {
        Member actor = actor(
                MemberStatus.ACTIVE,
                MemberRole.USER,
                VERSION
        );

        when(memberRepository.findById(ACTOR_ID))
                .thenReturn(Optional.of(actor));

        assertRejected(
                ACTOR_ID,
                VERSION,
                AdminOperationLogReadException.Code.ADMIN_REQUIRED
        );

        verify(memberRepository).findById(ACTOR_ID);
    }

    private void allowAdmin(
            long credentialVersion
    ) {
        Member actor = actor(
                MemberStatus.ACTIVE,
                MemberRole.ADMIN,
                credentialVersion
        );

        when(memberRepository.findById(ACTOR_ID))
                .thenReturn(Optional.of(actor));
    }

    private Member actor(
            MemberStatus status,
            MemberRole role,
            long credentialVersion
    ) {
        Member member = Member.createPending(
                "operation-summary-admin@example.com",
                "test-only-password-hash",
                "회원요약관리자"
        );

        // 접근 검증에 필요한 회원 상태를 테스트에서 구성한다.
        ReflectionTestUtils.setField(
                member,
                "id",
                ACTOR_ID
        );
        ReflectionTestUtils.setField(
                member,
                "status",
                status
        );
        ReflectionTestUtils.setField(
                member,
                "role",
                role
        );
        ReflectionTestUtils.setField(
                member,
                "credentialVersion",
                credentialVersion
        );

        return member;
    }

    private void assertRejected(
            Long actorMemberId,
            long credentialVersion,
            AdminOperationLogReadException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                service.getSummary(
                        actorMemberId,
                        credentialVersion
                )
        ).isInstanceOfSatisfying(
                AdminOperationLogReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );

        // 접근 검증에 실패하면 집계와 기준 시각 계산을 시작하지 않는다.
        verifyNoInteractions(
                summaryReadRepository,
                clock
        );
    }
}