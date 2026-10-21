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
class PasswordResetCompletedListenerTest {

    private static final PasswordResetCompleted EVENT =
            new PasswordResetCompleted(
                    7L,
                    "reset@example.com",
                    Instant.parse("2026-10-21T05:30:00Z")
            );

    @Mock
    PasswordResetCompletedMailSender mailSender;

    @Mock
    Executor executor;

    PasswordResetCompletedListener listener;

    @BeforeEach
    void setUp() {
        listener = new PasswordResetCompletedListener(
                mailSender,
                executor
        );
    }

    @Test
    void 메일_발송을_실행기에_맡기고_작업이_실행되면_한번_발송한다() {
        listener.on(EVENT);

        var captor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(captor.capture());

        // 실행기에 맡긴 작업을 아직 실행하지 않았으므로
        // 메일 발송도 시작되지 않아야 한다.
        verifyNoInteractions(mailSender);

        captor.getValue().run();

        verify(mailSender).send(EVENT);
        verifyNoMoreInteractions(mailSender, executor);
    }

    @Test
    void 작업_접수가_거절되어도_예외를_전파하거나_직접_발송하지_않는다() {
        doThrow(new RejectedExecutionException("테스트용 대기열 초과"))
                .when(executor)
                .execute(any(Runnable.class));

        assertThatCode(() -> listener.on(EVENT))
                .doesNotThrowAnyException();

        verify(executor).execute(any(Runnable.class));
        verifyNoMoreInteractions(executor);
        verifyNoInteractions(mailSender);
    }

    @Test
    void 메일_발송이_실패해도_작업_밖으로_예외를_전파하거나_재시도하지_않는다() {
        doThrow(new MailSendException("테스트용 SMTP 실패"))
                .when(mailSender)
                .send(EVENT);

        listener.on(EVENT);

        var captor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(captor.capture());

        verifyNoInteractions(mailSender);

        assertThatCode(() -> captor.getValue().run())
                .doesNotThrowAnyException();

        verify(mailSender, times(1)).send(EVENT);
        verifyNoMoreInteractions(mailSender, executor);
    }
}