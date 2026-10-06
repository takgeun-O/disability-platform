package io.github.takgeun.iyum.global.config;

import io.github.takgeun.iyum.auth.application.EmailVerificationMailRequestedListener;
import io.github.takgeun.iyum.auth.application.EmailVerificationMailSender;
import io.github.takgeun.iyum.auth.infrastructure.SmtpEmailVerificationMailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 1. application-dev.yaml에서 mail.enabled=true 인지 확인
 * 2. EmailVerificationMailProperties에 설정값을 바인딩
 * 3. Spring Boot가 준비한 JavaMailSender를 받는다.
 * 4. 이렇게 작성한 발송 구현체(emailVerificationMailSender)를 빈으로 등록한다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "iyum.auth.email-verification.mail",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(
        EmailVerificationMailProperties.class
)
public class EmailVerificationMailConfig {

    @Bean
    public EmailVerificationMailSender emailVerificationMailSender(
            JavaMailSender mailSender,
            EmailVerificationMailProperties properties
    ) {
        return new SmtpEmailVerificationMailSender(
                mailSender,
                properties
        );
    }

    // 커밋 후 이벤트를 처리하는 리스너 등록
    // mail.enabled=true 일 때 발송 구현체와 리스너가 함께 등록된다.
    // 리스너에 @Component를 붙이지 않은 이유도 여기서 직접 빈으로 등록하기 떄문임. (참고로 dev 프로필에서는 가입과 토큰 저장은 실행되지만 메일 발송은 실행되지 않게 해놓음)
    @Bean
    public EmailVerificationMailRequestedListener emailVerificationMailRequestedListener(
        EmailVerificationMailSender mailSender
    ) {
        return new EmailVerificationMailRequestedListener(
                mailSender
        );
    }
}
