package io.github.takgeun.iyum.admin.moderation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.infrastructure.ContentModerationLogRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class PostModerationServiceIntegrationTest {

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

    @MockitoSpyBean
    ContentModerationLogRepository logRepository;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private TransactionTemplate transactionTemplate;

    private Long adminId;
    private Long postId;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_AT));

        transactionTemplate =
                new TransactionTemplate(transactionManager);

        // 테스트마다 고유한 회원을 만들어 데이터 충돌을 피한다.
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        transactionTemplate.executeWithoutResult(status -> {
            Member admin = Member.createPending(
                    "admin-" + suffix + "@example.com",
                    "test-only-password-hash",
                    "관리테스트" + suffix
            );
            admin.activeAfterEmailVerification();
            entityManager.persist(admin);

            Member author = Member.createPending(
                    "author-" + suffix + "@example.com",
                    "test-only-password-hash",
                    "작성테스트" + suffix
            );
            author.activeAfterEmailVerification();
            entityManager.persist(author);

            Post post = Post.create(
                    author,
                    PostType.GENERAL,
                    PostTopic.DAILY,
                    "통합 테스트 제목",
                    "통합 테스트 내용"
            );
            entityManager.persist(post);

            entityManager.flush();

            adminId = admin.getId();
            postId = post.getId();

            jdbcTemplate.update("""
                    UPDATE members
                    SET role = 'ADMIN',
                        credential_version = ?
                    WHERE id = ?
                    """, CREDENTIAL_VERSION, adminId);

            entityManager.clear();
        });
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 상태_변경과_이력이_함께_커밋된다(
            ModerationAction action
    ) {
        prepareInitialState(action);

        PostModerationResult result =
                execute(action, "  운영 정책 검토  ");

        // 서비스 트랜잭션이 끝난 뒤 DB를 직접 확인한다.
        Instant storedHiddenAt = readHiddenAt();
        StoredLog log = readSingleLog();

        assertThat(result.postId()).isEqualTo(postId);
        assertThat(result.changed()).isTrue();
        assertThat(countLogs()).isEqualTo(1L);

        assertThat(log.actorId()).isEqualTo(adminId);
        assertThat(log.postId()).isEqualTo(postId);
        assertThat(log.commentId()).isNull();
        assertThat(log.action()).isEqualTo(action.name());
        assertThat(log.reason()).isEqualTo("운영 정책 검토");
        assertThat(log.createdAt()).isNotNull();

        if (action == ModerationAction.HIDE) {
            assertThat(result.hidden()).isTrue();
            assertThat(result.hiddenAt()).isNotNull();
            assertThat(storedHiddenAt).isNotNull();

            // Instant와 PostgreSQL 사이의 시간 정밀도 차이를 허용한다.
            assertThat(storedHiddenAt).isCloseTo(
                    result.hiddenAt(),
                    within(1, ChronoUnit.MICROS)
            );

            // 두 컬럼 모두 DB에서 읽은 값이므로 정확히 비교한다.
            assertThat(log.createdAt()).isEqualTo(storedHiddenAt);
        } else {
            assertThat(result.hidden()).isFalse();
            assertThat(result.hiddenAt()).isNull();
            assertThat(storedHiddenAt).isNull();
        }

        assertThat(jdbcTemplate.queryForObject("""
                SELECT content
                FROM community_posts
                WHERE id = ?
                """, String.class, postId))
                .isEqualTo("통합 테스트 내용");
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 동일한_요청을_반복해도_이력이_늘어나지_않는다(
            ModerationAction action
    ) {
        prepareInitialState(action);

        PostModerationResult first =
                execute(action, "최초 처리 사유");

        Instant hiddenAtAfterFirst = readHiddenAt();

        assertThat(first.changed()).isTrue();
        assertThat(countLogs()).isEqualTo(1L);

        PostModerationResult second =
                execute(action, "두 번째 요청 사유");

        assertThat(second.changed()).isFalse();
        assertThat(second.hidden())
                .isEqualTo(action == ModerationAction.HIDE);
        assertThat(second.hiddenAt()).isEqualTo(hiddenAtAfterFirst);

        assertThat(readHiddenAt()).isEqualTo(hiddenAtAfterFirst);
        assertThat(countLogs()).isEqualTo(1L);

        // 중복 요청으로 기존 이력의 사유도 덮어쓰지 않는다.
        assertThat(readSingleLog().reason())
                .isEqualTo("최초 처리 사유");
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void DB에_반영한_뒤_실패해도_상태와_이력이_함께_롤백된다(
            ModerationAction action
    ) {
        prepareInitialState(action);

        Instant hiddenAtBefore = readHiddenAt();

        IllegalStateException failure =
                new IllegalStateException("테스트용 저장 후 실패");

        doAnswer(invocation -> {
            ContentModerationLog log = invocation.getArgument(0);

            // 실패 테스트에서만 save 동작을 대체한다.
            // 실제 DB에 이력을 INSERT하고 게시글 변경도 flush한다.
            entityManager.persist(log);
            entityManager.flush();

            // 같은 트랜잭션 안에서는 변경이 DB에 반영된 것을 확인한다.
            assertThat(countLogs()).isEqualTo(1L);

            if (action == ModerationAction.HIDE) {
                assertThat(readHiddenAt()).isNotNull();
            } else {
                assertThat(readHiddenAt()).isNull();
            }

            // SQL 실행 후, 커밋 전에 오류를 발생시킨다.
            throw failure;
        }).when(logRepository).save(any(ContentModerationLog.class));

        assertThatThrownBy(() ->
                execute(action, "롤백 검증 사유")
        ).isSameAs(failure);

        // 서비스 트랜잭션 종료 후 별도 조회로 롤백 여부를 확인한다.
        assertThat(readHiddenAt()).isEqualTo(hiddenAtBefore);
        assertThat(countLogs()).isZero();
    }

    private void prepareInitialState(ModerationAction action) {
        if (action == ModerationAction.RESTORE) {
            transactionTemplate.executeWithoutResult(status -> {
                Post post = entityManager.find(Post.class, postId);
                post.hide(ORIGINAL_HIDDEN_AT);
            });
        }
    }

    private PostModerationResult execute(
            ModerationAction action,
            String reason
    ) {
        return switch (action) {
            case HIDE -> service.hide(
                    adminId,
                    CREDENTIAL_VERSION,
                    postId,
                    reason
            );
            case RESTORE -> service.restore(
                    adminId,
                    CREDENTIAL_VERSION,
                    postId,
                    reason
            );
        };
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

    private long countLogs() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM admin_content_moderation_logs
                WHERE post_id = ?
                """,
                Long.class,
                postId
        );

        return count.longValue();
    }

    private StoredLog readSingleLog() {
        return jdbcTemplate.queryForObject("""
                SELECT actor_member_id,
                       post_id,
                       comment_id,
                       action,
                       reason,
                       created_at
                FROM admin_content_moderation_logs
                WHERE post_id = ?
                """,
                (rs, rowNum) -> new StoredLog(
                        rs.getLong("actor_member_id"),
                        rs.getLong("post_id"),
                        rs.getObject("comment_id", Long.class),
                        rs.getString("action"),
                        rs.getString("reason"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                postId
        );
    }

    private record StoredLog(
            Long actorId,
            Long postId,
            Long commentId,
            String action,
            String reason,
            Instant createdAt
    ) {
    }
}