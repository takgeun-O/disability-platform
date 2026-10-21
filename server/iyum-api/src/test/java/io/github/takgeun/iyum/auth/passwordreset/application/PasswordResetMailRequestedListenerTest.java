package io.github.takgeun.iyum.auth.passwordreset.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetMailRequestedListenerTest {

    @Mock
    PasswordResetMailSender mailSender;

    @Mock
    Executor executor;

    PasswordResetMailRequestedListener listener;

    @BeforeEach
    void setUp() {
        listener = new PasswordResetMailRequestedListener(
                mailSender,
                executor
        );
    }

    @Test
    void 발송_작업을_실행기에_맡기고_작업이_실행될_때_메일을_보낸다() {
        PasswordResetMailRequested event = event();

        listener.on(event);

        var captor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(captor.capture());

        // 실행기에 작업을 맡겼을 뿐, 아직 발송하지 않았다.
        verifyNoInteractions(mailSender);

        // 실행기가 맡아 둔 작업을 실행하는 상황을 재현한다.
        captor.getValue().run();

        verify(mailSender).send(event);
        verifyNoMoreInteractions(mailSender, executor);
    }

    @Test
    void 실행기가_작업을_거절하면_예외를_전파하지_않는다() {
        doThrow(new RejectedExecutionException("테스트용 큐 포화"))
                .when(executor)
                .execute(any(Runnable.class));

        PasswordResetMailRequested event = event();

        assertThatCode(() -> listener.on(event))
                .doesNotThrowAnyException();

        verify(executor).execute(any(Runnable.class));
        verifyNoInteractions(mailSender);
        verifyNoMoreInteractions(executor);
    }

    @Test
    void SMTP_발송에_실패하면_예외를_전파하거나_재시도하지_않는다() {
        PasswordResetMailRequested event = event();

        doThrow(new MailSendException("테스트용 SMTP 실패"))
                .when(mailSender)
                .send(event);

        listener.on(event);

        var captor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(captor.capture());

        assertThatCode(() -> captor.getValue().run())
                .doesNotThrowAnyException();

        // 최초 발송 시도 한 번만 실행한다.
        verify(mailSender, times(1)).send(event);

        // 추가 발송이나 실행기 재접수가 없어야 한다.
        verifyNoMoreInteractions(mailSender, executor);
    }

    private PasswordResetMailRequested event() {
        return new PasswordResetMailRequested(
                7L,
                "reset@example.com",
                "a".repeat(43),
                Instant.parse("2026-10-21T05:30:00Z")
        );
    }
}