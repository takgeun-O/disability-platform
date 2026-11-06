package io.github.takgeun.iyum.admin.moderation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
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

    private static final String COMMENT_CONTENT = "통합 테스트 댓글";

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

        // 준비 데이터는 별도 트랜잭션으로 먼저 커밋한다.
        // 테스트 메서드에 @Transactional을 붙이지 않아야
        // 서비스의 실제 커밋과 롤백 결과를 확인할 수 있다.
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
                    COMMENT_CONTENT
            );
            entityManager.persist(comment);

            entityManager.flush();

            adminId = admin.getId();
            postId = post.getId();
            commentId = comment.getId();

            int updatedRows = jdbcTemplate.update("""
                    UPDATE members
                    SET role = 'ADMIN',
                        credential_version = ?
                    WHERE id = ?
                    """, CREDENTIAL_VERSION, adminId);

            assertThat(updatedRows).isEqualTo(1);

            // SQL로 변경한 역할이 영속성 컨텍스트의 기존 값과
            // 달라졌으므로 관리 중인 엔티티를 비운다.
            entityManager.clear();
        });
    }

    /**
     * 조치 × 부모 숨김 여부 × 본문 수정 이력 여부 × 요청 방식.
     *
     * 기존 자유 입력 경로와 새 유형 경로가 모두
     * 동일한 트랜잭션 정책을 지키는지 검증한다.
     */
    private static Stream<Arguments> moderationCases() {
        return Stream.of(ModerationAction.HIDE, ModerationAction.RESTORE)
                .flatMap(action ->
                        Stream.of(false, true).flatMap(parentHidden ->
                                Stream.of(false, true).flatMap(edited ->
                                        Stream.of(false, true).map(typed ->
                                                Arguments.of(
                                                        action,
                                                        parentHidden,
                                                        edited,
                                                        typed
                                                )
                                        )
                                )
                        )
                );
    }

    @ParameterizedTest
    @MethodSource("moderationCases")
    void 댓글_상태와_이력이_함께_커밋되고_부모는_유지된다(
            ModerationAction action,
            boolean parentHidden,
            boolean edited,
            boolean typed
    ) {
        prepareContentUpdatedAt(edited);
        prepareInitialState(action, parentHidden);

        Map<String, Object> parentBefore = readParentSnapshot();

        CommentModerationResult result =
                execute(action, "  댓글 운영 정책 검토  ", typed);

        StoredLog log = readSingleLog();

        assertChangedResult(action, result);
        assertThat(countLogs()).isEqualTo(1L);

        assertLog(
                log,
                action,
                typed
                        ? ModerationReasonType.OTHER
                        : ModerationReasonType.LEGACY,
                typed ? "댓글 운영 정책 검토" : null,
                "댓글 운영 정책 검토"
        );

        if (action == ModerationAction.HIDE) {
            assertThat(log.createdAt()).isEqualTo(readCommentHiddenAt());
        }

        assertCommentPreserved(edited);
        assertThat(readParentSnapshot()).isEqualTo(parentBefore);
    }

    @ParameterizedTest
    @MethodSource("moderationCases")
    void 동일한_요청을_반복해도_댓글과_이력이_변경되지_않는다(
            ModerationAction action,
            boolean parentHidden,
            boolean edited,
            boolean typed
    ) {
        prepareContentUpdatedAt(edited);
        prepareInitialState(action, parentHidden);

        Map<String, Object> parentBefore = readParentSnapshot();

        CommentModerationResult first =
                execute(action, "최초 처리 사유", typed);

        assertChangedResult(action, first);
        assertThat(countLogs()).isEqualTo(1L);

        Map<String, Object> commentAfterFirst = readCommentSnapshot();
        Instant hiddenAtAfterFirst = readCommentHiddenAt();
        StoredLog firstLog = readSingleLog();

        // 두 번째 요청이 실제 UPDATE를 발생시키면
        // updated_at 변경도 스냅샷 비교에서 잡히도록 한다.
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_AT.plusSeconds(120)));

        CommentModerationResult second =
                execute(action, "두 번째 요청 사유", typed);

        assertThat(second.postId()).isEqualTo(postId);
        assertThat(second.commentId()).isEqualTo(commentId);
        assertThat(second.changed()).isFalse();
        assertThat(second.hidden())
                .isEqualTo(action == ModerationAction.HIDE);
        assertThat(second.hiddenAt()).isEqualTo(hiddenAtAfterFirst);

        assertThat(readCommentSnapshot()).isEqualTo(commentAfterFirst);
        assertThat(readParentSnapshot()).isEqualTo(parentBefore);
        assertThat(countLogs()).isEqualTo(1L);

        // 개수뿐 아니라 최초 이력의 사유와 시각도 보존되어야 한다.
        assertThat(readSingleLog()).isEqualTo(firstLog);

        assertCommentPreserved(edited);
    }

    @ParameterizedTest
    @MethodSource("moderationCases")
    void DB_반영_후_실패하면_댓글과_이력이_함께_롤백된다(
            ModerationAction action,
            boolean parentHidden,
            boolean edited,
            boolean typed
    ) {
        prepareContentUpdatedAt(edited);
        prepareInitialState(action, parentHidden);

        Map<String, Object> parentBefore = readParentSnapshot();
        Map<String, Object> commentBefore = readCommentSnapshot();

        IllegalStateException failure =
                new IllegalStateException("테스트용 저장 후 실패");

        doAnswer(invocation -> {
            ContentModerationLog log = invocation.getArgument(0);

            // save 호출 지점에서 이력 INSERT와 댓글 UPDATE를
            // 실제 DB에 flush한 뒤 예외를 발생시킨다.
            // flush는 커밋이 아니므로 서비스 트랜잭션에서 롤백된다.
            entityManager.persist(log);
            entityManager.flush();

            assertThat(countLogs()).isEqualTo(1L);

            assertLog(
                    readSingleLog(),
                    action,
                    typed
                            ? ModerationReasonType.OTHER
                            : ModerationReasonType.LEGACY,
                    typed ? "롤백 검증 사유" : null,
                    "롤백 검증 사유"
            );

            if (action == ModerationAction.HIDE) {
                assertThat(readCommentHiddenAt()).isNotNull();
            } else {
                assertThat(readCommentHiddenAt()).isNull();
            }

            assertCommentPreserved(edited);
            assertThat(readParentSnapshot()).isEqualTo(parentBefore);

            throw failure;
        }).when(logRepository).save(any(ContentModerationLog.class));

        assertThatThrownBy(() ->
                execute(action, "롤백 검증 사유", typed)
        ).isSameAs(failure);

        // 서비스 트랜잭션이 종료된 뒤 DB에서 다시 조회한다.
        assertThat(readCommentSnapshot()).isEqualTo(commentBefore);
        assertThat(readParentSnapshot()).isEqualTo(parentBefore);
        assertThat(countLogs()).isZero();

        assertCommentPreserved(edited);
    }

    @ParameterizedTest
    @CsvSource({
            "HIDE, false, SPAM_ADVERTISING, , 광고·홍보·스팸",
            "HIDE, true, SPAM_ADVERTISING, , 광고·홍보·스팸",
            "RESTORE, false, NO_VIOLATION, , 재검토 결과 위반 없음",
            "RESTORE, true, NO_VIOLATION, , 재검토 결과 위반 없음",
            "HIDE, true, OTHER, 개별 숨김 사유, 개별 숨김 사유",
            "RESTORE, true, OTHER, 개별 복원 사유, 개별 복원 사유",
            "HIDE, false, SPAM_ADVERTISING, 반복 광고 확인, 반복 광고 확인",
            "RESTORE, false, MODERATION_ERROR, 조치 대상 착오, 조치 대상 착오"
    })
    void 사유_유형과_상세를_저장하고_부모와_본문수정시각을_유지한다(
            ModerationAction action,
            boolean parentHidden,
            ModerationReasonType reasonType,
            String detail,
            String expectedReason
    ) {
        prepareContentUpdatedAt(true);
        prepareInitialState(action, parentHidden);

        Map<String, Object> parentBefore = readParentSnapshot();

        String inputDetail = detail == null
                ? null
                : "  " + detail + "  ";

        CommentModerationResult result =
                executeTyped(action, reasonType, inputDetail);

        assertChangedResult(action, result);
        assertThat(countLogs()).isEqualTo(1L);

        StoredLog log = readSingleLog();

        assertLog(log, action, reasonType, detail, expectedReason);

        if (action == ModerationAction.HIDE) {
            assertThat(log.createdAt()).isEqualTo(readCommentHiddenAt());
        }

        assertCommentPreserved(true);
        assertThat(readParentSnapshot()).isEqualTo(parentBefore);
    }

    @ParameterizedTest
    @CsvSource({
            "HIDE, , 상세 설명",
            "RESTORE, , 상세 설명",
            "HIDE, LEGACY, 상세 설명",
            "RESTORE, LEGACY, 상세 설명",
            "HIDE, NO_VIOLATION, 상세 설명",
            "RESTORE, SPAM_ADVERTISING, 상세 설명",
            "HIDE, OTHER, ",
            "RESTORE, OTHER, "
    })
    void 잘못된_유형과_상세는_거부하고_DB를_변경하지_않는다(
            ModerationAction action,
            ModerationReasonType reasonType,
            String detail
    ) {
        prepareContentUpdatedAt(true);
        prepareInitialState(action, true);

        Map<String, Object> parentBefore = readParentSnapshot();
        Map<String, Object> commentBefore = readCommentSnapshot();

        assertThatThrownBy(() ->
                executeTyped(action, reasonType, detail)
        ).isInstanceOfSatisfying(
                ContentModerationException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                ContentModerationException.Code.INVALID_REASON
                        )
        );

        assertThat(readCommentSnapshot()).isEqualTo(commentBefore);
        assertThat(readParentSnapshot()).isEqualTo(parentBefore);
        assertThat(countLogs()).isZero();
    }

    @ParameterizedTest
    @EnumSource(
            value = ModerationAction.class,
            names = {"HIDE", "RESTORE"}
    )
    void 이미_요청한_상태라면_유형_요청도_댓글과_이력을_변경하지_않는다(
            ModerationAction action
    ) {
        prepareContentUpdatedAt(true);

        // HIDE 요청은 이미 숨김인 댓글로,
        // RESTORE 요청은 이미 정상인 댓글로 준비한다.
        ModerationAction initialState = action == ModerationAction.HIDE
                ? ModerationAction.RESTORE
                : ModerationAction.HIDE;

        prepareInitialState(initialState, true);

        Map<String, Object> parentBefore = readParentSnapshot();
        Map<String, Object> commentBefore = readCommentSnapshot();
        Instant hiddenAtBefore = readCommentHiddenAt();

        CommentModerationResult result = executeTyped(
                action,
                ModerationReasonType.OTHER,
                "중복 요청 확인"
        );

        assertThat(result.postId()).isEqualTo(postId);
        assertThat(result.commentId()).isEqualTo(commentId);
        assertThat(result.changed()).isFalse();
        assertThat(result.hidden())
                .isEqualTo(action == ModerationAction.HIDE);
        assertThat(result.hiddenAt()).isEqualTo(hiddenAtBefore);

        assertThat(readCommentSnapshot()).isEqualTo(commentBefore);
        assertThat(readParentSnapshot()).isEqualTo(parentBefore);
        assertThat(countLogs()).isZero();

        assertCommentPreserved(true);
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

            // 복원 테스트는 미리 숨겨진 댓글이 필요하다.
            if (action == ModerationAction.RESTORE) {
                Comment comment =
                        entityManager.find(Comment.class, commentId);

                comment.hide(COMMENT_HIDDEN_AT);
            }
        });

        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_AT.plusSeconds(60)));
    }

    private void prepareContentUpdatedAt(boolean edited) {
        // 본문 수정 자체의 동작은 별도 테스트에서 검증한다.
        // 여기서는 운영 조치 전후 값의 보존 여부를 확인한다.
        Timestamp value = edited
                ? Timestamp.from(CONTENT_UPDATED_AT)
                : null;

        int updatedRows = jdbcTemplate.update("""
                UPDATE community_comments
                SET content_updated_at = ?
                WHERE id = ?
                """, value, commentId);

        assertThat(updatedRows).isEqualTo(1);
    }

    private CommentModerationResult execute(
            ModerationAction action,
            String reason,
            boolean typed
    ) {
        if (typed) {
            return executeTyped(
                    action,
                    ModerationReasonType.OTHER,
                    reason
            );
        }

        // 기존 자유 입력 경로를 유지하는 동안의 회귀 검증용이다.
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

    private CommentModerationResult executeTyped(
            ModerationAction action,
            ModerationReasonType reasonType,
            String detail
    ) {
        return switch (action) {
            case HIDE -> service.hide(
                    adminId,
                    CREDENTIAL_VERSION,
                    postId,
                    commentId,
                    reasonType,
                    detail
            );
            case RESTORE -> service.restore(
                    adminId,
                    CREDENTIAL_VERSION,
                    postId,
                    commentId,
                    reasonType,
                    detail
            );
        };
    }

    private void assertChangedResult(
            ModerationAction action,
            CommentModerationResult result
    ) {
        assertThat(result.postId()).isEqualTo(postId);
        assertThat(result.commentId()).isEqualTo(commentId);
        assertThat(result.changed()).isTrue();
        assertThat(result.hidden())
                .isEqualTo(action == ModerationAction.HIDE);

        Instant storedHiddenAt = readCommentHiddenAt();

        if (action == ModerationAction.HIDE) {
            assertThat(result.hiddenAt()).isNotNull();
            assertThat(storedHiddenAt).isNotNull();

            // Java Instant와 PostgreSQL timestamp 사이의
            // 나노초/마이크로초 정밀도 차이를 허용한다.
            assertThat(storedHiddenAt).isCloseTo(
                    result.hiddenAt(),
                    within(1, ChronoUnit.MICROS)
            );
        } else {
            assertThat(result.hiddenAt()).isNull();
            assertThat(storedHiddenAt).isNull();
        }
    }

    private void assertLog(
            StoredLog log,
            ModerationAction action,
            ModerationReasonType reasonType,
            String reasonDetail,
            String reason
    ) {
        assertThat(log.actorId()).isEqualTo(adminId);
        assertThat(log.postId()).isNull();
        assertThat(log.commentId()).isEqualTo(commentId);
        assertThat(log.action()).isEqualTo(action.name());
        assertThat(log.reasonType()).isEqualTo(reasonType.name());
        assertThat(log.reasonDetail()).isEqualTo(reasonDetail);
        assertThat(log.reason()).isEqualTo(reason);
        assertThat(log.createdAt()).isNotNull();
    }

    private void assertCommentPreserved(boolean edited) {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT content
                FROM community_comments
                WHERE id = ?
                """, String.class, commentId))
                .isEqualTo(COMMENT_CONTENT);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT deleted_at IS NULL
                FROM community_comments
                WHERE id = ?
                """, Boolean.class, commentId))
                .isTrue();

        assertThat(readCommentContentUpdatedAt())
                .isEqualTo(edited ? CONTENT_UPDATED_AT : null);
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

    private Instant readCommentContentUpdatedAt() {
        return jdbcTemplate.queryForObject("""
                SELECT content_updated_at
                FROM community_comments
                WHERE id = ?
                """,
                (rs, rowNum) -> {
                    Timestamp value =
                            rs.getTimestamp("content_updated_at");

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
                       created_at,
                       updated_at
                FROM community_posts
                WHERE id = ?
                """, postId);
    }

    private Map<String, Object> readCommentSnapshot() {
        return jdbcTemplate.queryForMap("""
                SELECT post_id,
                       author_id,
                       content,
                       hidden_at,
                       deleted_at,
                       created_at,
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
                """, Long.class, commentId);

        assertThat(count).isNotNull();
        return count.longValue();
    }

    private StoredLog readSingleLog() {
        // queryForObject는 결과가 정확히 한 행이어야 한다.
        // 이력이 없거나 중복 생성되면 여기서도 실패한다.
        return jdbcTemplate.queryForObject("""
                SELECT actor_member_id,
                       post_id,
                       comment_id,
                       action,
                       reason_type,
                       reason_detail,
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
                        rs.getString("reason_type"),
                        rs.getString("reason_detail"),
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
            String reasonType,
            String reasonDetail,
            String reason,
            Instant createdAt
    ) {
    }
}