package io.github.takgeun.iyum.auth.infrastructure;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class EmailVerificationTokenHasherTest {

    private final EmailVerificationTokenHasher hasher =
            new EmailVerificationTokenHasher();

    @Test
    void 알려진_SHA256_결과와_일치한다() {
        String result = hasher.hash("abc");

        assertThat(result).isEqualTo(
                "ba7816bf8f01cfea414140de5dae2223"
                        + "b00361a396177a9cb410ff61f20015ad"
        );
    }

    @Test
    void 대소문자와_앞뒤_공백을_임의로_변경하지_않는다() {
        String original = hasher.hash("abc");

        assertThat(hasher.hash("Abc"))
                .isNotEqualTo(original);

        assertThat(hasher.hash(" abc "))
                .isNotEqualTo(original);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void 비어있는_토큰은_거절한다(String rawToken) {
        assertThatThrownBy(() -> hasher.hash(rawToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

}