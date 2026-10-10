package io.github.takgeun.iyum.auth.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 애플리케이션 시작 시 다음 과정을 거친다.
 * 1. Spring이 EmailVerificationMailConfig를 발견하고 활성화 조건 확인
 * 2. emailVerificationMailSender()로 SMTP 발송 구현체 등록
 * 3. 발송 구현체와 emailVerificationMailExecutor 빈을 주입하여 리스너를 생성하고 등록한다.
 * 4. 리스너의 @TransactionalEventListener 메서드를 감지해, EmailVerificationMailRequested 이벤트를 처리하도록 연결한다.
 */
@Slf4j
public class EmailVerificationMailRequestedListener {
    private final EmailVerificationMailSender mailSender;
    private final Executor executor;

    public EmailVerificationMailRequestedListener(
            EmailVerificationMailSender mailSender,
            @Qualifier("emailVerificationMailExecutor") Executor executor
    ) {
        this.mailSender = mailSender;
        this.executor = executor;
    }

    // Spring은 이벤트 타입으로 리스너를 연결한다.
    // 여기서 연결 기준은 메서드 파라미터의 타입 : EmailVerificationMailRequested
    // 여기서 AFTER_COMMIT 설정을 했으니 이벤트 발행 시 Spring은 이 트랜잭션이 성공적으로 커밋되면 처리하도록 등록한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(EmailVerificationMailRequested event) {
        // 리스너가 실제로 메일 발송 처리
        try {
            // 커밋 전에는 큐에도 넣지 않는다. 요청 응답은 SMTP 완료를 기다리지 않는다.
            executor.execute(() -> send(event));
        } catch (RejectedExecutionException exception) {
            // 발송 실패 로그
            log.error("이메일 인증 메일 큐 등록 실패. memberId={}, failureType={}",
                    event.issuedToken().memberId(), exception.getClass().getSimpleName());
        }
    }

    private void send(EmailVerificationMailRequested event) {
        try {
            mailSender.send(event.recipientEmail(), event.issuedToken());
        } catch (RuntimeException exception) {
            // 이미 커밋한 DB 결과는 유지한다. 민감한 예외 메시지·본문·토큰은 기록하지 않는다.
            log.error("이메일 인증 메일 발송 실패. memberId={}, failureType={}",
                    event.issuedToken().memberId(), exception.getClass().getSimpleName());
        }
    }
}
