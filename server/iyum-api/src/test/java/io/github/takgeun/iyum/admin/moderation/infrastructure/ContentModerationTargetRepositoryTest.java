package io.github.takgeun.iyum.admin.moderation.infrastructure;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class ContentModerationTargetRepositoryTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-29T10:00:00Z");

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-29T11:00:00Z");

    private static final Instant DELETED_AT =
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
    MemberRepository memberRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    EntityManager entityManager;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Long memberId;
    private Post post;
    private Comment comment;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_AT));

        Member member = Member.createPending(
                "moderation-target@example.com",
                "test-only-password-hash",
                "테스트회원"
        );
        member.activeAfterEmailVerification();
        memberRepository.saveAndFlush(member);
        memberId = member.getId();

        post = Post.create(
                member,
                PostType.GENERAL,
                PostTopic.DAILY,
                "조회 테스트 제목",
                "조회 테스트 내용"
        );
        entityManager.persist(post);

        comment = Comment.create(
                post,
                member,
                "조회 테스트 댓글"
        );
        entityManager.persist(comment);

        entityManager.flush();
    }

    enum ContentState {
        VISIBLE,
        HIDDEN,
        DELETED,
        HIDDEN_AND_DELETED;

        boolean hidden() {
            return this == HIDDEN || this == HIDDEN_AND_DELETED;
        }

        boolean deleted() {
            return this == DELETED || this == HIDDEN_AND_DELETED;
        }
    }

    @ParameterizedTest
    @EnumSource(ContentState.class)
    void 게시글은_상태와_관계없이_잠금_조회한다(
            ContentState state
    ) {
        applyState(post, state);
        Long postId = post.getId();

        flushAndClear();
        lockMember();

        Post found = postRepository
                .findPostByIdForModerationForUpdate(postId)
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(postId);
        assertThat(found.getHiddenAt())
                .isEqualTo(state.hidden() ? HIDDEN_AT : null);
        assertThat(found.getDeletedAt())
                .isEqualTo(state.deleted() ? DELETED_AT : null);

        assertThat(entityManager.getLockMode(found))
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    static Stream<Arguments> commentStates() {
        return Arrays.stream(ContentState.values())
                .flatMap(postState ->
                        Arrays.stream(ContentState.values())
                                .map(commentState ->
                                        Arguments.of(
                                                postState,
                                                commentState
                                        )
                                )
                );
    }

    @ParameterizedTest
    @MethodSource("commentStates")
    void 댓글은_부모와_자신의_상태와_관계없이_잠금_조회한다(
            ContentState postState,
            ContentState commentState
    ) {
        applyState(post, postState);
        applyState(comment, commentState);

        Long postId = post.getId();
        Long commentId = comment.getId();

        flushAndClear();
        lockMember();

        // 회원 -> 부모 게시글 -> 댓글 순서로 잠금을 획득한다.
        Post lockedPost = postRepository
                .findPostByIdForModerationForUpdate(postId)
                .orElseThrow();

        Comment found = commentRepository
                .findCommentByIdForModerationForUpdate(
                        postId,
                        commentId
                )
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(commentId);
        assertThat(found.getPost().getId()).isEqualTo(postId);

        assertThat(lockedPost.getHiddenAt())
                .isEqualTo(postState.hidden() ? HIDDEN_AT : null);
        assertThat(lockedPost.getDeletedAt())
                .isEqualTo(postState.deleted() ? DELETED_AT : null);

        assertThat(found.getHiddenAt())
                .isEqualTo(commentState.hidden() ? HIDDEN_AT : null);
        assertThat(found.getDeletedAt())
                .isEqualTo(commentState.deleted() ? DELETED_AT : null);

        assertThat(entityManager.getLockMode(lockedPost))
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(entityManager.getLockMode(found))
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void 존재하지_않는_게시글은_조회되지_않는다() {
        flushAndClear();
        lockMember();

        assertThat(
                postRepository.findPostByIdForModerationForUpdate(-1L)
        ).isEmpty();
    }

    @Test
    void 존재하지_않는_댓글은_조회되지_않는다() {
        Long postId = post.getId();

        flushAndClear();
        lockMember();

        postRepository.findPostByIdForModerationForUpdate(postId)
                .orElseThrow();

        assertThat(
                commentRepository.findCommentByIdForModerationForUpdate(
                        postId,
                        -1L
                )
        ).isEmpty();
    }

    @Test
    void 다른_게시글에_속한_댓글은_조회되지_않는다() {
        Post anotherPost = Post.create(
                post.getAuthor(),
                PostType.GENERAL,
                PostTopic.DAILY,
                "다른 게시글",
                "다른 게시글 내용"
        );
        entityManager.persist(anotherPost);

        Long anotherPostId = anotherPost.getId();
        Long commentId = comment.getId();

        flushAndClear();
        lockMember();

        postRepository
                .findPostByIdForModerationForUpdate(anotherPostId)
                .orElseThrow();

        assertThat(
                commentRepository.findCommentByIdForModerationForUpdate(
                        anotherPostId,
                        commentId
                )
        ).isEmpty();
    }

    private void applyState(Post target, ContentState state) {
        if (state.hidden()) {
            target.hide(HIDDEN_AT);
        }

        if (state.deleted()) {
            target.delete(DELETED_AT);
        }
    }

    private void applyState(Comment target, ContentState state) {
        if (state.hidden()) {
            target.hide(HIDDEN_AT);
        }

        if (state.deleted()) {
            target.delete(DELETED_AT);
        }
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private void lockMember() {
        memberRepository.findByIdForUpdate(memberId).orElseThrow();
    }
}