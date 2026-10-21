package io.github.takgeun.iyum.auth.passwordreset.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
public class PasswordResetMailRequestedListener {

    private final PasswordResetMailSender mailSender;
    private final Executor executor;

    public PasswordResetMailRequestedListener(
            PasswordResetMailSender mailSender,
            Executor executor
    ) {
        this.mailSender = mailSender;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PasswordResetMailRequested event) {
        try {
            // 메일 발송 작업을 실행기에 맡긴다.
            // 실제로 별도 스레드에서 실행하도록 전용 실행기를 연결
            executor.execute(() -> send(event));
        } catch (RejectedExecutionException exception) {
            log.warn(
                    "비밀번호 재설정 메일 작업 접수 실패: memberId={}, failureType={}",
                    event.memberId(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void send(PasswordResetMailRequested event) {
        try {
            mailSender.send(event);
        } catch (RuntimeException exception) {
            // 이미 커밋된 토큰 발급 결과는 유지한다.
            // SMTP 오류를 작업 밖으로 전파하거나 자동 재시도하지 않음
            log.warn(
                    "비밀번호 재설정 메일 발송 실패: memberId={}, failureType={}",
                    event.memberId(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
