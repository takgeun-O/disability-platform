package io.github.takgeun.iyum.admin.moderation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.infrastructure.ContentModerationLogRepository;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.Map;
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
class CommentModerationServiceIntegrationTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-29T09:00:00Z");

    private static final Instant CONTENT_UPDATED_AT =
            Instant.parse("2026-10-29T09:00:30Z");

    private static final Instant POST_HIDDEN_AT =
            Instant.parse("2026-10-29T10:00:00Z");

    private static final Instant COMMENT_HIDDEN_AT =
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
    CommentModerationService service;

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
                    "부모 게시글 제목",
                    "부모 게시글 내용"
            );
            entityManager.persist(post);

            Comment comment = Comment.create(
                    post,
                    author,
                    "통합 테스트 댓글"
            );
            entityManager.persist(comment);

            entityManager.flush();

            adminId = admin.getId();
            postId = post.getId();
            commentId = comment.getId();

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
    @CsvSource({
            "HIDE, false, false",
            "HIDE, false, true",
            "HIDE, true, false",
            "HIDE, true, true",
            "RESTORE, false, false",
            "RESTORE, false, true",
            "RESTORE, true, false",
            "RESTORE, true, true"
    })
    void 댓글_상태와_이력이_함께_커밋되고_부모는_유지된다(
            ModerationAction action,
            boolean parentHidden,
            boolean edited
    ) {
        prepareContentUpdatedAt(edited);
        prepareInitialState(action, parentHidden);

        Map<String, Object> parentBefore = readParentSnapshot();

        CommentModerationResult result =
                execute(action, "  댓글 운영 정책 검토  ");

        Instant storedHiddenAt = readCommentHiddenAt();
        StoredLog log = readSingleLog();

        assertThat(result.postId()).isEqualTo(postId);
        assertThat(result.commentId()).isEqualTo(commentId);
        assertThat(result.changed()).isTrue();
        assertThat(countLogs()).isEqualTo(1L);

        assertThat(log.actorId()).isEqualTo(adminId);
        assertThat(log.postId()).isNull();
        assertThat(log.commentId()).isEqualTo(commentId);
        assertThat(log.action()).isEqualTo(action.name());
        assertThat(log.reason()).isEqualTo("댓글 운영 정책 검토");
        assertThat(log.createdAt()).isNotNull();

        if (action == ModerationAction.HIDE) {
            assertThat(result.hidden()).isTrue();
            assertThat(result.hiddenAt()).isNotNull();
            assertThat(storedHiddenAt).isNotNull();

            assertThat(storedHiddenAt).isCloseTo(
                    result.hiddenAt(),
                    within(1, ChronoUnit.MICROS)
            );

            assertThat(log.createdAt()).isEqualTo(storedHiddenAt);
        } else {
            assertThat(result.hidden()).isFalse();
            assertThat(result.hiddenAt()).isNull();
            assertThat(storedHiddenAt).isNull();
        }

        assertThat(jdbcTemplate.queryForObject("""
                SELECT content
                FROM community_comments
                WHERE id = ?
                """, String.class, commentId))
                .isEqualTo("통합 테스트 댓글");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT deleted_at IS NULL
                FROM community_comments
                WHERE id = ?
                """, Boolean.class, commentId))
                .isTrue();

        assertThat(readParentSnapshot()).isEqualTo(parentBefore);
        assertThat(readCommentContentUpdatedAt())
                .isEqualTo(edited ? CONTENT_UPDATED_AT : null);
    }

    @ParameterizedTest
    @CsvSource({
            "HIDE, false, false",
            "HIDE, false, true",
            "HIDE, true, false",
            "HIDE, true, true",
            "RESTORE, false, false",
            "RESTORE, false, true",
            "RESTORE, true, false",
            "RESTORE, true, true"
    })
    void 동일한_요청을_반복해도_이력이_늘어나지_않는다(
            ModerationAction action,
            boolean parentHidden,
            boolean edited
    ) {
        prepareContentUpdatedAt(edited);
        prepareInitialState(action, parentHidden);

        Map<String, Object> parentBefore = readParentSnapshot();

        CommentModerationResult first =
                execute(action, "최초 처리 사유");

        Map<String, Object> commentAfterFirst =
                readCommentSnapshot();

        Instant hiddenAtAfterFirst = readCommentHiddenAt();

        assertThat(first.changed()).isTrue();
        assertThat(countLogs()).isEqualTo(1L);

        CommentModerationResult second =
                execute(action, "두 번째 요청 사유");

        assertThat(second.changed()).isFalse();
        assertThat(second.hidden())
                .isEqualTo(action == ModerationAction.HIDE);
        assertThat(second.hiddenAt()).isEqualTo(hiddenAtAfterFirst);

        assertThat(readCommentSnapshot()).isEqualTo(commentAfterFirst);
        assertThat(countLogs()).isEqualTo(1L);
        assertThat(readSingleLog().reason()).isEqualTo("최초 처리 사유");
        assertThat(readParentSnapshot()).isEqualTo(parentBefore);

        assertThat(readCommentContentUpdatedAt())
                .isEqualTo(edited ? CONTENT_UPDATED_AT : null);
    }

    @ParameterizedTest
    @CsvSource({
            "HIDE, false, false",
            "HIDE, false, true",
            "HIDE, true, false",
            "HIDE, true, true",
            "RESTORE, false, false",
            "RESTORE, false, true",
            "RESTORE, true, false",
            "RESTORE, true, true"
    })
    void DB_반영_후_실패하면_댓글과_이력이_함께_롤백된다(
            ModerationAction action,
            boolean parentHidden,
            boolean edited
    ) {
        prepareContentUpdatedAt(edited);
        prepareInitialState(action, parentHidden);

        Map<String, Object> parentBefore = readParentSnapshot();
        Map<String, Object> commentBefore = readCommentSnapshot();

        IllegalStateException failure =
                new IllegalStateException("테스트용 저장 후 실패");

        doAnswer(invocation -> {
            ContentModerationLog log = invocation.getArgument(0);

            // 이력 INSERT와 댓글 변경 UPDATE를 실제 DB에 반영한다.
            entityManager.persist(log);
            entityManager.flush();

            // 아직 커밋 전이지만 같은 트랜잭션에서는 조회할 수 있다.
            assertThat(countLogs()).isEqualTo(1L);

            if (action == ModerationAction.HIDE) {
                assertThat(readCommentHiddenAt()).isNotNull();
            } else {
                assertThat(readCommentHiddenAt()).isNull();
            }

            assertThat(readParentSnapshot()).isEqualTo(parentBefore);

            throw failure;
        }).when(logRepository).save(any(ContentModerationLog.class));

        assertThatThrownBy(() ->
                execute(action, "롤백 검증 사유")
        ).isSameAs(failure);

        // 서비스 트랜잭션이 종료된 뒤 DB에서 다시 확인한다.
        assertThat(readCommentSnapshot()).isEqualTo(commentBefore);
        assertThat(readParentSnapshot()).isEqualTo(parentBefore);
        assertThat(countLogs()).isZero();

        assertThat(readCommentContentUpdatedAt())
                .isEqualTo(edited ? CONTENT_UPDATED_AT : null);
    }

    private void prepareInitialState(
            ModerationAction action,
            boolean parentHidden
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            if (parentHidden) {
                Post post = entityManager.find(Post.class, postId);
                post.hide(POST_HIDDEN_AT);
            }

            if (action == ModerationAction.RESTORE) {
                Comment comment =
                        entityManager.find(Comment.class, commentId);

                comment.hide(COMMENT_HIDDEN_AT);
            }
        });

        // 이후 서비스 변경의 수정 시각이 초기 상태와 구별되도록 한다.
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_AT.plusSeconds(60)));
    }

    private CommentModerationResult execute(
            ModerationAction action,
            String reason
    ) {
        return switch (action) {
            case HIDE -> service.hide(
                    adminId,
                    CREDENTIAL_VERSION,
                    postId,
                    commentId,
                    reason
            );
            case RESTORE -> service.restore(
                    adminId,
                    CREDENTIAL_VERSION,
                    postId,
                    commentId,
                    reason
            );
        };
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

    private Map<String, Object> readParentSnapshot() {
        return jdbcTemplate.queryForMap("""
                SELECT title,
                       content,
                       hidden_at,
                       deleted_at,
                       updated_at
                FROM community_posts
                WHERE id = ?
                """, postId);
    }

    private Map<String, Object> readCommentSnapshot() {
        return jdbcTemplate.queryForMap("""
                SELECT post_id,
                       content,
                       hidden_at,
                       deleted_at,
                       updated_at,
                       content_updated_at
                FROM community_comments
                WHERE id = ?
                """, commentId);
    }

    private long countLogs() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM admin_content_moderation_logs
                WHERE comment_id = ?
                """,
                Long.class,
                commentId
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
                WHERE comment_id = ?
                """,
                (rs, rowNum) -> new StoredLog(
                        rs.getLong("actor_member_id"),
                        rs.getObject("post_id", Long.class),
                        rs.getLong("comment_id"),
                        rs.getString("action"),
                        rs.getString("reason"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                commentId
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

    private void prepareContentUpdatedAt(boolean edited) {
        // 이번 테스트는 운영 조치 전후의 값 보존을 검증한다.
        // 본문 수정 자체의 기록 동작은 별도 테스트에서 검증한다.
        Timestamp value = edited
                ? Timestamp.from(CONTENT_UPDATED_AT)
                : null;

        int updatedRows = jdbcTemplate.update("""
            UPDATE community_comments
            SET content_updated_at = ?
            WHERE id = ?
            """,
                value,
                commentId
        );

        assertThat(updatedRows).isEqualTo(1);
    }

    private Instant readCommentContentUpdatedAt() {
        return jdbcTemplate.queryForObject("""
            SELECT content_updated_at
            FROM community_comments
            WHERE id = ?
            """,
                (rs, rowNum) -> {
                    Timestamp value =
                            rs.getTimestamp("content_updated_at");

                    return value == null
                            ? null
                            : value.toInstant();
                },
                commentId
        );
    }
}