package io.github.takgeun.iyum.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class EmailVerificationMailDispatchConfig {
    @Bean
    public ThreadPoolTaskExecutor emailVerificationMailExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-verification-");
        // 큐가 가득 차면 거절한다. 요청 스레드에서 SMTP를 실행하는 CallerRunsPolicy는 사용하지 않는다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
