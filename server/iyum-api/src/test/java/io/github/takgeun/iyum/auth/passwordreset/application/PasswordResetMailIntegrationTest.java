package io.github.takgeun.iyum.auth.passwordreset.application;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true",

        "iyum.auth.password-reset.token-ttl=30m",
        "iyum.auth.password-reset.resend-min-interval=60s",
        "iyum.auth.password-reset.resend-max-per-hour=5",

        "iyum.auth.password-reset.mail.enabled=true",
        "iyum.auth.password-reset.mail.from=noreply@iyum.test",
        "iyum.auth.password-reset.mail.reset-page-url="
                + "http://127.0.0.1:8443/reset-password",

        "iyum.auth.email-verification.mail.enabled=false"
})
@Testcontainers
class PasswordResetMailIntegrationTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    PasswordResetTokenIssueService issueService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ApplicationEventPublisher events;

    @MockitoBean
    JavaMailSender smtp;

    // 실제 설정 클래스의 전용 실행기만 교체한다.
    @MockitoBean(
            name = "passwordResetMailExecutor",
            enforceOverride = true
    )
    ThreadPoolTaskExecutor executor;

    @Test
    void 커밋_후에만_발송_작업을_등록하고_메일을_보낸다() {
        Member member = createActiveMember();

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            issueService.issue(member.getEmail());

            // 같은 트랜잭션 안에서는 저장된 토큰을 확인할 수 있다.
            assertThat(tokenCount(member.getId())).isEqualTo(1);

            // 아직 커밋 전이므로 작업을 등록하면 안 된다.
            verify(executor, never()).execute(any(Runnable.class));
            verifyNoInteractions(smtp);
        });

        // 위 트랜잭션이 커밋된 후 작업이 등록되어야 한다.
        Runnable task = captureSubmittedTask();

        assertThat(tokenCount(member.getId())).isEqualTo(1);
        verifyNoInteractions(smtp);

        // 실행기에 전달된 작업을 실행한다.
        task.run();

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo())
                .containsExactly(member.getEmail());

        assertThat(message.getSubject())
                .isEqualTo("[IYUM] 비밀번호를 재설정해 주세요");

        assertThat(message.getText())
                .contains(
                        "http://127.0.0.1:8443/reset-password?token="
                );

        verifyNoMoreInteractions(smtp);
    }

    @Test
    void 롤백하면_토큰을_저장하거나_발송_작업을_등록하지_않는다() {
        Member member = createActiveMember();

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                transaction.executeWithoutResult(status -> {
                    issueService.issue(member.getEmail());

                    assertThat(tokenCount(member.getId())).isEqualTo(1);
                    verify(executor, never())
                            .execute(any(Runnable.class));

                    throw new IllegalStateException("테스트용 롤백");
                })
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("테스트용 롤백");

        // 트랜잭션 밖에서 다시 조회한다.
        assertThat(tokenCount(member.getId())).isZero();

        verify(executor, never()).execute(any(Runnable.class));
        verifyNoInteractions(smtp);
    }

    @Test
    void SMTP_실패가_발생해도_커밋된_토큰은_유지된다() {
        Member member = createActiveMember();

        doThrow(new MailSendException("테스트용 SMTP 실패"))
                .when(smtp)
                .send(any(SimpleMailMessage.class));

        // 서비스 트랜잭션이 종료되면서 발송 작업이 등록된다.
        issueService.issue(member.getEmail());

        Runnable task = captureSubmittedTask();

        var before = tokenSnapshot(member.getId());
        assertThat(before).hasSize(1);

        // 실제 리스너가 SMTP 예외를 처리해야 한다.
        assertThatCode(task::run)
                .doesNotThrowAnyException();

        assertThat(tokenSnapshot(member.getId()))
                .isEqualTo(before);

        verify(smtp, times(1)).send(any(SimpleMailMessage.class));
        verifyNoMoreInteractions(smtp);
    }

    @Test
    void 실행기가_작업을_거절해도_발급은_완료되고_토큰은_유지된다() {
        Member member = createActiveMember();

        doThrow(new RejectedExecutionException("테스트용 큐 포화"))
                .when(executor)
                .execute(any(Runnable.class));

        assertThatCode(() -> issueService.issue(member.getEmail()))
                .doesNotThrowAnyException();

        assertThat(tokenCount(member.getId())).isEqualTo(1);

        verify(executor, times(1)).execute(any(Runnable.class));
        verifyNoInteractions(smtp);
    }

    @Test
    void 트랜잭션_없이_이벤트를_발행하면_발송하지_않는다() {
        events.publishEvent(new PasswordResetMailRequested(
                7L,
                "reset@example.com",
                "a".repeat(43),
                Instant.now().plusSeconds(1800)
        ));

        verify(executor, never()).execute(any(Runnable.class));
        verifyNoInteractions(smtp);
    }

    private Runnable captureSubmittedTask() {
        var captor = ArgumentCaptor.forClass(Runnable.class);

        verify(executor, times(1)).execute(captor.capture());

        return captor.getValue();
    }

    private Member createActiveMember() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "");

        Member member = Member.createPending(
                "reset-" + suffix + "@example.com",
                "test-password-hash",
                "회원" + suffix.substring(0, 16)
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private long tokenCount(Long memberId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM password_reset_tokens
                WHERE member_id = ?
                """,
                Long.class,
                memberId
        );
    }

    private List<Map<String, Object>> tokenSnapshot(Long memberId) {
        return jdbcTemplate.queryForList(
                """
                SELECT id, member_id, token_hash,
                       created_at, expires_at,
                       used_at, revoked_at, version
                FROM password_reset_tokens
                WHERE member_id = ?
                ORDER BY id
                """,
                memberId
        );
    }
}