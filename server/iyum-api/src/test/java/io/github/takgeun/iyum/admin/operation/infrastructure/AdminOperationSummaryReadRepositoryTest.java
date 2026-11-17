package io.github.takgeun.iyum.admin.operation.infrastructure;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationSummaryCountsResult;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
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
class AdminOperationSummaryReadRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-11-04T03:00:00Z");

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
    AdminOperationSummaryReadRepository repository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Long actorId;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(NOW));

        Member actor = Member.createPending(
                "operation-log-read@example.com",
                "test-only-password-hash",
                "기록조회회원"
        );
        actor.activeAfterEmailVerification();

        entityManager.persist(actor);
        entityManager.flush();
        actorId = actor.getId();

        jdbcTemplate.update(
                "UPDATE members SET role = 'ADMIN' WHERE id = ?",
                actorId
        );

        // JDBC로 변경한 역할을 다시 읽는다.
        entityManager.clear();
        Member author = entityManager.find(Member.class, actorId);

        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "운영 기록 조회 대상",
                "게시글 내용"
        );
        entityManager.persist(post);

        Comment comment = Comment.create(
                post,
                author,
                "댓글 내용"
        );
        entityManager.persist(comment);
        entityManager.flush();

        postId = post.getId();
        commentId = comment.getId();
    }

    private static final Instant START = Instant.parse("2026-11-03T15:00:00Z");
    private static final Instant END = Instant.parse("2026-11-04T15:00:00Z");

    @Test
    void 기록이_없으면_모든_집계가_0이다() {
        assertCounts(0, 0, 0, 0);
    }

    @Test
    void 게시글_댓글_반복_조치와_LEGACY를_기록별로_집계한다() {
        savePostLog(ModerationAction.HIDE, NOW);
        savePostLog(ModerationAction.RESTORE, NOW.plusSeconds(1));
        savePostLog(ModerationAction.HIDE, NOW.plusSeconds(2));
        saveCommentLog(ModerationAction.RESTORE, NOW);
        entityManager.persist(ContentModerationLog.forComment(
                actorId, commentId, ModerationAction.HIDE,
                ModerationReasonType.SPAM_ADVERTISING, null, NOW
        ));
        assertCounts(5, 5, 3, 2);
    }

    @Test
    void 한국_날짜의_시작은_포함하고_다음날_시작은_제외한다() {
        savePostLog(ModerationAction.HIDE, START.minusNanos(1000));
        savePostLog(ModerationAction.HIDE, START);
        saveCommentLog(ModerationAction.RESTORE, END.minusNanos(1000));
        saveCommentLog(ModerationAction.HIDE, END);
        assertCounts(4, 2, 1, 1);
    }

    @Test
    void 대상의_복원_삭제와_작성자_탈퇴는_과거_기록을_바꾸지_않는다() {
        savePostLog(ModerationAction.HIDE, NOW);
        saveCommentLog(ModerationAction.RESTORE, NOW);
        entityManager.find(Post.class, postId).hide(NOW);
        entityManager.flush();
        assertCounts(2, 2, 1, 1);
        // 현재 상태만 달라져도 집계는 저장된 로그의 action과 createdAt만 사용한다.
        jdbcTemplate.update("UPDATE community_posts SET hidden_at = NULL, deleted_at = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW), postId);
        jdbcTemplate.update("UPDATE community_comments SET deleted_at = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW), commentId);
        entityManager.clear();
        entityManager.find(Member.class, actorId).withdraw(NOW);
        assertCounts(2, 2, 1, 1);
    }

    private void assertCounts(long total, long today, long hide, long restore) {
        flushAndClear();
        assertThat(repository.findCounts(START, END)).isEqualTo(
                new AdminOperationSummaryCountsResult(
                        total, today, hide, restore
                )
        );
    }

    private Long savePostLog(ModerationAction action, Instant at) {
        ContentModerationLog log = ContentModerationLog.forPost(
                actorId,
                postId,
                action,
                "운영 기록 조회 테스트",
                at
        );

        entityManager.persist(log);
        entityManager.flush();
        return log.getId();
    }

    private Long saveCommentLog(ModerationAction action, Instant at) {
        ContentModerationLog log = ContentModerationLog.forComment(
                actorId,
                commentId,
                action,
                "운영 기록 조회 테스트",
                at
        );

        entityManager.persist(log);
        entityManager.flush();
        return log.getId();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

}
