package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.global.config.EmailVerificationMailDispatchConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailVerificationMailRequestedListenerTest {
    EmailVerificationMailRequested event() {
        return new EmailVerificationMailRequested("test@example.com",
                new IssuedEmailVerificationToken(1L, "A".repeat(43), Instant.parse("2026-10-10T00:30:00Z")));
    }
    @Test void rejectedQueueDoesNotCallSmtpOnRequestThreadOrThrow() {
        var sender = mock(EmailVerificationMailSender.class);
        var listener = new EmailVerificationMailRequestedListener(sender,
                task -> { throw new RejectedExecutionException("test rejection"); });
        assertThatCode(() -> listener.on(event())).doesNotThrowAnyException();
        verifyNoInteractions(sender);
    }
    @Test void smtpFailureIsContainedAfterCommit() {
        var sender = mock(EmailVerificationMailSender.class);
        doThrow(new MailSendException("test failure")).when(sender).send(anyString(), any());
        var listener = new EmailVerificationMailRequestedListener(sender, Runnable::run);
        assertThatCode(() -> listener.on(event())).doesNotThrowAnyException();
        verify(sender).send(anyString(), any());
    }
    @Test void productionExecutorIsBoundedAndRejectsInsteadOfRunningInline() throws Exception {
        var executor = new EmailVerificationMailDispatchConfig().emailVerificationMailExecutor();
        executor.initialize();
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        Runnable block = () -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        };
        try {
            executor.execute(block);
            executor.execute(block);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            for (int index = 0; index < 100; index++) executor.execute(() -> {});
            assertThatThrownBy(() -> executor.execute(() -> fail("must not run inline")))
                    .isInstanceOf(RejectedExecutionException.class);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
