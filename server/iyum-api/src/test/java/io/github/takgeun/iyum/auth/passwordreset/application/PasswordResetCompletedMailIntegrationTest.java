package io.github.takgeun.iyum.auth.passwordreset.application;

import io.github.takgeun.iyum.auth.passwordreset.domain.PasswordResetToken;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenGenerator;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenHasher;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * | 상황 | 기대 결과 |
 * |---|---|
 * | 비밀번호 변경 후 커밋 전 | 메일 작업 접수 안 됨 |
 * | 커밋 성공 | 완료 메일 작업 1개 접수 |
 * | 변경 후 롤백 | DB 원복, 메일 작업 접수 안 됨 |
 * | SMTP 발송 실패 | 변경된 비밀번호·인증 버전·토큰 상태 유지 |
 * | 실행기 대기열 초과 | 비밀번호 변경 성공 유지 |
 * | 트랜잭션 없이 이벤트 발행 | 메일 작업 접수 안 됨 |
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true",

        "iyum.auth.password-reset.mail.enabled=true",
        "iyum.auth.password-reset.mail.from=noreply@iyum.test",
        "iyum.auth.password-reset.mail.reset-page-url="
                + "http://127.0.0.1:8443/reset-password",

        "iyum.auth.email-verification.mail.enabled=false"
})
@Testcontainers
class PasswordResetCompletedMailIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-10-21T05:30:00Z");

    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "NewPassword456!";

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
    PasswordResetService resetService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordResetTokenRepository tokenRepository;

    @Autowired
    PasswordResetTokenGenerator tokenGenerator;

    @Autowired
    PasswordResetTokenHasher tokenHasher;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ApplicationEventPublisher events;

    @MockitoBean
    Clock clock;

    // SMTP 경계만 Mock으로 교체하므로 실제 메일은 발송되지 않는다.
    @MockitoBean
    JavaMailSender smtp;

    // 실제 설정 클래스가 등록한 실행기를 교체한다.
    // 빈 등록 자체가 빠져 있으면 테스트 설정 단계에서 실패한다.
    @MockitoBean(
            name = "passwordResetMailExecutor",
            enforceOverride = true
    )
    ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void 커밋_후에만_완료_메일_작업을_등록하고_발송한다() {
        Fixture fixture = createFixture();

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            resetService.reset(fixture.rawToken(), NEW_PASSWORD);

            // 서비스의 flush()로 SQL은 실행됐지만 아직 커밋 전이다.
            assertResetSaved(fixture);

            verify(executor, never()).execute(any(Runnable.class));
            verifyNoInteractions(smtp);
        });

        // 외부 트랜잭션의 커밋 이후 작업이 접수되어야 한다.
        Runnable task = captureSubmittedTask();

        assertResetSaved(fixture);
        verifyNoInteractions(smtp);

        task.run();

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assertThat(message.getFrom())
                .isEqualTo("noreply@iyum.test");
        assertThat(message.getTo())
                .containsExactly(fixture.email());
        assertThat(message.getSubject())
                .isEqualTo("[IYUM] 비밀번호가 변경되었습니다");

        assertThat(message.getText())
                .contains("2026-10-21 14:30:00 KST")
                .contains("새 비밀번호로 다시 로그인해 주세요.")
                .doesNotContain(
                        OLD_PASSWORD,
                        NEW_PASSWORD,
                        fixture.rawToken(),
                        "token="
                );

        verifyNoMoreInteractions(smtp);
    }

    @Test
    void 이벤트_발행_후_롤백하면_DB가_원복되고_메일_작업도_등록하지_않는다() {
        Fixture fixture = createFixture();
        var before = databaseSnapshot(fixture.memberId());

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                transaction.executeWithoutResult(status -> {
                    // 서비스 안에서 flush와 이벤트 발행까지 진행한다.
                    resetService.reset(fixture.rawToken(), NEW_PASSWORD);

                    assertResetSaved(fixture);
                    verify(executor, never())
                            .execute(any(Runnable.class));
                    verifyNoInteractions(smtp);

                    // 이벤트 발행 뒤라도 커밋 전에 실패하면 롤백되어야 한다.
                    throw new IllegalStateException("테스트용 롤백");
                })
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("테스트용 롤백");

        assertThat(databaseSnapshot(fixture.memberId()))
                .isEqualTo(before);

        verify(executor, never()).execute(any(Runnable.class));
        verifyNoInteractions(smtp);
    }

    @Test
    void SMTP_실패가_발생해도_커밋된_변경은_유지된다() {
        Fixture fixture = createFixture();

        doThrow(new MailSendException("테스트용 SMTP 실패"))
                .when(smtp)
                .send(any(SimpleMailMessage.class));

        resetService.reset(fixture.rawToken(), NEW_PASSWORD);

        Runnable task = captureSubmittedTask();

        assertResetSaved(fixture);
        var beforeMail = databaseSnapshot(fixture.memberId());

        assertThatCode(task::run)
                .doesNotThrowAnyException();

        assertThat(databaseSnapshot(fixture.memberId()))
                .isEqualTo(beforeMail);

        verify(smtp, times(1)).send(any(SimpleMailMessage.class));
        verifyNoMoreInteractions(smtp);
    }

    @Test
    void 실행기가_작업을_거절해도_비밀번호_변경은_완료된다() {
        Fixture fixture = createFixture();

        doThrow(new RejectedExecutionException("테스트용 큐 포화"))
                .when(executor)
                .execute(any(Runnable.class));

        assertThatCode(() ->
                resetService.reset(fixture.rawToken(), NEW_PASSWORD)
        ).doesNotThrowAnyException();

        assertResetSaved(fixture);

        verify(executor, times(1)).execute(any(Runnable.class));
        verifyNoInteractions(smtp);
    }

    @Test
    void 트랜잭션_없이_이벤트를_발행하면_메일_작업을_등록하지_않는다() {
        events.publishEvent(new PasswordResetCompleted(
                7L,
                "reset@example.com",
                NOW
        ));

        verify(executor, never()).execute(any(Runnable.class));
        verifyNoInteractions(smtp);
    }

    private Runnable captureSubmittedTask() {
        var captor = ArgumentCaptor.forClass(Runnable.class);

        verify(executor, times(1)).execute(captor.capture());

        return captor.getValue();
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "");

        Member member = Member.createPending(
                "reset-" + suffix + "@example.com",
                passwordEncoder.encode(OLD_PASSWORD),
                "회원" + suffix.substring(0, 16)
        );

        member.activeAfterEmailVerification();
        Member savedMember = memberRepository.saveAndFlush(member);

        String rawToken = tokenGenerator.generate();

        PasswordResetToken token = PasswordResetToken.issue(
                savedMember.getId(),
                tokenHasher.hash(rawToken),
                NOW.minusSeconds(60),
                NOW.plusSeconds(1800)
        );

        PasswordResetToken savedToken =
                tokenRepository.saveAndFlush(token);

        return new Fixture(
                savedMember.getId(),
                savedMember.getEmail(),
                rawToken,
                savedToken.getId()
        );
    }

    private void assertResetSaved(Fixture fixture) {
        // 영속성 컨텍스트의 객체 대신 실제 DB 값을 확인한다.
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM members WHERE id = ?",
                String.class,
                fixture.memberId()
        );

        assertThat(passwordEncoder.matches(NEW_PASSWORD, passwordHash))
                .isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, passwordHash))
                .isFalse();

        Long credentialVersion = jdbcTemplate.queryForObject(
                "SELECT credential_version FROM members WHERE id = ?",
                Long.class,
                fixture.memberId()
        );

        assertThat(credentialVersion).isEqualTo(1L);

        Boolean used = jdbcTemplate.queryForObject(
                """
                SELECT used_at IS NOT NULL AND revoked_at IS NULL
                FROM password_reset_tokens
                WHERE id = ?
                """,
                Boolean.class,
                fixture.tokenId()
        );

        assertThat(used).isTrue();
    }

    private Map<String, Object> databaseSnapshot(Long memberId) {
        var member = jdbcTemplate.queryForMap(
                """
                SELECT id, email, password_hash, status,
                       credential_version, updated_at
                FROM members
                WHERE id = ?
                """,
                memberId
        );

        var tokens = jdbcTemplate.queryForList(
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

        return Map.of(
                "member", member,
                "tokens", tokens
        );
    }

    private record Fixture(
            Long memberId,
            String email,
            String rawToken,
            Long tokenId
    ) {
        @Override
        public String toString() {
            return "Fixture[REDACTED]";
        }
    }
}