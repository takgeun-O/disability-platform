package io.github.takgeun.iyum.auth.passwordreset.infrastructure;

import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetCompleted;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetCompletedMailSender;
import io.github.takgeun.iyum.auth.passwordreset.config.PasswordResetMailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

// SMTP 구현 클래스
// 메일 기능 활성화 조건에 맞춰 빈으로 등록할 것
@RequiredArgsConstructor
public class SmtpPasswordResetCompletedMailSender
        implements PasswordResetCompletedMailSender {

    private static final DateTimeFormatter CHANGED_AT_FORMAT =
            DateTimeFormatter
                    .ofPattern("uuuu-MM-dd HH:mm:ss 'KST'")
                    .withZone(ZoneId.of("Asia/Seoul"));

    private final JavaMailSender mailSender;
    private final PasswordResetMailProperties properties;

    @Override
    public void send(PasswordResetCompleted event) {
        validate(event);

        // 메일 발송 시각이 아니라 이벤트에 담긴 변경 시각을 표시한다.
        String changedAt = CHANGED_AT_FORMAT.format(event.changedAt());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(event.email());
        message.setSubject("[IYUM] 비밀번호가 변경되었습니다");
        message.setText("""
                안녕하세요. IYUM입니다.

                회원님의 비밀번호가 변경되었습니다.

                변경 시각: %s

                기존 로그인 세션은 더 이상 사용할 수 없습니다.
                IYUM에서 새 비밀번호로 다시 로그인해 주세요.

                본인이 변경하지 않았다면 IYUM 사이트에 직접 접속하여
                비밀번호 재설정을 다시 진행하고,
                사용 중인 이메일 계정의 보안 상태도 확인해 주세요.

                이 메일에는 비밀번호나 비밀번호 재설정 토큰이 포함되어 있지 않습니다.
                """.formatted(changedAt));

        // SMTP 실패는 호출자에게 전달한다.
        // 다음 단계의 비동기 리스너에서 기록하고 처리한다.
        mailSender.send(message);
    }

    private void validate(PasswordResetCompleted event) {
        if(event == null) {
            throw new IllegalArgumentException(
                    "비밀번호 변경 완료 메일 발송 정보가 필요합니다."
            );
        }

        if(event.email() == null || event.email().isBlank()) {
            throw new IllegalArgumentException(
                    "수신 이메일 주소가 필요합니다."
            );
        }

        if(event.changedAt() == null) {
            throw new IllegalArgumentException(
                    "비밀번호 변경 시각이 필요합니다."
            );
        }
    }
}
