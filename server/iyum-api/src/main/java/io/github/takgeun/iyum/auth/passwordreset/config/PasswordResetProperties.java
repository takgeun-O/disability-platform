package io.github.takgeun.iyum.auth.passwordreset.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "iyum.auth.password-reset")
public record PasswordResetProperties(
        @DefaultValue("30m") Duration tokenTtl,
        @DefaultValue("60s") Duration resendMinInterval,
        @DefaultValue("5") int resendMaxPerHour,
        @DefaultValue("30") int requestMaxPerIpPerHour
) {

    public PasswordResetProperties {
        if (tokenTtl == null
                || tokenTtl.isZero()
                || tokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "비밀번호 재설정 토큰의 유효 기간은 0보다 커야 합니다."
            );
        }

        if (resendMinInterval == null
                || resendMinInterval.isZero()
                || resendMinInterval.isNegative()
                || resendMinInterval.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException(
                    "재전송 최소 간격은 0초 초과, 1시간 이하여야 합니다."
            );
        }

        if (resendMaxPerHour < 1) {
            throw new IllegalArgumentException(
                    "시간당 발급 횟수는 1 이상이어야 합니다."
            );
        }

        if (requestMaxPerIpPerHour < 1) {
            throw new IllegalArgumentException(
                    "IP별 시간당 요청 횟수는 1 이상이어야 합니다."
            );
        }
    }
}