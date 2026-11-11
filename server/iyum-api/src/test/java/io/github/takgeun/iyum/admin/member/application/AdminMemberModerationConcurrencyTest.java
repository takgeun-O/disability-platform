package io.github.takgeun.iyum.admin.member.application;

import io.github.takgeun.iyum.admin.member.domain.MemberModerationAction;
import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.datasource.hikari.maximum-pool-size=6"
})
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class AdminMemberModerationConcurrencyTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-11T10:00:00Z");

    private static final long ADMIN_VERSION = 3L;
    private static final long TARGET_VERSION = 5L;

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
    AdminMemberModerationService service;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private TransactionTemplate transactionTemplate;

    private Long firstAdminId;
    private Long secondAdminId;
    private Long targetId;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_AT));

        transactionTemplate =
                new TransactionTemplate(transactionManager);

        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        transactionTemplate.executeWithoutResult(status -> {
            // ID 순서:
            // 첫 번째 관리자 < 대상 회원 < 두 번째 관리자
            Member firstAdmin = persistActiveMember("adminA", suffix);
            Member target = persistActiveMember("target", suffix);
            Member secondAdmin = persistActiveMember("adminB", suffix);

            entityManager.flush();

            firstAdminId = firstAdmin.getId();
            secondAdminId = secondAdmin.getId();
            targetId = target.getId();

            jdbcTemplate.update("""
                    UPDATE members
                    SET role = 'ADMIN',
                        credential_version = ?
                    WHERE id IN (?, ?)
                    """,
                    ADMIN_VERSION,
                    firstAdminId,
                    secondAdminId
            );

            jdbcTemplate.update("""
                    UPDATE members
                    SET credential_version = ?
                    WHERE id = ?
                    """, TARGET_VERSION, targetId);

            entityManager.clear();
        });
    }

    @ParameterizedTest
    @EnumSource(MemberModerationAction.class)
    void 서로_다른_관리자의_동일_요청은_한_번만_변경한다(
            MemberModerationAction action
    ) throws Exception {
        transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.update("""
                        UPDATE members
                        SET status = ?
                        WHERE id = ?
                        """, action.beforeStatus().name(), targetId)
        );

        assertThat(firstAdminId).isLessThan(targetId);
        assertThat(secondAdminId).isGreaterThan(targetId);

        CountDownLatch firstProcessed = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<AdminMemberModerationResult> first =
                    executor.submit(() ->
                            transactionTemplate.execute(status -> {
                                configureLockTimeout();
                                firstPid.set(currentBackendPid());

                                AdminMemberModerationResult result =
                                        execute(
                                                action,
                                                firstAdminId,
                                                "첫 번째 관리자 처리"
                                        );

                                // 실제 SQL까지 실행하되 아직 커밋하지 않는다.
                                entityManager.flush();

                                firstProcessed.countDown();
                                awaitRelease(allowFirstCommit);

                                return result;
                            })
                    );

            awaitSignal(
                    firstProcessed,
                    first,
                    "첫 번째 관리자 처리가 완료되지 않았습니다."
            );

            Future<AdminMemberModerationResult> second =
                    executor.submit(() ->
                            transactionTemplate.execute(status -> {
                                configureLockTimeout();
                                secondPid.set(currentBackendPid());
                                secondStarted.countDown();

                                return execute(
                                        action,
                                        secondAdminId,
                                        "두 번째 관리자 처리"
                                );
                            })
                    );

            awaitSignal(
                    secondStarted,
                    second,
                    "두 번째 관리자 요청이 시작되지 않았습니다."
            );

            assertThat(firstPid.get()).isNotEqualTo(secondPid.get());

            // 두 번째 요청이 첫 번째 요청의 DB 잠금을 기다려야 한다.
            awaitBlockedBy(
                    secondPid.get(),
                    firstPid.get(),
                    second
            );

            allowFirstCommit.countDown();

            AdminMemberModerationResult firstResult =
                    first.get(15, TimeUnit.SECONDS);

            AdminMemberModerationResult secondResult =
                    second.get(15, TimeUnit.SECONDS);

            assertThat(firstResult.memberId()).isEqualTo(targetId);
            assertThat(secondResult.memberId()).isEqualTo(targetId);

            assertThat(firstResult.changed()).isTrue();
            assertThat(secondResult.changed()).isFalse();

            assertThat(firstResult.status())
                    .isEqualTo(action.afterStatus());
            assertThat(secondResult.status())
                    .isEqualTo(action.afterStatus());

            assertThat(jdbcTemplate.queryForObject("""
                    SELECT status
                    FROM members
                    WHERE id = ?
                    """, String.class, targetId))
                    .isEqualTo(action.afterStatus().name());

            // 두 요청 중 실제 변경은 한 번이므로 버전도 한 번만 증가한다.
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT credential_version
                    FROM members
                    WHERE id = ?
                    """, Long.class, targetId))
                    .isEqualTo(TARGET_VERSION + 1L);

            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*)
                    FROM admin_member_moderation_logs
                    WHERE target_member_id = ?
                    """, Long.class, targetId))
                    .isEqualTo(1L);

            StoredLog log = readSingleLog();

            assertThat(log.actorId()).isEqualTo(firstAdminId);
            assertThat(log.action()).isEqualTo(action.name());
            assertThat(log.beforeStatus())
                    .isEqualTo(action.beforeStatus().name());
            assertThat(log.afterStatus())
                    .isEqualTo(action.afterStatus().name());
            assertThat(log.reason()).isEqualTo("첫 번째 관리자 처리");
        } finally {
            // 실패하더라도 대기 중인 첫 번째 작업을 해제한다.
            allowFirstCommit.countDown();

            executor.shutdownNow();

            assertThat(executor.awaitTermination(40, TimeUnit.SECONDS))
                    .as("테스트 작업 스레드가 종료되어야 한다")
                    .isTrue();
        }
    }

    private Member persistActiveMember(String label, String suffix) {
        Member member = Member.createPending(
                label + "-" + suffix + "@example.com",
                "test-only-password-hash",
                label + suffix
        );
        member.activeAfterEmailVerification();
        entityManager.persist(member);
        return member;
    }

    private AdminMemberModerationResult execute(
            MemberModerationAction action,
            Long actorId,
            String reason
    ) {
        return switch (action) {
            case SUSPEND -> service.suspend(
                    actorId,
                    ADMIN_VERSION,
                    targetId,
                    reason
            );
            case LIFT_SUSPENSION -> service.liftSuspension(
                    actorId,
                    ADMIN_VERSION,
                    targetId,
                    reason
            );
        };
    }

    private int currentBackendPid() {
        return jdbcTemplate.queryForObject(
                "SELECT pg_backend_pid()",
                Integer.class
        );
    }

    private void configureLockTimeout() {
        jdbcTemplate.execute("SET LOCAL lock_timeout = '30s'");
    }

    private void awaitSignal(
            CountDownLatch latch,
            Future<?> task,
            String timeoutMessage
    ) throws Exception {
        if (latch.await(15, TimeUnit.SECONDS)) {
            return;
        }

        if (task.isDone()) {
            // 작업이 예외로 종료됐다면 원래 원인을 전달한다.
            task.get();
        }

        throw new AssertionError(timeoutMessage);
    }

    private void awaitRelease(CountDownLatch latch) {
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "첫 번째 트랜잭션의 커밋 허용 대기 시간 초과"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "첫 번째 트랜잭션 대기 중 인터럽트",
                    exception
            );
        }
    }

    private void awaitBlockedBy(
            int waitingPid,
            int blockingPid,
            Future<?> waitingTask
    ) throws Exception {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(15);

        while (System.nanoTime() < deadline) {
            if (waitingTask.isDone()) {
                waitingTask.get();

                throw new AssertionError(
                        "두 번째 요청이 잠금 대기 없이 완료되었습니다."
                );
            }

            Boolean blocked = jdbcTemplate.queryForObject(
                    "SELECT ? = ANY(pg_blocking_pids(?))",
                    Boolean.class,
                    blockingPid,
                    waitingPid
            );

            if (Boolean.TRUE.equals(blocked)) {
                return;
            }

            // 단순 시간 경과가 아니라 실제 DB 잠금 대기를 확인한다.
            Thread.sleep(50);
        }

        throw new AssertionError(
                "두 번째 요청의 DB 잠금 대기를 확인하지 못했습니다."
        );
    }

    private StoredLog readSingleLog() {
        return jdbcTemplate.queryForObject("""
                SELECT actor_member_id,
                       action,
                       before_status,
                       after_status,
                       reason
                FROM admin_member_moderation_logs
                WHERE target_member_id = ?
                """,
                (rs, rowNum) -> new StoredLog(
                        rs.getLong("actor_member_id"),
                        rs.getString("action"),
                        rs.getString("before_status"),
                        rs.getString("after_status"),
                        rs.getString("reason")
                ),
                targetId
        );
    }

    private record StoredLog(
            Long actorId,
            String action,
            String beforeStatus,
            String afterStatus,
            String reason
    ) {
    }
}