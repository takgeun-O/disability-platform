package io.github.takgeun.iyum.community.comment.infrastructure;

import io.github.takgeun.iyum.community.comment.application.CommentSort;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.domain.CommentLike;
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
class CommentVisibilityIntegrationTest {

    private static final Instant BASE_TIME =
            Instant.parse("2026-10-30T00:00:00Z");

    private static final PageRequest LIMIT = PageRequest.of(0, 20);

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
    CommentRepository commentRepository;

    @Autowired
    CommentLikeRepository commentLikeRepository;

    @Autowired
    EntityManager entityManager;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Long authorId;

    private Post visiblePost;
    private Post hiddenPost;
    private Post deletedPost;
    private Post hiddenAndDeletedPost;

    private Comment oldVisible;
    private Comment hidden;
    private Comment deleted;
    private Comment hiddenAndDeleted;
    private Comment newVisible;
    private Comment otherAuthorComment;

    private Comment onHiddenPost;
    private Comment individuallyHiddenOnHiddenPost;
    private Comment onDeletedPost;
    private Comment onHiddenAndDeletedPost;

    private List<Comment> allComments;

    @BeforeEach
    void setUp() {
        setTime(BASE_TIME);

        Member author = createMember(
                "comment-visibility-a@example.com",
                "댓글조회회원A"
        );
        Member other = createMember(
                "comment-visibility-b@example.com",
                "댓글조회회원B"
        );
        Member third = createMember(
                "comment-visibility-c@example.com",
                "댓글조회회원C"
        );

        authorId = author.getId();

        visiblePost = createPost(author, "공개 게시글");
        hiddenPost = createPost(author, "숨김 게시글");
        deletedPost = createPost(author, "삭제 게시글");
        hiddenAndDeletedPost = createPost(author, "숨김 삭제 게시글");

        // 공개 게시글 안의 댓글.
        oldVisible = createComment(visiblePost, author, "오래된 공개", 0);
        hidden = createComment(visiblePost, author, "숨김 댓글", 10);
        deleted = createComment(visiblePost, author, "삭제 댓글", 20);
        hiddenAndDeleted =
                createComment(visiblePost, author, "숨김 삭제 댓글", 30);
        newVisible = createComment(visiblePost, author, "최신 공개", 40);
        otherAuthorComment =
                createComment(visiblePost, other, "다른 회원 댓글", 50);

        // 내 댓글 후속 조회의 커서 사이에도 들어가도록 시각을 배치한다.
        onHiddenPost =
                createComment(hiddenPost, author, "숨긴 부모의 공개 댓글", 15);
        individuallyHiddenOnHiddenPost =
                createComment(hiddenPost, author, "부모와 함께 숨긴 댓글", 25);
        onDeletedPost =
                createComment(deletedPost, author, "삭제 부모의 댓글", 22);
        onHiddenAndDeletedPost =
                createComment(hiddenAndDeletedPost, author, "숨김 삭제 부모 댓글", 32);

        allComments = List.of(
                oldVisible,
                hidden,
                deleted,
                hiddenAndDeleted,
                newVisible,
                otherAuthorComment,
                onHiddenPost,
                individuallyHiddenOnHiddenPost,
                onDeletedPost,
                onHiddenAndDeletedPost
        );

        setTime(BASE_TIME.plusSeconds(60));

        // 공개 댓글의 인기순: 오래된 공개 2개 -> 최신 공개 1개 -> 다른 회원 0개.
        like(oldVisible, author);
        like(oldVisible, other);
        like(newVisible, other);

        // 숨김·삭제 댓글에도 기존 좋아요가 남아 있는 상황.
        like(hidden, author);
        like(deleted, author);
        like(deleted, other);
        like(deleted, third);
        like(hiddenAndDeleted, author);

        like(onHiddenPost, author);
        like(individuallyHiddenOnHiddenPost, author);
        like(onDeletedPost, author);
        like(onHiddenAndDeletedPost, author);

        Instant hiddenAt = BASE_TIME.plusSeconds(60);
        Instant deletedAt = BASE_TIME.plusSeconds(61);

        hidden.hide(hiddenAt);
        deleted.delete(deletedAt);
        hiddenAndDeleted.hide(hiddenAt);
        hiddenAndDeleted.delete(deletedAt);

        individuallyHiddenOnHiddenPost.hide(hiddenAt);

        hiddenPost.hide(hiddenAt);
        deletedPost.delete(deletedAt);
        hiddenAndDeletedPost.hide(hiddenAt);
        hiddenAndDeletedPost.delete(deletedAt);

        flushAndClear();
    }

    @ParameterizedTest
    @EnumSource(
            value = CommentSort.class,
            names = {"OLDEST", "LATEST"}
    )
    void 시간순_첫_조회는_공개_댓글만_반환한다(CommentSort sort) {
        List<Comment> result = switch (sort) {
            case OLDEST -> commentRepository.findFirstVisibleComments(
                    visiblePost.getId(), LIMIT
            );
            case LATEST -> commentRepository.findFirstVisibleCommentsLatest(
                    visiblePost.getId(), LIMIT
            );
            default -> throw new IllegalArgumentException();
        };

        if (sort == CommentSort.OLDEST) {
            assertThat(result).extracting(Comment::getId)
                    .containsExactly(
                            oldVisible.getId(),
                            newVisible.getId(),
                            otherAuthorComment.getId()
                    );
        } else {
            assertThat(result).extracting(Comment::getId)
                    .containsExactly(
                            otherAuthorComment.getId(),
                            newVisible.getId(),
                            oldVisible.getId()
                    );
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = CommentSort.class,
            names = {"OLDEST", "LATEST"}
    )
    void 시간순_후속_조회는_숨김과_삭제된_댓글을_건너뛴다(
            CommentSort sort
    ) {
        if (sort == CommentSort.OLDEST) {
            List<Comment> result = commentRepository.findVisibleCommentsAfter(
                    visiblePost.getId(),
                    oldVisible.getCreatedAt(),
                    oldVisible.getId(),
                    LIMIT
            );

            assertThat(result).extracting(Comment::getId)
                    .containsExactly(
                            newVisible.getId(),
                            otherAuthorComment.getId()
                    );
        } else {
            List<Comment> result =
                    commentRepository.findVisibleCommentsLatestAfter(
                            visiblePost.getId(),
                            newVisible.getCreatedAt(),
                            newVisible.getId(),
                            LIMIT
                    );

            assertThat(result).extracting(Comment::getId)
                    .containsExactly(oldVisible.getId());
        }
    }

    @Test
    void 인기순_첫_조회는_좋아요가_많아도_삭제된_댓글을_제외한다() {
        List<CommentPopularityRow> result =
                commentRepository.findFirstVisibleCommentsPopular(
                        visiblePost.getId(), LIMIT
                );

        assertThat(result)
                .extracting(CommentPopularityRow::getCommentId)
                .containsExactly(
                        oldVisible.getId(),
                        newVisible.getId(),
                        otherAuthorComment.getId()
                );

        assertThat(result)
                .extracting(CommentPopularityRow::getLikeCount)
                .containsExactly(2L, 1L, 0L);
    }

    @Test
    void 인기순_후속_조회도_숨김과_삭제된_댓글을_제외한다() {
        List<CommentPopularityRow> result =
                commentRepository.findVisibleCommentsPopularAfter(
                        visiblePost.getId(),
                        2L,
                        oldVisible.getCreatedAt(),
                        oldVisible.getId(),
                        LIMIT
                );

        assertThat(result)
                .extracting(CommentPopularityRow::getCommentId)
                .containsExactly(
                        newVisible.getId(),
                        otherAuthorComment.getId()
                );
    }

    @Test
    void 본문_일괄_조회도_동일한_공개_조건을_사용한다() {
        List<Comment> result = commentRepository.findVisibleCommentsByIds(
                visiblePost.getId(),
                allCommentIds()
        );

        // IN 조회는 결과 순서를 보장하지 않는다.
        assertThat(result).extracting(Comment::getId)
                .containsExactlyInAnyOrder(
                        oldVisible.getId(),
                        newVisible.getId(),
                        otherAuthorComment.getId()
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {"HIDDEN", "DELETED", "HIDDEN_AND_DELETED"})
    void 부모가_비공개이면_모든_댓글_조회에서_제외한다(String state) {
        Long postId = switch (state) {
            case "HIDDEN" -> hiddenPost.getId();
            case "DELETED" -> deletedPost.getId();
            case "HIDDEN_AND_DELETED" -> hiddenAndDeletedPost.getId();
            default -> throw new IllegalArgumentException(state);
        };

        assertThat(commentRepository.findFirstVisibleComments(
                postId, LIMIT
        )).isEmpty();

        assertThat(commentRepository.findFirstVisibleCommentsLatest(
                postId, LIMIT
        )).isEmpty();

        assertThat(commentRepository.findFirstVisibleCommentsPopular(
                postId, LIMIT
        )).isEmpty();

        // 부모 조건만 빠지면 댓글이 실제로 조회되는 커서 범위로 설정한다.
        assertThat(commentRepository.findVisibleCommentsAfter(
                postId,
                BASE_TIME.minusSeconds(1),
                1L,
                LIMIT
        )).isEmpty();

        assertThat(commentRepository.findVisibleCommentsLatestAfter(
                postId,
                BASE_TIME.plusSeconds(100),
                Long.MAX_VALUE,
                LIMIT
        )).isEmpty();

        assertThat(commentRepository.findVisibleCommentsPopularAfter(
                postId,
                99L,
                BASE_TIME,
                1L,
                LIMIT
        )).isEmpty();

        assertThat(commentRepository.findVisibleCommentsByIds(
                postId, allCommentIds()
        )).isEmpty();
    }

    @Test
    void 게시글별_댓글_수는_부모와_댓글의_공개_조건을_반영한다() {
        List<PostCommentCount> result =
                commentRepository.countVisibleCommentsByPostIds(
                        List.of(
                                visiblePost.getId(),
                                hiddenPost.getId(),
                                deletedPost.getId(),
                                hiddenAndDeletedPost.getId()
                        )
                );

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.getPostId()).isEqualTo(visiblePost.getId());
            assertThat(row.getCommentCount()).isEqualTo(3L);
        });
    }

    @Test
    void 내_댓글_목록과_개수와_후속_조회는_같은_조건을_사용한다() {
        assertThat(commentRepository.countVisibleCommentsByAuthorId(authorId))
                .isEqualTo(2L);

        List<Comment> first =
                commentRepository.findFirstVisibleCommentsByAuthorId(
                        authorId, LIMIT
                );

        assertThat(first).extracting(Comment::getId)
                .containsExactly(
                        newVisible.getId(),
                        oldVisible.getId()
                );

        List<Comment> after =
                commentRepository.findVisibleCommentsByAuthorIdAfter(
                        authorId,
                        newVisible.getCreatedAt(),
                        newVisible.getId(),
                        LIMIT
                );

        assertThat(after).extracting(Comment::getId)
                .containsExactly(oldVisible.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 좋아요_요약은_공개_댓글만_반환한다(boolean loggedIn) {
        List<CommentLikeSummary> summaries =
                commentLikeRepository.findSummariesByCommentIds(
                        allCommentIds(),
                        loggedIn ? authorId : null
                );

        // 좋아요 0개인 공개 댓글은 요약 행이 없다.
        assertThat(summaries)
                .extracting(CommentLikeSummary::getCommentId)
                .containsExactlyInAnyOrder(
                        oldVisible.getId(),
                        newVisible.getId()
                );

        CommentLikeSummary oldSummary =
                findSummary(summaries, oldVisible.getId());

        assertThat(oldSummary.getLikeCount()).isEqualTo(2L);
        assertThat(oldSummary.getViewerLikeCount())
                .isEqualTo(loggedIn ? 1L : 0L);

        CommentLikeSummary newSummary =
                findSummary(summaries, newVisible.getId());

        assertThat(newSummary.getLikeCount()).isEqualTo(1L);
        assertThat(newSummary.getViewerLikeCount()).isZero();

        // 화면 요약에서 제외해도 좋아요 데이터는 보존한다.
        assertThat(commentLikeRepository.countByComment_Id(hidden.getId()))
                .isEqualTo(1L);
        assertThat(commentLikeRepository.countByComment_Id(deleted.getId()))
                .isEqualTo(3L);
        assertThat(
                commentLikeRepository.countByComment_Id(onHiddenPost.getId())
        ).isEqualTo(1L);
    }

    @Test
    void 부모를_복원해도_개별적으로_숨긴_댓글은_계속_제외한다() {
        Post target = entityManager.find(Post.class, hiddenPost.getId());

        assertThat(target.restore()).isTrue();
        flushAndClear();

        List<Comment> result = commentRepository.findFirstVisibleComments(
                hiddenPost.getId(), LIMIT
        );

        assertThat(result).extracting(Comment::getId)
                .containsExactly(onHiddenPost.getId());

        assertThat(commentRepository.countVisibleCommentsByAuthorId(authorId))
                .isEqualTo(3L);

        List<PostCommentCount> counts =
                commentRepository.countVisibleCommentsByPostIds(
                        List.of(hiddenPost.getId())
                );

        assertThat(counts).singleElement().satisfies(row ->
                assertThat(row.getCommentCount()).isEqualTo(1L)
        );

        List<CommentLikeSummary> summaries =
                commentLikeRepository.findSummariesByCommentIds(
                        List.of(
                                onHiddenPost.getId(),
                                individuallyHiddenOnHiddenPost.getId()
                        ),
                        authorId
                );

        assertThat(summaries).singleElement().satisfies(row -> {
            assertThat(row.getCommentId()).isEqualTo(onHiddenPost.getId());
            assertThat(row.getLikeCount()).isEqualTo(1L);
            assertThat(row.getViewerLikeCount()).isEqualTo(1L);
        });

        Comment stillHidden = entityManager.find(
                Comment.class,
                individuallyHiddenOnHiddenPost.getId()
        );
        assertThat(stillHidden.getHiddenAt()).isNotNull();
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

    private Post createPost(Member author, String title) {
        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                title,
                "테스트 게시글 본문"
        );

        entityManager.persist(post);
        entityManager.flush();

        return post;
    }

    private Comment createComment(
            Post post,
            Member author,
            String content,
            long seconds
    ) {
        setTime(BASE_TIME.plusSeconds(seconds));

        Comment comment = Comment.create(post, author, content);

        entityManager.persist(comment);
        entityManager.flush();

        return comment;
    }

    private void like(Comment comment, Member member) {
        entityManager.persist(CommentLike.create(comment, member));
    }

    private void setTime(Instant time) {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(time));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<Long> allCommentIds() {
        return allComments.stream()
                .map(Comment::getId)
                .toList();
    }

    private CommentLikeSummary findSummary(
            List<CommentLikeSummary> summaries,
            Long commentId
    ) {
        return summaries.stream()
                .filter(row -> row.getCommentId().equals(commentId))
                .findFirst()
                .orElseThrow();
    }
}