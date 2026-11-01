package io.github.takgeun.iyum.admin.content.infrastructure;

import io.github.takgeun.iyum.admin.content.application.AdminCommentReadQuery;
import io.github.takgeun.iyum.admin.content.application.AdminCommentStatus;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
class AdminCommentReadRepositoryTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-30T10:00:00Z");

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-30T11:00:00Z");

    private static final Instant DELETED_AT =
            Instant.parse("2026-10-30T12:00:00Z");

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
    AdminCommentReadRepository repository;

    @Autowired
    EntityManager entityManager;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Member author;
    private Post targetPost;
    private Post otherPost;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_AT));

        author = Member.createPending(
                "admin-comment-read@example.com",
                "test-only-password-hash",
                "IYUM작성회원"
        );
        author.activeAfterEmailVerification();
        entityManager.persist(author);

        targetPost = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "IYUM 부모 게시글",
                "부모 게시글 내용"
        );

        otherPost = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "다른 게시글",
                "다른 게시글 내용"
        );

        entityManager.persist(targetPost);
        entityManager.persist(otherPost);
        entityManager.flush();
    }

    enum ContentState {
        VISIBLE,
        HIDDEN,
        DELETED,
        HIDDEN_AND_DELETED
    }

    static Stream<Arguments> parentAndCommentFilters() {
        return Arrays.stream(ContentState.values())
                .flatMap(parentState ->
                        Arrays.stream(AdminCommentStatus.values())
                                .map(filter ->
                                        Arguments.of(parentState, filter)
                                )
                );
    }

    @ParameterizedTest
    @MethodSource("parentAndCommentFilters")
    void 부모_상태와_관계없이_해당_게시글의_댓글_상태로_조회한다(
            ContentState parentState,
            AdminCommentStatus filter
    ) {
        Comment visible = createComment(
                "공개 댓글",
                ContentState.VISIBLE
        );
        Comment hidden = createComment(
                "숨김 댓글",
                ContentState.HIDDEN
        );
        Comment deleted = createComment(
                "삭제 댓글",
                ContentState.DELETED
        );
        Comment hiddenAndDeleted = createComment(
                "숨긴 뒤 삭제한 댓글",
                ContentState.HIDDEN_AND_DELETED
        );

        // 다른 게시글에도 모든 상태의 댓글을 넣는다.
        // 목록뿐 아니라 전체 개수에서도 제외되어야 한다.
        for (ContentState state : ContentState.values()) {
            createComment(
                    otherPost,
                    "다른 게시글 댓글",
                    state,
                    CREATED_AT
            );
        }

        applyParentState(parentState);

        List<Long> expectedIds = switch (filter) {
            case ALL -> List.of(
                    visible.getId(),
                    hidden.getId(),
                    deleted.getId(),
                    hiddenAndDeleted.getId()
            );
            case VISIBLE -> List.of(visible.getId());
            case HIDDEN -> List.of(hidden.getId());
            case DELETED -> List.of(
                    deleted.getId(),
                    hiddenAndDeleted.getId()
            );
        };

        List<AdminCommentStatus> expectedStatuses =
                filter == AdminCommentStatus.ALL
                        ? List.of(
                        AdminCommentStatus.VISIBLE,
                        AdminCommentStatus.HIDDEN,
                        AdminCommentStatus.DELETED,
                        AdminCommentStatus.DELETED
                )
                        : Collections.nCopies(expectedIds.size(), filter);

        Long postId = targetPost.getId();
        flushAndClear();

        Page<Comment> result = repository.findComments(
                postId,
                new AdminCommentReadQuery(filter, null, 0, 20)
        );

        assertThat(result.getContent())
                .extracting(Comment::getId)
                .containsExactlyElementsOf(expectedIds);

        assertThat(result.getTotalElements())
                .isEqualTo(expectedIds.size());

        // 응답 변환에 사용할 상태 판정도 함께 확인한다.
        assertThat(result.getContent())
                .extracting(AdminCommentStatus::from)
                .containsExactlyElementsOf(expectedStatuses);
    }

    @Test
    void 댓글_본문을_대소문자_구분_없이_부분_검색한다() {
        Comment matching = createComment(
                "저는 IYUM을 사용하고 있어요",
                ContentState.VISIBLE
        );

        // 부모 제목과 작성자 닉네임에는 IYUM이 있지만 본문에는 없다.
        createComment(
                "검색어가 없는 댓글",
                ContentState.VISIBLE
        );

        // 본문이 일치해도 다른 게시글의 댓글은 제외한다.
        createComment(
                otherPost,
                "다른 게시글의 IYUM 댓글",
                ContentState.VISIBLE,
                CREATED_AT
        );

        Long postId = targetPost.getId();
        flushAndClear();

        Page<Comment> result = repository.findComments(
                postId,
                new AdminCommentReadQuery(
                        AdminCommentStatus.ALL,
                        "  iYuM  ",
                        0,
                        20
                )
        );

        assertThat(result.getContent())
                .extracting(Comment::getId)
                .containsExactly(matching.getId());

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "!%_"})
    void 검색어의_특수문자를_일반_문자로_검색한다(
            String keyword
    ) {
        Comment matching = createComment(
                "앞" + keyword + "뒤",
                ContentState.VISIBLE
        );

        createComment("앞뒤", ContentState.VISIBLE);
        createComment("앞ABC뒤", ContentState.VISIBLE);
        createComment("앞X뒤", ContentState.VISIBLE);

        Long postId = targetPost.getId();
        flushAndClear();

        Page<Comment> result = repository.findComments(
                postId,
                new AdminCommentReadQuery(
                        AdminCommentStatus.ALL,
                        keyword,
                        0,
                        20
                )
        );

        assertThat(result.getContent())
                .extracting(Comment::getId)
                .containsExactly(matching.getId());

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 게시글_상태필터_검색어를_목록과_전체_개수에_동일하게_적용한다() {
        Comment first = createComment(
                "보청기 첫 번째",
                ContentState.HIDDEN
        );
        Comment second = createComment(
                "보청기 두 번째",
                ContentState.HIDDEN
        );

        createComment("보청기 공개", ContentState.VISIBLE);
        createComment("보청기 삭제", ContentState.DELETED);
        createComment(
                "보청기 숨김 후 삭제",
                ContentState.HIDDEN_AND_DELETED
        );
        createComment("인공와우 후기", ContentState.HIDDEN);

        createComment(
                otherPost,
                "보청기 다른 게시글",
                ContentState.HIDDEN,
                CREATED_AT
        );

        Long postId = targetPost.getId();
        flushAndClear();

        Page<Comment> firstPage = repository.findComments(
                postId,
                new AdminCommentReadQuery(
                        AdminCommentStatus.HIDDEN,
                        "보청기",
                        0,
                        1
                )
        );

        Page<Comment> secondPage = repository.findComments(
                postId,
                new AdminCommentReadQuery(
                        AdminCommentStatus.HIDDEN,
                        "보청기",
                        1,
                        1
                )
        );

        assertThat(firstPage.getContent())
                .extracting(Comment::getId)
                .containsExactly(first.getId());

        assertThat(secondPage.getContent())
                .extracting(Comment::getId)
                .containsExactly(second.getId());

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(secondPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void 작성시각_오름차순이고_같은_시각이면_ID_오름차순이다() {
        // ID가 작아도 작성 시각이 늦으면 뒤에 나와야 한다.
        Comment later = createComment(
                targetPost,
                "늦게 작성된 댓글",
                ContentState.VISIBLE,
                CREATED_AT.plusSeconds(60)
        );

        Comment earlierFirst = createComment(
                targetPost,
                "먼저 작성된 댓글 첫 번째",
                ContentState.VISIBLE,
                CREATED_AT
        );

        Comment earlierSecond = createComment(
                targetPost,
                "먼저 작성된 댓글 두 번째",
                ContentState.VISIBLE,
                CREATED_AT
        );

        Long postId = targetPost.getId();
        flushAndClear();

        Page<Comment> result = readPage(postId, 0, 20);

        assertThat(result.getContent())
                .extracting(Comment::getId)
                .containsExactly(
                        earlierFirst.getId(),
                        earlierSecond.getId(),
                        later.getId()
                );
    }

    @Test
    void 페이지별로_중복과_누락_없이_조회한다() {
        List<Long> ids = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            Comment saved = createComment(
                    "페이지 댓글 " + i,
                    ContentState.VISIBLE
            );
            ids.add(saved.getId());
        }

        Long postId = targetPost.getId();
        flushAndClear();

        Page<Comment> first = readPage(postId, 0, 2);
        Page<Comment> second = readPage(postId, 1, 2);
        Page<Comment> last = readPage(postId, 2, 2);

        assertThat(first.getContent())
                .extracting(Comment::getId)
                .containsExactly(ids.get(0), ids.get(1));

        assertThat(second.getContent())
                .extracting(Comment::getId)
                .containsExactly(ids.get(2), ids.get(3));

        assertThat(last.getContent())
                .extracting(Comment::getId)
                .containsExactly(ids.get(4));

        for (Page<Comment> page : List.of(first, second, last)) {
            assertThat(page.getTotalElements()).isEqualTo(5);
            assertThat(page.getTotalPages()).isEqualTo(3);
        }

        assertThat(first.hasNext()).isTrue();
        assertThat(second.hasNext()).isTrue();
        assertThat(last.hasNext()).isFalse();
    }

    @Test
    void 범위를_벗어난_페이지는_전체_개수를_유지한_빈_목록이다() {
        createComment("댓글 하나", ContentState.VISIBLE);

        Long postId = targetPost.getId();
        flushAndClear();

        Page<Comment> result = readPage(postId, 1, 1);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void 해당_게시글에_댓글이_없으면_빈_목록이다() {
        createComment(
                otherPost,
                "다른 게시글에만 있는 댓글",
                ContentState.VISIBLE,
                CREATED_AT
        );

        Long postId = targetPost.getId();
        flushAndClear();

        Page<Comment> result = readPage(postId, 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
    }

    @Test
    void 검색_결과가_없으면_빈_목록이다() {
        createComment("보청기 후기", ContentState.VISIBLE);

        Long postId = targetPost.getId();
        flushAndClear();

        Page<Comment> result = repository.findComments(
                postId,
                new AdminCommentReadQuery(
                        AdminCommentStatus.ALL,
                        "일치하지않는검색어",
                        0,
                        20
                )
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void 댓글_작성자를_함께_로딩한다() {
        createComment("작성자 확인 댓글", ContentState.VISIBLE);

        Long postId = targetPost.getId();
        flushAndClear();

        Page<Comment> result = readPage(postId, 0, 20);

        assertThat(result.getContent()).hasSize(1);

        Comment found = result.getContent().getFirst();

        boolean authorLoaded = entityManager
                .getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .isLoaded(found, "author");

        assertThat(authorLoaded).isTrue();

        entityManager.clear();

        assertThat(found.getAuthor().getNickname())
                .isEqualTo("IYUM작성회원");
    }

    private Page<Comment> readPage(
            Long postId,
            int page,
            int size
    ) {
        return repository.findComments(
                postId,
                new AdminCommentReadQuery(
                        AdminCommentStatus.ALL,
                        null,
                        page,
                        size
                )
        );
    }

    private Comment createComment(
            String content,
            ContentState state
    ) {
        return createComment(
                targetPost,
                content,
                state,
                CREATED_AT
        );
    }

    private Comment createComment(
            Post parent,
            String content,
            ContentState state,
            Instant createdAt
    ) {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(createdAt));

        Comment saved = Comment.create(
                parent,
                author,
                content
        );

        entityManager.persist(saved);
        entityManager.flush();

        if (state == ContentState.HIDDEN
                || state == ContentState.HIDDEN_AND_DELETED) {
            saved.hide(HIDDEN_AT);
        }

        if (state == ContentState.DELETED
                || state == ContentState.HIDDEN_AND_DELETED) {
            saved.delete(DELETED_AT);
        }

        entityManager.flush();
        return saved;
    }

    private void applyParentState(ContentState state) {
        if (state == ContentState.HIDDEN
                || state == ContentState.HIDDEN_AND_DELETED) {
            targetPost.hide(HIDDEN_AT);
        }

        if (state == ContentState.DELETED
                || state == ContentState.HIDDEN_AND_DELETED) {
            targetPost.delete(DELETED_AT);
        }
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}