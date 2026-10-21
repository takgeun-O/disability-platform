package io.github.takgeun.iyum.auth.passwordreset.config;

import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetCompletedListener;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetCompletedMailSender;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetIpRateLimiter;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.SmtpPasswordResetCompletedMailSender;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.util.concurrent.Executor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PasswordResetProperties.class)
public class PasswordResetConfig {

    // 제한기를 Spring 빈으로 등록
    @Bean
    public PasswordResetIpRateLimiter passwordResetIpRateLimiter(
            PasswordResetProperties properties,
            Clock clock
    ) {
        return new PasswordResetIpRateLimiter(properties, clock);
    }
}
