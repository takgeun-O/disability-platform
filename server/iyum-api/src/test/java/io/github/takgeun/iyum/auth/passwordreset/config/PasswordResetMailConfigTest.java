package io.github.takgeun.iyum.auth.passwordreset.config;

import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetCompletedListener;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetCompletedMailSender;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetMailRequestedListener;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetMailSender;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.SmtpPasswordResetCompletedMailSender;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.SmtpPasswordResetMailSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.URI;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PasswordResetMailConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(PasswordResetMailConfig.class);

    @Test
    void 활성화_설정이_없으면_메일_관련_빈을_등록하지_않는다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            assertThat(context)
                    .doesNotHaveBean(PasswordResetMailSender.class);
            assertThat(context)
                    .doesNotHaveBean(PasswordResetMailRequestedListener.class);
            assertThat(context)
                    .doesNotHaveBean(PasswordResetMailProperties.class);
            assertThat(context)
                    .doesNotHaveBean("passwordResetMailExecutor");

            assertThat(context)
                    .doesNotHaveBean(PasswordResetCompletedMailSender.class);

            assertThat(context)
                    .doesNotHaveBean(PasswordResetCompletedListener.class);
        });
    }

    @Test
    void 비활성화하면_메일_관련_빈을_등록하지_않는다() {
        contextRunner
                .withPropertyValues(
                        "iyum.auth.password-reset.mail.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    assertThat(context)
                            .doesNotHaveBean(PasswordResetMailSender.class);
                    assertThat(context)
                            .doesNotHaveBean(PasswordResetMailRequestedListener.class);
                    assertThat(context)
                            .doesNotHaveBean("passwordResetMailExecutor");

                    assertThat(context)
                            .doesNotHaveBean(PasswordResetCompletedMailSender.class);

                    assertThat(context)
                            .doesNotHaveBean(PasswordResetCompletedListener.class);
                });
    }

    @Test
    void 활성화하면_발송기와_리스너와_전용_실행기를_등록한다() {
        JavaMailSender smtp = mock(JavaMailSender.class);

        enabledRunner()
                .withBean(JavaMailSender.class, () -> smtp)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    assertThat(context)
                            .hasSingleBean(PasswordResetMailSender.class);
                    assertThat(context)
                            .hasSingleBean(PasswordResetMailRequestedListener.class);
                    assertThat(context)
                            .hasSingleBean(PasswordResetMailProperties.class);

                    assertThat(context.getBean(PasswordResetMailSender.class))
                            .isInstanceOf(SmtpPasswordResetMailSender.class);

                    assertThat(context)
                            .hasSingleBean(PasswordResetCompletedMailSender.class);

                    assertThat(context)
                            .hasSingleBean(PasswordResetCompletedListener.class);

                    assertThat(context.getBean(PasswordResetCompletedMailSender.class))
                            .isInstanceOf(SmtpPasswordResetCompletedMailSender.class);

                    PasswordResetMailProperties properties =
                            context.getBean(PasswordResetMailProperties.class);

                    assertThat(properties.from())
                            .isEqualTo("noreply@iyum.test");

                    assertThat(properties.resetPageUrl())
                            .isEqualTo(
                                    URI.create(
                                            "http://127.0.0.1:8443/reset-password"
                                    )
                            );

                    ThreadPoolTaskExecutor executor = context.getBean(
                            "passwordResetMailExecutor",
                            ThreadPoolTaskExecutor.class
                    );

                    assertThat(executor.getCorePoolSize()).isEqualTo(2);
                    assertThat(executor.getMaxPoolSize()).isEqualTo(2);
                    assertThat(executor.getQueueCapacity()).isEqualTo(100);
                    assertThat(executor.getThreadNamePrefix())
                            .isEqualTo("password-reset-mail-");

                    assertThat(
                            executor.getThreadPoolExecutor()
                                    .getRejectedExecutionHandler()
                    ).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);

                    // 설정을 구성하는 것만으로 메일을 보내지는 않는다.
                    verifyNoInteractions(smtp);
                });
    }

    @Test
    void 활성화했는데_재설정_URL이_잘못되면_시작에_실패한다() {
        enabledRunner()
                .withBean(
                        JavaMailSender.class,
                        () -> mock(JavaMailSender.class)
                )
                .withPropertyValues(
                        "iyum.auth.password-reset.mail.reset-page-url="
                                + "https://example.com/reset-password?token=existing"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    void 활성화했는데_SMTP_발송_빈이_없으면_시작에_실패한다() {
        enabledRunner().run(context -> {
            assertThat(context).hasFailed();
        });
    }

    private ApplicationContextRunner enabledRunner() {
        return contextRunner.withPropertyValues(
                "iyum.auth.password-reset.mail.enabled=true",
                "iyum.auth.password-reset.mail.from=noreply@iyum.test",
                "iyum.auth.password-reset.mail.reset-page-url="
                        + "http://127.0.0.1:8443/reset-password"
        );
    }
}