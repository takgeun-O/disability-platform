package io.github.takgeun.iyum.auth.passwordreset.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class PasswordResetTokenCryptoTest {

    private final PasswordResetTokenGenerator generator =
            new PasswordResetTokenGenerator();

    private final PasswordResetTokenHasher hasher =
            new PasswordResetTokenHasher();

    @Test
    void URL에_사용할_수_있는_32바이트_토큰을_생성한다() {
        String rawToken = generator.generate();

        assertThat(rawToken)
                .hasSize(43)
                .matches("[A-Za-z0-9_-]{43}");

        assertThat(
                Base64.getUrlDecoder().decode(rawToken)
        ).hasSize(32);
    }

    @Test
    void 반복_호출에서_고정된_토큰을_반환하지_않는다() {
        Set<String> tokens = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            tokens.add(generator.generate());
        }

        assertThat(tokens).hasSize(100);
    }

    @Test
    void 알려진_SHA256_결과와_일치한다() {
        assertThat(hasher.hash("abc")).isEqualTo(
                "ba7816bf8f01cfea414140de5dae2223"
                        + "b00361a396177a9cb410ff61f20015ad"
        );
    }

    @Test
    void 생성한_토큰은_동일한_64자리_해시로_변환된다() {
        String rawToken = generator.generate();

        String first = hasher.hash(rawToken);
        String second = hasher.hash(rawToken);

        assertThat(first)
                .matches("[0-9a-f]{64}")
                .isEqualTo(second)
                .isNotEqualTo(rawToken);
    }

    @Test
    void 대소문자와_앞뒤_공백을_임의로_변환하지_않는다() {
        String original = hasher.hash("Abc_123");

        assertThat(hasher.hash("abc_123")).isNotEqualTo(original);
        assertThat(hasher.hash(" Abc_123")).isNotEqualTo(original);
        assertThat(hasher.hash("Abc_123 ")).isNotEqualTo(original);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void 원본_토큰이_없으면_거부한다(String rawToken) {
        assertThatThrownBy(() -> hasher.hash(rawToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("원본 토큰이 필요합니다.");
    }
}