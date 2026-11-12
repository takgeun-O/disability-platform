package io.github.takgeun.iyum.community.report.application;

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
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import java.util.UUID;
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
class ContentReportCreateServiceIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-11-12T10:00:00Z");

    private static final String POST_TITLE =
            "신고 대상 게시글";

    private static final String POST_CONTENT =
            " \n게시글 첫 문장\n\n두  번째 문장\t ";

    private static final String COMMENT_CONTENT =
            " \n댓글 첫 문장\n두  번째 문장\t ";

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
    ContentReportCreateService service;

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
    private Long handlingAdminId;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);

        transactions = new TransactionTemplate(transactionManager);

        // 준비 데이터를 커밋해야 다른 스레드의 트랜잭션에서도 보인다.
        fixture = transactions.execute(status -> {
            String suffix = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 12);

            Member reporter = activeMember(
                    "reporter-" + suffix + "@example.com",
                    "신고" + suffix
            );

            Member author = activeMember(
                    "author-" + suffix + "@example.com",
                    "작성" + suffix
            );

            entityManager.persist(reporter);
            entityManager.persist(author);

            Post post = Post.create(
                    author,
                    PostType.GENERAL,
                    PostTopic.DAILY,
                    POST_TITLE,
                    POST_CONTENT
            );
            entityManager.persist(post);

            Post otherPost = Post.create(
                    author,
                    PostType.GENERAL,
                    PostTopic.DAILY,
                    "다른 게시글",
                    "다른 게시글 본문"
            );
            entityManager.persist(otherPost);

            Comment comment = Comment.create(
                    post,
                    author,
                    COMMENT_CONTENT
            );
            entityManager.persist(comment);

            entityManager.flush();

            return new Fixture(
                    reporter.getId(),
                    reporter.getCredentialVersion(),
                    author.getId(),
                    post.getId(),
                    otherPost.getId(),
                    comment.getId()
            );
        });
    }

    @AfterEach
    void tearDown() {
        if (fixture == null) {
            return;
        }

        // 이 테스트에서 생성한 데이터만 참조 순서에 맞춰 정리한다.
        transactions.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    """
                    DELETE FROM content_reports
                    WHERE reporter_member_id = ?
                    """,
                    fixture.reporterId()
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
                    WHERE id IN (?, ?)
                    """,
                    fixture.postId(),
                    fixture.otherPostId()
            );

            jdbcTemplate.update(
                    """
                    DELETE FROM members
                    WHERE id IN (?, ?)
                    """,
                    fixture.reporterId(),
                    fixture.authorId()
            );
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 신고를_접수하고_커밋된_내용을_다시_조회한다(
            boolean postTarget
    ) {
        ContentReportCreateResult result = report(postTarget);

        ContentReport found =
                reportRepository.findById(result.reportId()).orElseThrow();

        assertThat(found.getReporterMemberId())
                .isEqualTo(fixture.reporterId());
        assertThat(found.getReasonType())
                .isEqualTo(ContentReportReasonType.OTHER);
        assertThat(found.getReasonDetail())
                .isEqualTo("검토가 필요합니다.");
        assertThat(found.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(found.getCreatedAt()).isEqualTo(NOW);

        if (postTarget) {
            assertThat(found.getPostId()).isEqualTo(fixture.postId());
            assertThat(found.getCommentId()).isNull();
            assertThat(found.getTitleSnapshot()).isEqualTo(POST_TITLE);
            assertThat(found.getContentSnapshot()).isEqualTo(POST_CONTENT);
        } else {
            assertThat(found.getPostId()).isNull();
            assertThat(found.getCommentId())
                    .isEqualTo(fixture.commentId());
            assertThat(found.getTitleSnapshot()).isNull();
            assertThat(found.getContentSnapshot())
                    .isEqualTo(COMMENT_CONTENT);
        }

        assertThat(result.status())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(reportCount()).isEqualTo(1L);

        transactions.executeWithoutResult(status -> {
            Member reporter = entityManager.find(
                    Member.class,
                    fixture.reporterId()
            );
            Post post = entityManager.find(
                    Post.class,
                    fixture.postId()
            );
            Comment comment = entityManager.find(
                    Comment.class,
                    fixture.commentId()
            );

            // 신고만으로 인증 버전이나 콘텐츠 공개 상태를 변경하지 않는다.
            assertThat(reporter.getCredentialVersion())
                    .isEqualTo(fixture.credentialVersion());

            assertThat(post.getHiddenAt()).isNull();
            assertThat(post.getDeletedAt()).isNull();
            assertThat(comment.getHiddenAt()).isNull();
            assertThat(comment.getDeletedAt()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"HIDDEN", "DELETED"})
    void 부모_게시글이_숨김이나_삭제_상태이면_두_종류_신고를_거부한다(
            String state
    ) {
        transactions.executeWithoutResult(status -> {
            Post post = entityManager.find(
                    Post.class,
                    fixture.postId()
            );

            if ("HIDDEN".equals(state)) {
                post.hide(NOW.minusSeconds(60));
            } else {
                post.delete(NOW.minusSeconds(60));
            }
        });

        assertFailure(
                ContentReportCreateException.Code.POST_NOT_FOUND,
                () -> report(true)
        );

        assertFailure(
                ContentReportCreateException.Code.POST_NOT_FOUND,
                () -> report(false)
        );

        assertThat(reportCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HIDDEN", "DELETED"})
    void 댓글이_숨김이나_삭제_상태이면_댓글_신고를_거부한다(
            String state
    ) {
        transactions.executeWithoutResult(status -> {
            Comment comment = entityManager.find(
                    Comment.class,
                    fixture.commentId()
            );

            if ("HIDDEN".equals(state)) {
                comment.hide(NOW.minusSeconds(60));
            } else {
                comment.delete(NOW.minusSeconds(60));
            }
        });

        assertFailure(
                ContentReportCreateException.Code.COMMENT_NOT_FOUND,
                () -> report(false)
        );

        assertThat(reportCount()).isZero();
    }

    @Test
    void 다른_게시글의_댓글을_지정하면_신고를_거부한다() {
        assertFailure(
                ContentReportCreateException.Code.COMMENT_NOT_FOUND,
                () -> service.reportComment(
                        fixture.reporterId(),
                        fixture.credentialVersion(),
                        fixture.otherPostId(),
                        fixture.commentId(),
                        ContentReportReasonType.OTHER,
                        "검토가 필요합니다."
                )
        );

        assertThat(reportCount()).isZero();
    }

    @Test
    void 존재하지_않는_게시글이나_댓글은_신고할_수_없다() {
        assertFailure(
                ContentReportCreateException.Code.POST_NOT_FOUND,
                () -> service.reportPost(
                        fixture.reporterId(),
                        fixture.credentialVersion(),
                        Long.MAX_VALUE,
                        ContentReportReasonType.OTHER,
                        "검토가 필요합니다."
                )
        );

        assertFailure(
                ContentReportCreateException.Code.COMMENT_NOT_FOUND,
                () -> service.reportComment(
                        fixture.reporterId(),
                        fixture.credentialVersion(),
                        fixture.postId(),
                        Long.MAX_VALUE,
                        ContentReportReasonType.OTHER,
                        "검토가 필요합니다."
                )
        );

        assertThat(reportCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 자기_콘텐츠는_실제_DB_작성자_정보를_기준으로_거부한다(
            boolean postTarget
    ) {
        // 테스트 데이터의 작성자를 신고자로 지정한다.
        // 운영 기능에 작성자 변경 API를 추가하는 것은 아니다.
        transactions.executeWithoutResult(status -> {
            if (postTarget) {
                jdbcTemplate.update(
                        """
                        UPDATE community_posts
                        SET author_id = ?
                        WHERE id = ?
                        """,
                        fixture.reporterId(),
                        fixture.postId()
                );
            } else {
                jdbcTemplate.update(
                        """
                        UPDATE community_comments
                        SET author_id = ?
                        WHERE id = ?
                        """,
                        fixture.reporterId(),
                        fixture.commentId()
                );
            }
        });

        assertFailure(
                ContentReportCreateException.Code.SELF_REPORT_NOT_ALLOWED,
                () -> report(postTarget)
        );

        assertThat(reportCount()).isZero();
    }

    @Test
    void 자기_게시글에_다른_회원이_쓴_댓글은_신고할_수_있다() {
        transactions.executeWithoutResult(status ->
                jdbcTemplate.update(
                        """
                        UPDATE community_posts
                        SET author_id = ?
                        WHERE id = ?
                        """,
                        fixture.reporterId(),
                        fixture.postId()
                )
        );

        ContentReportCreateResult result = report(false);

        ContentReport found =
                reportRepository.findById(result.reportId()).orElseThrow();

        assertThat(found.getCommentId()).isEqualTo(fixture.commentId());
        assertThat(found.getPostId()).isNull();
        assertThat(reportCount()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void DB의_인증_버전이_변경되면_기존_버전으로_신고할_수_없다(
            boolean postTarget
    ) {
        transactions.executeWithoutResult(status ->
                jdbcTemplate.update(
                        """
                        UPDATE members
                        SET credential_version = credential_version + 1
                        WHERE id = ?
                        """,
                        fixture.reporterId()
                )
        );

        assertFailure(
                ContentReportCreateException.Code.AUTHENTICATION_REQUIRED,
                () -> report(postTarget)
        );

        assertThat(reportCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 정지된_회원은_현재_인증_버전을_전달해도_신고할_수_없다(
            boolean postTarget
    ) {
        Long currentVersion = transactions.execute(status -> {
            Member reporter = entityManager.find(
                    Member.class,
                    fixture.reporterId()
            );

            reporter.suspend();

            return reporter.getCredentialVersion();
        });

        // 오래된 버전 때문이 아니라 현재 회원 상태 때문에
        // 거부되는지 확인하기 위해 변경 후 버전을 전달한다.
        assertFailure(
                ContentReportCreateException.Code.AUTHENTICATION_REQUIRED,
                () -> report(postTarget, currentVersion)
        );

        assertThat(reportCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(ContentReportStatus.class)
    void 기존_신고는_상태와_무관하게_재접수를_거부한다(
            ContentReportStatus reportStatus
    ) {
        ContentReportCreateResult first = report(true);

        prepareExistingReportStatus(first.reportId(), reportStatus);

        ContentReport beforeRetry = reportRepository
                .findById(first.reportId())
                .orElseThrow();

        assertThat(beforeRetry.getStatus()).isEqualTo(reportStatus);

        if (reportStatus == ContentReportStatus.RECEIVED) {
            assertThat(beforeRetry.getHandledByMemberId()).isNull();
            assertThat(beforeRetry.getHandledAt()).isNull();
            assertThat(beforeRetry.getHandlingReason()).isNull();
        } else {
            assertThat(beforeRetry.getHandledByMemberId())
                    .isEqualTo(handlingAdminId);
            assertThat(beforeRetry.getHandledAt())
                    .isEqualTo(NOW.plusSeconds(60));
            assertThat(beforeRetry.getHandlingReason())
                    .isEqualTo("기존 신고의 중복 접수 정책 검증");
        }

        // 재요청 전 값을 별도로 보관한다.
        Long originalHandlerId = beforeRetry.getHandledByMemberId();
        Instant originalHandledAt = beforeRetry.getHandledAt();
        String originalHandlingReason = beforeRetry.getHandlingReason();

        assertFailure(
                ContentReportCreateException.Code.REPORT_ALREADY_EXISTS,
                () -> service.reportPost(
                        fixture.reporterId(),
                        fixture.credentialVersion(),
                        fixture.postId(),
                        ContentReportReasonType.PERSONAL_INFORMATION,
                        "새로운 상세 설명"
                )
        );

        ContentReport found = reportRepository
                .findById(first.reportId())
                .orElseThrow();

        assertThat(reportCount()).isEqualTo(1L);
        assertThat(found.getId()).isEqualTo(first.reportId());
        assertThat(found.getReporterMemberId())
                .isEqualTo(fixture.reporterId());
        assertThat(found.getPostId()).isEqualTo(fixture.postId());
        assertThat(found.getCommentId()).isNull();

        assertThat(found.getStatus()).isEqualTo(reportStatus);
        assertThat(found.getReasonType())
                .isEqualTo(ContentReportReasonType.OTHER);
        assertThat(found.getReasonDetail())
                .isEqualTo("검토가 필요합니다.");
        assertThat(found.getTitleSnapshot()).isEqualTo(POST_TITLE);
        assertThat(found.getContentSnapshot()).isEqualTo(POST_CONTENT);
        assertThat(found.getCreatedAt()).isEqualTo(NOW);

        // 중복 요청이 기존 신고의 처리 정보도 변경하지 않아야 한다.
        assertThat(found.getHandledByMemberId())
                .isEqualTo(originalHandlerId);
        assertThat(found.getHandledAt())
                .isEqualTo(originalHandledAt);
        assertThat(found.getHandlingReason())
                .isEqualTo(originalHandlingReason);
    }

    @Test
    void 외부_트랜잭션이_실패하면_신고_저장도_롤백된다() {
        IllegalStateException failure =
                new IllegalStateException("후속 작업 실패");

        assertThatThrownBy(() ->
                transactions.executeWithoutResult(status -> {
                    report(true);

                    // 서비스는 외부 트랜잭션에 참여하므로
                    // 저장 이후 실패하면 신고도 함께 롤백되어야 한다.
                    throw failure;
                })
        ).isSameAs(failure);

        assertThat(reportCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 같은_회원의_동시_신고는_한_건만_접수된다(
            boolean postTarget
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Outcome> first = executor.submit(() -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("시작 신호 대기 시간 초과");
                }

                return concurrentReport(postTarget);
            });

            Future<Outcome> second = executor.submit(() -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("시작 신호 대기 시간 초과");
                }

                return concurrentReport(postTarget);
            });

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("두 요청이 시작 준비를 마쳐야 한다.")
                    .isTrue();

            start.countDown();

            List<Outcome> outcomes = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );

            assertThat(outcomes)
                    .filteredOn(outcome -> outcome.errorCode() == null)
                    .hasSize(1);

            assertThat(outcomes)
                    .filteredOn(outcome ->
                            outcome.errorCode()
                                    == ContentReportCreateException.Code
                                    .REPORT_ALREADY_EXISTS
                    )
                    .hasSize(1);

            Long successfulReportId = outcomes.stream()
                    .filter(outcome -> outcome.errorCode() == null)
                    .findFirst()
                    .orElseThrow()
                    .reportId();

            assertThat(successfulReportId).isNotNull();
            assertThat(reportCount()).isEqualTo(1L);

            ContentReport found = postTarget
                    ? reportRepository.findByReporterMemberIdAndPostId(
                    fixture.reporterId(),
                    fixture.postId()
            ).orElseThrow()
                    : reportRepository.findByReporterMemberIdAndCommentId(
                    fixture.reporterId(),
                    fixture.commentId()
            ).orElseThrow();

            assertThat(found.getId()).isEqualTo(successfulReportId);
            assertThat(found.getStatus())
                    .isEqualTo(ContentReportStatus.RECEIVED);

        } finally {
            // 준비 단계에서 테스트가 실패해도 대기 중인 작업을 해제한다.
            start.countDown();

            executor.shutdownNow();

            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS))
                    .as("DB 데이터 정리 전에 요청 스레드가 종료되어야 한다.")
                    .isTrue();
        }
    }

    private Outcome concurrentReport(boolean postTarget) {
        TransactionTemplate requestTransaction =
                new TransactionTemplate(transactionManager);

        try {
            ContentReportCreateResult result =
                    requestTransaction.execute(status -> {
                        // 테스트가 잠금 문제로 무한정 대기하지 않도록
                        // 이 트랜잭션에만 제한 시간을 적용한다.
                        jdbcTemplate.execute(
                                "SET LOCAL lock_timeout = '5s'"
                        );
                        jdbcTemplate.execute(
                                "SET LOCAL statement_timeout = '10s'"
                        );

                        return report(postTarget);
                    });

            if (result == null) {
                throw new IllegalStateException("신고 접수 결과가 없습니다.");
            }

            return new Outcome(result.reportId(), null);

        } catch (ContentReportCreateException exception) {
            return new Outcome(null, exception.getCode());
        }
    }

    private ContentReportCreateResult report(boolean postTarget) {
        return report(postTarget, fixture.credentialVersion());
    }

    private ContentReportCreateResult report(
            boolean postTarget,
            long credentialVersion
    ) {
        if (postTarget) {
            return service.reportPost(
                    fixture.reporterId(),
                    credentialVersion,
                    fixture.postId(),
                    ContentReportReasonType.OTHER,
                    "  검토가 필요합니다.  "
            );
        }

        return service.reportComment(
                fixture.reporterId(),
                credentialVersion,
                fixture.postId(),
                fixture.commentId(),
                ContentReportReasonType.OTHER,
                "  검토가 필요합니다.  "
        );
    }

    private long reportCount() {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM content_reports
                WHERE reporter_member_id = ?
                """,
                Long.class,
                fixture.reporterId()
        );

        if (count == null) {
            throw new IllegalStateException("신고 개수를 조회하지 못했습니다.");
        }

        return count;
    }

    private Member activeMember(String email, String nickname) {
        Member member = Member.createPending(
                email,
                "test-only-password-hash",
                nickname
        );

        member.activeAfterEmailVerification();

        return member;
    }

    private void assertFailure(
            ContentReportCreateException.Code expectedCode,
            ThrowingCallable action
    ) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        ContentReportCreateException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
                );
    }

    private void prepareExistingReportStatus(
            Long reportId,
            ContentReportStatus reportStatus
    ) {
        if (reportStatus == ContentReportStatus.RECEIVED) {
            return;
        }

        handlingAdminId = transactions.execute(transactionStatus -> {
            String suffix = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 12);

            Member admin = activeMember(
                    "report-handler-" + suffix + "@example.com",
                    "관리" + suffix
            );

            // 테스트 준비용 권한 설정이다.
            ReflectionTestUtils.setField(admin, "role", MemberRole.ADMIN);

            entityManager.persist(admin);
            entityManager.flush();

            ContentReport report = reportRepository
                    .findById(reportId)
                    .orElseThrow();

            String handlingReason = "기존 신고의 중복 접수 정책 검증";
            Instant handledAt = NOW.plusSeconds(60);

            switch (reportStatus) {
                case RESOLVED -> report.resolve(
                        admin.getId(),
                        handlingReason,
                        handledAt
                );
                case DISMISSED -> report.dismiss(
                        admin.getId(),
                        handlingReason,
                        handledAt
                );
                default -> throw new IllegalArgumentException(
                        "지원하지 않는 준비 상태: " + reportStatus
                );
            }

            entityManager.flush();

            return admin.getId();
        });
    }

    private record Fixture(
            Long reporterId,
            long credentialVersion,
            Long authorId,
            Long postId,
            Long otherPostId,
            Long commentId
    ) {
    }

    private record Outcome(
            Long reportId,
            ContentReportCreateException.Code errorCode
    ) {
    }
}