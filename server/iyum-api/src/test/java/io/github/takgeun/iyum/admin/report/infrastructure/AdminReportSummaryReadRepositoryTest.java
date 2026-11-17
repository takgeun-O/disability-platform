package io.github.takgeun.iyum.admin.report.infrastructure;

import io.github.takgeun.iyum.admin.report.application.AdminReportSummaryCountsResult;
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
class AdminReportSummaryReadRepositoryTest {

    // 한국 시간 2026-11-17 00:00
    private static final Instant START =
            Instant.parse("2026-11-16T15:00:00Z");

    // 한국 시간 2026-11-18 00:00
    private static final Instant END =
            Instant.parse("2026-11-17T15:00:00Z");

    private static final Instant NOW =
            Instant.parse("2026-11-17T03:00:00Z");

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
    AdminReportSummaryReadRepository repository;

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

        author = saveMember();
        reporter = saveMember();
        handler = saveMember();

        ReflectionTestUtils.setField(
                handler,
                "role",
                MemberRole.ADMIN
        );

        entityManager.flush();
    }

    @Test
    void 신고가_없으면_모든_집계는_0이다() {
        assertCounts(
                0L,
                0L,
                0L
        );
    }

    @Test
    void 처리_대기는_날짜와_무관하고_오늘_접수는_종결된_신고도_포함한다() {
        saveReport(savePost(), BEFORE);
        saveReport(savePost(), NOW);

        ContentReport resolved = saveReport(
                savePost(),
                NOW
        );
        resolved.resolve(
                handler.getId(),
                "처리 완료 테스트",
                NOW.plusSeconds(60)
        );

        ContentReport dismissed = saveReport(
                savePost(),
                NOW
        );
        dismissed.dismiss(
                handler.getId(),
                "기각 테스트",
                NOW.plusSeconds(60)
        );

        // 대기: 어제 접수 1건 + 오늘 접수 1건
        // 오늘 접수: 접수·처리 완료·기각 각 1건
        // 오늘 종결: 처리 완료·기각 각 1건
        assertCounts(
                2L,
                3L,
                2L
        );
    }

    @Test
    void 오늘_접수는_시작을_포함하고_다음날_시작은_제외한다() {
        // PostgreSQL의 마이크로초 정밀도에 맞춘다.
        List<Instant> createdTimes = List.of(
                START.minusNanos(1_000),
                START,
                END.minusNanos(1_000),
                END
        );

        for (Instant createdAt : createdTimes) {
            saveReport(
                    savePost(),
                    createdAt
            );
        }

        // 날짜와 무관한 처리 대기는 4건.
        // 오늘 접수는 START와 END 직전의 2건.
        assertCounts(
                4L,
                2L,
                0L
        );
    }

    @Test
    void 오늘_종결은_접수일이_아닌_처리일의_경계로_완료와_기각을_센다() {
        List<Instant> handledTimes = List.of(
                START.minusNanos(1_000),
                START,
                END.minusNanos(1_000),
                END
        );

        for (Instant handledAt : handledTimes) {
            // 모든 신고는 어제 접수한다.
            ContentReport resolved = saveReport(
                    savePost(),
                    BEFORE
            );
            resolved.resolve(
                    handler.getId(),
                    "처리 완료 테스트",
                    handledAt
            );

            ContentReport dismissed = saveReport(
                    savePost(),
                    BEFORE
            );
            dismissed.dismiss(
                    handler.getId(),
                    "기각 테스트",
                    handledAt
            );
        }

        // 오늘 접수는 0건.
        // START와 END 직전의 처리 완료·기각 각 2건만 포함.
        assertCounts(
                0L,
                0L,
                4L
        );
    }

    @Test
    void 오늘_접수하고_다음날_종결한_신고는_오늘_접수에만_포함한다() {
        ContentReport resolved = saveReport(
                savePost(),
                NOW
        );
        resolved.resolve(
                handler.getId(),
                "다음날 처리 완료",
                END
        );

        ContentReport dismissed = saveReport(
                savePost(),
                NOW
        );
        dismissed.dismiss(
                handler.getId(),
                "다음날 기각",
                END.plusSeconds(60)
        );

        // 현재 상태는 이미 종결이므로 처리 대기에도 포함하지 않는다.
        assertCounts(
                0L,
                2L,
                0L
        );
    }

    @Test
    void 동일_게시글의_여러_신고를_각각_집계한다() {
        Post post = savePost();

        saveReport(post, NOW);

        // 같은 신고자의 중복 신고 제약을 피한다.
        Member otherReporter = saveMember();

        ContentReport anotherReport = ContentReport.forPost(
                otherReporter.getId(),
                post.getId(),
                ContentReportReasonType.ABUSE_HATE,
                null,
                post.getTitle(),
                post.getContent(),
                NOW.plusSeconds(1)
        );

        entityManager.persist(anotherReport);

        assertCounts(
                2L,
                2L,
                0L
        );
    }

    @Test
    void 게시글과_댓글을_숨기거나_복원하거나_삭제해도_신고_집계는_유지된다() {
        Post post = savePost();

        Comment comment = Comment.create(
                post,
                author,
                "신고 요약 테스트 댓글"
        );
        entityManager.persist(comment);
        entityManager.flush();

        saveReport(post, NOW);

        ContentReport commentReport = ContentReport.forComment(
                reporter.getId(),
                comment.getId(),
                ContentReportReasonType.ABUSE_HATE,
                null,
                comment.getContent(),
                NOW
        );
        entityManager.persist(commentReport);

        Long postId = post.getId();
        Long commentId = comment.getId();

        assertCounts(
                2L,
                2L,
                0L
        );

        // assertCounts에서 clear하므로 관리 상태의 엔티티를 다시 조회한다.
        post = entityManager.find(Post.class, postId);
        comment = entityManager.find(Comment.class, commentId);

        post.hide(NOW.plusSeconds(60));
        comment.hide(NOW.plusSeconds(60));

        assertCounts(
                2L,
                2L,
                0L
        );

        post = entityManager.find(Post.class, postId);
        comment = entityManager.find(Comment.class, commentId);

        post.restore();
        comment.restore();

        assertCounts(
                2L,
                2L,
                0L
        );

        post = entityManager.find(Post.class, postId);
        comment = entityManager.find(Comment.class, commentId);

        post.delete(NOW.plusSeconds(120));
        comment.delete(NOW.plusSeconds(120));

        assertCounts(
                2L,
                2L,
                0L
        );
    }

    private Member saveMember() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(BEFORE));

        int sequence = ++memberSequence;

        Member member = Member.createPending(
                "report-summary-" + sequence + "@example.com",
                "test-only-password-hash",
                "요약회원" + sequence
        );
        member.activeAfterEmailVerification();

        entityManager.persist(member);
        entityManager.flush();

        return member;
    }

    private Post savePost() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(BEFORE));

        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "신고 요약 테스트 게시글",
                "신고 요약 집계를 검증하는 본문입니다."
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

    private void assertCounts(
            long pendingReportCount,
            long todayReportCount,
            long todayHandledReportCount
    ) {
        entityManager.flush();
        entityManager.clear();

        AdminReportSummaryCountsResult result =
                repository.findCounts(
                        START,
                        END
                );

        assertThat(result).isEqualTo(
                new AdminReportSummaryCountsResult(
                        pendingReportCount,
                        todayReportCount,
                        todayHandledReportCount
                )
        );
    }
}