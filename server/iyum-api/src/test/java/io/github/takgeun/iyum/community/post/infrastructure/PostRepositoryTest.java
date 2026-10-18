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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Test
    void 첫_커서_조회는_생성시각과_ID_내림차순으로_개수를_제한한다() {
        Member author = createAuthor();

        Instant olderTime = CREATED_TIME;
        Instant newerTime = CREATED_TIME.plusSeconds(60);

        Post newerLowId = savePostAt(
                author, PostType.GENERAL, PostTopic.DAILY,
                "새 글 첫 번째", newerTime
        );

        Post newerHighId = savePostAt(
                author, PostType.GENERAL, PostTopic.DAILY,
                "새 글 두 번째", newerTime
        );

        // 나중에 저장해서 ID가 더 크더라도 생성 시각이 오래됐으면 뒤로 간다.
        savePostAt(
                author, PostType.GENERAL, PostTopic.DAILY,
                "오래된 글", olderTime
        );

        entityManager.clear();

        List<Post> result = postRepository.findFirstVisiblePosts(
                null,
                null,
                PageRequest.of(0, 2)
        );

        assertThat(result)
                .extracting(Post::getId)
                .containsExactly(
                        newerHighId.getId(),
                        newerLowId.getId()
                );

        // 영속성 컨텍스트에서 분리한 뒤에도 작성자 정보를 읽을 수 있어야 한다.
        entityManager.clear();

        assertThat(result)
                .allSatisfy(post ->
                        assertThat(post.getAuthor().getNickname())
                                .isEqualTo("작성회원")
                );
    }

    @Test
    void 후속_커서_조회는_같은_시각의_작은_ID와_더_오래된_글을_반환한다() {
        Member author = createAuthor();

        // PostgreSQL에 저장되는 마이크로초 정밀도도 함께 확인한다.
        Instant sameTime =
                Instant.parse("2026-10-18T02:00:00.123456Z");

        Post older = savePostAt(
                author, PostType.GENERAL, PostTopic.DAILY,
                "오래된 글", sameTime.minusSeconds(1)
        );

        Post first = savePostAt(
                author, PostType.GENERAL, PostTopic.DAILY,
                "같은 시각 첫 번째", sameTime
        );

        Post second = savePostAt(
                author, PostType.GENERAL, PostTopic.DAILY,
                "같은 시각 두 번째", sameTime
        );

        Post third = savePostAt(
                author, PostType.GENERAL, PostTopic.DAILY,
                "같은 시각 세 번째", sameTime
        );

        entityManager.clear();

        List<Post> firstBatch = postRepository.findFirstVisiblePosts(
                null,
                null,
                PageRequest.of(0, 2)
        );

        assertThat(firstBatch)
                .extracting(Post::getId)
                .containsExactly(third.getId(), second.getId());

        Post cursor = firstBatch.getLast();

        assertThat(cursor.getCreatedAt()).isEqualTo(sameTime);

        List<Post> nextBatch = postRepository.findVisiblePostsAfter(
                null,
                null,
                cursor.getCreatedAt(),
                cursor.getId(),
                PageRequest.of(0, 2)
        );

        assertThat(nextBatch)
                .extracting(Post::getId)
                .containsExactly(first.getId(), older.getId());

        Post last = nextBatch.getLast();

        assertThat(postRepository.findVisiblePostsAfter(
                null,
                null,
                last.getCreatedAt(),
                last.getId(),
                PageRequest.of(0, 2)
        )).isEmpty();
    }

    @Test
    void 첫_조회와_후속_조회는_필터를_적용하고_삭제된_글을_제외한다() {
        Member author = createAuthor();

        Post target = savePostAt(
                author, PostType.QUESTION, PostTopic.HEARING_AID,
                "조회 대상", CREATED_TIME
        );

        Post otherType = savePostAt(
                author, PostType.REVIEW, PostTopic.HEARING_AID,
                "다른 유형", CREATED_TIME
        );

        Post otherTopic = savePostAt(
                author, PostType.QUESTION, PostTopic.DAILY,
                "다른 주제", CREATED_TIME
        );

        Post deleted = savePostAt(
                author, PostType.QUESTION, PostTopic.HEARING_AID,
                "삭제된 글", CREATED_TIME
        );

        deleted.delete(CREATED_TIME.plusSeconds(1));

        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.findFirstVisiblePosts(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                PageRequest.of(0, 10)
        ))
                .extracting(Post::getId)
                .containsExactly(target.getId());

        assertThat(postRepository.findVisiblePostsAfter(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                CREATED_TIME.plusSeconds(60),
                Long.MAX_VALUE,
                PageRequest.of(0, 10)
        ))
                .extracting(Post::getId)
                .containsExactly(target.getId());

        // 주제만 필터링
        assertThat(postRepository.findFirstVisiblePosts(
                null,
                PostTopic.HEARING_AID,
                PageRequest.of(0, 10)
        ))
                .extracting(Post::getId)
                .containsExactly(otherType.getId(), target.getId());

        // 유형만 필터링
        assertThat(postRepository.findVisiblePostsAfter(
                PostType.QUESTION,
                null,
                CREATED_TIME.plusSeconds(60),
                Long.MAX_VALUE,
                PageRequest.of(0, 10)
        ))
                .extracting(Post::getId)
                .containsExactly(otherTopic.getId(), target.getId());
    }

    @Test
    void 커서_게시글이_삭제되고_새_글이_추가되어도_이어서_조회한다() {
        Member author = createAuthor();

        Post older = savePostAt(
                author, PostType.GENERAL, PostTopic.DAILY,
                "오래된 글", CREATED_TIME
        );

        savePostAt(
                author, PostType.GENERAL, PostTopic.DAILY,
                "커서가 될 글", CREATED_TIME.plusSeconds(1)
        );

        savePostAt(
                author, PostType.GENERAL, PostTopic.DAILY,
                "첫 번째 글", CREATED_TIME.plusSeconds(2)
        );

        entityManager.clear();

        List<Post> firstBatch = postRepository.findFirstVisiblePosts(
                null,
                null,
                PageRequest.of(0, 2)
        );

        Post cursorPost = firstBatch.getLast();
        Instant cursorTime = cursorPost.getCreatedAt();
        Long cursorId = cursorPost.getId();

        cursorPost.delete(CREATED_TIME.plusSeconds(3));
        entityManager.flush();

        Member managedAuthor = memberRepository.findById(author.getId())
                .orElseThrow();

        savePostAt(
                managedAuthor, PostType.GENERAL, PostTopic.DAILY,
                "새로 추가된 글", CREATED_TIME.plusSeconds(4)
        );

        entityManager.clear();

        List<Post> nextBatch = postRepository.findVisiblePostsAfter(
                null,
                null,
                cursorTime,
                cursorId,
                PageRequest.of(0, 2)
        );

        // 새로 추가된 최신 글은 현재 커서보다 앞에 있으므로 이어 읽는 목록에는 끼어들지 않는다.
        // 새로 추가된 최신 글은 첫 목록을 새로 조회할 때 보여야 함.
        assertThat(nextBatch)
                .extracting(Post::getId)
                .containsExactly(older.getId());
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

    private Post savePostAt(
            Member author,
            PostType type,
            PostTopic topic,
            String title,
            Instant createdAt
    ) {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(createdAt));

        return postRepository.saveAndFlush(
                Post.create(
                        author,
                        type,
                        topic,
                        title,
                        "테스트 본문"
                )
        );
    }
}