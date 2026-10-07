package io.github.takgeun.iyum.auth.infrastructure;

import io.github.takgeun.iyum.auth.application.EmailVerificationMailSender;
import io.github.takgeun.iyum.auth.application.IssuedEmailVerificationToken;
import io.github.takgeun.iyum.global.config.EmailVerificationMailConfig;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(
        named = "IYUM_RUN_LOCAL_MAIL_TEST",
        matches = "true"
)
public class LocalEmailVerificationMailTest {

    // ApplicationContextRunner : 테스트에 필요한 Spring 설정만 올려주는 도구
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    MailSenderAutoConfiguration.class
                            )
                    )
                    .withUserConfiguration(
                            EmailVerificationMailConfig.class
                    )
                    .withPropertyValues(
                            "spring.mail.host=127.0.0.1",
                            "spring.mail.port=1025",
                            "spring.mail.default-encoding=UTF-8",
                            "spring.mail.properties[mail.smtp.auth]=false",
                            "spring.mail.properties[mail.smtp.starttls.enable]=false",
                            "spring.mail.properties[mail.smtp.connectiontimeout]=5000",
                            "spring.mail.properties[mail.smtp.timeout]=5000",
                            "spring.mail.properties[mail.smtp.writetimeout]=5000",
                            "iyum.auth.email-verification.mail.enabled=true",
                            "iyum.auth.email-verification.mail.from=noreply@iyum.test",
                            "iyum.auth.email-verification.mail.verification-page-url="
                                    + "http://127.0.0.1:8443/register/verify"
                    );

    @Test
    void 로컬_MailPit에_인증_메일을_전송한다() {
        contextRunner.run(context -> {
            assertThat(context)
                    .hasSingleBean(EmailVerificationMailSender.class);

            EmailVerificationMailSender sender =
                    context.getBean(
                            EmailVerificationMailSender.class
                    );

            String rawToken =
                    new EmailVerificationTokenGenerator().generate();

            IssuedEmailVerificationToken issuedToken =
                    new IssuedEmailVerificationToken(
                            1L,
                            rawToken,
                            Instant.now().plusSeconds(30 * 60)
                    );

            sender.send(
                    "mail-check@example.com",
                    issuedToken
            );
        });
    }
}
