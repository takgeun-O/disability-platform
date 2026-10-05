package io.github.takgeun.iyum.auth.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

public class EmailVerificationTokenTest {

    // 형식 검증용 값. 실제 토큰 생성 결과가 아님.
    private static final String TOKEN_HASH = "a".repeat(64);

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-06T00:00:00Z");

    private static final Instant EXPIRES_AT =
            CREATED_AT.plusSeconds(1800);

    @Test
    void 발급된_토큰은_유효기간_안에_사용할_수_있다() {
        EmailVerificationToken token = newToken();

        assertThat(token.getMemberId()).isEqualTo(1L);
        assertThat(token.getUsedAt()).isNull();
        assertThat(token.getRevokedAt()).isNull();

        assertThat(token.isUsable(CREATED_AT)).isTrue();

        assertThat(
                token.isUsable(EXPIRES_AT.minusSeconds(1))
        ).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 1800, 1801})
    void 발급_전이거나_만료된_토큰은_사용할_수_없다(
            long seconds
    ) {
        EmailVerificationToken token = newToken();
        Instant now = CREATED_AT.plusSeconds(seconds);

        assertThat(token.isUsable(now)).isFalse();

        assertThatThrownBy(() -> token.use(now))
                .isInstanceOf(IllegalStateException.class);

        assertThat(token.getUsedAt()).isNull();
    }

    @Test
    void 사용한_토큰은_다시_사용할_수_없다() {
        EmailVerificationToken token = newToken();
        Instant usedAt = CREATED_AT.plusSeconds(10);

        token.use(usedAt);

        assertThat(token.getUsedAt()).isEqualTo(usedAt);

        assertThat(
                token.isUsable(CREATED_AT.plusSeconds(20))
        ).isFalse();

        assertThatThrownBy(() ->
                token.use(CREATED_AT.plusSeconds(20))
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 폐기한_토큰은_사용할_수_없다() {
        EmailVerificationToken token = newToken();
        Instant revokedAt = CREATED_AT.plusSeconds(10);

        token.revoke(revokedAt);

        assertThat(token.getRevokedAt())
                .isEqualTo(revokedAt);

        assertThatThrownBy(() ->
                token.use(CREATED_AT.plusSeconds(20))
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 반복해서_폐기해도_최초_폐기_시각을_유지한다() {
        EmailVerificationToken token = newToken();
        Instant revokedAt = CREATED_AT.plusSeconds(10);

        token.revoke(revokedAt);
        token.revoke(CREATED_AT.plusSeconds(20));

        assertThat(token.getRevokedAt())
                .isEqualTo(revokedAt);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void 만료_시각은_발급_시각보다_늦어야_한다(
            long seconds
    ) {
        assertThatThrownBy(() ->
                EmailVerificationToken.issue(
                        1L,
                        TOKEN_HASH,
                        CREATED_AT,
                        CREATED_AT.plusSeconds(seconds)
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private EmailVerificationToken newToken() {
        return EmailVerificationToken.issue(
                1L,
                TOKEN_HASH,
                CREATED_AT,
                EXPIRES_AT
        );
    }
}
