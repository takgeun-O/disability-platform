package io.github.takgeun.iyum.auth.passwordreset.config;

import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetCompletedListener;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetCompletedMailSender;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetMailRequestedListener;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetMailSender;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.SmtpPasswordResetCompletedMailSender;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.SmtpPasswordResetMailSender;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 메일 발송 기능이 켜져 있을 때 비밀번호 재설정 메일을 처리할 전용 작업 실행기를 만들어 Spring에 등록하는 설정
 * 클래스 상단 애노테이션은 등록 조건을 설정하고
 * 메서드 내부는 작업 처리 방식을 설정한다.
 */
@Configuration(proxyBeanMethods = false)    // 이 클래스는 Spring 빈을 등록하는 설정 클래스임을 선언
@ConditionalOnProperty(
        // prefix + name 은 검사할 설정 이름이 된다. (iyum.auth.password-reset.mail.enabled)
        // YAML에서 설정한 그 옵션임!
        prefix = "iyum.auth.password-reset.mail",
        name = "enabled",
        havingValue = "true",   // 값이 true일 경우 적용해라
        matchIfMissing = false  // 설정이 없으면 적용하지 마라
)
// PasswordResetMailProperties를 Spring에 등록하고 외부 설정값을 연결하도록 활성화
// YAML의 설정을 Java 객체로 읽을 수 있게 해준다.
@EnableConfigurationProperties(PasswordResetMailProperties.class)   // 설정을 Java 객체로 사용할 수 있게 해주는 역할
public class PasswordResetMailConfig {

    @Bean
    public ThreadPoolTaskExecutor passwordResetMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 메일 발송 작업을 동시에 최대 2개 실행한다.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);

        // 실행 중인 작업 외에 최대 100개까지 대기시킨다.
        executor.setQueueCapacity(100);

        // password-reset-mail-1, password-reset-mail-2, ...
        // 이런 식으로 작업 스레드 이름 앞에 접두사를 붙인다.
        // 그러면 로그나 디버거에서 "비밀번호 재설정 메일을 처리하는 스레드군!" 하고 구분할 수 있음
        executor.setThreadNamePrefix("password-reset-mail-");

        // 큐가 가득 차면 거절 예외를 발생시킨다.
        // PasswordResetMailRequestedListener가 해당 예외 처리
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );

        // 정상 종료 시 접수한 작업의 완료를 기다린다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);    // 최대 10초 기다리는데, 10초 지나도 작업이 남아있으면 기다림을 끝내고 종료 절차를 이어감.

        return executor;
    }

    @Bean
    public PasswordResetMailSender passwordResetMailSender(
            JavaMailSender javaMailSender,
            PasswordResetMailProperties properties
    ) {
        return new SmtpPasswordResetMailSender(
                javaMailSender,
                properties
        );
    }

    @Bean
    public PasswordResetMailRequestedListener passwordResetMailRequestedListener(
            PasswordResetMailSender mailSender,
            @Qualifier("passwordResetMailExecutor") Executor executor   // 기존 이메일 인증용 실행기와 구분해서 비밀번호 재설정 전용 실행기 선택
    ) {
        return new PasswordResetMailRequestedListener(
                mailSender,
                executor
        );
    }

    @Bean
    public PasswordResetCompletedMailSender passwordResetCompletedMailSender(
            JavaMailSender javaMailSender,
            PasswordResetMailProperties properties
    ) {
        return new SmtpPasswordResetCompletedMailSender(
                javaMailSender,
                properties
        );
    }

    @Bean
    public PasswordResetCompletedListener passwordResetCompletedListener(
            PasswordResetCompletedMailSender mailSender,
            @Qualifier("passwordResetMailExecutor") Executor executor
    ) {
        return new PasswordResetCompletedListener(
                mailSender,
                executor
        );
    }
}
