package io.github.takgeun.iyum.member.infrastructure;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class MemberPostVisibilityRepositoryTest {

    private static final Instant OLD_CREATED_AT =
            Instant.parse("2026-10-29T00:00:00Z");

    private static final Instant NEW_CREATED_AT =
            Instant.parse("2026-10-30T00:00:00Z");

    private static final Instant STATE_CHANGED_AT =
            Instant.parse("2026-10-30T01:00:00Z");

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
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    private Long authorId;
    private Long oldVisibleId;
    private Long newVisibleId;
    private Long hiddenId;
    private Long otherVisibleId;

    @BeforeEach
    void setUp() {
        Member author = createActiveMember();
        Member otherAuthor = createActiveMember();

        authorId = author.getId();

        oldVisibleId = createPost(
                author,
                OLD_CREATED_AT,
                false,
                false
        );

        newVisibleId = createPost(
                author,
                NEW_CREATED_AT,
                false,
                false
        );

        // 같은 작성 시각이지만 더 큰 ID를 갖도록 나중에 생성한다.
        hiddenId = createPost(
                author,
                NEW_CREATED_AT,
                true,
                false
        );

        createPost(
                author,
                NEW_CREATED_AT.plusSeconds(10),
                false,
                true
        );

        createPost(
                author,
                NEW_CREATED_AT.plusSeconds(20),
                true,
                true
        );

        otherVisibleId = createPost(
                otherAuthor,
                NEW_CREATED_AT.plusSeconds(30),
                false,
                false
        );

        createPost(
                otherAuthor,
                NEW_CREATED_AT.plusSeconds(40),
                true,
                false
        );

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 내_게시글은_숨김을_포함하고_삭제와_다른_회원의_글은_제외한다() {
        var posts = postRepository
                .findFirstNotDeletedPostsByAuthorId(
                        authorId,
                        PageRequest.of(0, 10)
                );

        assertThat(posts)
                .extracting(Post::getId)
                .containsExactly(
                        hiddenId,
                        newVisibleId,
                        oldVisibleId
                );

        // 조회 개수 제한도 적용된다.
        var limited = postRepository
                .findFirstNotDeletedPostsByAuthorId(
                        authorId,
                        PageRequest.of(0, 2)
                );

        assertThat(limited)
                .extracting(Post::getId)
                .containsExactly(hiddenId, newVisibleId);
    }

    @Test
    void 숨김_항목을_커서로_사용해도_같은_시각의_다음_항목을_조회한다() {
        var posts = postRepository
                .findNotDeletedPostsByAuthorIdAfter(
                        authorId,
                        NEW_CREATED_AT,
                        hiddenId,
                        PageRequest.of(0, 10)
                );

        assertThat(posts)
                .extracting(Post::getId)
                .containsExactly(newVisibleId, oldVisibleId);

        var olderPosts = postRepository
                .findNotDeletedPostsByAuthorIdAfter(
                        authorId,
                        NEW_CREATED_AT,
                        newVisibleId,
                        PageRequest.of(0, 10)
                );

        assertThat(olderPosts)
                .extracting(Post::getId)
                .containsExactly(oldVisibleId);
    }

    @Test
    void 내_게시글_개수에도_숨김_항목을_포함한다() {
        assertThat(
                postRepository.countNotDeletedPostsByAuthorId(
                        authorId
                )
        ).isEqualTo(3L);
    }

    @Test
    void 공개_피드에서는_숨김과_삭제_항목이_계속_제외된다() {
        var posts = postRepository.findFirstVisiblePosts(
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertThat(posts)
                .extracting(Post::getId)
                .containsExactly(
                        otherVisibleId,
                        newVisibleId,
                        oldVisibleId
                );

        assertThat(
                postRepository.findVisiblePostById(hiddenId)
        ).isEmpty();
    }

    private Member createActiveMember() {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");

        Member member = Member.createPending(
                "mypage-post-" + unique + "@example.com",
                "test-only-password-hash",
                "회원" + unique.substring(0, 12)
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private Long createPost(
            Member author,
            Instant createdAt,
            boolean hidden,
            boolean deleted
    ) {
        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "조회 검증 게시글",
                "조회 검증 본문"
        );

        if (hidden) {
            post.hide(STATE_CHANGED_AT);
        }

        if (deleted) {
            post.delete(STATE_CHANGED_AT);
        }

        Post saved = postRepository.saveAndFlush(post);

        // 작성 시각을 고정해 정렬과 동일 시각의 ID 비교를 검증한다.
        jdbcTemplate.update(
                """
                UPDATE community_posts
                SET created_at = ?
                WHERE id = ?
                """,
                Timestamp.from(createdAt),
                saved.getId()
        );

        return saved.getId();
    }
}