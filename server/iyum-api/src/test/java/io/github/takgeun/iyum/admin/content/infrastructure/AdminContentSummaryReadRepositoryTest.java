package io.github.takgeun.iyum.admin.content.infrastructure;

import io.github.takgeun.iyum.admin.content.application.AdminContentSummaryCountsResult;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
class AdminContentSummaryReadRepositoryTest {

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
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );
        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
    }

    @Autowired
    AdminContentSummaryReadRepository repository;

    @Autowired
    EntityManager entityManager;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Member author;

    @BeforeEach
    void setUp() {
        setAuditingTime(BEFORE);

        author = Member.createPending(
                "content-summary@example.com",
                "test-only-password-hash",
                "콘텐츠요약회원"
        );
        author.activeAfterEmailVerification();

        entityManager.persist(author);
        entityManager.flush();
    }

    @Test
    void 콘텐츠가_없으면_모든_집계는_0이다() {
        assertCounts(
                0L,
                0L,
                0L,
                0L
        );
    }

    @Test
    void 오늘_작성은_시작을_포함하고_다음날_시작은_제외한다() {
        // 댓글 생성에 사용할 부모 게시글은 오늘 집계에서 제외한다.
        Post parent = savePost(BEFORE);

        // 기존 테스트와 동일하게 마이크로초 단위로 경계를 준비한다.
        List<Instant> createdTimes = List.of(
                START.minusNanos(1_000),
                START,
                END.minusNanos(1_000),
                END
        );

        for (Instant createdAt : createdTimes) {
            savePost(createdAt);

            saveComment(
                    parent,
                    createdAt
            );
        }

        // 게시글과 댓글 수를 다르게 만들어 필드가 뒤바뀌어도 검출한다.
        saveComment(
                parent,
                NOW
        );

        assertCounts(
                2L,
                3L,
                0L,
                0L
        );
    }

    @Test
    void 오늘_작성은_정상_숨김_삭제_숨김후삭제를_모두_포함한다() {
        Post parent = savePost(BEFORE);

        savePost(NOW);
        saveComment(parent, NOW);

        Post hiddenPost = savePost(NOW);
        Comment hiddenComment = saveComment(parent, NOW);

        hiddenPost.hide(NOW.plusSeconds(60));
        hiddenComment.hide(NOW.plusSeconds(60));

        Post deletedPost = savePost(NOW);
        Comment deletedComment = saveComment(parent, NOW);

        deletedPost.delete(NOW.plusSeconds(60));
        deletedComment.delete(NOW.plusSeconds(60));

        Post hiddenAndDeletedPost = savePost(NOW);
        Comment hiddenAndDeletedComment = saveComment(parent, NOW);

        hiddenAndDeletedPost.hide(NOW.plusSeconds(60));
        hiddenAndDeletedComment.hide(NOW.plusSeconds(60));

        hiddenAndDeletedPost.delete(NOW.plusSeconds(120));
        hiddenAndDeletedComment.delete(NOW.plusSeconds(120));

        // 오늘 작성은 상태별 4건을 모두 포함한다.
        // 현재 숨김은 삭제되지 않은 숨김 대상 1건만 포함한다.
        assertCounts(
                4L,
                4L,
                1L,
                1L
        );
    }

    @Test
    void 현재_숨김은_작성일과_숨김일이_오늘이_아니어도_포함한다() {
        Post firstPost = savePost(BEFORE);
        Post secondPost = savePost(BEFORE);

        Comment firstComment = saveComment(firstPost, BEFORE);
        Comment secondComment = saveComment(firstPost, BEFORE);
        Comment thirdComment = saveComment(secondPost, BEFORE);

        Instant hiddenAt = BEFORE.plusSeconds(60);

        firstPost.hide(hiddenAt);
        secondPost.hide(hiddenAt);

        firstComment.hide(hiddenAt);
        secondComment.hide(hiddenAt);
        thirdComment.hide(hiddenAt);

        assertCounts(
                0L,
                0L,
                2L,
                3L
        );
    }

    @Test
    void 숨김과_복원은_현재_숨김_수만_변경한다() {
        Post post = savePost(NOW);
        Comment comment = saveComment(post, NOW);

        Long postId = post.getId();
        Long commentId = comment.getId();

        assertCounts(
                1L,
                1L,
                0L,
                0L
        );

        // assertCounts에서 clear하므로 엔티티를 다시 조회한다.
        post = entityManager.find(Post.class, postId);
        comment = entityManager.find(Comment.class, commentId);

        post.hide(NOW.plusSeconds(60));
        comment.hide(NOW.plusSeconds(60));

        assertCounts(
                1L,
                1L,
                1L,
                1L
        );

        post = entityManager.find(Post.class, postId);
        comment = entityManager.find(Comment.class, commentId);

        post.restore();
        comment.restore();

        assertCounts(
                1L,
                1L,
                0L,
                0L
        );
    }

    @Test
    void 숨김_후_삭제하면_현재_숨김에서_제외하고_오늘_작성은_유지한다() {
        Post post = savePost(NOW);
        Comment comment = saveComment(post, NOW);

        post.hide(NOW.plusSeconds(60));
        comment.hide(NOW.plusSeconds(60));

        Long postId = post.getId();
        Long commentId = comment.getId();

        assertCounts(
                1L,
                1L,
                1L,
                1L
        );

        post = entityManager.find(Post.class, postId);
        comment = entityManager.find(Comment.class, commentId);

        post.delete(NOW.plusSeconds(120));
        comment.delete(NOW.plusSeconds(120));

        // hiddenAt이 남아 있어도 deletedAt이 있으면 숨김 집계에서 제외한다.
        assertCounts(
                1L,
                1L,
                0L,
                0L
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 부모_게시글의_숨김과_삭제는_댓글_집계에_영향을_주지_않는다(
            boolean deleteParent
    ) {
        Post parent = savePost(BEFORE);

        saveComment(parent, NOW);

        Comment hiddenComment = saveComment(parent, NOW);
        hiddenComment.hide(NOW.plusSeconds(60));

        Long parentId = parent.getId();

        assertCounts(
                0L,
                2L,
                0L,
                1L
        );

        parent = entityManager.find(Post.class, parentId);
        parent.hide(NOW.plusSeconds(120));

        if (deleteParent) {
            parent.delete(NOW.plusSeconds(180));
        }

        // 부모만 숨김: 숨김 게시글 1건.
        // 부모 숨김 후 삭제: 숨김 게시글 0건.
        // 두 경우 모두 댓글의 오늘 작성 2건·현재 숨김 1건은 유지한다.
        assertCounts(
                0L,
                2L,
                deleteParent ? 0L : 1L,
                1L
        );
    }

    private Post savePost(
            Instant createdAt
    ) {
        setAuditingTime(createdAt);

        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "콘텐츠 요약 테스트 게시글",
                "게시글 요약 집계를 검증하는 본문입니다."
        );

        entityManager.persist(post);
        entityManager.flush();

        return post;
    }

    private Comment saveComment(
            Post post,
            Instant createdAt
    ) {
        setAuditingTime(createdAt);

        Comment comment = Comment.create(
                post,
                author,
                "댓글 요약 집계를 검증하는 본문입니다."
        );

        entityManager.persist(comment);
        entityManager.flush();

        return comment;
    }

    private void setAuditingTime(
            Instant time
    ) {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(time));
    }

    private void assertCounts(
            long todayPostCount,
            long todayCommentCount,
            long hiddenPostCount,
            long hiddenCommentCount
    ) {
        entityManager.flush();
        entityManager.clear();

        AdminContentSummaryCountsResult result =
                repository.findCounts(
                        START,
                        END
                );

        assertThat(result).isEqualTo(
                new AdminContentSummaryCountsResult(
                        todayPostCount,
                        todayCommentCount,
                        hiddenPostCount,
                        hiddenCommentCount
                )
        );
    }
}