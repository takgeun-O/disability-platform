package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.community.report.infrastructure.ContentReportRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
class AdminReportHandlingServiceIntegrationTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-12T10:00:00Z");

    private static final Instant HANDLED_AT =
            Instant.parse("2026-11-13T01:00:00Z");

    private static final String POST_TITLE = "신고 대상 게시글";
    private static final String POST_CONTENT = " \n게시글 원문\n두 번째 줄\t ";
    private static final String COMMENT_CONTENT = " \n댓글 원문\t ";
    private static final String REPORT_REASON = "접수 당시 신고 사유";

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
    AdminReportHandlingService service;

    @Autowired
    ContentReportRepository reportRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    Clock clock;

    private TransactionTemplate transactions;
    private Fixture fixture;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(HANDLED_AT);

        transactions = new TransactionTemplate(transactionManager);

        // 다른 스레드에서도 조회할 수 있도록 준비 데이터를 커밋한다.
        fixture = Objects.requireNonNull(
                transactions.execute(status -> {
                    String suffix = UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 12);

                    Member firstAdmin = createMember(
                            "admin1-" + suffix + "@example.com",
                            "관리1" + suffix,
                            MemberRole.ADMIN
                    );

                    Member secondAdmin = createMember(
                            "admin2-" + suffix + "@example.com",
                            "관리2" + suffix,
                            MemberRole.ADMIN
                    );

                    Member reporter = createMember(
                            "reporter-" + suffix + "@example.com",
                            "신고" + suffix,
                            MemberRole.USER
                    );

                    Member author = createMember(
                            "author-" + suffix + "@example.com",
                            "작성" + suffix,
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

                    ContentReport postReport = ContentReport.forPost(
                            reporter.getId(),
                            post.getId(),
                            ContentReportReasonType.OTHER,
                            REPORT_REASON,
                            POST_TITLE,
                            POST_CONTENT,
                            CREATED_AT
                    );

                    ContentReport commentReport = ContentReport.forComment(
                            reporter.getId(),
                            comment.getId(),
                            ContentReportReasonType.OTHER,
                            REPORT_REASON,
                            COMMENT_CONTENT,
                            CREATED_AT
                    );

                    entityManager.persist(postReport);
                    entityManager.persist(commentReport);
                    entityManager.flush();

                    return new Fixture(
                            firstAdmin.getId(),
                            firstAdmin.getCredentialVersion(),
                            secondAdmin.getId(),
                            secondAdmin.getCredentialVersion(),
                            reporter.getId(),
                            reporter.getCredentialVersion(),
                            author.getId(),
                            author.getCredentialVersion(),
                            post.getId(),
                            comment.getId(),
                            postReport.getId(),
                            commentReport.getId()
                    );
                })
        );
    }

    @AfterEach
    void tearDown() {
        if (fixture == null) {
            return;
        }

        // 처리 관리자 FK를 포함해 참조 순서대로 정리한다.
        transactions.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    """
                    DELETE FROM content_reports
                    WHERE id IN (?, ?)
                    """,
                    fixture.postReportId(),
                    fixture.commentReportId()
            );

            jdbcTemplate.update(
                    "DELETE FROM community_comments WHERE id = ?",
                    fixture.commentId()
            );

            jdbcTemplate.update(
                    "DELETE FROM community_posts WHERE id = ?",
                    fixture.postId()
            );

            jdbcTemplate.update(
                    """
                    DELETE FROM members
                    WHERE id IN (?, ?, ?, ?)
                    """,
                    fixture.firstAdminId(),
                    fixture.secondAdminId(),
                    fixture.reporterId(),
                    fixture.authorId()
            );
        });
    }

    @ParameterizedTest
    @CsvSource({
            "true,  true",
            "true,  false",
            "false, true",
            "false, false"
    })
    void 게시글과_댓글_신고의_처리_정보를_커밋한다(
            boolean postTarget,
            boolean resolve
    ) {
        Long reportId = reportId(postTarget);

        AdminReportHandlingResult result = handle(
                resolve,
                fixture.firstAdminId(),
                fixture.firstAdminVersion(),
                reportId,
                " \t 검토를 완료했습니다. \n"
        );

        ContentReport found = readReport(reportId);

        ContentReportStatus expectedStatus = resolve
                ? ContentReportStatus.RESOLVED
                : ContentReportStatus.DISMISSED;

        assertThat(found.getStatus()).isEqualTo(expectedStatus);
        assertThat(found.getHandledByMemberId())
                .isEqualTo(fixture.firstAdminId());
        assertThat(found.getHandledAt()).isEqualTo(HANDLED_AT);
        assertThat(found.getHandlingReason())
                .isEqualTo("검토를 완료했습니다.");

        assertThat(result.reportId()).isEqualTo(reportId);
        assertThat(result.status()).isEqualTo(found.getStatus());
        assertThat(result.handledByMemberId())
                .isEqualTo(found.getHandledByMemberId());
        assertThat(result.handledAt()).isEqualTo(found.getHandledAt());
        assertThat(result.handlingReason())
                .isEqualTo(found.getHandlingReason());

        assertOriginalReportFields(found, postTarget);

        // 다른 신고는 함께 처리되지 않는다.
        assertReceived(readReport(reportId(!postTarget)));

        assertContentAndMembersUnchanged();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 잘못된_사유는_DB의_신고를_변경하지_않는다(
            boolean resolve
    ) {
        assertFailure(
                AdminReportHandlingException.Code.INVALID_HANDLING_REASON,
                () -> handle(
                        resolve,
                        fixture.firstAdminId(),
                        fixture.firstAdminVersion(),
                        fixture.postReportId(),
                        " \t\n"
                )
        );

        ContentReport found = readReport(fixture.postReportId());

        assertReceived(found);
        assertOriginalReportFields(found, true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void DB의_인증_버전과_다르면_처리하지_않는다(
            boolean resolve
    ) {
        transactions.executeWithoutResult(status ->
                jdbcTemplate.update(
                        """
                        UPDATE members
                        SET credential_version = credential_version + 1
                        WHERE id = ?
                        """,
                        fixture.firstAdminId()
                )
        );

        assertFailure(
                AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED,
                () -> handle(
                        resolve,
                        fixture.firstAdminId(),
                        fixture.firstAdminVersion(),
                        fixture.postReportId(),
                        "검토 완료"
                )
        );

        assertReceived(readReport(fixture.postReportId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 실제_DB의_일반_회원은_처리할_수_없다(
            boolean resolve
    ) {
        assertFailure(
                AdminReportHandlingException.Code.ADMIN_REQUIRED,
                () -> handle(
                        resolve,
                        fixture.reporterId(),
                        fixture.reporterVersion(),
                        fixture.postReportId(),
                        "검토 완료"
                )
        );

        assertReceived(readReport(fixture.postReportId()));
    }

    @ParameterizedTest
    @CsvSource({
            "true,  true",
            "true,  false",
            "false, true",
            "false, false"
    })
    void 종결된_신고는_다른_관리자도_덮어쓸_수_없다(
            boolean firstResolve,
            boolean secondResolve
    ) {
        AdminReportHandlingResult first = handle(
                firstResolve,
                fixture.firstAdminId(),
                fixture.firstAdminVersion(),
                fixture.postReportId(),
                "최초 처리 사유"
        );

        assertFailure(
                AdminReportHandlingException.Code.REPORT_ALREADY_HANDLED,
                () -> handle(
                        secondResolve,
                        fixture.secondAdminId(),
                        fixture.secondAdminVersion(),
                        fixture.postReportId(),
                        "덮어쓰려는 처리 사유"
                )
        );

        ContentReport found = readReport(fixture.postReportId());

        assertThat(found.getStatus()).isEqualTo(first.status());
        assertThat(found.getHandledByMemberId())
                .isEqualTo(first.handledByMemberId());
        assertThat(found.getHandledAt()).isEqualTo(first.handledAt());
        assertThat(found.getHandlingReason())
                .isEqualTo(first.handlingReason());

        assertOriginalReportFields(found, true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 외부_트랜잭션이_실패하면_처리_정보도_롤백된다(
            boolean resolve
    ) {
        IllegalStateException failure =
                new IllegalStateException("후속 작업 실패");

        assertThatThrownBy(() ->
                transactions.executeWithoutResult(status -> {
                    handle(
                            resolve,
                            fixture.firstAdminId(),
                            fixture.firstAdminVersion(),
                            fixture.postReportId(),
                            "롤백될 처리 사유"
                    );

                    // UPDATE가 실행된 후에도 롤백되는지 확인한다.
                    entityManager.flush();

                    throw failure;
                })
        ).isSameAs(failure);

        ContentReport found = readReport(fixture.postReportId());

        assertReceived(found);
        assertOriginalReportFields(found, true);
        assertContentAndMembersUnchanged();
    }

    @ParameterizedTest
    @CsvSource({
            "true,  true",
            "true,  false",
            "false, true",
            "false, false"
    })
    void 서로_다른_관리자의_동시_처리는_한_건만_성공한다(
            boolean firstResolve,
            boolean secondResolve
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch firstHandled = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);

        CompletableFuture<Integer> secondBackendPid =
                new CompletableFuture<>();

        try {
            Future<AdminReportHandlingResult> first =
                    executor.submit(() ->
                            transactions.execute(status -> {
                                configureTransactionTimeouts();

                                AdminReportHandlingResult result = handle(
                                        firstResolve,
                                        fixture.firstAdminId(),
                                        fixture.firstAdminVersion(),
                                        fixture.postReportId(),
                                        "첫 번째 관리자 처리"
                                );

                                entityManager.flush();
                                firstHandled.countDown();

                                // 신고 행의 잠금을 유지한 상태로 기다린다.
                                awaitLatch(
                                        allowFirstCommit,
                                        "첫 번째 요청 커밋 허용"
                                );

                                return result;
                            })
                    );

            assertThat(firstHandled.await(15, TimeUnit.SECONDS))
                    .as("첫 번째 요청이 신고를 처리하고 잠금을 유지해야 한다.")
                    .isTrue();

            Future<AdminReportHandlingException.Code> second =
                    executor.submit(() -> {
                        try {
                            transactions.executeWithoutResult(status -> {
                                configureTransactionTimeouts();

                                Integer pid = jdbcTemplate.queryForObject(
                                        "SELECT pg_backend_pid()",
                                        Integer.class
                                );

                                secondBackendPid.complete(
                                        Objects.requireNonNull(pid)
                                );

                                handle(
                                        secondResolve,
                                        fixture.secondAdminId(),
                                        fixture.secondAdminVersion(),
                                        fixture.postReportId(),
                                        "두 번째 관리자 처리"
                                );
                            });

                            return null;
                        } catch (AdminReportHandlingException exception) {
                            return exception.getCode();
                        }
                    });

            int pid = secondBackendPid.get(10, TimeUnit.SECONDS);

            // 단순 동시 시작이 아니라 실제 DB 잠금 대기를 확인한다.
            awaitDatabaseLockWait(pid);

            allowFirstCommit.countDown();

            AdminReportHandlingResult winner =
                    first.get(15, TimeUnit.SECONDS);

            assertThat(second.get(15, TimeUnit.SECONDS))
                    .isEqualTo(
                            AdminReportHandlingException.Code
                                    .REPORT_ALREADY_HANDLED
                    );

            assertThat(winner).isNotNull();

            ContentReport found = readReport(fixture.postReportId());

            assertThat(found.getStatus()).isEqualTo(winner.status());
            assertThat(found.getHandledByMemberId())
                    .isEqualTo(fixture.firstAdminId());
            assertThat(found.getHandledAt()).isEqualTo(HANDLED_AT);
            assertThat(found.getHandlingReason())
                    .isEqualTo("첫 번째 관리자 처리");

            assertOriginalReportFields(found, true);
            assertReceived(readReport(fixture.commentReportId()));
            assertContentAndMembersUnchanged();

        } finally {
            // 중간에 검증이 실패해도 첫 번째 요청의 대기를 해제한다.
            allowFirstCommit.countDown();

            executor.shutdownNow();

            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS))
                    .as("DB 데이터를 정리하기 전에 요청 스레드가 종료되어야 한다.")
                    .isTrue();
        }
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
        ReflectionTestUtils.setField(member, "role", role);

        entityManager.persist(member);

        return member;
    }

    private Long reportId(boolean postTarget) {
        return postTarget
                ? fixture.postReportId()
                : fixture.commentReportId();
    }

    private ContentReport readReport(Long reportId) {
        return reportRepository.findById(reportId).orElseThrow();
    }

    private AdminReportHandlingResult handle(
            boolean resolve,
            Long actorId,
            long credentialVersion,
            Long reportId,
            String reason
    ) {
        if (resolve) {
            return service.resolve(
                    actorId,
                    credentialVersion,
                    reportId,
                    reason
            );
        }

        return service.dismiss(
                actorId,
                credentialVersion,
                reportId,
                reason
        );
    }

    private void assertReceived(ContentReport report) {
        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(report.getHandledByMemberId()).isNull();
        assertThat(report.getHandledAt()).isNull();
        assertThat(report.getHandlingReason()).isNull();
    }

    private void assertOriginalReportFields(
            ContentReport report,
            boolean postTarget
    ) {
        assertThat(report.getReporterMemberId())
                .isEqualTo(fixture.reporterId());
        assertThat(report.getReasonType())
                .isEqualTo(ContentReportReasonType.OTHER);
        assertThat(report.getReasonDetail()).isEqualTo(REPORT_REASON);
        assertThat(report.getCreatedAt()).isEqualTo(CREATED_AT);

        if (postTarget) {
            assertThat(report.getPostId()).isEqualTo(fixture.postId());
            assertThat(report.getCommentId()).isNull();
            assertThat(report.getTitleSnapshot()).isEqualTo(POST_TITLE);
            assertThat(report.getContentSnapshot()).isEqualTo(POST_CONTENT);
        } else {
            assertThat(report.getPostId()).isNull();
            assertThat(report.getCommentId())
                    .isEqualTo(fixture.commentId());
            assertThat(report.getTitleSnapshot()).isNull();
            assertThat(report.getContentSnapshot())
                    .isEqualTo(COMMENT_CONTENT);
        }
    }

    private void assertContentAndMembersUnchanged() {
        transactions.executeWithoutResult(status -> {
            Post post = entityManager.find(
                    Post.class,
                    fixture.postId()
            );

            Comment comment = entityManager.find(
                    Comment.class,
                    fixture.commentId()
            );

            assertThat(post.getHiddenAt()).isNull();
            assertThat(post.getDeletedAt()).isNull();
            assertThat(comment.getHiddenAt()).isNull();
            assertThat(comment.getDeletedAt()).isNull();

            assertActiveMember(
                    fixture.firstAdminId(),
                    fixture.firstAdminVersion()
            );
            assertActiveMember(
                    fixture.secondAdminId(),
                    fixture.secondAdminVersion()
            );
            assertActiveMember(
                    fixture.reporterId(),
                    fixture.reporterVersion()
            );
            assertActiveMember(
                    fixture.authorId(),
                    fixture.authorVersion()
            );
        });
    }

    private void assertActiveMember(
            Long memberId,
            long expectedVersion
    ) {
        Member member = entityManager.find(Member.class, memberId);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getCredentialVersion())
                .isEqualTo(expectedVersion);
    }

    private void configureTransactionTimeouts() {
        jdbcTemplate.execute("SET LOCAL lock_timeout = '20s'");
        jdbcTemplate.execute("SET LOCAL statement_timeout = '25s'");
    }

    private void awaitDatabaseLockWait(int backendPid)
            throws InterruptedException {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbcTemplate.queryForObject(
                    """
                    SELECT cardinality(pg_blocking_pids(?)) > 0
                    """,
                    Boolean.class,
                    backendPid
            );

            if (Boolean.TRUE.equals(waiting)) {
                return;
            }

            Thread.sleep(25);
        }

        throw new AssertionError(
                "두 번째 요청의 실제 DB 잠금 대기를 확인하지 못했습니다."
        );
    }

    private void awaitLatch(
            CountDownLatch latch,
            String description
    ) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        description + " 대기 시간 초과"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    description + " 대기 중 인터럽트",
                    exception
            );
        }
    }

    private void assertFailure(
            AdminReportHandlingException.Code expectedCode,
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action
    ) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        AdminReportHandlingException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
                );
    }

    private record Fixture(
            Long firstAdminId,
            long firstAdminVersion,
            Long secondAdminId,
            long secondAdminVersion,
            Long reporterId,
            long reporterVersion,
            Long authorId,
            long authorVersion,
            Long postId,
            Long commentId,
            Long postReportId,
            Long commentReportId
    ) {
    }
}