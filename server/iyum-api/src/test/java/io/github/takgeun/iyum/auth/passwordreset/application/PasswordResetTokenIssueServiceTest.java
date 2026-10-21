package io.github.takgeun.iyum.auth.passwordreset.application;

import io.github.takgeun.iyum.auth.passwordreset.config.PasswordResetProperties;
import io.github.takgeun.iyum.auth.passwordreset.domain.PasswordResetToken;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenGenerator;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenHasher;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenIssueServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final String EMAIL = "reset@example.com";

    private static final String RAW_TOKEN = "test-only-raw-token";
    private static final String TOKEN_HASH = "a".repeat(64);

    private static final Instant NOW =
            Instant.parse("2026-10-21T04:00:00Z");

    @Mock
    MemberRepository memberRepository;

    @Mock
    PasswordResetTokenRepository tokenRepository;

    @Mock
    PasswordResetTokenGenerator tokenGenerator;

    @Mock
    PasswordResetTokenHasher tokenHasher;

    @Mock
    ApplicationEventPublisher events;

    @Mock
    Member member;

    PasswordResetTokenIssueService service;

    @BeforeEach
    void setUp() {
        var properties = new PasswordResetProperties(
                Duration.ofMinutes(30),
                Duration.ofSeconds(60),
                5,
                30
        );

        service = new PasswordResetTokenIssueService(
                memberRepository,
                tokenRepository,
                tokenGenerator,
                tokenHasher,
                properties,
                events,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void 최초_요청은_이메일을_정규화하고_해시를_저장한_뒤_이벤트를_발행한다() {
        givenActiveMember();
        givenIssuanceAllowed(Optional.empty(), 0);
        givenGeneratedToken();

        service.issue("  RESET@EXAMPLE.COM  ");

        verify(memberRepository).findByEmailForUpdate(EMAIL);

        var tokenCaptor =
                ArgumentCaptor.forClass(PasswordResetToken.class);

        verify(tokenRepository).saveAndFlush(tokenCaptor.capture());

        PasswordResetToken saved = tokenCaptor.getValue();

        assertThat(saved.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getTokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getExpiresAt())
                .isEqualTo(NOW.plusSeconds(1800));
        assertThat(saved.getUsedAt()).isNull();
        assertThat(saved.getRevokedAt()).isNull();

        var eventCaptor =
                ArgumentCaptor.forClass(PasswordResetMailRequested.class);

        verify(events).publishEvent(eventCaptor.capture());

        PasswordResetMailRequested event = eventCaptor.getValue();

        assertThat(event.memberId()).isEqualTo(MEMBER_ID);
        assertThat(event.email()).isEqualTo(EMAIL);
        assertThat(event.rawToken()).isEqualTo(RAW_TOKEN);
        assertThat(event.expiresAt())
                .isEqualTo(NOW.plusSeconds(1800));

        assertThat(event.toString())
                .doesNotContain(EMAIL, RAW_TOKEN);
    }

    @Test
    void 미가입_이메일은_발급하지_않고_종료한다() {
        when(memberRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.empty());

        service.issue(EMAIL);

        verifyNoInteractions(
                tokenRepository,
                tokenGenerator,
                tokenHasher,
                events
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = "ACTIVE",
            mode = EnumSource.Mode.EXCLUDE
    )
    void ACTIVE가_아니면_발급하지_않는다(MemberStatus status) {
        when(memberRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(member));
        when(member.getStatus()).thenReturn(status);

        service.issue(EMAIL);

        verifyNoInteractions(
                tokenRepository,
                tokenGenerator,
                tokenHasher,
                events
        );
    }

    @Test
    void 마지막_발급에서_60초가_지나지_않으면_기존_토큰을_건드리지_않는다() {
        givenActiveMember();

        when(tokenRepository.findLatestIssuedAt(MEMBER_ID))
                .thenReturn(Optional.of(NOW.minusSeconds(59)));

        service.issue(EMAIL);

        verify(tokenRepository).findLatestIssuedAt(MEMBER_ID);
        verifyNoMoreInteractions(tokenRepository);
        verifyNoInteractions(tokenGenerator, tokenHasher, events);
    }

    @Test
    void 정확히_60초가_지나고_이력이_4개이면_기존_토큰을_폐기하고_발급한다() {
        givenActiveMember();

        when(tokenRepository.findLatestIssuedAt(MEMBER_ID))
                .thenReturn(Optional.of(NOW.minusSeconds(60)));

        when(tokenRepository.countIssuedInWindow(
                MEMBER_ID,
                NOW.minusSeconds(3600),
                NOW
        )).thenReturn(4L);

        PasswordResetToken previous = PasswordResetToken.issue(
                MEMBER_ID,
                "b".repeat(64),
                NOW.minusSeconds(60),
                NOW.plusSeconds(1740)
        );

        when(tokenRepository.findUnconsumedForUpdate(MEMBER_ID))
                .thenReturn(List.of(previous));

        givenGeneratedToken();

        service.issue(EMAIL);

        assertThat(previous.getRevokedAt()).isEqualTo(NOW);
        assertThat(previous.getUsedAt()).isNull();

        verify(tokenRepository).saveAndFlush(
                any(PasswordResetToken.class)
        );

        verify(events).publishEvent(
                any(PasswordResetMailRequested.class)
        );
    }

    @Test
    void 최근_1시간에_이미_5개를_발급했다면_추가_발급하지_않는다() {
        givenActiveMember();

        when(tokenRepository.findLatestIssuedAt(MEMBER_ID))
                .thenReturn(Optional.of(NOW.minusSeconds(60)));

        when(tokenRepository.countIssuedInWindow(
                MEMBER_ID,
                NOW.minusSeconds(3600),
                NOW
        )).thenReturn(5L);

        service.issue(EMAIL);

        verify(tokenRepository, never())
                .findUnconsumedForUpdate(any());

        verify(tokenRepository, never())
                .saveAndFlush(any(PasswordResetToken.class));

        verifyNoInteractions(tokenGenerator, tokenHasher, events);
    }

    @Test
    void 토큰_저장에_실패하면_메일_이벤트를_발행하지_않는다() {
        givenActiveMember();
        givenIssuanceAllowed(Optional.empty(), 0);

        when(tokenGenerator.generate()).thenReturn(RAW_TOKEN);
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);

        when(tokenRepository.saveAndFlush(
                any(PasswordResetToken.class)
        )).thenThrow(new IllegalStateException("테스트용 저장 실패"));

        assertThatThrownBy(() -> service.issue(EMAIL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("테스트용 저장 실패");

        verifyNoInteractions(events);
    }

    private void givenActiveMember() {
        when(memberRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(member));
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getId()).thenReturn(MEMBER_ID);
    }

    private void givenIssuanceAllowed(
            Optional<Instant> latestIssuedAt,
            long issuedCount
    ) {
        when(tokenRepository.findLatestIssuedAt(MEMBER_ID))
                .thenReturn(latestIssuedAt);

        when(tokenRepository.countIssuedInWindow(
                MEMBER_ID,
                NOW.minusSeconds(3600),
                NOW
        )).thenReturn(issuedCount);

        when(tokenRepository.findUnconsumedForUpdate(MEMBER_ID))
                .thenReturn(List.of());
    }

    private void givenGeneratedToken() {
        when(tokenGenerator.generate()).thenReturn(RAW_TOKEN);
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(member.getEmail()).thenReturn(EMAIL);
    }
}