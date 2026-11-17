package io.github.takgeun.iyum.admin.dashboard.infrastructure;

import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardCountsResult;
import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardPendingReportResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetType;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class AdminDashboardReadRepositoryTest {

    // 한국 시간 2026-11-17 00:00
    private static final Instant START =
            Instant.parse("2026-11-16T15:00:00Z");

    // 한국 시간 2026-11-18 00:00
    private static final Instant END =
            Instant.parse("2026-11-17T15:00:00Z");

    private static final Instant NOW =
            Instant.parse("2026-11-17T03:00:00Z");

    // 기본 작성자·신고자·관리자는 오늘 집계에서 제외한다.
    private static final Instant BEFORE =
            START.minusSeconds(86_400);

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
    AdminDashboardReadRepository repository;

    @Autowired
    EntityManager entityManager;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Member author;
    private Member reporter;
    private Member handler;
    private int memberSequence;

    @BeforeEach
    void setUp() {
        memberSequence = 0;

        author = saveMember(BEFORE);
        author.activeAfterEmailVerification();

        reporter = saveMember(BEFORE);
        reporter.activeAfterEmailVerification();

        handler = saveMember(BEFORE);
        handler.activeAfterEmailVerification();

        ReflectionTestUtils.setField(
                handler,
                "role",
                MemberRole.ADMIN
        );

        entityManager.flush();
    }

    @Test
    void 집계할_데이터가_없으면_모든_개수는_0이다() {
        flushAndClear();

        AdminDashboardCountsResult result =
                repository.findCounts(START, END);

        assertThat(result).isEqualTo(
                new AdminDashboardCountsResult(
                        0L,
                        0L,
                        0L,
                        0L
                )
        );

        assertThat(repository.findPendingReports()).isEmpty();
    }

    @Test
    void 오늘_집계는_시작을_포함하고_다음날_시작은_제외한다() {
        // PostgreSQL의 마이크로초 정밀도에 맞춰 준비한다.
        List<Instant> times = List.of(
                START.minusNanos(1_000),
                START,
                END.minusNanos(1_000),
                END
        );

        for (Instant time : times) {
            saveMember(time);

            Post post = savePost(time);
            saveReport(post, time);
        }

        flushAndClear();

        AdminDashboardCountsResult result =
                repository.findCounts(START, END);

        // 처리 대기는 날짜와 무관하므로 네 신고를 모두 센다.
        // 오늘 생성 건수는 START와 END 직전의 두 건이다.
        assertThat(result).isEqualTo(
                new AdminDashboardCountsResult(
                        4L,
                        2L,
                        2L,
                        2L
                )
        );
    }

    @Test
    void 처리_대기는_접수만_세고_오늘_신고는_종결된_신고도_센다() {
        // 어제 접수된 미처리 신고도 처리 대기에 포함한다.
        saveReport(
                savePost(BEFORE),
                START.minusSeconds(60)
        );

        // 오늘 접수된 미처리 신고.
        saveReport(savePost(BEFORE), NOW);

        ContentReport resolved =
                saveReport(savePost(BEFORE), NOW);

        resolved.resolve(
                handler.getId(),
                "처리 완료 테스트",
                NOW.plusSeconds(60)
        );

        ContentReport dismissed =
                saveReport(savePost(BEFORE), NOW);

        dismissed.dismiss(
                handler.getId(),
                "기각 테스트",
                NOW.plusSeconds(60)
        );

        flushAndClear();

        AdminDashboardCountsResult result =
                repository.findCounts(START, END);

        assertThat(result).isEqualTo(
                new AdminDashboardCountsResult(
                        2L,
                        3L,
                        0L,
                        0L
                )
        );
    }

    @Test
    void 오늘_게시글은_숨김과_삭제_여부와_무관하게_센다() {
        savePost(NOW);

        Post hidden = savePost(NOW);
        hidden.hide(NOW.plusSeconds(60));

        Post deleted = savePost(NOW);
        deleted.delete(NOW.plusSeconds(60));

        Post hiddenAndDeleted = savePost(NOW);
        hiddenAndDeleted.hide(NOW.plusSeconds(60));
        hiddenAndDeleted.delete(NOW.plusSeconds(120));

        flushAndClear();

        AdminDashboardCountsResult result =
                repository.findCounts(START, END);

        assertThat(result).isEqualTo(
                new AdminDashboardCountsResult(
                        0L,
                        0L,
                        4L,
                        0L
                )
        );
    }

    @Test
    void 오늘_회원은_미인증_활성_정지_탈퇴를_모두_센다() {
        // 이메일 인증 대기 회원.
        saveMember(NOW);

        Member active = saveMember(NOW);
        active.activeAfterEmailVerification();

        Member suspended = saveMember(NOW);
        suspended.activeAfterEmailVerification();
        suspended.suspend();

        Member withdrawn = saveMember(NOW);
        withdrawn.activeAfterEmailVerification();
        withdrawn.withdraw(NOW.plusSeconds(60));

        flushAndClear();

        AdminDashboardCountsResult result =
                repository.findCounts(START, END);

        assertThat(result).isEqualTo(
                new AdminDashboardCountsResult(
                        0L,
                        0L,
                        0L,
                        4L
                )
        );
    }

    @Test
    void 처리_완료와_기각_신고만_있으면_처리_대기_목록은_비어_있다() {
        ContentReport resolved =
                saveReport(savePost(BEFORE), NOW);

        resolved.resolve(
                handler.getId(),
                "처리 완료 테스트",
                NOW.plusSeconds(60)
        );

        ContentReport dismissed =
                saveReport(savePost(BEFORE), NOW);

        dismissed.dismiss(
                handler.getId(),
                "기각 테스트",
                NOW.plusSeconds(60)
        );

        flushAndClear();

        assertThat(repository.findPendingReports()).isEmpty();

        assertThat(
                repository.findCounts(START, END)
                        .pendingReportCount()
        ).isZero();
    }

    @Test
    void 접수_신고를_오래된_순과_ID순으로_최대_5건_조회한다() {
        // 종결된 신고가 가장 오래됐어도 목록에 포함되면 안 된다.
        ContentReport resolved = saveReport(
                savePost(BEFORE),
                START.minusSeconds(120)
        );
        resolved.resolve(
                handler.getId(),
                "처리 완료 테스트",
                NOW
        );

        ContentReport dismissed = saveReport(
                savePost(BEFORE),
                START.minusSeconds(110)
        );
        dismissed.dismiss(
                handler.getId(),
                "기각 테스트",
                NOW
        );

        // 저장 순서와 접수 시각 순서를 의도적으로 다르게 만든다.
        ContentReport fifth = saveReport(
                savePost(BEFORE),
                NOW.plusSeconds(120)
        );

        ContentReport sameTimeFirst =
                saveReport(savePost(BEFORE), NOW);

        ContentReport sameTimeSecond =
                saveReport(savePost(BEFORE), NOW);

        // 나중에 저장되어 ID가 더 커도 접수 시각이 가장 오래됐다.
        ContentReport oldest = saveReport(
                savePost(BEFORE),
                START.minusSeconds(60)
        );

        ContentReport fourth = saveReport(
                savePost(BEFORE),
                NOW.plusSeconds(60)
        );

        saveReport(
                savePost(BEFORE),
                NOW.plusSeconds(180)
        );

        flushAndClear();

        List<AdminDashboardPendingReportResult> result =
                repository.findPendingReports();

        assertThat(result)
                .extracting(AdminDashboardPendingReportResult::reportId)
                .containsExactly(
                        oldest.getId(),
                        sameTimeFirst.getId(),
                        sameTimeSecond.getId(),
                        fourth.getId(),
                        fifth.getId()
                );

        // 목록은 5건이지만 전체 처리 대기는 6건이다.
        assertThat(
                repository.findCounts(START, END)
                        .pendingReportCount()
        ).isEqualTo(6L);
    }

    @Test
    void 게시글과_댓글_신고의_대상과_접수_정보를_반환한다() {
        Post post = savePost(BEFORE);

        ContentReport postReport =
                saveReport(post, NOW);

        Comment comment = saveComment(post);

        Instant commentReportedAt = NOW.plusSeconds(1);

        ContentReport commentReport =
                saveCommentReport(comment, commentReportedAt);

        flushAndClear();

        List<AdminDashboardPendingReportResult> result =
                repository.findPendingReports();

        assertThat(result).containsExactly(
                new AdminDashboardPendingReportResult(
                        postReport.getId(),
                        AdminReportTargetType.POST,
                        post.getId(),
                        ContentReportReasonType.SPAM_ADVERTISING,
                        NOW
                ),
                new AdminDashboardPendingReportResult(
                        commentReport.getId(),
                        AdminReportTargetType.COMMENT,
                        comment.getId(),
                        ContentReportReasonType.ABUSE_HATE,
                        commentReportedAt
                )
        );
    }

    @Test
    void 같은_대상의_여러_신고를_합치지_않고_각각_센다() {
        Post post = savePost(BEFORE);

        ContentReport first = saveReport(post, NOW);

        // 동일 신고자의 중복 신고 제약을 피하도록 다른 회원을 사용한다.
        Member otherReporter = saveMember(BEFORE);
        otherReporter.activeAfterEmailVerification();

        ContentReport second = ContentReport.forPost(
                otherReporter.getId(),
                post.getId(),
                ContentReportReasonType.ABUSE_HATE,
                null,
                post.getTitle(),
                post.getContent(),
                NOW.plusSeconds(1)
        );

        entityManager.persist(second);

        flushAndClear();

        List<AdminDashboardPendingReportResult> result =
                repository.findPendingReports();

        assertThat(result)
                .extracting(AdminDashboardPendingReportResult::reportId)
                .containsExactly(
                        first.getId(),
                        second.getId()
                );

        assertThat(result)
                .extracting(AdminDashboardPendingReportResult::targetId)
                .containsExactly(
                        post.getId(),
                        post.getId()
                );

        AdminDashboardCountsResult counts =
                repository.findCounts(START, END);

        assertThat(counts.pendingReportCount()).isEqualTo(2L);
        assertThat(counts.todayReportCount()).isEqualTo(2L);
    }

    @Test
    void 대상이_숨김_후_삭제되어도_접수_신고는_집계와_목록에_남는다() {
        Post post = savePost(BEFORE);
        Comment comment = saveComment(post);

        ContentReport postReport =
                saveReport(post, NOW);

        ContentReport commentReport =
                saveCommentReport(comment, NOW.plusSeconds(1));

        post.hide(NOW.plusSeconds(60));
        comment.hide(NOW.plusSeconds(60));

        post.delete(NOW.plusSeconds(120));
        comment.delete(NOW.plusSeconds(120));

        flushAndClear();

        List<AdminDashboardPendingReportResult> result =
                repository.findPendingReports();

        assertThat(result)
                .extracting(AdminDashboardPendingReportResult::reportId)
                .containsExactly(
                        postReport.getId(),
                        commentReport.getId()
                );

        assertThat(result)
                .extracting(AdminDashboardPendingReportResult::targetType)
                .containsExactly(
                        AdminReportTargetType.POST,
                        AdminReportTargetType.COMMENT
                );

        AdminDashboardCountsResult counts =
                repository.findCounts(START, END);

        assertThat(counts.pendingReportCount()).isEqualTo(2L);
        assertThat(counts.todayReportCount()).isEqualTo(2L);
    }

    private Member saveMember(Instant createdAt) {
        setAuditingTime(createdAt);

        int sequence = ++memberSequence;

        Member member = Member.createPending(
                "dashboard-" + sequence + "@example.com",
                "test-only-password-hash",
                "대시보드회원" + sequence
        );

        entityManager.persist(member);
        entityManager.flush();

        return member;
    }

    private Post savePost(Instant createdAt) {
        setAuditingTime(createdAt);

        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "대시보드 테스트 게시글",
                "대시보드 집계 테스트 본문입니다."
        );

        entityManager.persist(post);
        entityManager.flush();

        return post;
    }

    private ContentReport saveReport(
            Post post,
            Instant createdAt
    ) {
        ContentReport report = ContentReport.forPost(
                reporter.getId(),
                post.getId(),
                ContentReportReasonType.SPAM_ADVERTISING,
                null,
                post.getTitle(),
                post.getContent(),
                createdAt
        );

        entityManager.persist(report);
        entityManager.flush();

        return report;
    }

    private void setAuditingTime(Instant time) {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(time));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Comment saveComment(Post post) {
        setAuditingTime(BEFORE);

        Comment comment = Comment.create(
                post,
                author,
                "대시보드 테스트 댓글입니다."
        );

        entityManager.persist(comment);
        entityManager.flush();

        return comment;
    }

    private ContentReport saveCommentReport(
            Comment comment,
            Instant createdAt
    ) {
        ContentReport report = ContentReport.forComment(
                reporter.getId(),
                comment.getId(),
                ContentReportReasonType.ABUSE_HATE,
                null,
                comment.getContent(),
                createdAt
        );

        entityManager.persist(report);
        entityManager.flush();

        return report;
    }
}