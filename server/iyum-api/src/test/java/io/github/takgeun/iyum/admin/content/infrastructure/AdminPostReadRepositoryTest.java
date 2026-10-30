package io.github.takgeun.iyum.admin.content.infrastructure;

import io.github.takgeun.iyum.admin.content.application.AdminPostReadQuery;
import io.github.takgeun.iyum.admin.content.application.AdminPostStatus;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.Page;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.ArrayList;
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
class AdminPostReadRepositoryTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-30T10:00:00Z");

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-30T11:00:00Z");

    private static final Instant DELETED_AT =
            Instant.parse("2026-10-30T12:00:00Z");

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
    AdminPostReadRepository repository;

    @Autowired
    EntityManager entityManager;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Member author;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_AT));

        author = Member.createPending(
                "admin-post-read@example.com",
                "test-only-password-hash",
                "조회검증회원"
        );
        author.activeAfterEmailVerification();

        entityManager.persist(author);
        entityManager.flush();
    }

    enum ContentState {
        VISIBLE,
        HIDDEN,
        DELETED,
        HIDDEN_AND_DELETED
    }

    @ParameterizedTest
    @EnumSource(AdminPostStatus.class)
    void 상태별로_게시글과_전체_개수를_조회한다(
            AdminPostStatus status
    ) {
        Post visible = createPost(
                "공개 게시글",
                ContentState.VISIBLE
        );
        Post hidden = createPost(
                "숨김 게시글",
                ContentState.HIDDEN
        );
        Post deleted = createPost(
                "삭제 게시글",
                ContentState.DELETED
        );
        Post hiddenAndDeleted = createPost(
                "숨긴 뒤 삭제한 게시글",
                ContentState.HIDDEN_AND_DELETED
        );

        // 작성 시각이 같으므로 ID 내림차순이다.
        List<Long> expectedIds = switch (status) {
            case ALL -> List.of(
                    hiddenAndDeleted.getId(),
                    deleted.getId(),
                    hidden.getId(),
                    visible.getId()
            );
            case VISIBLE -> List.of(visible.getId());
            case HIDDEN -> List.of(hidden.getId());
            case DELETED -> List.of(
                    hiddenAndDeleted.getId(),
                    deleted.getId()
            );
        };

        flushAndClear();

        Page<Post> result = repository.findPosts(
                new AdminPostReadQuery(status, null, 0, 20)
        );

        assertThat(result.getContent())
                .extracting(Post::getId)
                .containsExactlyElementsOf(expectedIds);

        assertThat(result.getTotalElements())
                .isEqualTo(expectedIds.size());
    }

    @Test
    void 제목을_대소문자_구분_없이_부분_검색하고_본문은_검색하지_않는다() {
        Post matching = createPost(
                "나의 IYUM 사용 후기",
                ContentState.VISIBLE
        );

        createPost(
                "다른 제목",
                "본문에만 iyum이 있습니다.",
                ContentState.VISIBLE,
                CREATED_AT
        );

        flushAndClear();

        Page<Post> result = repository.findPosts(
                new AdminPostReadQuery(
                        AdminPostStatus.ALL,
                        "  iYuM  ",
                        0,
                        20
                )
        );

        assertThat(result.getContent())
                .extracting(Post::getId)
                .containsExactly(matching.getId());

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "!%_"})
    void 검색어의_특수문자를_일반_문자로_검색한다(
            String keyword
    ) {
        Post matching = createPost(
                "앞" + keyword + "뒤",
                ContentState.VISIBLE
        );

        createPost("앞뒤", ContentState.VISIBLE);
        createPost("앞ABC뒤", ContentState.VISIBLE);
        createPost("앞X뒤", ContentState.VISIBLE);

        flushAndClear();

        Page<Post> result = repository.findPosts(
                new AdminPostReadQuery(
                        AdminPostStatus.ALL,
                        keyword,
                        0,
                        20
                )
        );

        assertThat(result.getContent())
                .extracting(Post::getId)
                .containsExactly(matching.getId());

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 상태와_검색어를_함께_적용하고_전체_개수에도_반영한다() {
        Post first = createPost(
                "보청기 첫 번째",
                ContentState.HIDDEN
        );
        Post second = createPost(
                "보청기 두 번째",
                ContentState.HIDDEN
        );

        createPost("보청기 공개", ContentState.VISIBLE);
        createPost("보청기 삭제", ContentState.DELETED);
        createPost(
                "보청기 숨김 후 삭제",
                ContentState.HIDDEN_AND_DELETED
        );
        createPost("인공와우 후기", ContentState.HIDDEN);

        flushAndClear();

        Page<Post> firstPage = repository.findPosts(
                new AdminPostReadQuery(
                        AdminPostStatus.HIDDEN,
                        "보청기",
                        0,
                        1
                )
        );
        Page<Post> secondPage = repository.findPosts(
                new AdminPostReadQuery(
                        AdminPostStatus.HIDDEN,
                        "보청기",
                        1,
                        1
                )
        );

        assertThat(firstPage.getContent())
                .extracting(Post::getId)
                .containsExactly(second.getId());

        assertThat(secondPage.getContent())
                .extracting(Post::getId)
                .containsExactly(first.getId());

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(secondPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void 작성시각_내림차순이고_같은_시각이면_ID_내림차순이다() {
        Instant later = CREATED_AT.plusSeconds(60);

        Post newerFirst = createPost(
                "최근 게시글 첫 번째",
                "내용",
                ContentState.VISIBLE,
                later
        );
        Post newerSecond = createPost(
                "최근 게시글 두 번째",
                "내용",
                ContentState.VISIBLE,
                later
        );

        // ID가 가장 커도 작성 시각이 오래됐으면 뒤에 나와야 한다.
        Post older = createPost(
                "오래된 게시글",
                "내용",
                ContentState.VISIBLE,
                CREATED_AT
        );

        flushAndClear();

        Page<Post> result = repository.findPosts(
                new AdminPostReadQuery(
                        AdminPostStatus.ALL,
                        null,
                        0,
                        20
                )
        );

        assertThat(result.getContent())
                .extracting(Post::getId)
                .containsExactly(
                        newerSecond.getId(),
                        newerFirst.getId(),
                        older.getId()
                );
    }

    @Test
    void 페이지별로_중복과_누락_없이_조회한다() {
        List<Long> ids = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            Post saved = createPost(
                    "페이지 게시글 " + i,
                    ContentState.VISIBLE
            );
            ids.add(saved.getId());
        }

        flushAndClear();

        Page<Post> first = readPage(0, 2);
        Page<Post> second = readPage(1, 2);
        Page<Post> last = readPage(2, 2);

        assertThat(first.getContent())
                .extracting(Post::getId)
                .containsExactly(ids.get(4), ids.get(3));

        assertThat(second.getContent())
                .extracting(Post::getId)
                .containsExactly(ids.get(2), ids.get(1));

        assertThat(last.getContent())
                .extracting(Post::getId)
                .containsExactly(ids.get(0));

        for (Page<Post> page : List.of(first, second, last)) {
            assertThat(page.getTotalElements()).isEqualTo(5);
            assertThat(page.getTotalPages()).isEqualTo(3);
        }

        assertThat(first.hasNext()).isTrue();
        assertThat(second.hasNext()).isTrue();
        assertThat(last.hasNext()).isFalse();
    }

    @Test
    void 범위를_벗어난_페이지는_전체_개수를_유지한_빈_목록이다() {
        createPost("게시글 하나", ContentState.VISIBLE);
        flushAndClear();

        Page<Post> result = readPage(1, 1);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void 검색_결과가_없으면_빈_목록과_전체_개수_0을_반환한다() {
        createPost("보청기 후기", ContentState.VISIBLE);
        flushAndClear();

        Page<Post> result = repository.findPosts(
                new AdminPostReadQuery(
                        AdminPostStatus.ALL,
                        "일치하지않는검색어",
                        0,
                        20
                )
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
    }

    @ParameterizedTest
    @EnumSource(ContentState.class)
    void 상세는_상태와_관계없이_원문과_작성자를_조회한다(
            ContentState state
    ) {
        Post saved = createPost(
                "관리자 확인용 제목",
                "관리자 확인용 원문",
                state,
                CREATED_AT
        );
        Long postId = saved.getId();

        flushAndClear();

        Post found = repository.findPostById(postId)
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(postId);
        assertThat(found.getTitle())
                .isEqualTo("관리자 확인용 제목");
        assertThat(found.getContent())
                .isEqualTo("관리자 확인용 원문");

        boolean hidden = state == ContentState.HIDDEN
                || state == ContentState.HIDDEN_AND_DELETED;

        boolean deleted = state == ContentState.DELETED
                || state == ContentState.HIDDEN_AND_DELETED;

        assertThat(found.getHiddenAt())
                .isEqualTo(hidden ? HIDDEN_AT : null);
        assertThat(found.getDeletedAt())
                .isEqualTo(deleted ? DELETED_AT : null);

        assertAuthorLoaded(found);

        entityManager.clear();

        // 영속성 컨텍스트에서 분리해도 작성자 정보를 읽을 수 있다.
        assertThat(found.getAuthor().getNickname())
                .isEqualTo("조회검증회원");
    }

    @Test
    void 존재하지_않는_게시글의_상세는_빈_Optional이다() {
        flushAndClear();

        assertThat(repository.findPostById(-1L)).isEmpty();
    }

    @Test
    void 목록_조회도_작성자를_함께_로딩한다() {
        createPost("작성자 확인 게시글", ContentState.VISIBLE);
        flushAndClear();

        Page<Post> result = readPage(0, 20);

        assertThat(result.getContent()).hasSize(1);

        Post found = result.getContent().getFirst();
        assertAuthorLoaded(found);

        entityManager.clear();

        assertThat(found.getAuthor().getNickname())
                .isEqualTo("조회검증회원");
    }

    private Page<Post> readPage(int page, int size) {
        return repository.findPosts(
                new AdminPostReadQuery(
                        AdminPostStatus.ALL,
                        null,
                        page,
                        size
                )
        );
    }

    private Post createPost(
            String title,
            ContentState state
    ) {
        return createPost(
                title,
                "테스트 게시글 내용",
                state,
                CREATED_AT
        );
    }

    private Post createPost(
            String title,
            String content,
            ContentState state,
            Instant createdAt
    ) {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(createdAt));

        Post saved = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                title,
                content
        );

        // Auditing으로 지정한 작성 시각을 저장한다.
        entityManager.persist(saved);
        entityManager.flush();

        if (state == ContentState.HIDDEN
                || state == ContentState.HIDDEN_AND_DELETED) {
            saved.hide(HIDDEN_AT);
        }

        // 숨김 후 삭제 상태는 hide → delete 순서로 만든다.
        if (state == ContentState.DELETED
                || state == ContentState.HIDDEN_AND_DELETED) {
            saved.delete(DELETED_AT);
        }

        entityManager.flush();
        return saved;
    }

    private void assertAuthorLoaded(Post target) {
        boolean loaded = entityManager
                .getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .isLoaded(target, "author");

        assertThat(loaded).isTrue();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}