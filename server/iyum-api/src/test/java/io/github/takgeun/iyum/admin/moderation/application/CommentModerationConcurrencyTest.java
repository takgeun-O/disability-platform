package io.github.takgeun.iyum.admin.moderation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.application.PostDeleteService;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.datasource.hikari.maximum-pool-size=6"
})
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class CommentModerationConcurrencyTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-29T09:00:00Z");

    private static final Instant ORIGINAL_HIDDEN_AT =
            Instant.parse("2026-10-29T10:00:00Z");

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
    CommentModerationService moderationService;

    @Autowired
    PostDeleteService postDeleteService;

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
    private Long authorId;
    private Long postId;
    private Long commentId;

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
            Member firstAdmin = createMember("A", suffix, true);
            Member secondAdmin = createMember("B", suffix, true);
            Member author = createMember("C", suffix, false);

            Post post = Post.create(
                    author,
                    PostType.GENERAL,
                    PostTopic.DAILY,
                    "동시성 테스트 제목",
                    "동시성 테스트 본문"
            );
            entityManager.persist(post);

            Comment comment = Comment.create(
                    post,
                    author,
                    "동시성 테스트 댓글"
            );
            entityManager.persist(comment);

            entityManager.flush();

            firstAdminId = firstAdmin.getId();
            secondAdminId = secondAdmin.getId();
            authorId = author.getId();
            postId = post.getId();
            commentId = comment.getId();

            entityManager.clear();
        });
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 서로_다른_관리자의_동일_요청은_한_번만_변경한다(
            ModerationAction action
    ) throws Exception {
        prepareComment(action);

        assertThat(firstAdminId).isNotEqualTo(secondAdminId);

        var results = runWithBlockedSecond(
                () -> moderate(
                        action,
                        firstAdminId,
                        "첫 번째 관리자 처리"
                ),
                () -> moderate(
                        action,
                        secondAdminId,
                        "두 번째 관리자 처리"
                )
        );

        assertThat(results.first().changed()).isTrue();
        assertThat(results.second().changed()).isFalse();

        boolean expectedHidden = action == ModerationAction.HIDE;

        assertThat(results.first().hidden()).isEqualTo(expectedHidden);
        assertThat(results.second().hidden()).isEqualTo(expectedHidden);

        Instant storedHiddenAt = readCommentHiddenAt();

        if (expectedHidden) {
            assertThat(storedHiddenAt).isNotNull();
        } else {
            assertThat(storedHiddenAt).isNull();
        }

        assertThat(results.second().hiddenAt()).isEqualTo(storedHiddenAt);

        assertThat(countLogs()).isEqualTo(1L);
        assertLog(firstAdminId, action, "첫 번째 관리자 처리");
        assertThat(readPostDeletedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 게시글_삭제가_먼저_커밋되면_댓글_처리를_거절한다(
            ModerationAction action
    ) {
        prepareComment(action);
        Instant hiddenAtBefore = readCommentHiddenAt();

        assertThatThrownBy(() -> runWithBlockedSecond(
                () -> {
                    postDeleteService.delete(authorId, postId);
                    return Boolean.TRUE;
                },
                () -> moderate(
                        action,
                        firstAdminId,
                        "삭제 이후 댓글 처리"
                )
        ))
                .isInstanceOfSatisfying(
                        ExecutionException.class,
                        exception ->
                                assertThat(exception.getCause())
                                        .isInstanceOfSatisfying(
                                                ContentModerationException.class,
                                                cause ->
                                                        assertThat(cause.getCode())
                                                                .isEqualTo(
                                                                        ContentModerationException
                                                                                .Code.POST_DELETED
                                                                )
                                        )
                );

        assertThat(readPostDeletedAt()).isNotNull();
        assertThat(readCommentHiddenAt()).isEqualTo(hiddenAtBefore);
        assertThat(countLogs()).isZero();
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 댓글_처리가_먼저_커밋되면_이후_게시글을_삭제할_수_있다(
            ModerationAction action
    ) throws Exception {
        prepareComment(action);

        var results = runWithBlockedSecond(
                () -> moderate(
                        action,
                        firstAdminId,
                        "삭제 전 댓글 처리"
                ),
                () -> {
                    postDeleteService.delete(authorId, postId);
                    return Boolean.TRUE;
                }
        );

        assertThat(results.first().changed()).isTrue();
        assertThat(results.second()).isTrue();
        assertThat(readPostDeletedAt()).isNotNull();

        if (action == ModerationAction.HIDE) {
            assertThat(readCommentHiddenAt()).isNotNull();
        } else {
            assertThat(readCommentHiddenAt()).isNull();
        }

        assertThat(countLogs()).isEqualTo(1L);
        assertLog(firstAdminId, action, "삭제 전 댓글 처리");
    }

    private Member createMember(
            String label,
            String suffix,
            boolean admin
    ) {
        Member member = Member.createPending(
                "member-" + label + "-" + suffix + "@example.com",
                "test-only-password-hash",
                "테스트회원" + label + suffix
        );
        member.activeAfterEmailVerification();

        entityManager.persist(member);
        entityManager.flush();

        if (admin) {
            jdbcTemplate.update("""
                    UPDATE members
                    SET role = 'ADMIN',
                        credential_version = ?
                    WHERE id = ?
                    """, CREDENTIAL_VERSION, member.getId());
        }

        return member;
    }

    private void prepareComment(ModerationAction action) {
        if (action == ModerationAction.RESTORE) {
            transactionTemplate.executeWithoutResult(status -> {
                Comment comment =
                        entityManager.find(Comment.class, commentId);

                comment.hide(ORIGINAL_HIDDEN_AT);
            });
        }
    }

    private CommentModerationResult moderate(
            ModerationAction action,
            Long actorId,
            String reason
    ) {
        return switch (action) {
            case HIDE -> moderationService.hide(
                    actorId,
                    CREDENTIAL_VERSION,
                    postId,
                    commentId,
                    reason
            );
            case RESTORE -> moderationService.restore(
                    actorId,
                    CREDENTIAL_VERSION,
                    postId,
                    commentId,
                    reason
            );
        };
    }

    private <A, B> ConcurrentResults<A, B> runWithBlockedSecond(
            Supplier<A> firstWork,
            Supplier<B> secondWork
    ) throws Exception {
        CountDownLatch firstProcessed = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<A> first = executor.submit(() ->
                    transactionTemplate.execute(status -> {
                        configureLockTimeout();
                        firstPid.set(currentBackendPid());

                        A result = firstWork.get();

                        firstProcessed.countDown();
                        awaitRelease(allowFirstCommit);

                        return result;
                    })
            );

            assertThat(firstProcessed.await(10, TimeUnit.SECONDS))
                    .as("첫 번째 작업이 완료되어야 한다")
                    .isTrue();

            Future<B> second = executor.submit(() ->
                    transactionTemplate.execute(status -> {
                        configureLockTimeout();
                        secondPid.set(currentBackendPid());
                        secondStarted.countDown();

                        return secondWork.get();
                    })
            );

            assertThat(secondStarted.await(10, TimeUnit.SECONDS))
                    .as("두 번째 작업이 시작되어야 한다")
                    .isTrue();

            assertThat(firstPid.get()).isNotEqualTo(secondPid.get());

            awaitBlockedBy(
                    secondPid.get(),
                    firstPid.get(),
                    second
            );

            allowFirstCommit.countDown();

            A firstResult = first.get(10, TimeUnit.SECONDS);
            B secondResult = second.get(10, TimeUnit.SECONDS);

            return new ConcurrentResults<>(firstResult, secondResult);
        } finally {
            allowFirstCommit.countDown();
            executor.shutdownNow();

            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS))
                    .as("테스트 작업 스레드가 종료되어야 한다")
                    .isTrue();
        }
    }

    private int currentBackendPid() {
        return jdbcTemplate.queryForObject(
                "SELECT pg_backend_pid()",
                Integer.class
        );
    }

    private void configureLockTimeout() {
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

            Thread.sleep(50);
        }

        throw new AssertionError(
                "두 번째 요청의 DB 잠금 대기를 확인하지 못했습니다."
        );
    }

    private Instant readCommentHiddenAt() {
        return jdbcTemplate.queryForObject("""
                SELECT hidden_at
                FROM community_comments
                WHERE id = ?
                """,
                (rs, rowNum) -> {
                    Timestamp value = rs.getTimestamp("hidden_at");
                    return value == null ? null : value.toInstant();
                },
                commentId
        );
    }

    private Instant readPostDeletedAt() {
        return jdbcTemplate.queryForObject("""
                SELECT deleted_at
                FROM community_posts
                WHERE id = ?
                """,
                (rs, rowNum) -> {
                    Timestamp value = rs.getTimestamp("deleted_at");
                    return value == null ? null : value.toInstant();
                },
                postId
        );
    }

    private long countLogs() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM admin_content_moderation_logs
                WHERE comment_id = ?
                """, Long.class, commentId);

        return count.longValue();
    }

    private void assertLog(
            Long expectedActorId,
            ModerationAction action,
            String reason
    ) {
        jdbcTemplate.queryForObject("""
                SELECT actor_member_id,
                       post_id,
                       action,
                       reason
                FROM admin_content_moderation_logs
                WHERE comment_id = ?
                """,
                (rs, rowNum) -> {
                    assertThat(rs.getLong("actor_member_id"))
                            .isEqualTo(expectedActorId.longValue());
                    assertThat(rs.getObject("post_id")).isNull();
                    assertThat(rs.getString("action"))
                            .isEqualTo(action.name());
                    assertThat(rs.getString("reason")).isEqualTo(reason);
                    return Boolean.TRUE;
                },
                commentId
        );
    }

    private record ConcurrentResults<A, B>(
            A first,
            B second
    ) {
    }
}