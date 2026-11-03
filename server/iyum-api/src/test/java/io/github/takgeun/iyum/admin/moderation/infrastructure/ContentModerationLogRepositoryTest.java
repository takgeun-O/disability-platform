package io.github.takgeun.iyum.admin.moderation.infrastructure;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class ContentModerationLogRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-10-29T12:00:00Z");

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
    ContentModerationLogRepository logRepository;

    @Autowired
    MemberRepository memberRepository;

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
                "moderator@example.com",
                "test-only-password-hash",
                "테스트회원"
        );
        actor.activeAfterEmailVerification();
        memberRepository.saveAndFlush(actor);
        actorId = actor.getId();

        jdbcTemplate.update(
                "UPDATE members SET role = 'ADMIN' WHERE id = ?",
                actorId
        );

        // JDBC 변경 전의 회원 객체가 1차 캐시에 남지 않도록 한다.
        entityManager.clear();

        Member author = entityManager.find(Member.class, actorId);

        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "게시글 제목",
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

    @Test
    void 게시글_숨김_이력을_저장하고_다시_조회한다() {
        ContentModerationLog saved = logRepository.saveAndFlush(
                ContentModerationLog.forPost(
                        actorId,
                        postId,
                        ModerationAction.HIDE,
                        "  운영 정책 위반  ",
                        NOW
                )
        );

        Long logId = saved.getId();
        entityManager.clear();

        ContentModerationLog found =
                logRepository.findById(logId).orElseThrow();

        assertThat(found.getActorMemberId()).isEqualTo(actorId);
        assertThat(found.getPostId()).isEqualTo(postId);
        assertThat(found.getCommentId()).isNull();
        assertThat(found.getAction()).isEqualTo(ModerationAction.HIDE);
        assertThat(found.getReason()).isEqualTo("운영 정책 위반");
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void 댓글_복원_이력을_저장하고_다시_조회한다() {
        ContentModerationLog saved = logRepository.saveAndFlush(
                ContentModerationLog.forComment(
                        actorId,
                        commentId,
                        ModerationAction.RESTORE,
                        "검토 결과 복원",
                        NOW
                )
        );

        Long logId = saved.getId();
        entityManager.clear();

        ContentModerationLog found =
                logRepository.findById(logId).orElseThrow();

        assertThat(found.getActorMemberId()).isEqualTo(actorId);
        assertThat(found.getPostId()).isNull();
        assertThat(found.getCommentId()).isEqualTo(commentId);
        assertThat(found.getAction()).isEqualTo(ModerationAction.RESTORE);
        assertThat(found.getReason()).isEqualTo("검토 결과 복원");
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "NO_TARGET",
            "BOTH_TARGETS",
            "UNKNOWN_ACTOR",
            "UNKNOWN_POST",
            "UNKNOWN_COMMENT",
            "INVALID_ACTION",
            "BLANK_REASON",
            "TOO_LONG_REASON"
    })
    void 잘못된_이력은_DB_제약조건으로_차단한다(String scenario) {
        Long actor = actorId;
        Long post = postId;
        Long comment = null;
        String action = "HIDE";
        String reason = "운영 정책 위반";

        switch (scenario) {
            case "NO_TARGET" -> post = null;
            case "BOTH_TARGETS" -> comment = commentId;
            case "UNKNOWN_ACTOR" -> actor = -1L;
            case "UNKNOWN_POST" -> post = -1L;
            case "UNKNOWN_COMMENT" -> {
                post = null;
                comment = -1L;
            }
            case "INVALID_ACTION" -> action = "DELETE";
            case "BLANK_REASON" -> reason = "   ";
            case "TOO_LONG_REASON" -> reason = "가".repeat(501);
            default -> throw new IllegalArgumentException(scenario);
        }

        Object[] parameters = {
                actor,
                post,
                comment,
                action,
                reason,
                Timestamp.from(NOW)
        };

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO admin_content_moderation_logs (
                    actor_member_id,
                    post_id,
                    comment_id,
                    action,
                    reason,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """, parameters))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}