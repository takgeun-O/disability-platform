package io.github.takgeun.iyum.auth.infrastructure;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

public class EmailVerificationTokenGeneratorTest {

    private final EmailVerificationTokenGenerator generator =
            new EmailVerificationTokenGenerator();

    @Test
    void URL에_사용할_수_있는_32바이트_토큰을_생성한다() {
        String token = generator.generate();

        assertThat(token)
                .hasSize(43)
                .matches("[A-Za-z0-9_-]{43}");

        byte[] decoded = Base64.getUrlDecoder().decode(token);

        assertThat(decoded).hasSize(32);
    }

    @Test
    void 연속해서_발급한_토큰은_서로_다르다() {
        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).isNotEqualTo(second);
    }
}
