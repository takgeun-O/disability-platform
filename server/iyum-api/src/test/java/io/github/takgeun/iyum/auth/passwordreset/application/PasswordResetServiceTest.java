package io.github.takgeun.iyum.auth.passwordreset.application;

import io.github.takgeun.iyum.auth.passwordreset.domain.PasswordResetToken;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenHasher;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Long MEMBER_ID = 7L;

    private static final String RAW_TOKEN = "a".repeat(43);
    private static final String TOKEN_HASH = "b".repeat(64);

    private static final String OLD_HASH = "old-password-hash";
    private static final String NEW_HASH = "new-password-hash";
    private static final String NEW_PASSWORD = "NewPassword123!";

    private static final Instant NOW =
            Instant.parse("2026-10-21T05:00:00Z");

    private static final Instant EXPIRES_AT =
            NOW.plusSeconds(1800);

    @Mock
    MemberRepository memberRepository;

    @Mock
    PasswordResetTokenRepository tokenRepository;

    @Mock
    PasswordResetTokenHasher tokenHasher;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    Clock clock;

    @Mock
    ApplicationEventPublisher events;

    PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                memberRepository,
                tokenRepository,
                tokenHasher,
                passwordEncoder,
                clock,
                events
        );
    }

    @Test
    void 비밀번호를_변경하고_현재_토큰을_사용하며_다른_토큰을_폐기한다() {
        Member member = activeMember();
        PasswordResetToken token = usableToken();

        PasswordResetToken otherToken = PasswordResetToken.issue(
                MEMBER_ID,
                "c".repeat(64),
                NOW.minusSeconds(120),
                EXPIRES_AT
        );

        prepareLookup(member, token);

        when(passwordEncoder.matches(NEW_PASSWORD, OLD_HASH))
                .thenReturn(false);
        when(passwordEncoder.encode(NEW_PASSWORD))
                .thenReturn(NEW_HASH);
        when(tokenRepository.findUnconsumedForUpdate(MEMBER_ID))
                .thenReturn(List.of(token, otherToken));

        service.reset(RAW_TOKEN, NEW_PASSWORD);

        assertThat(member.getPasswordHash()).isEqualTo(NEW_HASH);
        assertThat(member.getCredentialVersion()).isEqualTo(1L);

        assertThat(token.getUsedAt()).isEqualTo(NOW);
        assertThat(token.getRevokedAt()).isNull();

        assertThat(otherToken.getUsedAt()).isNull();
        assertThat(otherToken.getRevokedAt()).isEqualTo(NOW);

        // 회원 잠금 → 토큰 잠금 → DB 반영 → 이벤트 발행 순서를 확인한다.
        var order = inOrder(
                memberRepository,
                tokenRepository,
                events
        );

        order.verify(tokenRepository)
                .findMemberIdByTokenHash(TOKEN_HASH);

        order.verify(memberRepository)
                .findByIdForUpdate(MEMBER_ID);

        order.verify(tokenRepository)
                .findByTokenHashForUpdate(TOKEN_HASH);

        order.verify(tokenRepository)
                .findUnconsumedForUpdate(MEMBER_ID);

        order.verify(memberRepository).flush();

        var captor = ArgumentCaptor.forClass(PasswordResetCompleted.class);

        order.verify(events).publishEvent(captor.capture());

        PasswordResetCompleted event = captor.getValue();

        assertThat(event.memberId()).isEqualTo(MEMBER_ID);
        assertThat(event.email()).isEqualTo(member.getEmail());
        assertThat(event.changedAt()).isEqualTo(NOW);

        assertThat(event.toString())
                .isEqualTo("PasswordResetCompleted[REDACTED]");

        verifyNoMoreInteractions(events);
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "used", "revoked", "future"})
    void 사용할_수_없는_토큰이면_변경하지_않는다(String state) {
        Member member = activeMember();

        PasswordResetToken token = switch (state) {
            case "expired" -> PasswordResetToken.issue(
                    MEMBER_ID,
                    TOKEN_HASH,
                    NOW.minusSeconds(60),
                    NOW
            );
            case "future" -> PasswordResetToken.issue(
                    MEMBER_ID,
                    TOKEN_HASH,
                    NOW.plusSeconds(60),
                    EXPIRES_AT
            );
            default -> usableToken();
        };

        if ("used".equals(state)) {
            token.use(NOW.minusSeconds(30));
        }
        if ("revoked".equals(state)) {
            token.revoke(NOW.minusSeconds(30));
        }

        Instant usedBefore = token.getUsedAt();
        Instant revokedBefore = token.getRevokedAt();

        prepareLookup(member, token);

        assertThatThrownBy(() ->
                service.reset(RAW_TOKEN, NEW_PASSWORD)
        )
                .isInstanceOfSatisfying(
                        PasswordResetException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        PasswordResetException.Code
                                                .PASSWORD_RESET_TOKEN_INVALID
                                )
                );

        assertMemberUnchanged(member);

        assertThat(token.getUsedAt()).isEqualTo(usedBefore);
        assertThat(token.getRevokedAt()).isEqualTo(revokedBefore);

        verifyNoInteractions(passwordEncoder);
        verify(tokenRepository, never())
                .findUnconsumedForUpdate(any());
        verify(memberRepository, never()).flush();
        verifyNoInteractions(events);
    }

    @Test
    void 현재와_같은_비밀번호이면_토큰을_소비하지_않는다() {
        Member member = activeMember();
        PasswordResetToken token = usableToken();

        prepareLookup(member, token);

        when(passwordEncoder.matches(NEW_PASSWORD, OLD_HASH))
                .thenReturn(true);

        assertThatThrownBy(() ->
                service.reset(RAW_TOKEN, NEW_PASSWORD)
        )
                .isInstanceOfSatisfying(
                        PasswordResetException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        PasswordResetException.Code
                                                .PASSWORD_RESET_PASSWORD_UNCHANGED
                                )
                );

        assertMemberUnchanged(member);
        assertTokenUnused(token);

        verify(passwordEncoder, never()).encode(any());
        verify(tokenRepository, never())
                .findUnconsumedForUpdate(any());
        verify(memberRepository, never()).flush();
        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "Ab12345",
            "abcdefgh",
            "12345678"
    })
    void 잘못된_비밀번호이면_저장소를_조회하지_않는다(String password) {
        assertInvalidPassword(password);
        verifyNoInteractions(events);
    }

    @Test
    void 비밀번호가_UTF8_72바이트를_초과하면_거절한다() {
        // 한글 24자 = 72바이트, A1을 더하면 총 74바이트다.
        String password = "가".repeat(24) + "A1";

        assertInvalidPassword(password);
        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "short-token",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa+"
    })
    void 토큰_형식이_잘못되면_저장소를_조회하지_않는다(String rawToken) {
        assertThatThrownBy(() ->
                service.reset(rawToken, NEW_PASSWORD)
        )
                .isInstanceOfSatisfying(
                        PasswordResetException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        PasswordResetException.Code
                                                .PASSWORD_RESET_TOKEN_INVALID
                                )
                );

        verifyNoInteractions(
                tokenHasher,
                tokenRepository,
                memberRepository,
                passwordEncoder,
                clock
        );
        verifyNoInteractions(events);
    }

    @Test
    void 해시_생성_중_토큰이_만료되면_변경하지_않는다() {
        Member member = activeMember();
        PasswordResetToken token = usableToken();

        prepareLookup(member, token);

        // 첫 확인에서는 유효하지만 최종 확인에서는 만료된 상황이다.
        when(clock.instant()).thenReturn(NOW, EXPIRES_AT);

        when(passwordEncoder.matches(NEW_PASSWORD, OLD_HASH))
                .thenReturn(false);
        when(passwordEncoder.encode(NEW_PASSWORD))
                .thenReturn(NEW_HASH);
        when(tokenRepository.findUnconsumedForUpdate(MEMBER_ID))
                .thenReturn(List.of(token));

        assertThatThrownBy(() ->
                service.reset(RAW_TOKEN, NEW_PASSWORD)
        )
                .isInstanceOfSatisfying(
                        PasswordResetException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        PasswordResetException.Code
                                                .PASSWORD_RESET_TOKEN_INVALID
                                )
                );

        assertMemberUnchanged(member);
        assertTokenUnused(token);

        verify(passwordEncoder).encode(NEW_PASSWORD);
        verify(memberRepository, never()).flush();
        verifyNoInteractions(events);
    }

    @Test
    void 비밀번호_해시_생성이_실패하면_변경하지_않는다() {
        Member member = activeMember();
        PasswordResetToken token = usableToken();

        prepareLookup(member, token);

        when(passwordEncoder.matches(NEW_PASSWORD, OLD_HASH))
                .thenReturn(false);

        IllegalStateException failure =
                new IllegalStateException("테스트용 인코딩 실패");

        when(passwordEncoder.encode(NEW_PASSWORD))
                .thenThrow(failure);

        assertThatThrownBy(() ->
                service.reset(RAW_TOKEN, NEW_PASSWORD)
        ).isSameAs(failure);

        assertMemberUnchanged(member);
        assertTokenUnused(token);

        verify(tokenRepository, never())
                .findUnconsumedForUpdate(any());
        verify(memberRepository, never()).flush();
        verifyNoInteractions(events);
    }

    @Test
    void DB_반영에_실패하면_변경_완료_이벤트를_발행하지_않는다() {
        Member member = activeMember();
        PasswordResetToken token = usableToken();

        prepareLookup(member, token);

        when(passwordEncoder.matches(NEW_PASSWORD, OLD_HASH))
                .thenReturn(false);
        when(passwordEncoder.encode(NEW_PASSWORD))
                .thenReturn(NEW_HASH);
        when(tokenRepository.findUnconsumedForUpdate(MEMBER_ID))
                .thenReturn(List.of(token));

        IllegalStateException failure =
                new IllegalStateException("테스트용 DB 반영 실패");

        doThrow(failure)
                .when(memberRepository)
                .flush();

        assertThatThrownBy(() ->
                service.reset(RAW_TOKEN, NEW_PASSWORD)
        ).isSameAs(failure);

        verify(memberRepository).flush();
        verifyNoInteractions(events);
    }

    private void prepareLookup(
            Member member,
            PasswordResetToken token
    ) {
        when(tokenHasher.hash(RAW_TOKEN))
                .thenReturn(TOKEN_HASH);
        when(tokenRepository.findMemberIdByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(MEMBER_ID));
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(tokenRepository.findByTokenHashForUpdate(TOKEN_HASH))
                .thenReturn(Optional.of(token));
        when(clock.instant()).thenReturn(NOW);
    }

    private void assertInvalidPassword(String password) {
        assertThatThrownBy(() ->
                service.reset(RAW_TOKEN, password)
        )
                .isInstanceOfSatisfying(
                        PasswordResetException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        PasswordResetException.Code
                                                .PASSWORD_RESET_PASSWORD_INVALID
                                )
                );

        verifyNoInteractions(
                tokenHasher,
                tokenRepository,
                memberRepository,
                passwordEncoder,
                clock
        );
    }

    private void assertMemberUnchanged(Member member) {
        assertThat(member.getPasswordHash()).isEqualTo(OLD_HASH);
        assertThat(member.getCredentialVersion()).isZero();
    }

    private void assertTokenUnused(PasswordResetToken token) {
        assertThat(token.getUsedAt()).isNull();
        assertThat(token.getRevokedAt()).isNull();
    }

    private Member activeMember() {
        Member member = Member.createPending(
                "reset@example.com",
                OLD_HASH,
                "재설정회원"
        );

        member.activeAfterEmailVerification();

        return member;
    }

    private PasswordResetToken usableToken() {
        return PasswordResetToken.issue(
                MEMBER_ID,
                TOKEN_HASH,
                NOW.minusSeconds(60),
                EXPIRES_AT
        );
    }
}