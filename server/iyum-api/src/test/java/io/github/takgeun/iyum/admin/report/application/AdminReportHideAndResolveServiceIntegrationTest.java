package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.admin.moderation.application.ContentModerationException;
import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportHandlingOutcome;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
class AdminReportHideAndResolveServiceIntegrationTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-13T05:00:00Z");

    private static final Instant PREVIOUS_TIME =
            Instant.parse("2026-11-13T06:00:00Z");

    private static final Instant HANDLED_AT =
            Instant.parse("2026-11-13T07:00:00Z");

    private static final String POST_TITLE = "신고 대상 게시글";

    private static final String POST_CONTENT =
            "게시글 원문\n두 번째 줄";

    private static final String COMMENT_CONTENT =
            "댓글 원문\n두 번째 줄";

    private static final String REPORT_REASON =
            "접수 당시 신고 사유";

    private static final String HANDLING_REASON =
            "운영 정책 위반을 확인하여 숨김 처리합니다.";

    private static final String PREVIOUS_REASON =
            "이전 운영 조치";

    private static final String FAILURE_MARKER =
            "test_hide_and_resolve_report_update_failure";

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    private AdminReportHideAndResolveService service;

    @Autowired
    private AdminReportHandlingService handlingService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private Clock clock;

    private TransactionTemplate transactions;
    private Fixture fixture;
    private Long concurrencyAdminId;

    enum Target {
        POST,
        COMMENT
    }

    enum Operation {
        HIDE_AND_RESOLVE,
        RESOLVE,
        DISMISS
    }

    @BeforeEach
    void setUp() {

        concurrencyAdminId = null;

        when(clock.instant()).thenReturn(HANDLED_AT);

        transactions = new TransactionTemplate(
                transactionManager
        );

        // 준비 데이터를 먼저 커밋한다.
        // 서비스 호출은 이 트랜잭션이 끝난 뒤 실행한다.
        fixture = Objects.requireNonNull(
                transactions.execute(status -> {
                    String suffix = UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 12);

                    Member admin = createMember(
                            "admin-" + suffix + "@example.com",
                            "관리" + suffix,
                            MemberRole.ADMIN
                    );

                    Member author = createMember(
                            "author-" + suffix + "@example.com",
                            "작성" + suffix,
                            MemberRole.USER
                    );

                    Member reporter = createMember(
                            "reporter-" + suffix + "@example.com",
                            "신고1" + suffix,
                            MemberRole.USER
                    );

                    Member anotherReporter = createMember(
                            "reporter2-" + suffix + "@example.com",
                            "신고2" + suffix,
                            MemberRole.USER
                    );

                    Post post = Post.create(
                            author,
                            PostType.GENERAL,
                            PostTopic.DAILY,
                            POST_TITLE,
                            POST_CONTENT
                    );
                    entityManager.persist(post);

                    Comment comment = Comment.create(
                            post,
                            author,
                            COMMENT_CONTENT
                    );
                    entityManager.persist(comment);

                    entityManager.flush();

                    ContentReport postReport = createPostReport(
                            reporter.getId(),
                            post
                    );

                    ContentReport commentReport = createCommentReport(
                            reporter.getId(),
                            comment
                    );

                    // 같은 콘텐츠에 대한 다른 회원의 신고도 준비한다.
                    ContentReport anotherPostReport = createPostReport(
                            anotherReporter.getId(),
                            post
                    );

                    ContentReport anotherCommentReport =
                            createCommentReport(
                                    anotherReporter.getId(),
                                    comment
                            );

                    entityManager.flush();

                    return new Fixture(
                            admin.getId(),
                            admin.getCredentialVersion(),
                            author.getId(),
                            reporter.getId(),
                            anotherReporter.getId(),
                            post.getId(),
                            comment.getId(),
                            postReport.getId(),
                            commentReport.getId(),
                            anotherPostReport.getId(),
                            anotherCommentReport.getId()
                    );
                })
        );
    }

    @AfterEach
    void tearDown() {
        if (fixture == null) {
            return;
        }

        transactions.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    """
                    DELETE FROM admin_content_moderation_logs
                    WHERE post_id = ? OR comment_id = ?
                    """,
                    fixture.postId(),
                    fixture.commentId()
            );

            jdbcTemplate.update(
                    """
                    DELETE FROM content_reports
                    WHERE post_id = ? OR comment_id = ?
                    """,
                    fixture.postId(),
                    fixture.commentId()
            );

            jdbcTemplate.update(
                    """
                    DELETE FROM community_comments
                    WHERE id = ?
                    """,
                    fixture.commentId()
            );

            jdbcTemplate.update(
                    """
                    DELETE FROM community_posts
                    WHERE id = ?
                    """,
                    fixture.postId()
            );

            jdbcTemplate.update(
                    """
                    DELETE FROM members
                    WHERE id IN (?, ?, ?, ?)
                    """,
                    fixture.adminId(),
                    fixture.authorId(),
                    fixture.reporterId(),
                    fixture.anotherReporterId()
            );

            if (concurrencyAdminId != null) {
                jdbcTemplate.update(
                        """
                        DELETE FROM members
                        WHERE id = ?
                        """,
                        concurrencyAdminId
                );
            }
        });
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 콘텐츠_숨김과_운영_기록과_신고_처리를_함께_커밋한다(
            Target target
    ) {
        DatabaseSnapshot before = snapshot();

        AdminReportHandlingResult result = service.hideAndResolve(
                fixture.adminId(),
                fixture.adminVersion(),
                reportId(target),
                " \t" + HANDLING_REASON + "\n "
        );

        assertThat(result.reportId()).isEqualTo(reportId(target));
        assertThat(result.status())
                .isEqualTo(ContentReportStatus.RESOLVED);
        assertThat(result.handledByMemberId())
                .isEqualTo(fixture.adminId());
        assertThat(result.handledAt()).isEqualTo(HANDLED_AT);
        assertThat(result.handlingReason()).isEqualTo(HANDLING_REASON);

        // 서비스 트랜잭션 종료 후 새로운 트랜잭션에서 다시 읽는다.
        transactions.executeWithoutResult(status -> {
            ContentReport report = findReport(reportId(target));

            assertResolved(
                    report,
                    ContentReportHandlingOutcome.CONTENT_HIDDEN
            );
            assertOriginalReportFields(report, target);

            assertContentState(
                    target == Target.POST ? HANDLED_AT : null,
                    target == Target.COMMENT ? HANDLED_AT : null
            );

            List<ContentModerationLog> logs = findLogs();

            assertThat(logs).hasSize(1);

            assertLog(
                    logs.getFirst(),
                    target,
                    HANDLING_REASON,
                    HANDLED_AT
            );

            assertOtherReportsReceived(reportId(target));
        });

        assertThat(snapshot().members())
                .isEqualTo(before.members());
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 이미_숨긴_대상은_기존_숨김_시각과_운영_기록을_유지한다(
            Target target
    ) {
        prepareAlreadyHidden(target);

        DatabaseSnapshot before = snapshot();

        execute(target);

        transactions.executeWithoutResult(status -> {
            ContentReport report = findReport(reportId(target));

            assertResolved(
                    report,
                    ContentReportHandlingOutcome.CONTENT_ALREADY_HIDDEN
            );
            assertOriginalReportFields(report, target);

            assertContentState(
                    target == Target.POST ? PREVIOUS_TIME : null,
                    target == Target.COMMENT ? PREVIOUS_TIME : null
            );

            List<ContentModerationLog> logs = findLogs();

            assertThat(logs).hasSize(1);

            assertLog(
                    logs.getFirst(),
                    target,
                    PREVIOUS_REASON,
                    PREVIOUS_TIME
            );

            assertOtherReportsReceived(reportId(target));
        });

        DatabaseSnapshot after = snapshot();

        // 이미 숨긴 콘텐츠와 기존 운영 기록은 UPDATE하지 않는다.
        assertThat(after.posts()).isEqualTo(before.posts());
        assertThat(after.comments()).isEqualTo(before.comments());
        assertThat(after.logs()).isEqualTo(before.logs());
        assertThat(after.members()).isEqualTo(before.members());
    }

    @Test
    void 부모_게시글이_숨겨져_있어도_댓글을_개별_숨김_처리한다() {
        transactions.executeWithoutResult(status -> {
            Post post = entityManager.find(
                    Post.class,
                    fixture.postId()
            );

            post.hide(PREVIOUS_TIME);

            entityManager.persist(
                    ContentModerationLog.forPost(
                            fixture.adminId(),
                            fixture.postId(),
                            ModerationAction.HIDE,
                            ModerationReasonType.OTHER,
                            PREVIOUS_REASON,
                            PREVIOUS_TIME
                    )
            );
        });

        DatabaseSnapshot before = snapshot();

        execute(Target.COMMENT);

        transactions.executeWithoutResult(status -> {
            assertResolved(
                    findReport(fixture.commentReportId()),
                    ContentReportHandlingOutcome.CONTENT_HIDDEN
            );

            assertContentState(
                    PREVIOUS_TIME,
                    HANDLED_AT
            );

            List<ContentModerationLog> logs = findLogs();

            assertThat(logs).hasSize(2);

            ContentModerationLog postLog = logs.stream()
                    .filter(log -> log.getPostId() != null)
                    .findFirst()
                    .orElseThrow();

            ContentModerationLog commentLog = logs.stream()
                    .filter(log -> log.getCommentId() != null)
                    .findFirst()
                    .orElseThrow();

            assertLog(
                    postLog,
                    Target.POST,
                    PREVIOUS_REASON,
                    PREVIOUS_TIME
            );

            assertLog(
                    commentLog,
                    Target.COMMENT,
                    HANDLING_REASON,
                    HANDLED_AT
            );

            assertOtherReportsReceived(
                    fixture.commentReportId()
            );
        });

        // 부모 게시글은 기존 숨김 상태 그대로 유지한다.
        assertThat(snapshot().posts())
                .isEqualTo(before.posts());

        assertThat(snapshot().members())
                .isEqualTo(before.members());
    }

    @ParameterizedTest
    @CsvSource({
            "POST, POST, POST_DELETED",
            "COMMENT, COMMENT, COMMENT_DELETED",
            "COMMENT, POST, POST_DELETED"
    })
    void 삭제된_대상이나_부모_게시글은_처리를_거부한다(
            Target reportTarget,
            Target deletedTarget,
            ContentModerationException.Code expectedCode
    ) {
        transactions.executeWithoutResult(status -> {
            if (deletedTarget == Target.POST) {
                entityManager.find(
                        Post.class,
                        fixture.postId()
                ).delete(PREVIOUS_TIME);
            } else {
                entityManager.find(
                        Comment.class,
                        fixture.commentId()
                ).delete(PREVIOUS_TIME);
            }
        });

        DatabaseSnapshot before = snapshot();

        assertThatThrownBy(() -> execute(reportTarget))
                .isInstanceOfSatisfying(
                        ContentModerationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
                );

        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, RESOLVED",
            "POST, DISMISSED",
            "COMMENT, RESOLVED",
            "COMMENT, DISMISSED"
    })
    void 종결된_신고는_콘텐츠_숨김이나_기록_추가_없이_거부한다(
            Target target,
            ContentReportStatus existingStatus
    ) {
        transactions.executeWithoutResult(status -> {
            ContentReport report = findReport(reportId(target));

            if (existingStatus == ContentReportStatus.RESOLVED) {
                report.resolve(
                        fixture.adminId(),
                        PREVIOUS_REASON,
                        PREVIOUS_TIME
                );
            } else {
                report.dismiss(
                        fixture.adminId(),
                        PREVIOUS_REASON,
                        PREVIOUS_TIME
                );
            }
        });

        DatabaseSnapshot before = snapshot();

        assertThatThrownBy(() -> execute(target))
                .isInstanceOfSatisfying(
                        AdminReportHandlingException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        AdminReportHandlingException.Code
                                                .REPORT_ALREADY_HANDLED
                                )
                );

        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 잘못된_처리_사유는_DB를_변경하지_않는다(
            Target target
    ) {
        DatabaseSnapshot before = snapshot();

        assertThatThrownBy(() ->
                service.hideAndResolve(
                        fixture.adminId(),
                        fixture.adminVersion(),
                        reportId(target),
                        " \t\n "
                )
        ).isInstanceOfSatisfying(
                AdminReportHandlingException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminReportHandlingException.Code
                                        .INVALID_HANDLING_REASON
                        )
        );

        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void flush_후_외부_트랜잭션이_실패하면_모든_변경을_롤백한다(
            Target target
    ) {
        DatabaseSnapshot before = snapshot();

        IllegalStateException failure =
                new IllegalStateException("후속 작업 실패");

        assertThatThrownBy(() ->
                transactions.executeWithoutResult(status -> {
                    execute(target);

                    // 콘텐츠 UPDATE, 로그 INSERT, 신고 UPDATE를
                    // 실제 DB에 반영한 뒤 후속 실패를 발생시킨다.
                    entityManager.flush();

                    // 같은 트랜잭션의 JDBC 조회에서도 변경이 보여야 한다.
                    assertFlushedChanges(target);

                    throw failure;
                })
        ).isSameAs(failure);

        // 롤백 후 별도 DB 조회에서는 모든 값이 이전과 같아야 한다.
        assertThat(snapshot()).isEqualTo(before);

        transactions.executeWithoutResult(status -> {
            assertReceived(findReport(reportId(target)));
            assertContentState(null, null);
            assertThat(findLogs()).isEmpty();
        });
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 신고_UPDATE가_DB에서_실패하면_숨김과_운영_기록도_롤백한다(
            Target target
    ) {
        DatabaseSnapshot before = snapshot();

        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "");

        String functionName = "test_report_fail_fn_" + suffix;
        String triggerName = "test_report_fail_tr_" + suffix;

        /*
         * Testcontainers DB에만 임시 트리거를 만든다.
         *
         * 지정한 신고의 RESOLVED UPDATE가 실행되면
         * 실제 PostgreSQL 오류를 발생시킨다.
         *
         * Repository Mock으로 예외를 대신 만들지 않고,
         * 서비스 트랜잭션의 저장 실패와 롤백을 검증한다.
         */
        try {
            installReportUpdateFailure(
                    functionName,
                    triggerName,
                    reportId(target)
            );

            assertThatThrownBy(() -> execute(target))
                    .isInstanceOf(RuntimeException.class)
                    .hasStackTraceContaining(FAILURE_MARKER);

            /*
             * 커밋 단계 예외는 JPA/Spring에서 감싸질 수 있으므로
             * 외부 예외 타입 하나에 고정하지 않는다.
             *
             * 대신 고유한 오류 문구를 검사하여
             * 의도한 DB 트리거 실패인지 확인한다.
             */
            assertThat(snapshot()).isEqualTo(before);

            transactions.executeWithoutResult(status -> {
                assertReceived(findReport(reportId(target)));
                assertContentState(null, null);
                assertThat(findLogs()).isEmpty();
            });
        } finally {
            try {
                jdbcTemplate.execute(
                        "DROP TRIGGER IF EXISTS "
                                + triggerName
                                + " ON content_reports"
                );
            } finally {
                jdbcTemplate.execute(
                        "DROP FUNCTION IF EXISTS "
                                + functionName
                                + "()"
                );
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 오래된_인증_버전의_요청은_DB를_변경하지_않는다(
            boolean postTarget
    ) {
        transactions.executeWithoutResult(status ->
                jdbcTemplate.update(
                        """
                        UPDATE members
                        SET credential_version = credential_version + 1
                        WHERE id = ?
                        """,
                        fixture.adminId()
                )
        );

        DatabaseSnapshot before = snapshot();

        Target target = postTarget
                ? Target.POST
                : Target.COMMENT;

        assertThatThrownBy(() -> execute(target))
                .isInstanceOfSatisfying(
                        AdminReportHandlingException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        AdminReportHandlingException.Code
                                                .AUTHENTICATION_REQUIRED
                                )
                );

        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, HIDE_AND_RESOLVE, HIDE_AND_RESOLVE",
            "POST, HIDE_AND_RESOLVE, RESOLVE",
            "POST, HIDE_AND_RESOLVE, DISMISS",
            "POST, RESOLVE, HIDE_AND_RESOLVE",
            "POST, DISMISS, HIDE_AND_RESOLVE",
            "COMMENT, HIDE_AND_RESOLVE, HIDE_AND_RESOLVE",
            "COMMENT, HIDE_AND_RESOLVE, RESOLVE",
            "COMMENT, HIDE_AND_RESOLVE, DISMISS",
            "COMMENT, RESOLVE, HIDE_AND_RESOLVE",
            "COMMENT, DISMISS, HIDE_AND_RESOLVE"
    })
    void 같은_신고의_동시_처리는_먼저_처리한_요청만_성공한다(
            Target target,
            Operation firstOperation,
            Operation secondOperation
    ) throws Exception {
        RaceResult race = runConcurrentRequests(
                target,
                firstOperation,
                secondOperation,
                true
        );

        assertThat(race.second().result()).isNull();

        assertThat(race.second().errorCode())
                .isEqualTo(
                        AdminReportHandlingException.Code
                                .REPORT_ALREADY_HANDLED
                );

        ContentReportStatus expectedStatus =
                firstOperation == Operation.DISMISS
                        ? ContentReportStatus.DISMISSED
                        : ContentReportStatus.RESOLVED;

        ContentReportHandlingOutcome expectedOutcome =
                firstOperation == Operation.HIDE_AND_RESOLVE
                        ? ContentReportHandlingOutcome.CONTENT_HIDDEN
                        : ContentReportHandlingOutcome.NO_CONTENT_ACTION;

        assertThat(race.first().result().status())
                .isEqualTo(expectedStatus);

        transactions.executeWithoutResult(status -> {
            ContentReport report = findReport(reportId(target));

            assertThat(report.getStatus())
                    .isEqualTo(expectedStatus);

            assertThat(report.getHandlingOutcome())
                    .isEqualTo(expectedOutcome);

            assertThat(report.getHandledByMemberId())
                    .isEqualTo(fixture.adminId());

            assertThat(report.getHandledAt())
                    .isEqualTo(HANDLED_AT);

            assertThat(report.getHandlingReason())
                    .isEqualTo("첫 번째 관리자 처리");

            assertOriginalReportFields(report, target);
            assertOtherReportsReceived(reportId(target));

            if (firstOperation == Operation.HIDE_AND_RESOLVE) {
                assertContentState(
                        target == Target.POST ? HANDLED_AT : null,
                        target == Target.COMMENT ? HANDLED_AT : null
                );

                List<ContentModerationLog> logs = findLogs();

                assertThat(logs).hasSize(1);

                assertLog(
                        logs.getFirst(),
                        target,
                        "첫 번째 관리자 처리",
                        HANDLED_AT
                );
            } else {
                // 먼저 완료·기각된 신고를 나중 요청이 숨기면 안 된다.
                assertContentState(null, null);
                assertThat(findLogs()).isEmpty();
            }
        });

        /*
         * 첫 번째 요청이 flush한 직후의 DB 값과 최종 DB 값을 비교한다.
         *
         * 두 번째 요청은 잠금 해제 후 이미 종결된 신고를 확인하고
         * 실패해야 하므로 어떤 변경도 추가하면 안 된다.
         */
        assertThat(snapshot())
                .isEqualTo(race.first().snapshotAfterFlush());
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 같은_콘텐츠의_서로_다른_신고는_숨김_한_번으로_각각_처리한다(
            Target target
    ) throws Exception {
        RaceResult race = runConcurrentRequests(
                target,
                Operation.HIDE_AND_RESOLVE,
                Operation.HIDE_AND_RESOLVE,
                false
        );

        assertThat(race.second().errorCode()).isNull();
        assertThat(race.second().result()).isNotNull();

        assertThat(race.first().result().reportId())
                .isEqualTo(reportId(target));

        assertThat(race.second().result().reportId())
                .isEqualTo(anotherReportId(target));

        assertThat(race.first().result().status())
                .isEqualTo(ContentReportStatus.RESOLVED);

        assertThat(race.second().result().status())
                .isEqualTo(ContentReportStatus.RESOLVED);

        transactions.executeWithoutResult(status -> {
            ContentReport firstReport = findReport(
                    reportId(target)
            );

            ContentReport secondReport = findReport(
                    anotherReportId(target)
            );

            assertThat(firstReport.getStatus())
                    .isEqualTo(ContentReportStatus.RESOLVED);

            assertThat(firstReport.getHandlingOutcome())
                    .isEqualTo(
                            ContentReportHandlingOutcome.CONTENT_HIDDEN
                    );

            assertThat(firstReport.getHandledByMemberId())
                    .isEqualTo(fixture.adminId());

            assertThat(firstReport.getHandlingReason())
                    .isEqualTo("첫 번째 관리자 처리");

            assertThat(firstReport.getHandledAt())
                    .isEqualTo(HANDLED_AT);

            assertThat(secondReport.getStatus())
                    .isEqualTo(ContentReportStatus.RESOLVED);

            assertThat(secondReport.getHandlingOutcome())
                    .isEqualTo(
                            ContentReportHandlingOutcome
                                    .CONTENT_ALREADY_HIDDEN
                    );

            assertThat(secondReport.getHandledByMemberId())
                    .isEqualTo(race.secondAdminId());

            assertThat(secondReport.getHandlingReason())
                    .isEqualTo("두 번째 관리자 처리");

            assertThat(secondReport.getHandledAt())
                    .isEqualTo(HANDLED_AT);

            assertThat(secondReport.getReporterMemberId())
                    .isEqualTo(fixture.anotherReporterId());

            assertThat(secondReport.getReasonType())
                    .isEqualTo(ContentReportReasonType.OTHER);

            assertThat(secondReport.getReasonDetail())
                    .isEqualTo(REPORT_REASON);

            assertThat(secondReport.getCreatedAt())
                    .isEqualTo(CREATED_AT);

            assertOriginalReportFields(firstReport, target);

            if (target == Target.POST) {
                assertThat(secondReport.getPostId())
                        .isEqualTo(fixture.postId());

                assertThat(secondReport.getCommentId()).isNull();

                assertThat(secondReport.getTitleSnapshot())
                        .isEqualTo(POST_TITLE);

                assertThat(secondReport.getContentSnapshot())
                        .isEqualTo(POST_CONTENT);
            } else {
                assertThat(secondReport.getPostId()).isNull();

                assertThat(secondReport.getCommentId())
                        .isEqualTo(fixture.commentId());

                assertThat(secondReport.getTitleSnapshot()).isNull();

                assertThat(secondReport.getContentSnapshot())
                        .isEqualTo(COMMENT_CONTENT);
            }

            assertContentState(
                    target == Target.POST ? HANDLED_AT : null,
                    target == Target.COMMENT ? HANDLED_AT : null
            );

            // 실제 숨김을 실행한 첫 번째 관리자의 기록만 남는다.
            List<ContentModerationLog> logs = findLogs();

            assertThat(logs).hasSize(1);

            assertLog(
                    logs.getFirst(),
                    target,
                    "첫 번째 관리자 처리",
                    HANDLED_AT
            );

            // 다른 콘텐츠의 신고는 자동으로 처리하지 않는다.
            Target otherTarget = target == Target.POST
                    ? Target.COMMENT
                    : Target.POST;

            assertReceived(
                    findReport(reportId(otherTarget))
            );

            assertReceived(
                    findReport(anotherReportId(otherTarget))
            );
        });

        DatabaseSnapshot after = snapshot();
        DatabaseSnapshot firstFlushed =
                race.first().snapshotAfterFlush();

        /*
         * 두 번째 요청은 자신의 신고 처리 정보만 변경한다.
         * 콘텐츠와 운영 기록을 다시 변경하면 안 된다.
         */
        assertThat(after.posts())
                .isEqualTo(firstFlushed.posts());

        assertThat(after.comments())
                .isEqualTo(firstFlushed.comments());

        assertThat(after.logs())
                .isEqualTo(firstFlushed.logs());

        assertThat(after.members())
                .isEqualTo(firstFlushed.members());
    }

    private Member createMember(
            String email,
            String nickname,
            MemberRole role
    ) {
        Member member = Member.createPending(
                email,
                "test-only-password-hash",
                nickname
        );

        member.activeAfterEmailVerification();

        ReflectionTestUtils.setField(
                member,
                "role",
                role
        );

        entityManager.persist(member);

        return member;
    }

    private ContentReport createPostReport(
            Long reporterId,
            Post post
    ) {
        ContentReport report = ContentReport.forPost(
                reporterId,
                post.getId(),
                ContentReportReasonType.OTHER,
                REPORT_REASON,
                post.getTitle(),
                post.getContent(),
                CREATED_AT
        );

        entityManager.persist(report);

        return report;
    }

    private ContentReport createCommentReport(
            Long reporterId,
            Comment comment
    ) {
        ContentReport report = ContentReport.forComment(
                reporterId,
                comment.getId(),
                ContentReportReasonType.OTHER,
                REPORT_REASON,
                comment.getContent(),
                CREATED_AT
        );

        entityManager.persist(report);

        return report;
    }

    private AdminReportHandlingResult execute(
            Target target
    ) {
        return service.hideAndResolve(
                fixture.adminId(),
                fixture.adminVersion(),
                reportId(target),
                HANDLING_REASON
        );
    }

    private Long reportId(Target target) {
        return target == Target.POST
                ? fixture.postReportId()
                : fixture.commentReportId();
    }

    private ContentReport findReport(Long id) {
        return Objects.requireNonNull(
                entityManager.find(
                        ContentReport.class,
                        id
                )
        );
    }

    private List<ContentModerationLog> findLogs() {
        return entityManager.createQuery(
                        """
                        SELECT log
                        FROM ContentModerationLog log
                        WHERE log.postId = :postId
                           OR log.commentId = :commentId
                        ORDER BY log.id
                        """,
                        ContentModerationLog.class
                )
                .setParameter(
                        "postId",
                        fixture.postId()
                )
                .setParameter(
                        "commentId",
                        fixture.commentId()
                )
                .getResultList();
    }

    private void prepareAlreadyHidden(
            Target target
    ) {
        transactions.executeWithoutResult(status -> {
            ContentModerationLog log;

            if (target == Target.POST) {
                entityManager.find(
                        Post.class,
                        fixture.postId()
                ).hide(PREVIOUS_TIME);

                log = ContentModerationLog.forPost(
                        fixture.adminId(),
                        fixture.postId(),
                        ModerationAction.HIDE,
                        ModerationReasonType.OTHER,
                        PREVIOUS_REASON,
                        PREVIOUS_TIME
                );
            } else {
                entityManager.find(
                        Comment.class,
                        fixture.commentId()
                ).hide(PREVIOUS_TIME);

                log = ContentModerationLog.forComment(
                        fixture.adminId(),
                        fixture.commentId(),
                        ModerationAction.HIDE,
                        ModerationReasonType.OTHER,
                        PREVIOUS_REASON,
                        PREVIOUS_TIME
                );
            }

            entityManager.persist(log);
        });
    }

    private void assertResolved(
            ContentReport report,
            ContentReportHandlingOutcome outcome
    ) {
        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RESOLVED);

        assertThat(report.getHandlingOutcome())
                .isEqualTo(outcome);

        assertThat(report.getHandledByMemberId())
                .isEqualTo(fixture.adminId());

        assertThat(report.getHandledAt())
                .isEqualTo(HANDLED_AT);

        assertThat(report.getHandlingReason())
                .isEqualTo(HANDLING_REASON);
    }

    private void assertReceived(
            ContentReport report
    ) {
        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);

        assertThat(report.getHandledByMemberId()).isNull();
        assertThat(report.getHandledAt()).isNull();
        assertThat(report.getHandlingReason()).isNull();
        assertThat(report.getHandlingOutcome()).isNull();
    }

    private void assertOtherReportsReceived(
            Long handledReportId
    ) {
        List<Long> reportIds = List.of(
                fixture.postReportId(),
                fixture.commentReportId(),
                fixture.anotherPostReportId(),
                fixture.anotherCommentReportId()
        );

        for (Long id : reportIds) {
            if (!id.equals(handledReportId)) {
                assertReceived(findReport(id));
            }
        }
    }

    private void assertOriginalReportFields(
            ContentReport report,
            Target target
    ) {
        assertThat(report.getReporterMemberId())
                .isEqualTo(fixture.reporterId());

        assertThat(report.getReasonType())
                .isEqualTo(ContentReportReasonType.OTHER);

        assertThat(report.getReasonDetail())
                .isEqualTo(REPORT_REASON);

        assertThat(report.getCreatedAt())
                .isEqualTo(CREATED_AT);

        if (target == Target.POST) {
            assertThat(report.getPostId())
                    .isEqualTo(fixture.postId());

            assertThat(report.getCommentId()).isNull();

            assertThat(report.getTitleSnapshot())
                    .isEqualTo(POST_TITLE);

            assertThat(report.getContentSnapshot())
                    .isEqualTo(POST_CONTENT);
        } else {
            assertThat(report.getPostId()).isNull();

            assertThat(report.getCommentId())
                    .isEqualTo(fixture.commentId());

            assertThat(report.getTitleSnapshot()).isNull();

            assertThat(report.getContentSnapshot())
                    .isEqualTo(COMMENT_CONTENT);
        }
    }

    private void assertContentState(
            Instant expectedPostHiddenAt,
            Instant expectedCommentHiddenAt
    ) {
        Post post = entityManager.find(
                Post.class,
                fixture.postId()
        );

        Comment comment = entityManager.find(
                Comment.class,
                fixture.commentId()
        );

        assertThat(post.getHiddenAt())
                .isEqualTo(expectedPostHiddenAt);

        assertThat(comment.getHiddenAt())
                .isEqualTo(expectedCommentHiddenAt);

        assertThat(post.getDeletedAt()).isNull();
        assertThat(comment.getDeletedAt()).isNull();

        assertThat(post.getTitle()).isEqualTo(POST_TITLE);
        assertThat(post.getContent()).isEqualTo(POST_CONTENT);

        assertThat(comment.getContent())
                .isEqualTo(COMMENT_CONTENT);

        assertThat(comment.getContentUpdatedAt()).isNull();
    }

    private void assertLog(
            ContentModerationLog log,
            Target target,
            String reason,
            Instant createdAt
    ) {
        assertThat(log.getActorMemberId())
                .isEqualTo(fixture.adminId());

        assertThat(log.getAction())
                .isEqualTo(ModerationAction.HIDE);

        assertThat(log.getReasonType())
                .isEqualTo(ModerationReasonType.OTHER);

        assertThat(log.getReasonDetail()).isEqualTo(reason);
        assertThat(log.getReason()).isEqualTo(reason);
        assertThat(log.getCreatedAt()).isEqualTo(createdAt);

        if (target == Target.POST) {
            assertThat(log.getPostId())
                    .isEqualTo(fixture.postId());

            assertThat(log.getCommentId()).isNull();
        } else {
            assertThat(log.getPostId()).isNull();

            assertThat(log.getCommentId())
                    .isEqualTo(fixture.commentId());
        }
    }

    private void assertFlushedChanges(
            Target target
    ) {
        String reportStatus = jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM content_reports
                WHERE id = ?
                """,
                String.class,
                reportId(target)
        );

        String outcome = jdbcTemplate.queryForObject(
                """
                SELECT handling_outcome
                FROM content_reports
                WHERE id = ?
                """,
                String.class,
                reportId(target)
        );

        assertThat(reportStatus).isEqualTo("RESOLVED");
        assertThat(outcome).isEqualTo("CONTENT_HIDDEN");

        String hiddenSql = target == Target.POST
                ? """
                  SELECT hidden_at IS NOT NULL
                  FROM community_posts
                  WHERE id = ?
                  """
                : """
                  SELECT hidden_at IS NOT NULL
                  FROM community_comments
                  WHERE id = ?
                  """;

        Long targetId = target == Target.POST
                ? fixture.postId()
                : fixture.commentId();

        Boolean hidden = jdbcTemplate.queryForObject(
                hiddenSql,
                Boolean.class,
                targetId
        );

        assertThat(hidden).isTrue();

        Long logCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM admin_content_moderation_logs
                WHERE post_id = ? OR comment_id = ?
                """,
                Long.class,
                fixture.postId(),
                fixture.commentId()
        );

        assertThat(logCount).isEqualTo(1L);
    }

    private void installReportUpdateFailure(
            String functionName,
            String triggerName,
            Long reportId
    ) {
        /*
         * 이름은 테스트가 생성한 영숫자와 밑줄로만 구성되며,
         * reportId 역시 DB가 생성한 Long 값이다.
         */
        jdbcTemplate.execute(
                """
                CREATE FUNCTION %s()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $test$
                BEGIN
                    RAISE EXCEPTION USING
                        ERRCODE = '23514',
                        MESSAGE = '%s';
                    RETURN NEW;
                END;
                $test$;
                """.formatted(
                        functionName,
                        FAILURE_MARKER
                )
        );

        jdbcTemplate.execute(
                """
                CREATE TRIGGER %s
                BEFORE UPDATE ON content_reports
                FOR EACH ROW
                WHEN (
                    NEW.id = %d
                    AND NEW.status = 'RESOLVED'
                )
                EXECUTE FUNCTION %s();
                """.formatted(
                        triggerName,
                        reportId,
                        functionName
                )
        );
    }

    private DatabaseSnapshot snapshot() {
        /*
         * JPA의 관리 중인 객체가 아닌 실제 DB 값을 비교한다.
         * SELECT *로 updated_at 등 예상하지 못한 변경도 확인한다.
         *
         * 자동 증가 시퀀스 값은 트랜잭션 롤백 대상이 아니므로
         * 비교 대상에 포함하지 않는다.
         */
        return new DatabaseSnapshot(
                jdbcTemplate.queryForList(
                        """
                        SELECT *
                        FROM members
                        WHERE id IN (?, ?, ?, ?)
                        ORDER BY id
                        """,
                        fixture.adminId(),
                        fixture.authorId(),
                        fixture.reporterId(),
                        fixture.anotherReporterId()
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT *
                        FROM community_posts
                        WHERE id = ?
                        ORDER BY id
                        """,
                        fixture.postId()
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT *
                        FROM community_comments
                        WHERE id = ?
                        ORDER BY id
                        """,
                        fixture.commentId()
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT *
                        FROM content_reports
                        WHERE post_id = ? OR comment_id = ?
                        ORDER BY id
                        """,
                        fixture.postId(),
                        fixture.commentId()
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT *
                        FROM admin_content_moderation_logs
                        WHERE post_id = ? OR comment_id = ?
                        ORDER BY id
                        """,
                        fixture.postId(),
                        fixture.commentId()
                )
        );
    }

    private RaceResult runConcurrentRequests(
            Target target,
            Operation firstOperation,
            Operation secondOperation,
            boolean sameReport
    ) throws Exception {
        TestAdmin secondAdmin = createConcurrencyAdmin();

        Long firstReportId = reportId(target);
        Long secondReportId = sameReport
                ? firstReportId
                : anotherReportId(target);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch allowFirstCommit =
                new CountDownLatch(1);

        CompletableFuture<Integer> firstPreparedPid =
                new CompletableFuture<>();

        CompletableFuture<Integer> secondStartedPid =
                new CompletableFuture<>();

        try {
            Future<FirstRequestResult> firstFuture =
                    executor.submit(() -> {
                        try {
                            return transactions.execute(status -> {
                                configureConcurrencyTimeouts();

                                int backendPid = currentBackendPid();

                                AdminReportHandlingResult result =
                                        executeOperation(
                                                firstOperation,
                                                fixture.adminId(),
                                                fixture.adminVersion(),
                                                firstReportId,
                                                "첫 번째 관리자 처리"
                                        );

                                entityManager.flush();

                                DatabaseSnapshot afterFlush = snapshot();

                                /*
                                 * 서비스 호출이 끝나도 바깥 트랜잭션은
                                 * 아직 커밋하지 않았으므로 잠금이 유지된다.
                                 */
                                firstPreparedPid.complete(backendPid);

                                awaitFirstCommitPermission(
                                        allowFirstCommit
                                );

                                return new FirstRequestResult(
                                        result,
                                        afterFlush
                                );
                            });
                        } catch (RuntimeException | Error exception) {
                            firstPreparedPid.completeExceptionally(
                                    exception
                            );
                            throw exception;
                        }
                    });

            int firstPid = firstPreparedPid.get(
                    15,
                    TimeUnit.SECONDS
            );

            Future<SecondRequestResult> secondFuture =
                    executor.submit(() -> {
                        try {
                            AdminReportHandlingResult result =
                                    transactions.execute(status -> {
                                        configureConcurrencyTimeouts();

                                        secondStartedPid.complete(
                                                currentBackendPid()
                                        );

                                        return executeOperation(
                                                secondOperation,
                                                secondAdmin.id(),
                                                secondAdmin.credentialVersion(),
                                                secondReportId,
                                                "두 번째 관리자 처리"
                                        );
                                    });

                            return new SecondRequestResult(
                                    Objects.requireNonNull(result),
                                    null
                            );
                        } catch (AdminReportHandlingException exception) {
                            secondStartedPid.completeExceptionally(
                                    exception
                            );

                            return new SecondRequestResult(
                                    null,
                                    exception.getCode()
                            );
                        } catch (RuntimeException | Error exception) {
                            secondStartedPid.completeExceptionally(
                                    exception
                            );
                            throw exception;
                        }
                    });

            int secondPid = secondStartedPid.get(
                    10,
                    TimeUnit.SECONDS
            );

            assertThat(secondPid).isNotEqualTo(firstPid);

            /*
             * 두 번째 요청이 단순히 실행 중인지 확인하는 것이 아니라
             * 첫 번째 요청의 DB 잠금 때문에 대기 중인지 확인한다.
             */
            awaitBlockedBy(
                    secondPid,
                    firstPid
            );

            assertThat(secondFuture.isDone())
                    .as("첫 번째 요청이 커밋하기 전에는 두 번째 요청이 끝나면 안 된다.")
                    .isFalse();

            allowFirstCommit.countDown();

            FirstRequestResult firstResult = firstFuture.get(
                    15,
                    TimeUnit.SECONDS
            );

            SecondRequestResult secondResult = secondFuture.get(
                    15,
                    TimeUnit.SECONDS
            );

            return new RaceResult(
                    Objects.requireNonNull(firstResult),
                    Objects.requireNonNull(secondResult),
                    secondAdmin.id()
            );
        } finally {
            /*
             * 중간 검증이 실패해도 첫 번째 요청이 잠금을
             * 계속 잡고 있지 않도록 반드시 대기를 해제한다.
             */
            allowFirstCommit.countDown();

            executor.shutdown();

            boolean terminated = executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
            );

            if (!terminated) {
                executor.shutdownNow();

                terminated = executor.awaitTermination(
                        10,
                        TimeUnit.SECONDS
                );
            }

            assertThat(terminated)
                    .as("DB 데이터를 정리하기 전에 요청 스레드가 종료되어야 한다.")
                    .isTrue();
        }
    }

    private AdminReportHandlingResult executeOperation(
            Operation operation,
            Long adminId,
            long credentialVersion,
            Long reportId,
            String reason
    ) {
        return switch (operation) {
            case HIDE_AND_RESOLVE -> service.hideAndResolve(
                    adminId,
                    credentialVersion,
                    reportId,
                    reason
            );

            case RESOLVE -> handlingService.resolve(
                    adminId,
                    credentialVersion,
                    reportId,
                    reason
            );

            case DISMISS -> handlingService.dismiss(
                    adminId,
                    credentialVersion,
                    reportId,
                    reason
            );
        };
    }

    private TestAdmin createConcurrencyAdmin() {
        TestAdmin admin = Objects.requireNonNull(
                transactions.execute(status -> {
                    String suffix = UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 12);

                    Member member = createMember(
                            "concurrent-admin-" + suffix + "@example.com",
                            "동시관리" + suffix,
                            MemberRole.ADMIN
                    );

                    entityManager.flush();

                    return new TestAdmin(
                            member.getId(),
                            member.getCredentialVersion()
                    );
                })
        );

        concurrencyAdminId = admin.id();

        return admin;
    }

    private Long anotherReportId(Target target) {
        return target == Target.POST
                ? fixture.anotherPostReportId()
                : fixture.anotherCommentReportId();
    }

    private int currentBackendPid() {
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject(
                        "SELECT pg_backend_pid()",
                        Integer.class
                )
        );
    }

    private void configureConcurrencyTimeouts() {
        jdbcTemplate.execute(
                "SET LOCAL lock_timeout = '30s'"
        );

        jdbcTemplate.execute(
                "SET LOCAL statement_timeout = '40s'"
        );
    }

    private void awaitBlockedBy(
            int waitingPid,
            int blockingPid
    ) throws InterruptedException {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {
            Boolean blocked = jdbcTemplate.queryForObject(
                    """
                    SELECT CAST(? AS integer)
                           = ANY(pg_blocking_pids(CAST(? AS integer)))
                    """,
                    Boolean.class,
                    blockingPid,
                    waitingPid
            );

            if (Boolean.TRUE.equals(blocked)) {
                return;
            }

            Thread.sleep(25);
        }

        throw new AssertionError(
                "두 번째 요청이 첫 번째 요청의 DB 잠금을 "
                        + "기다리는 상태를 확인하지 못했습니다."
        );
    }

    private void awaitFirstCommitPermission(
            CountDownLatch latch
    ) {
        try {
            if (!latch.await(35, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "첫 번째 요청의 커밋 허용 대기 시간 초과"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "첫 번째 요청의 커밋 대기 중 인터럽트",
                    exception
            );
        }
    }

    private record TestAdmin(
            Long id,
            long credentialVersion
    ) {
    }

    private record FirstRequestResult(
            AdminReportHandlingResult result,
            DatabaseSnapshot snapshotAfterFlush
    ) {
    }

    private record SecondRequestResult(
            AdminReportHandlingResult result,
            AdminReportHandlingException.Code errorCode
    ) {
    }

    private record RaceResult(
            FirstRequestResult first,
            SecondRequestResult second,
            Long secondAdminId
    ) {
    }

    private record DatabaseSnapshot(
            List<Map<String, Object>> members,
            List<Map<String, Object>> posts,
            List<Map<String, Object>> comments,
            List<Map<String, Object>> reports,
            List<Map<String, Object>> logs
    ) {
    }

    private record Fixture(
            Long adminId,
            long adminVersion,
            Long authorId,
            Long reporterId,
            Long anotherReporterId,
            Long postId,
            Long commentId,
            Long postReportId,
            Long commentReportId,
            Long anotherPostReportId,
            Long anotherCommentReportId
    ) {
    }
}