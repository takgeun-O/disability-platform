package io.github.takgeun.iyum.admin.moderation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
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

import java.sql.Timestamp;
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
class PostModerationConcurrencyTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-29T10:00:00Z");

    private static final Instant ORIGINAL_HIDDEN_AT =
            Instant.parse("2026-10-29T11:00:00Z");

    private static final long CREDENTIAL_VERSION = 3L;

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
    PostModerationService service;

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
    private Long postId;

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
            Member firstAdmin = createAdmin("A", suffix);
            Member secondAdmin = createAdmin("B", suffix);

            Post post = Post.create(
                    firstAdmin,
                    PostType.GENERAL,
                    PostTopic.DAILY,
                    "동시성 테스트 제목",
                    "동시성 테스트 내용"
            );
            entityManager.persist(post);
            entityManager.flush();

            firstAdminId = firstAdmin.getId();
            secondAdminId = secondAdmin.getId();
            postId = post.getId();

            entityManager.clear();
        });
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class) // HIDE, RESTORE
    void 서로_다른_관리자의_동일_요청은_한_번만_변경한다(
            ModerationAction action
    ) throws Exception {

        // 복원 작업을 하려면 처음에 게시글이 숨겨져 있어야 하니까 미리 숨김 처리한다.
        if (action == ModerationAction.RESTORE) {
            transactionTemplate.executeWithoutResult(status -> {
                Post post = entityManager.find(Post.class, postId);
                post.hide(ORIGINAL_HIDDEN_AT);
            });
        }

        assertThat(firstAdminId).isNotEqualTo(secondAdminId);

        CountDownLatch firstProcessed = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();

        // 두 관리자 작업을 실행할 스레드 풀을 만든다.
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
//            1. 관리자 A
//            execute(...)로 숨김을 처리한 뒤 firstProcessed.countDown()으로 테스트 스레드에 알린다.
            Future<PostModerationResult> first = executor.submit(() ->
                    transactionTemplate.execute(status -> {
                        configureLockTimeout();
                        firstPid.set(currentBackendPid());

                        PostModerationResult result = execute(
                                action,
                                firstAdminId,
                                "첫 번째 관리자 처리"
                        );

//                        2. 관리자 A
//                         awaitRelease(allowFirstCommit)에서 기다린다. 서비스 메서드는 반환했지만 바깥의 트랜잭션 콜백이 끝나지 않아 아직 커밋되지 않음.
                        firstProcessed.countDown();
                        awaitRelease(allowFirstCommit);

                        return result;
                    })
            );

            assertThat(firstProcessed.await(10, TimeUnit.SECONDS))
                    .as("첫 번째 관리자의 처리가 완료되어야 한다")
                    .isTrue();

            // 두 번째 관리자가 별도 트랜잭션에서 같은 글을 action 처리 시도한다.
//            3. 테스트 스레드 -> 관리자 B
//             A의 처리 신호를 받은 뒤 B 작업을 제출한다. B는 자기 DB 세션의 PID를 기록하고
//             secondStarted.countDown()을 호출한 뒤 같은 게시글에 숨김을 요청한다.
            Future<PostModerationResult> second = executor.submit(() ->
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

            assertThat(secondStarted.await(10, TimeUnit.SECONDS))
                    .as("두 번째 관리자 요청이 시작되어야 한다")
                    .isTrue();

            assertThat(firstPid.get()).isNotEqualTo(secondPid.get());

            // 단순히 두 스레드를 실행하는 데 그치지 않고,
            // 실제 DB 잠금 대기가 발생했는지 확인한다.
            // 두 번째 DB 연결이 실제로 첫 번째 연결의 잠금을 기다리는지 확인
//            4. 테스트 스레드
//              awaitBlockedBy(secondPid, firstPid, second)로 B의 DB 세션이 실제로 A 떄문에 기다리는지 확인
            // 실제로 A 때문에 기다리는 게 맞다면 true가 나와야 A의 커밋을 허용한다.
            // 즉 두 번째 작업이 잠금 대기를 관찰하기 전에 끝나면 테스트는 실패함.
            awaitBlockedBy(
                    secondPid.get(),
                    firstPid.get(),
                    second
            );

            // 확인이 끝나면 allowFirstCommit.countDown()을 호출해 첫 번째 트랜잭션이 커밋되도록 한다.
            // 5. 테스트 스레드 -> 관리자 A
            //  A가 콜백을 빠져나가면서 커밋하고 잠금이 풀린다.
            allowFirstCommit.countDown();

            PostModerationResult firstResult =
                    first.get(10, TimeUnit.SECONDS);

            PostModerationResult secondResult =
                    second.get(10, TimeUnit.SECONDS);

            assertThat(firstResult.changed()).isTrue();
            // 6. 관리자 B
            //  처리를 재개한다. 게시글이 이미 숨겨져 있으므로 추가 변경 없이 changed = false
            assertThat(secondResult.changed()).isFalse();

            boolean expectedHidden =
                    action == ModerationAction.HIDE;

            assertThat(firstResult.hidden()).isEqualTo(expectedHidden);
            assertThat(secondResult.hidden()).isEqualTo(expectedHidden);

            Instant storedHiddenAt = readHiddenAt();

            if (expectedHidden) {
                assertThat(storedHiddenAt).isNotNull();
            } else {
                assertThat(storedHiddenAt).isNull();
            }

            // 두 번째 요청은 첫 번째 요청이 커밋한 값을 읽어야 한다.
            assertThat(secondResult.hiddenAt()).isEqualTo(storedHiddenAt);

            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*)
                    FROM admin_content_moderation_logs
                    WHERE post_id = ?
                    """, Long.class, postId))
                    .isEqualTo(1L);

            assertThat(jdbcTemplate.queryForObject("""
                    SELECT actor_member_id
                    FROM admin_content_moderation_logs
                    WHERE post_id = ?
                    """, Long.class, postId))
                    .isEqualTo(firstAdminId);

            assertThat(jdbcTemplate.queryForObject("""
                    SELECT action
                    FROM admin_content_moderation_logs
                    WHERE post_id = ?
                    """, String.class, postId))
                    .isEqualTo(action.name());

            assertThat(jdbcTemplate.queryForObject("""
                    SELECT reason
                    FROM admin_content_moderation_logs
                    WHERE post_id = ?
                    """, String.class, postId))
                    .isEqualTo("첫 번째 관리자 처리");
        } finally {
            // 검증 도중 실패하더라도 첫 번째 작업의 대기를 해제한다.
            allowFirstCommit.countDown();

            executor.shutdownNow();

            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS))
                    .as("테스트 작업 스레드가 종료되어야 한다")
                    .isTrue();
        }
    }

    private Member createAdmin(String label, String suffix) {
        Member member = Member.createPending(
                "admin-" + label + "-" + suffix + "@example.com",
                "test-only-password-hash",
                "관리테스트" + label + suffix
        );
        member.activeAfterEmailVerification();

        entityManager.persist(member);
        entityManager.flush();

        jdbcTemplate.update("""
                UPDATE members
                SET role = 'ADMIN',
                    credential_version = ?
                WHERE id = ?
                """, CREDENTIAL_VERSION, member.getId());

        return member;
    }

    private PostModerationResult execute(
            ModerationAction action,
            Long actorId,
            String reason
    ) {
        return switch (action) {
            case HIDE -> service.hide(
                    actorId,
                    CREDENTIAL_VERSION,
                    postId,
                    reason
            );
            case RESTORE -> service.restore(
                    actorId,
                    CREDENTIAL_VERSION,
                    postId,
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
        // 해당 테스트 트랜잭션에서만 적용되는 DB 잠금 대기 제한.
        jdbcTemplate.execute("SET LOCAL lock_timeout = '15s'");
    }

    private void awaitRelease(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
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
                + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {
            if (waitingTask.isDone()) {
                // 작업이 예외로 끝났다면 원래 실패 원인을 전달한다.
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

            // 정해진 시간을 기다린 뒤 성공으로 간주하지 않고,
            // DB의 실제 대기 상태를 짧은 간격으로 확인한다.
            Thread.sleep(50);
        }

        throw new AssertionError(
                "두 번째 요청의 DB 잠금 대기를 확인하지 못했습니다."
        );
    }

    private Instant readHiddenAt() {
        return jdbcTemplate.queryForObject("""
                SELECT hidden_at
                FROM community_posts
                WHERE id = ?
                """,
                (rs, rowNum) -> {
                    Timestamp value = rs.getTimestamp("hidden_at");
                    return value == null ? null : value.toInstant();
                },
                postId
        );
    }
}