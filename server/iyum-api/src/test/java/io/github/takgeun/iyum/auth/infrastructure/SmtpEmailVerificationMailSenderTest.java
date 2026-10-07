package io.github.takgeun.iyum.auth.infrastructure;

import io.github.takgeun.iyum.auth.application.IssuedEmailVerificationToken;
import io.github.takgeun.iyum.global.config.EmailVerificationMailProperties;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 실제 이메일을 보내는 대신, 발송 객체가 올바른 메일을 만들어 JavaMailSender에 전달하는지 검증하는 테스트
 * 그렇기 때문에 이 테스트가 보장하는 범위는 메일 구성과 발송 메소드 호출까지임.
 * 실제 SMTP 연결이나 수신함 도착 여부는 검증하지 않음.
 */
public class SmtpEmailVerificationMailSenderTest {

    // 1. 테스트용 원본 토큰 준비 : A가 43개인 고정 문자열 만들기
    // 실제 서비스에서는 난수를 사용하지만, 여기서는 메일에 지정한 토큰이 들어갔는지 쉽게 확인하기 위해 고정값 사용
    private static final String RAW_TOKEN = "A".repeat(43);

    private JavaMailSender mailSender;
    private SmtpEmailVerificationMailSender sender;

    // 2. 테스트 실행 전 setUp() 호출
    @BeforeEach
    void setUp() {
        // mailSender = mock(JavaMailSender.class); : 가짜 JavaMailSender 객체 만들기
        // 이 객체는 실제 SMTP 서버에 접속하지 않고 어떤 메서드가 어떤 인자로 호출됐는지 기록
        // -> 따라서 이 테스트에서는 메일 서버나 Spring의 JavaMailSender 빈이 필요하지 않음.
        mailSender = mock(JavaMailSender.class);

        // 설정 객체를 직접 생성
        EmailVerificationMailProperties properties =
                new EmailVerificationMailProperties(
                        "noreply@iyum.test",
                        URI.create(
                                "http://127.0.0.1:8443/register/verify"
                        )
                );

        // 실제로 테스트할 객체 생성 (실제 메일 구성 로직을 실행하는 객체)
        sender = new SmtpEmailVerificationMailSender(
                mailSender, // 실제 발송을 대신하는 mock
                properties  // 직접 만든 실제 설정 객체
        );
    }

    @Test
    void 수신자와_원본_토큰_링크와_만료_시각을_전달한다() {
        // 3. 발송에 사용할 토큰 정보 생성
        // 메서드 인자를 준비하면서 issuedToken() 호출
        // -> 고정된 ID, 원본 토큰, 만료 시각을 담은 객체 반환
        // -> 실제 sender.send() 로직 실행
        sender.send(
                "member@example.com",
                issuedToken()
        );

        // 4. 전달된 메일 객체를 꺼내기 위한 captor 생성
        // ArgumentCaptor: 메서드 호출에 전달된 인자를 잡아서 확인하는 도구
        // 여기서는 mailSender.send(...)에 전달된 SimpleMailMessage를 확인하려는 의도
        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        // 하는 일 2가지
        // - mailSender.send(...)가 기본적으로 한 번 호출됐는지 검증
        // - 그 호출에 전달된 메일 객체를 captor에 저장
        verify(mailSender).send(captor.capture());

        // 실제 발송 대상으로 전달됐떤 메일 객체를 드디어 꺼낼 수 있음.
        SimpleMailMessage message = captor.getValue();

        // 5. 메일 내용 검증
        assertThat(message.getFrom())
                .isEqualTo("noreply@iyum.test");
        assertThat(message.getTo())
                .containsExactly("member@example.com");

        assertThat(message.getSubject())
                .isEqualTo("[IYUM] 이메일 주소를 인증해 주세요");

        assertThat(message.getText())
                .contains(
                        "http://127.0.0.1:8443/register/verify?token="
                                + RAW_TOKEN
                )
                .contains("2026-10-06 21:30:00 KST");

        // 6. 토큰 해시가 본문에 없는지 검증
        String tokenHash =
                new EmailVerificationTokenHasher().hash(RAW_TOKEN);

        assertThat(message.getText())
                .doesNotContain(tokenHash);
    }

    @Test
    void SMTP_발송_실패를_호출자에게_전달한다() {
        MailSendException failure =
                new MailSendException("SMTP 연결 실패 테스트");

        doThrow(failure)
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertThatThrownBy(() ->
                sender.send(
                        "member@example.com",
                        issuedToken()
                )
        ).isSameAs(failure);
    }

    @Test
    void 잘못된_토큰은_메일로_전송하지_않는다() {
        IssuedEmailVerificationToken invalidToken =
                new IssuedEmailVerificationToken(
                        1L,
                        "invalid-token",
                        Instant.parse("2026-10-06T12:30:00Z")
                );

        assertThatThrownBy(() ->
                sender.send(
                        "member@example.com",
                        invalidToken
                )
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mailSender);
    }

    private IssuedEmailVerificationToken issuedToken() {
        return new IssuedEmailVerificationToken(
                1L,
                RAW_TOKEN,
                Instant.parse("2026-10-06T12:30:00Z")
        );
    }
}
