package io.github.takgeun.iyum.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class EmailVerificationPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EmailVerificationConfig.class)
            .withPropertyValues("iyum.auth.email-verification.token-ttl=30m");

    @Test void defaultsAndOverridesBind() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(EmailVerificationProperties.class);
            assertThat(properties.tokenTtl()).isEqualTo(Duration.ofMinutes(30));
            assertThat(properties.resendMinInterval()).isEqualTo(Duration.ofSeconds(60));
            assertThat(properties.resendMaxPerHour()).isEqualTo(5);
        });
        runner.withPropertyValues("iyum.auth.email-verification.resend-min-interval=90s",
                "iyum.auth.email-verification.resend-max-per-hour=3").run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(EmailVerificationProperties.class);
            assertThat(properties.resendMinInterval()).isEqualTo(Duration.ofSeconds(90));
            assertThat(properties.resendMaxPerHour()).isEqualTo(3);
        });
    }

    @ParameterizedTest @ValueSource(strings = {"resend-min-interval=0s", "resend-min-interval=-1s",
            "resend-min-interval=2h", "resend-min-interval=invalid", "resend-max-per-hour=0",
            "resend-max-per-hour=-1", "resend-max-per-hour=1.5", "token-ttl=0s"})
    void invalidSettingsFailStartup(String setting) {
        runner.withPropertyValues("iyum.auth.email-verification." + setting)
                .run(context -> assertThat(context).hasFailed());
    }
}
