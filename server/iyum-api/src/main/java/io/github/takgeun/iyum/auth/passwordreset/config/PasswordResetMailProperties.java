package io.github.takgeun.iyum.auth.passwordreset.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@Valid
@ConfigurationProperties(prefix = "iyum.auth.password-reset.mail")
public record PasswordResetMailProperties(
        @NotBlank @Email String from,   // 메일에 표시할 발신 주소
        @NotNull URI resetPageUrl   // 새 비밀번호 입력 화면의 주소
) {

    public PasswordResetMailProperties {
        if (resetPageUrl != null) {
            String scheme = resetPageUrl.getScheme();

            boolean httpOrHttps =
                    "http".equalsIgnoreCase(scheme)
                            || "https".equalsIgnoreCase(scheme);

            if (!httpOrHttps
                    || resetPageUrl.getHost() == null
                    || resetPageUrl.getRawUserInfo() != null
                    || resetPageUrl.getRawQuery() != null
                    || resetPageUrl.getRawFragment() != null) {
                throw new IllegalArgumentException(
                        "비밀번호 재설정 화면 URL은 사용자 정보, 쿼리, "
                                + "프래그먼트가 없는 HTTP(S) 주소여야 합니다."
                );
            }
        }
    }
}
