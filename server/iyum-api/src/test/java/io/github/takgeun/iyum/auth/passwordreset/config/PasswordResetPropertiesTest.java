package io.github.takgeun.iyum.auth.passwordreset.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

class PasswordResetPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withBean(
                            Clock.class,
                            () -> Clock.fixed(
                                    Instant.parse("2026-10-21T05:00:00Z"),
                                    ZoneOffset.UTC
                            )
                    )
                    .withUserConfiguration(PasswordResetConfig.class);

    @Test
    void 설정이_없으면_기본값으로_등록된다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context)
                    .hasSingleBean(PasswordResetProperties.class);

            PasswordResetProperties properties =
                    context.getBean(PasswordResetProperties.class);

            assertThat(properties.tokenTtl())
                    .isEqualTo(Duration.ofMinutes(30));
            assertThat(properties.resendMinInterval())
                    .isEqualTo(Duration.ofSeconds(60));
            assertThat(properties.resendMaxPerHour()).isEqualTo(5);
            assertThat(properties.requestMaxPerIpPerHour()).isEqualTo(30);
        });
    }

    @Test
    void 지정한_설정값이_바인딩된다() {
        contextRunner
                .withPropertyValues(
                        "iyum.auth.password-reset.token-ttl=20m",
                        "iyum.auth.password-reset.resend-min-interval=90s",
                        "iyum.auth.password-reset.resend-max-per-hour=4",
                        "iyum.auth.password-reset.request-max-per-ip-per-hour=20"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    PasswordResetProperties properties =
                            context.getBean(PasswordResetProperties.class);

                    assertThat(properties.tokenTtl())
                            .isEqualTo(Duration.ofMinutes(20));
                    assertThat(properties.resendMinInterval())
                            .isEqualTo(Duration.ofSeconds(90));
                    assertThat(properties.resendMaxPerHour()).isEqualTo(4);
                    assertThat(properties.requestMaxPerIpPerHour())
                            .isEqualTo(20);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "iyum.auth.password-reset.token-ttl=0s",
            "iyum.auth.password-reset.token-ttl=-1s",
            "iyum.auth.password-reset.resend-min-interval=0s",
            "iyum.auth.password-reset.resend-min-interval=-1s",
            "iyum.auth.password-reset.resend-min-interval=3601s",
            "iyum.auth.password-reset.resend-max-per-hour=0",
            "iyum.auth.password-reset.request-max-per-ip-per-hour=0"
    })
    void 잘못된_정책값이면_설정_등록에_실패한다(String property) {
        contextRunner
                .withPropertyValues(property)
                .run(context -> {
                    assertThat(context).hasFailed();

                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(
                                    IllegalArgumentException.class
                            );
                });
    }
}