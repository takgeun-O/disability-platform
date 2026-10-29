package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
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
class CommentDeleteVisibilityIntegrationTest {

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-30T00:00:00Z");

    private static final Instant DELETED_AT =
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
    CommentDeleteService commentDeleteService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private Long postAuthorId;
    private Long commentAuthorId;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            Member postAuthor = createActiveMember();
            Member commentAuthor = createActiveMember();

            Post post = postRepository.saveAndFlush(
                    Post.create(
                            postAuthor,
                            PostType.GENERAL,
                            PostTopic.DAILY,
                            "댓글 삭제 검증 게시글",
                            "게시글 본문"
                    )
            );

            Comment comment = commentRepository.saveAndFlush(
                    Comment.create(
                            post,
                            commentAuthor,
                            "삭제 검증 댓글"
                    )
            );

            postAuthorId = postAuthor.getId();
            commentAuthorId = commentAuthor.getId();
            postId = post.getId();
            commentId = comment.getId();
        });
    }

    @ParameterizedTest
    @CsvSource({
            "false, false",
            "false, true",
            "true, false",
            "true, true"
    })
    void 댓글_작성자는_숨김_여부와_관계없이_댓글을_삭제할_수_있다(
            boolean postHidden,
            boolean commentHidden
    ) {
        applyHiddenState(postHidden, commentHidden);

        Map<String, Object> parentBefore = postSnapshot();
        Instant createdAt = findComment().getCreatedAt();

        commentDeleteService.delete(
                commentAuthorId,
                postId,
                commentId
        );

        Comment deletedComment = findComment();

        assertThat(deletedComment.getDeletedAt()).isNotNull();
        assertThat(deletedComment.getHiddenAt())
                .isEqualTo(commentHidden ? HIDDEN_AT : null);
        assertThat(deletedComment.getCreatedAt())
                .isEqualTo(createdAt);
        assertThat(deletedComment.getContent())
                .isEqualTo("삭제 검증 댓글");

        // 댓글 삭제가 부모 게시글의 상태나 내용을 바꾸지 않는다.
        assertThat(postSnapshot()).isEqualTo(parentBefore);

        // 삭제된 댓글은 공개 조회 대상에서 제외된다.
        assertThat(
                commentRepository.findVisibleCommentsByIds(
                        postId,
                        java.util.List.of(commentId)
                )
        ).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "false, false",
            "false, true",
            "true, false",
            "true, true"
    })
    void 게시글_작성자라도_다른_회원의_댓글은_삭제할_수_없다(
            boolean postHidden,
            boolean commentHidden
    ) {
        applyHiddenState(postHidden, commentHidden);

        Map<String, Object> parentBefore = postSnapshot();
        Map<String, Object> commentBefore = commentSnapshot();

        assertDeleteRejected(
                postAuthorId,
                CommentDeleteException.Code.NOT_COMMENT_AUTHOR
        );

        assertThat(postSnapshot()).isEqualTo(parentBefore);
        assertThat(commentSnapshot()).isEqualTo(commentBefore);
    }

    @ParameterizedTest
    @CsvSource({
            "false, false",
            "false, true",
            "true, false",
            "true, true"
    })
    void 비활성_회원은_자신의_댓글도_삭제할_수_없다(
            boolean postHidden,
            boolean commentHidden
    ) {
        applyHiddenState(postHidden, commentHidden);

        jdbcTemplate.update(
                "UPDATE members SET status = 'PENDING' WHERE id = ?",
                commentAuthorId
        );

        Map<String, Object> parentBefore = postSnapshot();
        Map<String, Object> commentBefore = commentSnapshot();

        assertDeleteRejected(
                commentAuthorId,
                CommentDeleteException.Code.MEMBER_NOT_ACTIVE
        );

        assertThat(postSnapshot()).isEqualTo(parentBefore);
        assertThat(commentSnapshot()).isEqualTo(commentBefore);
    }

    @ParameterizedTest
    @CsvSource({
            "false, false",
            "false, true",
            "true, false",
            "true, true"
    })
    void 부모_게시글이_삭제되었으면_댓글_삭제를_거절한다(
            boolean postHidden,
            boolean commentHidden
    ) {
        applyHiddenState(postHidden, commentHidden);

        jdbcTemplate.update(
                """
                UPDATE community_posts
                SET deleted_at = ?
                WHERE id = ?
                """,
                Timestamp.from(DELETED_AT),
                postId
        );

        Map<String, Object> parentBefore = postSnapshot();
        Map<String, Object> commentBefore = commentSnapshot();

        assertDeleteRejected(
                commentAuthorId,
                CommentDeleteException.Code.POST_NOT_FOUND
        );

        assertThat(postSnapshot()).isEqualTo(parentBefore);
        assertThat(commentSnapshot()).isEqualTo(commentBefore);
    }

    private Member createActiveMember() {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");

        Member member = Member.createPending(
                "delete-" + unique + "@example.com",
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

    private Comment findComment() {
        return commentRepository.findById(commentId)
                .orElseThrow();
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

    private void assertDeleteRejected(
            Long memberId,
            CommentDeleteException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                commentDeleteService.delete(
                        memberId,
                        postId,
                        commentId
                )
        ).isInstanceOfSatisfying(
                CommentDeleteException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );
    }
}