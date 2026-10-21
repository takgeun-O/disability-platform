package io.github.takgeun.iyum.auth.passwordreset.application;

import io.github.takgeun.iyum.auth.passwordreset.domain.PasswordResetToken;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenHasher;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenRepository;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenValidationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-21T05:00:00Z");
    private static final String RAW = "a".repeat(43);
    private static final Long MEMBER_ID = 7L;
    @Mock PasswordResetTokenRepository tokens;
    @Mock MemberRepository members;
    private final PasswordResetTokenHasher hasher = new PasswordResetTokenHasher();
    private PasswordResetTokenValidationService service;

    @BeforeEach void setup() {
        service = new PasswordResetTokenValidationService(tokens, members, hasher, Clock.fixed(NOW, ZoneOffset.UTC));
    }
    private PasswordResetToken token(Instant created, Instant expires) {
        var token = PasswordResetToken.issue(MEMBER_ID, hasher.hash(RAW), created, expires);
        when(tokens.findByTokenHash(hasher.hash(RAW))).thenReturn(Optional.of(token));
        return token;
    }
    private void invalid(String value) {
        assertThatThrownBy(() -> service.validate(value)).isInstanceOfSatisfying(PasswordResetException.class,
                e -> assertThat(e.getCode()).isEqualTo(PasswordResetException.Code.PASSWORD_RESET_TOKEN_INVALID));
    }
    @Test void 유효_토큰을_반복_조회해도_소비하거나_쓰기_잠금을_잡지_않는다() {
        var token = token(NOW.minusSeconds(60), NOW.plusSeconds(1));
        when(members.existsByIdAndStatus(MEMBER_ID, MemberStatus.ACTIVE)).thenReturn(true);
        service.validate(RAW); service.validate(RAW);
        assertThat(token.getUsedAt()).isNull(); assertThat(token.getRevokedAt()).isNull();
        assertThat(token.getExpiresAt()).isEqualTo(NOW.plusSeconds(1)); assertThat(token.getVersion()).isZero();
        verify(tokens, times(2)).findByTokenHash(hasher.hash(RAW));
        verify(members, times(2)).existsByIdAndStatus(MEMBER_ID, MemberStatus.ACTIVE);
        verifyNoMoreInteractions(tokens, members);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"bad", " ", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n"})
    void 형식_오류는_조회하지_않는다(String value) { invalid(value); verifyNoInteractions(tokens, members); }
    @Test void 존재하지_않는_토큰은_거절한다() {
        when(tokens.findByTokenHash(hasher.hash(RAW))).thenReturn(Optional.empty());
        invalid(RAW); verifyNoInteractions(members);
    }
    @ParameterizedTest @ValueSource(strings = {"future", "expired", "boundary", "used", "revoked"})
    void 도메인의_발급_만료_사용_폐기_규칙을_그대로_적용한다(String state) {
        var token = token(state.equals("future") ? NOW.plusSeconds(1) : NOW.minusSeconds(60),
                state.equals("boundary") ? NOW : state.equals("expired") ? NOW.minusSeconds(1) : NOW.plusSeconds(60));
        if (state.equals("used")) token.use(NOW);
        if (state.equals("revoked")) token.revoke(NOW);
        invalid(RAW); verifyNoInteractions(members);
    }
    @Test void 발급_시각과_같은_시각은_유효하다() {
        token(NOW, NOW.plusSeconds(1));
        when(members.existsByIdAndStatus(MEMBER_ID, MemberStatus.ACTIVE)).thenReturn(true);
        service.validate(RAW);
    }
    @Test void 회원이_없거나_활성이_아니면_거절한다() {
        token(NOW.minusSeconds(1), NOW.plusSeconds(60));
        when(members.existsByIdAndStatus(MEMBER_ID, MemberStatus.ACTIVE)).thenReturn(false);
        invalid(RAW); verify(members).existsByIdAndStatus(MEMBER_ID, MemberStatus.ACTIVE);
    }
}
