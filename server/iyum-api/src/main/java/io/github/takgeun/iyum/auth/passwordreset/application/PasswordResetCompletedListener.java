package io.github.takgeun.iyum.auth.passwordreset.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

// 변경 완료 이벤트 리스너
@Slf4j
public class PasswordResetCompletedListener {

    private final PasswordResetCompletedMailSender mailSender;
    private final Executor executor;

    public PasswordResetCompletedListener(
            PasswordResetCompletedMailSender mailSender,
            Executor executor
    ) {
        this.mailSender = mailSender;
        this.executor = executor;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = false   // 트랜잭션 없이 발행한 이벤트는 이 리스너가 처리하지 않음.
    )
    public void on(PasswordResetCompleted event) {
        try {
            // 커밋 성공 후, 메일 발송 작업을 실행기에 맡긴다.
            executor.execute(() -> send(event));
        } catch (RejectedExecutionException exception) {
            // 실행기가 작업을 접수하지 못한 경우 처리
            // 대기열 초과나 실행기 종료로 작업이 거절되더라도
            // 이미 완료된 비밀번호 변경은 성공 상태로 유지
            log.warn(
                    "비밀번호 변경 완료 메일 작업 접수 실패: "
                            + "memberId={}, failureType={}",
                    event.memberId(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void send(PasswordResetCompleted event) {
        try {
            mailSender.send(event);
        } catch (RuntimeException exception) {
            // 접수된 작업에서 실제 메일 발송이 실패한 경우 처리
            // 이메일 주소나 예외 메시지 전체는 기록하지 않는다.
            // SMTP 예외 메시지에 개인정보가 포함될 수 있기 떄문.
            log.warn(
                    "비밀번호 변경 완료 메일 발송 실패: "
                            + "memberId={}, failureType={}",
                    event.memberId(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
