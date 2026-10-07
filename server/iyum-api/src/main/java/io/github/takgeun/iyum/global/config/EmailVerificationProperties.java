package io.github.takgeun.iyum.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// 이 접두사의 설정값을 이 객체에 바인딩하겠다는 의미
// 실제로 객체를 만들고 빈으로 등록하려면 @EnableConfigurationProperties 또는 @ConfigurationPropertiesScan이 필요.

/**
 * 1. application.yaml : 실제 설정값 작성
 * 2. EmailVerificationProperties : 설정값을 받을 객체 정의 (즉 지금 보고 있는 record)
 * 3. EmailVerificationConfig : 설정 객체를 Spring Bean으로 등록
 */
@ConfigurationProperties(
        prefix = "iyum.auth.email-verification"
)
public record EmailVerificationProperties(
        Duration tokenTtl
) {

    public EmailVerificationProperties {
        if (tokenTtl == null
                || tokenTtl.isZero()
                || tokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "이메일 인증 토큰의 유효 기간은 0보다 커야 합니다."
            );
        }
    }
}
