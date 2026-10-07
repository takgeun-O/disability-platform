package io.github.takgeun.iyum.auth.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 후 이벤트를 처리하는 리스너 작성
 */
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationMailRequestedListener {

    private final EmailVerificationMailSender mailSender;

    @TransactionalEventListener(
            // 이벤트가 발행된 트랜잭션이 성공적으로 커밋된 뒤 실행하도록 지정
            // AFTER_COMMIT이 자동으로 비동기 실행을 뜻하는 것은 아님.
            // 이번 구현은 같은 요청 스레드에서 커밋 후 발송을 시도하므로, SMTP가 느리면 가입 응답도 늦어질 수 있다.
            // 그래서 이전에 설정한 SMTP 타임아웃이 이 때 필요함.
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void on(EmailVerificationMailRequested event) {
        try {
            mailSender.send(
                    event.recipientEmail(),
                    event.issuedToken()
            );
        } catch (RuntimeException exception) {
            /**
             * 이 시점에는 회원과 토큰이 이미 저장됐음. 따라서 메일 발송 실패는 이미 완료된 가입과 별도로 처리해야 한다.
             *
             * 로그에는 두 정보만 남긴다.
             * - 어떤 회원의 발송이 실패했는지
             * - 어떤 종류의 예외였는지
             * 메일 본문이나 토큰이 예외 메시지에 포함될 가능성을 피하기 위해 예외 메시지와 예외 객체 전체를 출력하지 않음.
             */
            log.error(
                    "이메일 인증 메일 발송 실패. memberId={}, failureType={}",
                    event.issuedToken().memberId(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
