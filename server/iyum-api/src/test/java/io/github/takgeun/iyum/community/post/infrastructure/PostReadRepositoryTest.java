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
import org.springframework.data.domain.Page;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class PostReadRepositoryTest {

    private static final Instant BASE_TIME =
            Instant.parse("2026-10-13T03:00:00Z");

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
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long firstAuthorId;

    private Long oldQuestionId;
    private Long latestGeneralId;
    private Long recentQuestionId;
    private Long sameTimeQuestionId;
    private Long hearingAidGeneralId;
    private Long deletedId;

    @BeforeEach
    void preparePosts() {
        Member firstAuthor = createMember(
                "writer.one@example.com",
                "작성자하나"
        );

        Member secondAuthor = createMember(
                "writer.two@example.com",
                "작성자둘"
        );

        firstAuthorId = firstAuthor.getId();

        // 기준 시각 (미삭제)
        oldQuestionId = createPost(
                firstAuthor,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "오래된 질문",
                BASE_TIME
        );

        // 기준 + 300초 (미삭제)
        // ID는 작지만 작성 시각은 가장 최신인 공개 게시글이다.
        latestGeneralId = createPost(
                secondAuthor,
                PostType.GENERAL,
                PostTopic.DAILY,
                "최신 일반 글",
                BASE_TIME.plusSeconds(300)
        );

        // 기준 + 200초 (미삭제)
        recentQuestionId = createPost(
                firstAuthor,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "최근 보청기 질문",
                BASE_TIME.plusSeconds(200)
        );

        // 기준 + 200초 (미삭제)
        // 바로 앞 글과 시각은 같지만 ID는 더 크다.
        sameTimeQuestionId = createPost(
                secondAuthor,
                PostType.QUESTION,
                PostTopic.DAILY,
                "같은 시각의 일상 질문",
                BASE_TIME.plusSeconds(200)
        );

        // 기준 + 100초 (미삭제)
        hearingAidGeneralId = createPost(
                firstAuthor,
                PostType.GENERAL,
                PostTopic.HEARING_AID,
                "보청기 일반 글",
                BASE_TIME.plusSeconds(100)
        );

        // 기준 + 400초 (삭제됨)
        deletedId = createPost(
                secondAuthor,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "삭제된 최신 질문",
                BASE_TIME.plusSeconds(400)
        );

        // 삭제 기능 구현 전이므로 테스트 데이터만 SQL로 준비한다.
        jdbcTemplate.update(
                """
                UPDATE community_posts
                SET deleted_at = ?
                WHERE id = ?
                """,
                Timestamp.from(BASE_TIME.plusSeconds(500)),
                deletedId
        );

        // 이후 조회가 준비 과정의 관리 객체에 의존하지 않도록 한다.
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 삭제되지_않은_글을_작성자와_함께_최신순으로_조회한다() {

        // 유형·주제 제한 없이 첫 페이지를 조회
        Page<Post> page = postRepository.findVisiblePosts(
                null,
                null,
                PageRequest.of(0, 20)
        );

        // 6개의 게시글 중 삭제된 게시글인 deletedId 가 나오면 안된다.
        assertThat(page.getContent())   // 현재 페이지의 List<Post>
                .extracting(Post::getId)    // 각 게시글에서 ID만 추출
                .containsExactly(
                        latestGeneralId,
                        sameTimeQuestionId,
                        recentQuestionId,
                        hearingAidGeneralId,
                        oldQuestionId
                );

        assertThat(page.getTotalElements()).isEqualTo(5);

        // 조회 후 영속성 컨텍스트를 비워도 작성자 정보가 있어야 한다.
        entityManager.clear();

        // allSatisfy() : 목록의 모든 게시글에 대해 내부 검증을 실행한다
        // 조회 후 영속성 컨텍스트를 비웠으니 그 뒤에도 닉네임에 접근할 수 있는지 본다.
        // join fetch p.author로 작성자를 미리 가져왔다면 통과하는거고,
        // 작성자가 초기화되지 않은 지연 로딩 상태라면 보통 LazyInitializationException으로 실패할 것.
        assertThat(page.getContent()).allSatisfy(post ->
                assertThat(post.getAuthor().getNickname())
                        .isNotBlank()
        );
    }

    @Test
    void 페이지별_목록과_전체_개수를_정확히_반환한다() {
        Page<Post> first = postRepository.findVisiblePosts(
                null,
                null,
                PageRequest.of(0, 2)
        );

        Page<Post> second = postRepository.findVisiblePosts(
                null,
                null,
                PageRequest.of(1, 2)
        );

        Page<Post> last = postRepository.findVisiblePosts(
                null,
                null,
                PageRequest.of(2, 2)
        );

        assertThat(first.getContent())
                .extracting(Post::getId)
                .containsExactly(
                        latestGeneralId,
                        sameTimeQuestionId
                );

        assertThat(second.getContent())
                .extracting(Post::getId)
                .containsExactly(
                        recentQuestionId,
                        hearingAidGeneralId
                );

        assertThat(last.getContent())
                .extracting(Post::getId)
                .containsExactly(oldQuestionId);

        assertThat(first.getNumber()).isZero();
        assertThat(first.getSize()).isEqualTo(2);
        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();

        assertThat(last.getTotalElements()).isEqualTo(5);
        assertThat(last.hasNext()).isFalse();

        Page<Post> beyondLast = postRepository.findVisiblePosts(
                null,
                null,
                PageRequest.of(10, 2)
        );

        assertThat(beyondLast.getContent()).isEmpty();
        assertThat(beyondLast.getTotalElements()).isEqualTo(5);
    }

    @Test
    void 유형으로_필터링한다() {
        Page<Post> page = postRepository.findVisiblePosts(
                PostType.QUESTION,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(page.getContent())
                .extracting(Post::getId)
                .containsExactly(
                        sameTimeQuestionId,
                        recentQuestionId,
                        oldQuestionId
                );

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void 주제로_필터링한다() {
        Page<Post> page = postRepository.findVisiblePosts(
                null,
                PostTopic.HEARING_AID,
                PageRequest.of(0, 20)
        );

        assertThat(page.getContent())
                .extracting(Post::getId)
                .containsExactly(
                        recentQuestionId,
                        hearingAidGeneralId,
                        oldQuestionId
                );

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void 유형과_주제를_함께_적용하고_개수에도_반영한다() {
        // 일치하는 공개 글은 2건이다.
        // 페이지 크기를 1로 지정해 countQuery도 확인한다.
        Page<Post> first = postRepository.findVisiblePosts(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                PageRequest.of(0, 1)
        );

        Page<Post> second = postRepository.findVisiblePosts(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                PageRequest.of(1, 1)
        );

        assertThat(first.getContent())
                .extracting(Post::getId)
                .containsExactly(recentQuestionId);

        assertThat(second.getContent())
                .extracting(Post::getId)
                .containsExactly(oldQuestionId);

        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void 조건에_맞는_게시글이_없으면_빈_페이지를_반환한다() {
        Page<Post> page = postRepository.findVisiblePosts(
                PostType.REVIEW,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getTotalPages()).isZero();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void 공개_게시글의_상세와_작성자를_함께_조회한다() {
        Post post = postRepository
                .findVisiblePostById(recentQuestionId)
                .orElseThrow();

        entityManager.clear();

        assertThat(post.getId()).isEqualTo(recentQuestionId);
        assertThat(post.getTitle()).isEqualTo("최근 보청기 질문");
        assertThat(post.getContent())
                .isEqualTo("최근 보청기 질문 본문");

        assertThat(post.getType()).isEqualTo(PostType.QUESTION);
        assertThat(post.getTopic()).isEqualTo(PostTopic.HEARING_AID);
        assertThat(post.getDeletedAt()).isNull();

        assertThat(post.getAuthor().getId())
                .isEqualTo(firstAuthorId);

        assertThat(post.getAuthor().getNickname())
                .isEqualTo("작성자하나");
    }

    @Test
    void 삭제된_게시글은_DB에_있어도_상세_조회되지_않는다() {
        assertThat(postRepository.existsById(deletedId))
                .isTrue();

        assertThat(postRepository.findVisiblePostById(deletedId))
                .isEmpty();
    }

    @Test
    void 존재하지_않는_게시글은_빈_Optional을_반환한다() {
        assertThat(
                postRepository.findVisiblePostById(Long.MAX_VALUE)
        ).isEmpty();
    }

    private Member createMember(
            String email,
            String nickname
    ) {
        Member member = Member.createPending(
                email,
                "test-only-password-hash",
                nickname
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private Long createPost(
            Member author,
            PostType type,
            PostTopic topic,
            String title,
            Instant createdAt
    ) {
        Post post = postRepository.saveAndFlush(
                Post.create(
                        author,
                        type,
                        topic,
                        title,
                        title + " 본문"
                )
        );

        // 정렬을 정확히 검증하기 위해 테스트에서 작성 시각을 지정한다.
        jdbcTemplate.update(
                """
                UPDATE community_posts
                SET created_at = ?, updated_at = ?
                WHERE id = ?
                """,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                post.getId()
        );

        return post.getId();
    }
}