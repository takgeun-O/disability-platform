package io.github.takgeun.iyum.auth.infrastructure;

import io.github.takgeun.iyum.auth.application.EmailVerificationMailSender;
import io.github.takgeun.iyum.auth.application.IssuedEmailVerificationToken;
import io.github.takgeun.iyum.global.config.EmailVerificationMailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
public class SmtpEmailVerificationMailSender implements EmailVerificationMailSender {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter
                    .ofPattern("uuuu-MM-dd HH:mm:ss 'KST'")
                    .withZone(ZoneId.of("Asia/Seoul"));

    private final JavaMailSender mailSender;
    private final EmailVerificationMailProperties properties;

    @Override
    public void send(String recipientEmail, IssuedEmailVerificationToken issuedToken) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "수신 이메일 주소가 필요합니다."
            );
        }

        if (issuedToken == null
                || issuedToken.rawToken() == null
                || !issuedToken.rawToken().matches("[A-Za-z0-9_-]{43}")
                || issuedToken.expiresAt() == null) {
            throw new IllegalArgumentException(
                    "유효한 이메일 인증 토큰이 필요합니다."
            );
        }

        String verificationUrl = UriComponentsBuilder
                .fromUri(properties.verificationPageUrl())

                // 원본 토큰으로 링크 생성 (메일에는 원본 토큰이 들어가야 하며 DB에 저장한 해시를 넣으면 안된다.
                // http://127.0.0.1:8443/register/verify?token=원본토큰
                .queryParam("token", issuedToken.rawToken())
                .build()
                .encode()
                .toUriString();

        String expiresAt = EXPIRY_FORMAT.format(
                issuedToken.expiresAt()     // 만료 시각은 이미 발급된 값을 사용한다. (메일 보내는 시점에서 다시 30분을 더하는 방식이 아님!)
        );

        // 받는 사람, 제목, 본문을 담는 객체
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(properties.from());
        message.setTo(recipientEmail);
        message.setSubject("[IYUM] 이메일 주소를 인증해 주세요");
        message.setText("""
                안녕하세요. IYUM입니다.

                아래 링크에서 이메일 인증을 진행해 주세요.

                %s

                만료 시각: %s
                이 링크는 한 번만 사용할 수 있습니다.

                본인이 가입하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(verificationUrl, expiresAt));

        mailSender.send(message);       // 여기서 SMTP 통신 발생
    }
}
