package io.github.takgeun.iyum.auth.passwordreset.infrastructure;

import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetMailRequested;
import io.github.takgeun.iyum.auth.passwordreset.config.PasswordResetMailProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpPasswordResetMailSenderTest {

    private static final String EMAIL = "reset@example.com";
    private static final String RAW_TOKEN = "a".repeat(43);

    private static final Instant EXPIRES_AT =
            Instant.parse("2026-10-21T05:30:00Z");

    @Mock
    JavaMailSender mailSender;

    SmtpPasswordResetMailSender sender;

    @BeforeEach
    void setUp() {
        var properties = new PasswordResetMailProperties(
                "noreply@iyum.test",
                URI.create("http://127.0.0.1:8443/reset-password")
        );

        sender = new SmtpPasswordResetMailSender(
                mailSender,
                properties
        );
    }

    @Test
    void 수신자와_원본_토큰_링크와_만료_시각을_메일에_포함한다() {
        sender.send(request(EMAIL, RAW_TOKEN, EXPIRES_AT));

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assertThat(message.getFrom()).isEqualTo("noreply@iyum.test");
        assertThat(message.getTo()).containsExactly(EMAIL);
        assertThat(message.getSubject())
                .isEqualTo("[IYUM] 비밀번호를 재설정해 주세요");

        assertThat(message.getText())
                .contains(
                        "http://127.0.0.1:8443/reset-password?token="
                                + RAW_TOKEN
                )
                .contains("2026-10-21 14:30:00 KST")
                .contains("한 번만 사용할 수 있습니다.")
                .contains("가장 최근에 받은 링크")
                .contains("요청만으로 비밀번호가 변경되지는 않습니다.");

        verifyNoMoreInteractions(mailSender);
    }

    @Test
    void 발송_정보가_없으면_SMTP를_호출하지_않는다() {
        assertThatThrownBy(() -> sender.send(null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mailSender);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 수신자가_없으면_SMTP를_호출하지_않는다(String email) {
        assertThatThrownBy(() ->
                sender.send(request(email, RAW_TOKEN, EXPIRES_AT))
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mailSender);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "short-token",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa+",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/"
    })
    void 잘못된_토큰이면_SMTP를_호출하지_않는다(String rawToken) {
        assertThatThrownBy(() ->
                sender.send(request(EMAIL, rawToken, EXPIRES_AT))
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mailSender);
    }

    @Test
    void 만료_시각이_없으면_SMTP를_호출하지_않는다() {
        assertThatThrownBy(() ->
                sender.send(request(EMAIL, RAW_TOKEN, null))
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mailSender);
    }

    @Test
    void SMTP_실패는_호출자에게_전달하고_자동_재시도하지_않는다() {
        MailSendException failure =
                new MailSendException("테스트용 SMTP 실패");

        doThrow(failure)
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertThatThrownBy(() ->
                sender.send(request(EMAIL, RAW_TOKEN, EXPIRES_AT))
        ).isSameAs(failure);

        verify(mailSender, times(1))
                .send(any(SimpleMailMessage.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/reset-password",
            "ftp://example.com/reset-password",
            "https://user@example.com/reset-password",
            "https://example.com/reset-password?token=existing",
            "https://example.com/reset-password#section"
    })
    void 잘못된_재설정_화면_URL을_거부한다(String url) {
        assertThatThrownBy(() ->
                new PasswordResetMailProperties(
                        "noreply@iyum.test",
                        URI.create(url)
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private PasswordResetMailRequested request(
            String email,
            String rawToken,
            Instant expiresAt
    ) {
        return new PasswordResetMailRequested(
                7L,
                email,
                rawToken,
                expiresAt
        );
    }
}