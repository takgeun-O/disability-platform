package io.github.takgeun.iyum.auth.passwordreset.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class PasswordResetTokenTest {

    private static final Long MEMBER_ID = 1L;
    private static final String TOKEN_HASH = "a".repeat(64);

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-21T01:00:00Z");

    private static final Instant EXPIRES_AT =
            CREATED_AT.plusSeconds(30 * 60);

    private PasswordResetToken issueToken() {
        return PasswordResetToken.issue(
                MEMBER_ID,
                TOKEN_HASH,
                CREATED_AT,
                EXPIRES_AT
        );
    }

    @Test
    void 유효한_정보로_토큰을_발급한다() {
        PasswordResetToken token = issueToken();

        assertThat(token.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(token.getTokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(token.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(token.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(token.getUsedAt()).isNull();
        assertThat(token.getRevokedAt()).isNull();
        assertThat(token.getVersion()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidIssueArguments")
    void 잘못된_정보로는_발급할_수_없다(
            Long memberId,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        assertThatThrownBy(() ->
                PasswordResetToken.issue(
                        memberId,
                        tokenHash,
                        createdAt,
                        expiresAt
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidIssueArguments() {
        return Stream.of(
                Arguments.of(null, TOKEN_HASH, CREATED_AT, EXPIRES_AT),
                Arguments.of(0L, TOKEN_HASH, CREATED_AT, EXPIRES_AT),
                Arguments.of(-1L, TOKEN_HASH, CREATED_AT, EXPIRES_AT),
                Arguments.of(MEMBER_ID, null, CREATED_AT, EXPIRES_AT),
                Arguments.of(MEMBER_ID, "", CREATED_AT, EXPIRES_AT),
                Arguments.of(MEMBER_ID, "a".repeat(63), CREATED_AT, EXPIRES_AT),
                Arguments.of(MEMBER_ID, "a".repeat(65), CREATED_AT, EXPIRES_AT),
                Arguments.of(MEMBER_ID, "A".repeat(64), CREATED_AT, EXPIRES_AT),
                Arguments.of(MEMBER_ID, "g".repeat(64), CREATED_AT, EXPIRES_AT),
                Arguments.of(MEMBER_ID, TOKEN_HASH, null, EXPIRES_AT),
                Arguments.of(MEMBER_ID, TOKEN_HASH, CREATED_AT, null),
                Arguments.of(MEMBER_ID, TOKEN_HASH, CREATED_AT, CREATED_AT),
                Arguments.of(
                        MEMBER_ID,
                        TOKEN_HASH,
                        CREATED_AT,
                        CREATED_AT.minusSeconds(1)
                )
        );
    }

    @Test
    void 발급_시각부터_만료_직전까지만_사용할_수_있다() {
        PasswordResetToken token = issueToken();

        assertThat(token.isUsable(CREATED_AT.minusNanos(1))).isFalse();
        assertThat(token.isUsable(CREATED_AT)).isTrue();
        assertThat(token.isUsable(EXPIRES_AT.minusNanos(1))).isTrue();
        assertThat(token.isUsable(EXPIRES_AT)).isFalse();
        assertThat(token.isUsable(EXPIRES_AT.plusSeconds(1))).isFalse();

        // 유효성 검사 자체는 토큰을 사용 처리하지 않는다.
        assertThat(token.getUsedAt()).isNull();
        assertThat(token.getRevokedAt()).isNull();
    }

    @Test
    void 검사_시각이_없으면_거부한다() {
        PasswordResetToken token = issueToken();

        assertThatThrownBy(() -> token.isUsable(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> token.use(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(token.getUsedAt()).isNull();
    }

    @Test
    void 사용한_토큰은_다시_사용할_수_없다() {
        PasswordResetToken token = issueToken();
        Instant usedAt = CREATED_AT.plusSeconds(60);

        token.use(usedAt);

        assertThat(token.getUsedAt()).isEqualTo(usedAt);
        assertThat(token.isUsable(usedAt.plusSeconds(1))).isFalse();

        assertThatThrownBy(() -> token.use(usedAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(token.getUsedAt()).isEqualTo(usedAt);
        assertThat(token.getRevokedAt()).isNull();
    }

    @Test
    void 발급_이전이나_만료_이후에는_사용_처리할_수_없다() {
        PasswordResetToken token = issueToken();

        assertThatThrownBy(() ->
                token.use(CREATED_AT.minusNanos(1))
        ).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() ->
                token.use(EXPIRES_AT)
        ).isInstanceOf(IllegalStateException.class);

        assertThat(token.getUsedAt()).isNull();
    }

    @Test
    void 폐기한_토큰은_사용할_수_없다() {
        PasswordResetToken token = issueToken();
        Instant revokedAt = CREATED_AT.plusSeconds(60);

        token.revoke(revokedAt);

        assertThat(token.getRevokedAt()).isEqualTo(revokedAt);
        assertThat(token.isUsable(revokedAt)).isFalse();

        assertThatThrownBy(() ->
                token.use(revokedAt.plusSeconds(1))
        ).isInstanceOf(IllegalStateException.class);

        assertThat(token.getUsedAt()).isNull();
    }

    @Test
    void 반복_폐기해도_최초_폐기_시각을_유지한다() {
        PasswordResetToken token = issueToken();
        Instant revokedAt = CREATED_AT.plusSeconds(60);

        token.revoke(revokedAt);
        token.revoke(revokedAt.plusSeconds(30));

        assertThat(token.getRevokedAt()).isEqualTo(revokedAt);
    }

    @Test
    void 이미_사용한_토큰의_사용_기록은_폐기로_덮어쓰지_않는다() {
        PasswordResetToken token = issueToken();
        Instant usedAt = CREATED_AT.plusSeconds(60);

        token.use(usedAt);
        token.revoke(usedAt.plusSeconds(30));

        assertThat(token.getUsedAt()).isEqualTo(usedAt);
        assertThat(token.getRevokedAt()).isNull();
    }

    @Test
    void 잘못된_폐기_시각은_거부한다() {
        PasswordResetToken token = issueToken();

        assertThatThrownBy(() -> token.revoke(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                token.revoke(CREATED_AT.minusNanos(1))
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(token.getRevokedAt()).isNull();
    }

    @Test
    void 만료된_미사용_토큰도_폐기할_수_있다() {
        PasswordResetToken token = issueToken();
        Instant revokedAt = EXPIRES_AT.plusSeconds(60);

        token.revoke(revokedAt);

        assertThat(token.getRevokedAt()).isEqualTo(revokedAt);
        assertThat(token.getUsedAt()).isNull();
        assertThat(token.isUsable(revokedAt)).isFalse();
    }
}