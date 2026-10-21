package io.github.takgeun.iyum.auth.passwordreset.infrastructure;

import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetCompleted;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpPasswordResetCompletedMailSenderTest {

    private static final String EMAIL = "reset@example.com";

    private static final Instant CHANGED_AT =
            Instant.parse("2026-10-21T05:30:00Z");

    @Mock
    JavaMailSender mailSender;

    SmtpPasswordResetCompletedMailSender sender;

    @BeforeEach
    void setUp() {
        var properties = new PasswordResetMailProperties(
                "noreply@iyum.test",
                URI.create("http://127.0.0.1:8443/reset-password")
        );

        sender = new SmtpPasswordResetCompletedMailSender(
                mailSender,
                properties
        );
    }

    @Test
    void 수신자와_변경_시각과_보안_안내를_메일에_포함한다() {
        sender.send(event(EMAIL, CHANGED_AT));

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assertThat(message.getFrom())
                .isEqualTo("noreply@iyum.test");
        assertThat(message.getTo())
                .containsExactly(EMAIL);
        assertThat(message.getSubject())
                .isEqualTo("[IYUM] 비밀번호가 변경되었습니다");

        assertThat(message.getText())
                .contains("비밀번호가 변경되었습니다.")
                .contains("2026-10-21 14:30:00 KST")
                .contains("기존 로그인 세션은 더 이상 사용할 수 없습니다.")
                .contains("새 비밀번호로 다시 로그인해 주세요.")
                .contains("본인이 변경하지 않았다면")
                .contains("비밀번호 재설정을 다시 진행하고")
                .doesNotContain("token=");

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
                sender.send(event(email, CHANGED_AT))
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mailSender);
    }

    @Test
    void 변경_시각이_없으면_SMTP를_호출하지_않는다() {
        assertThatThrownBy(() ->
                sender.send(event(EMAIL, null))
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
                sender.send(event(EMAIL, CHANGED_AT))
        ).isSameAs(failure);

        verify(mailSender, times(1))
                .send(any(SimpleMailMessage.class));
        verifyNoMoreInteractions(mailSender);
    }

    private PasswordResetCompleted event(
            String email,
            Instant changedAt
    ) {
        return new PasswordResetCompleted(
                7L,
                email,
                changedAt
        );
    }
}