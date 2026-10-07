package io.github.takgeun.iyum.global.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/**
 * 메일 발신 주소와 인증 화면 주소를 담당
 */
@Validated          // Spring이 설정 객체를 만들 때 검증하도록 지정
@ConfigurationProperties(
        prefix = "iyum.auth.email-verification.mail"
)
public record EmailVerificationMailProperties(
        @NotBlank @Email String from,       // 발신 주소가 비어있지 않고 이메일 형식인가?
        @NotNull URI verificationPageUrl    // 인증 화면 주소가 필수임을 지정, URI : URL을 단순 문자열 대신 구조화된 타입으로 받음
) {

    public EmailVerificationMailProperties {
        if (verificationPageUrl != null) {
            String scheme = verificationPageUrl.getScheme();

            boolean httpOrHttps = "http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme);

            if (!httpOrHttps
                    || verificationPageUrl.getHost() == null
                    || verificationPageUrl.getRawUserInfo() != null
                    || verificationPageUrl.getRawQuery() != null
                    || verificationPageUrl.getRawFragment() != null) {
                throw new IllegalArgumentException(
                        "인증 화면 URL은 사용자 정보, 쿼리, "
                        + "프래그먼트가 없는 HTTP(S) 주소여야 합니다."
                );
            }
        }
    }
}
