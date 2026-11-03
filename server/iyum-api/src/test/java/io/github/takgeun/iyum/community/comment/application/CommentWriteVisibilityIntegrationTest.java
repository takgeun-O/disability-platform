package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentLikeRepository;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
class CommentWriteVisibilityIntegrationTest {

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-30T00:00:00Z");

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
    CommentCreateService commentCreateService;

    @Autowired
    CommentUpdateService commentUpdateService;

    @Autowired
    CommentLikeService commentLikeService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    CommentLikeRepository commentLikeRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private Long commentAuthorId;
    private Long viewerId;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            Member postAuthor = createActiveMember();
            Member commentAuthor = createActiveMember();
            Member viewer = createActiveMember();

            Post post = postRepository.saveAndFlush(
                    Post.create(
                            postAuthor,
                            PostType.GENERAL,
                            PostTopic.DAILY,
                            "댓글 변경 검증 게시글",
                            "게시글 본문"
                    )
            );

            Comment comment = commentRepository.saveAndFlush(
                    Comment.create(
                            post,
                            commentAuthor,
                            "기존 댓글 본문"
                    )
            );

            commentAuthorId = commentAuthor.getId();
            viewerId = viewer.getId();
            postId = post.getId();
            commentId = comment.getId();
        });
    }

    @Test
    void 숨겨진_게시글에는_댓글을_작성할_수_없다() {
        applyHiddenState(true, false);

        Map<String, Object> postBefore = postSnapshot();
        Map<String, Object> commentBefore = commentSnapshot();
        long countBefore = countStoredComments();

        assertThatThrownBy(() ->
                commentCreateService.create(
                        viewerId,
                        postId,
                        new CommentCreateCommand("새로운 댓글")
                )
        ).isInstanceOfSatisfying(
                CommentCreateException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                CommentCreateException.Code.POST_NOT_FOUND
                        )
        );

        assertThat(countStoredComments()).isEqualTo(countBefore);
        assertThat(postSnapshot()).isEqualTo(postBefore);
        assertThat(commentSnapshot()).isEqualTo(commentBefore);
    }

    @Test
    void 기존_댓글만_숨겨진_공개_게시글에는_새_댓글을_작성할_수_있다() {
        applyHiddenState(false, true);

        Map<String, Object> postBefore = postSnapshot();
        Map<String, Object> commentBefore = commentSnapshot();
        long countBefore = countStoredComments();

        commentCreateService.create(
                viewerId,
                postId,
                new CommentCreateCommand("새로운 댓글")
        );

        assertThat(countStoredComments())
                .isEqualTo(countBefore + 1);

        // 다른 댓글의 숨김 상태는 그대로 유지된다.
        assertThat(commentSnapshot()).isEqualTo(commentBefore);
        assertThat(postSnapshot()).isEqualTo(postBefore);
    }

    @ParameterizedTest
    @CsvSource({
            "true, false",
            "false, true",
            "true, true"
    })
    void 게시글이나_댓글이_숨겨지면_작성자도_댓글을_수정할_수_없다(
            boolean postHidden,
            boolean commentHidden
    ) {
        applyHiddenState(postHidden, commentHidden);

        Map<String, Object> postBefore = postSnapshot();
        Map<String, Object> commentBefore = commentSnapshot();

        CommentUpdateException.Code expectedCode = postHidden
                ? CommentUpdateException.Code.POST_NOT_FOUND
                : CommentUpdateException.Code.COMMENT_NOT_FOUND;

        assertThatThrownBy(() ->
                commentUpdateService.update(
                        commentAuthorId,
                        postId,
                        commentId,
                        new CommentUpdateCommand("변경 댓글 본문")
                )
        ).isInstanceOfSatisfying(
                CommentUpdateException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );

        assertThat(postSnapshot()).isEqualTo(postBefore);
        assertThat(commentSnapshot()).isEqualTo(commentBefore);
    }

    @ParameterizedTest
    @CsvSource({
            "true, false, false",
            "true, false, true",
            "false, true, false",
            "false, true, true",
            "true, true, false",
            "true, true, true"
    })
    void 게시글이나_댓글이_숨겨지면_좋아요_등록을_거절한다(
            boolean postHidden,
            boolean commentHidden,
            boolean alreadyLiked
    ) {
        assertLikeRequestRejected(
                postHidden,
                commentHidden,
                alreadyLiked,
                true
        );
    }

    @ParameterizedTest
    @CsvSource({
            "true, false, false",
            "true, false, true",
            "false, true, false",
            "false, true, true",
            "true, true, false",
            "true, true, true"
    })
    void 게시글이나_댓글이_숨겨지면_좋아요_취소를_거절한다(
            boolean postHidden,
            boolean commentHidden,
            boolean alreadyLiked
    ) {
        assertLikeRequestRejected(
                postHidden,
                commentHidden,
                alreadyLiked,
                false
        );
    }

    private void assertLikeRequestRejected(
            boolean postHidden,
            boolean commentHidden,
            boolean alreadyLiked,
            boolean register
    ) {
        // 좋아요가 있는 경우 공개 상태에서 먼저 등록하고 커밋한다.
        if (alreadyLiked) {
            commentLikeService.like(viewerId, postId, commentId);
        }

        long expectedCount = alreadyLiked ? 1L : 0L;

        assertThat(
                commentLikeRepository.countByComment_Id(commentId)
        ).isEqualTo(expectedCount);

        applyHiddenState(postHidden, commentHidden);

        Map<String, Object> postBefore = postSnapshot();
        Map<String, Object> commentBefore = commentSnapshot();
        Long likeIdBefore = findLikeId();

        CommentLikeException.Code expectedCode = postHidden
                ? CommentLikeException.Code.POST_NOT_FOUND
                : CommentLikeException.Code.COMMENT_NOT_FOUND;

        assertThatThrownBy(() -> {
            if (register) {
                commentLikeService.like(
                        viewerId,
                        postId,
                        commentId
                );
            } else {
                commentLikeService.unlike(
                        viewerId,
                        postId,
                        commentId
                );
            }
        }).isInstanceOfSatisfying(
                CommentLikeException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );

        // 공개 조회가 아니라 실제 저장된 좋아요 상태를 확인한다.
        assertThat(
                commentLikeRepository.countByComment_Id(commentId)
        ).isEqualTo(expectedCount);

        assertThat(findLikeId()).isEqualTo(likeIdBefore);
        assertThat(postSnapshot()).isEqualTo(postBefore);
        assertThat(commentSnapshot()).isEqualTo(commentBefore);
    }

    private Member createActiveMember() {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");

        Member member = Member.createPending(
                "comment-write-" + unique + "@example.com",
                "test-only-password-hash",
                "회원" + unique.substring(0, 12)
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private void applyHiddenState(
            boolean postHidden,
            boolean commentHidden
    ) {
        if (postHidden) {
            jdbcTemplate.update(
                    """
                    UPDATE community_posts
                    SET hidden_at = ?
                    WHERE id = ?
                    """,
                    Timestamp.from(HIDDEN_AT),
                    postId
            );
        }

        if (commentHidden) {
            jdbcTemplate.update(
                    """
                    UPDATE community_comments
                    SET hidden_at = ?
                    WHERE id = ?
                    """,
                    Timestamp.from(HIDDEN_AT),
                    commentId
            );
        }
    }

    private Long findLikeId() {
        return commentLikeRepository
                .findByComment_IdAndMember_Id(commentId, viewerId)
                .map(like -> like.getId())
                .orElse(null);
    }

    private long countStoredComments() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM community_comments
                WHERE post_id = ?
                """,
                Long.class,
                postId
        );
    }

    private Map<String, Object> postSnapshot() {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM community_posts WHERE id = ?",
                postId
        );
    }

    private Map<String, Object> commentSnapshot() {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM community_comments WHERE id = ?",
                commentId
        );
    }
}