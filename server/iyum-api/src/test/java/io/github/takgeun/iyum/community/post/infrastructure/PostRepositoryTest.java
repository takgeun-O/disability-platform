package io.github.takgeun.iyum.community.post.infrastructure;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class PostRepositoryTest {

    private static final Instant CREATED_TIME =
            Instant.parse("2026-10-13T03:00:00Z");

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
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    @BeforeEach
    void setUpTime() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_TIME));
    }

    @Test
    void 게시글을_작성자와_함께_저장하고_DB에서_다시_조회한다() {
        Member author = createAuthor();
        Long authorId = author.getId();
        String content = "  첫 번째 줄\n\n두 번째 줄  ";

        Post saved = postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        "  보청기 질문입니다  ",
                        content
                )
        );

        Long postId = saved.getId();

        entityManager.clear();

        Post found = postRepository.findById(postId).orElseThrow();

        assertThat(found.getId()).isEqualTo(postId);
        assertThat(found.getAuthor().getId()).isEqualTo(authorId);
        assertThat(found.getAuthor().getNickname()).isEqualTo("작성회원");
        assertThat(found.getType()).isEqualTo(PostType.QUESTION);
        assertThat(found.getTopic()).isEqualTo(PostTopic.HEARING_AID);
        assertThat(found.getTitle()).isEqualTo("보청기 질문입니다");
        assertThat(found.getContent()).isEqualTo(content);
        assertThat(found.getCreatedAt()).isEqualTo(CREATED_TIME);
        assertThat(found.getUpdatedAt()).isEqualTo(CREATED_TIME);
        assertThat(found.getDeletedAt()).isNull();
    }

    @Test
    void 이모지가_포함된_최대_길이의_제목과_본문을_저장한다() {
        Member author = createAuthor();
        String title = "😀".repeat(120);
        String content = "😀".repeat(10_000);

        Post saved = postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.REVIEW,
                        PostTopic.COCHLEAR_IMPLANT,
                        title,
                        content
                )
        );

        Long postId = saved.getId();

        entityManager.clear();

        Post found = postRepository.findById(postId).orElseThrow();

        assertThat(found.getTitle()).isEqualTo(title);
        assertThat(found.getContent()).isEqualTo(content);
    }

    @Test
    void 존재하지_않는_작성자는_DB에서_거부한다() {
        assertThatThrownBy(() ->
                insertDirectly(
                        Long.MAX_VALUE,
                        "GENERAL",
                        "제목",
                        "본문"
                )
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_community_posts_author");
    }

    @Test
    void 정의되지_않은_글_유형은_DB에서_거부한다() {
        Member author = createAuthor();

        assertThatThrownBy(() ->
                insertDirectly(
                        author.getId(),
                        "UNKNOWN",
                        "제목",
                        "본문"
                )
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_community_posts_type");
    }

    @Test
    void 최대_길이를_초과한_본문은_DB에서도_거부한다() {
        Member author = createAuthor();

        assertThatThrownBy(() ->
                insertDirectly(
                        author.getId(),
                        "GENERAL",
                        "제목",
                        "가".repeat(10_001)
                )
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_community_posts_content_length");
    }

    @Test
    void 게시글이_참조하는_회원은_물리적으로_삭제할_수_없다() {
        // 목적 : PostgreSQL의 외래키가 회원 삭제를 차단하는가?
        Member author = createAuthor();

        postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        "제목",
                        "본문"
                )
        );

        Long authorId = author.getId();

        // 저장 SQL은 이미 실행됐다.
        // 관리 중인 엔티티를 분리하고 DB 제약조건 자체를 검사한다.
        entityManager.clear();

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "DELETE FROM members WHERE id = ?",
                        authorId
                )
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_community_posts_author");
    }

    private Member createAuthor() {
        Member author = Member.createPending(
                "writer@example.com",
                "test-only-password-hash",
                "작성회원"
        );

        author.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(author);
    }

    private void insertDirectly(
            Long authorId,
            String postType,
            String title,
            String content
    ) {
        jdbcTemplate.update("""
                INSERT INTO community_posts (
                    author_id,
                    post_type,
                    topic,
                    title,
                    content,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, 'HEARING_AID', ?, ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                authorId,
                postType,
                title,
                content
        );
    }
}