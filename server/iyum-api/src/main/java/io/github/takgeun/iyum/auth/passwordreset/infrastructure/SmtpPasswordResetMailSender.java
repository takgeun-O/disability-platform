package io.github.takgeun.iyum.auth.passwordreset.infrastructure;

import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetMailRequested;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetMailSender;
import io.github.takgeun.iyum.auth.passwordreset.config.PasswordResetMailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;import org.springframework.mail.javamail.JavaMailSender;import org.springframework.web.util.UriComponentsBuilder;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

// @Component 붙이지 않음.
// 메일 기능이 활성화된 경우에만 설정 클래스가 빈을 등록하게 할 것
@RequiredArgsConstructor
public class SmtpPasswordResetMailSender
        implements PasswordResetMailSender {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter
                    .ofPattern("uuuu-MM-dd HH:mm:ss 'KST'")
                    .withZone(ZoneId.of("Asia/Seoul"));

    private final JavaMailSender mailSender;
    private final PasswordResetMailProperties properties;

    @Override
    public void send(PasswordResetMailRequested request) {
        validate(request);

        String resetUrl = UriComponentsBuilder
                .fromUri(properties.resetPageUrl())
                .queryParam("token", request.rawToken())
                .build()
                .encode()
                .toUriString();

        // 메일 발송 시각에서 30분을 새로 더하지 않는다.
        // DB에 저장한 토큰의 만료 시각을 그대로 표시한다.
        String expiresAt = EXPIRY_FORMAT.format(request.expiresAt());

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(properties.from());
        message.setTo(request.email());
        message.setSubject("[IYUM] 비밀번호를 재설정해 주세요");
        message.setText("""
                안녕하세요. IYUM입니다.

                아래 링크에서 새 비밀번호를 설정해 주세요.

                %s

                만료 시각: %s
                이 링크는 비밀번호 변경에 한 번만 사용할 수 있습니다.
                재설정 메일을 다시 요청했다면 가장 최근에 받은 링크를 이용해 주세요.

                비밀번호 변경이 완료되면 기존 로그인은 해제되며,
                새 비밀번호로 다시 로그인해야 합니다.

                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                요청만으로 비밀번호가 변경되지는 않습니다.

                이 링크를 다른 사람에게 전달하지 마세요.
                """.formatted(resetUrl, expiresAt));

        mailSender.send(message);
    }

    private void validate(PasswordResetMailRequested request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "비밀번호 재설정 메일 발송 정보가 필요합니다."
            );
        }

        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException(
                    "수신 이메일 주소가 필요합니다."
            );
        }

        if (request.rawToken() == null
                || !request.rawToken().matches("[A-Za-z0-9_-]{43}")
                || request.expiresAt() == null) {
            throw new IllegalArgumentException(
                    "유효한 비밀번호 재설정 토큰이 필요합니다."
            );
        }
    }
}
