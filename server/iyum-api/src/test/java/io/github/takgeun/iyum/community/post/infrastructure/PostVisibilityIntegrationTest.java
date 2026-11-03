package io.github.takgeun.iyum.community.post.infrastructure;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostLike;
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
import org.springframework.data.domain.PageRequest;
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
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class PostVisibilityIntegrationTest {

    private static final Instant BASE_TIME =
            Instant.parse("2026-10-30T00:00:00Z");

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
    PostLikeRepository postLikeRepository;

    @Autowired
    EntityManager entityManager;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Long authorId;

    private Post oldVisible;
    private Post hidden;
    private Post deleted;
    private Post hiddenAndDeleted;
    private Post newVisible;
    private Post otherAuthorPost;

    @BeforeEach
    void setUp() {
        setAuditingTime(BASE_TIME);

        Member author = createMember(
                "visibility-a@example.com",
                "조회테스트회원A"
        );

        Member other = createMember(
                "visibility-b@example.com",
                "조회테스트회원B"
        );

        authorId = author.getId();

        oldVisible = createPost(author, "오래된 공개 글", 0);
        hidden = createPost(author, "숨긴 글", 1);
        deleted = createPost(author, "삭제한 글", 2);
        hiddenAndDeleted = createPost(author, "숨긴 뒤 삭제한 글", 3);
        newVisible = createPost(author, "최신 공개 글", 4);
        otherAuthorPost = createPost(other, "다른 회원 글", 5);

        setAuditingTime(BASE_TIME.plusSeconds(60));

        // 오래된 공개 글: 좋아요 2개, 회원 A가 좋아요를 누름.
        entityManager.persist(PostLike.create(oldVisible, author));
        entityManager.persist(PostLike.create(oldVisible, other));

        // 최신 공개 글: 좋아요 1개, 회원 A는 누르지 않음.
        entityManager.persist(PostLike.create(newVisible, other));

        // 비공개 대상에도 기존 좋아요가 남아 있는 상황.
        entityManager.persist(PostLike.create(hidden, author));
        entityManager.persist(PostLike.create(deleted, author));
        entityManager.persist(PostLike.create(hiddenAndDeleted, author));

        hidden.hide(BASE_TIME.plusSeconds(60));

        deleted.delete(BASE_TIME.plusSeconds(60));

        hiddenAndDeleted.hide(BASE_TIME.plusSeconds(60));
        hiddenAndDeleted.delete(BASE_TIME.plusSeconds(61));

        flushAndClear();
    }

    @Test
    void 목록과_전체_개수에서_숨김과_삭제된_게시글을_제외한다() {
        // 페이지 크기를 1로 하여 전체 개수 조회도 검증한다.
        var firstPage = postRepository.findVisiblePosts(
                null,
                null,
                PageRequest.of(0, 1)
        );

        assertThat(firstPage.getContent())
                .extracting(Post::getId)
                .containsExactly(otherAuthorPost.getId());

        assertThat(firstPage.getTotalElements()).isEqualTo(3L);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);

        var secondPage = postRepository.findVisiblePosts(
                null,
                null,
                PageRequest.of(1, 1)
        );

        assertThat(secondPage.getContent())
                .extracting(Post::getId)
                .containsExactly(newVisible.getId());

        assertThat(secondPage.getTotalElements()).isEqualTo(3L);
    }

    @Test
    void 상세_조회는_공개_게시글만_반환한다() {
        assertThat(postRepository.findVisiblePostById(oldVisible.getId()))
                .isPresent();

        assertThat(postRepository.findVisiblePostById(newVisible.getId()))
                .isPresent();

        assertThat(postRepository.findVisiblePostById(hidden.getId()))
                .isEmpty();

        assertThat(postRepository.findVisiblePostById(deleted.getId()))
                .isEmpty();

        assertThat(
                postRepository.findVisiblePostById(hiddenAndDeleted.getId())
        ).isEmpty();
    }

    @Test
    void 피드_첫_조회는_공개_게시글만_최신순으로_반환한다() {
        List<Post> result = postRepository.findFirstVisiblePosts(
                null,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(result)
                .extracting(Post::getId)
                .containsExactly(
                        otherAuthorPost.getId(),
                        newVisible.getId(),
                        oldVisible.getId()
                );
    }

    @Test
    void 피드_후속_조회는_커서_뒤의_숨김과_삭제된_글을_건너뛴다() {
        List<Post> result = postRepository.findVisiblePostsAfter(
                null,
                null,
                newVisible.getCreatedAt(),
                newVisible.getId(),
                PageRequest.of(0, 20)
        );

        assertThat(result)
                .extracting(Post::getId)
                .containsExactly(oldVisible.getId());
    }

    @Test
    void 기존_공개전용_작성자_조회와_개수는_같은_공개_조건을_사용한다() {
        List<Post> result = postRepository.findFirstVisiblePostsByAuthorId(
                authorId,
                PageRequest.of(0, 20)
        );

        assertThat(result)
                .extracting(Post::getId)
                .containsExactly(
                        newVisible.getId(),
                        oldVisible.getId()
                );

        assertThat(postRepository.countVisiblePostsByAuthorId(authorId))
                .isEqualTo(2L);
    }

    @Test
    void 기존_공개전용_작성자_후속_조회는_숨김과_삭제된_글을_제외한다() {
        List<Post> result = postRepository.findVisiblePostsByAuthorIdAfter(
                authorId,
                newVisible.getCreatedAt(),
                newVisible.getId(),
                PageRequest.of(0, 20)
        );

        assertThat(result)
                .extracting(Post::getId)
                .containsExactly(oldVisible.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 좋아요_요약은_공개_게시글만_반환한다(boolean loggedIn) {
        Long viewerId = loggedIn ? authorId : null;

        List<PostLikeSummary> summaries =
                postLikeRepository.findSummariesByPostIds(
                        allPostIds(),
                        viewerId
                );

        // 다른 회원의 공개 글은 좋아요가 없으므로 요약 행이 없다.
        assertThat(summaries)
                .extracting(PostLikeSummary::getPostId)
                .containsExactlyInAnyOrder(
                        oldVisible.getId(),
                        newVisible.getId()
                );

        PostLikeSummary oldSummary =
                findSummary(summaries, oldVisible.getId());

        assertThat(oldSummary.getLikeCount()).isEqualTo(2L);
        assertThat(oldSummary.getViewerLikeCount())
                .isEqualTo(loggedIn ? 1L : 0L);

        PostLikeSummary newSummary =
                findSummary(summaries, newVisible.getId());

        assertThat(newSummary.getLikeCount()).isEqualTo(1L);
        assertThat(newSummary.getViewerLikeCount()).isZero();

        // 공개 요약에서 제외할 뿐 좋아요 행 자체를 삭제하지 않는다.
        assertThat(postLikeRepository.countByPost_Id(hidden.getId()))
                .isEqualTo(1L);
        assertThat(postLikeRepository.countByPost_Id(deleted.getId()))
                .isEqualTo(1L);
        assertThat(
                postLikeRepository.countByPost_Id(hiddenAndDeleted.getId())
        ).isEqualTo(1L);
    }

    @Test
    void 숨김을_해제하면_기존_좋아요와_함께_다시_조회된다() {
        Post target = entityManager.find(Post.class, hidden.getId());

        assertThat(target.restore()).isTrue();
        flushAndClear();

        assertThat(postRepository.findVisiblePostById(hidden.getId()))
                .isPresent();

        assertThat(postRepository.countVisiblePostsByAuthorId(authorId))
                .isEqualTo(3L);

        List<Post> myPosts = postRepository.findFirstVisiblePostsByAuthorId(
                authorId,
                PageRequest.of(0, 20)
        );

        assertThat(myPosts)
                .extracting(Post::getId)
                .containsExactly(
                        newVisible.getId(),
                        hidden.getId(),
                        oldVisible.getId()
                );

        List<PostLikeSummary> summaries =
                postLikeRepository.findSummariesByPostIds(
                        List.of(hidden.getId()),
                        authorId
                );

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.getPostId()).isEqualTo(hidden.getId());
            assertThat(summary.getLikeCount()).isEqualTo(1L);
            assertThat(summary.getViewerLikeCount()).isEqualTo(1L);
        });

        // 다른 삭제 상태의 게시글은 계속 제외된다.
        assertThat(postRepository.findVisiblePostById(deleted.getId()))
                .isEmpty();

        assertThat(
                postRepository.findVisiblePostById(hiddenAndDeleted.getId())
        ).isEmpty();
    }

    private Member createMember(String email, String nickname) {
        Member member = Member.createPending(
                email,
                "test-only-password-hash",
                nickname
        );
        member.activeAfterEmailVerification();

        entityManager.persist(member);
        entityManager.flush();

        return member;
    }

    private Post createPost(
            Member author,
            String title,
            long seconds
    ) {
        setAuditingTime(BASE_TIME.plusSeconds(seconds));

        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                title,
                "테스트 본문"
        );

        entityManager.persist(post);
        entityManager.flush();

        return post;
    }

    private void setAuditingTime(Instant time) {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(time));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<Long> allPostIds() {
        return List.of(
                oldVisible.getId(),
                hidden.getId(),
                deleted.getId(),
                hiddenAndDeleted.getId(),
                newVisible.getId(),
                otherAuthorPost.getId()
        );
    }

    private PostLikeSummary findSummary(
            List<PostLikeSummary> summaries,
            Long postId
    ) {
        return summaries.stream()
                .filter(summary -> summary.getPostId().equals(postId))
                .findFirst()
                .orElseThrow();
    }
}