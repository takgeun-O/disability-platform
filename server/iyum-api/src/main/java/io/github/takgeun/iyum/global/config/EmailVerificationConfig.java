package io.github.takgeun.iyum.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * @Configuration : Spring이 이 클래스를 설정 클래스로 인식
 * @EnableConfigurationProperties : 지정한 설정 클래스의 바인딩과 빈 등록 활성화
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmailVerificationProperties.class)
public class EmailVerificationConfig {

    // 서비스에서는 이렇게 사용될 것 -> Instant now = clock.instant();
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
