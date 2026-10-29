package io.github.takgeun.iyum.member.infrastructure;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class MemberCommentVisibilityRepositoryTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-29T00:00:00Z");

    private static final Instant STATE_CHANGED_AT =
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
    MemberRepository memberRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    private Member me;
    private Member other;

    @BeforeEach
    void setUp() {
        me = createActiveMember();
        other = createActiveMember();
    }

    @ParameterizedTest
    @CsvSource({
            // 부모 숨김, 부모 삭제, 댓글 숨김, 댓글 삭제, 포함 여부
            "false, false, false, false, true",
            "false, false, true,  false, true",
            "true,  false, false, false, true",
            "true,  false, true,  false, true",

            "false, false, false, true,  false",
            "false, false, true,  true,  false",
            "true,  false, false, true,  false",
            "true,  false, true,  true,  false",

            "false, true,  false, false, false",
            "false, true,  true,  false, false",
            "true,  true,  false, false, false",
            "true,  true,  true,  false, false",

            "false, true,  false, true,  false",
            "false, true,  true,  true,  false",
            "true,  true,  false, true,  false",
            "true,  true,  true,  true,  false"
    })
    void 첫조회와_후속조회와_개수에_동일한_상태_조건을_적용한다(
            boolean postHidden,
            boolean postDeleted,
            boolean commentHidden,
            boolean commentDeleted,
            boolean included
    ) {
        // 다른 사람이 작성한 게시글에 남긴 내 댓글도 조회되어야 한다.
        Post post = createPost(other, postHidden, postDeleted);

        Comment mine = createComment(
                post,
                me,
                CREATED_AT,
                commentHidden,
                commentDeleted
        );

        // 같은 상태라도 다른 회원의 댓글은 내 조회에서 제외되어야 한다.
        createComment(
                post,
                other,
                CREATED_AT,
                commentHidden,
                commentDeleted
        );

        Long memberId = me.getId();
        Long commentId = mine.getId();

        entityManager.flush();
        entityManager.clear();

        var first = commentRepository
                .findFirstNotDeletedCommentsByAuthorId(
                        memberId,
                        PageRequest.of(0, 10)
                );

        // 준비한 댓글보다 최신인 커서로 후속 조회 조건도 검사한다.
        var after = commentRepository
                .findNotDeletedCommentsByAuthorIdAfter(
                        memberId,
                        CREATED_AT.plusSeconds(1),
                        Long.MAX_VALUE,
                        PageRequest.of(0, 10)
                );

        long count = commentRepository
                .countNotDeletedCommentsByAuthorId(memberId);

        if (included) {
            assertThat(first)
                    .extracting(Comment::getId)
                    .containsExactly(commentId);

            assertThat(after)
                    .extracting(Comment::getId)
                    .containsExactly(commentId);

            assertThat(count).isEqualTo(1L);

            // 결과 변환에 필요한 숨김 상태도 확인할 수 있어야 한다.
            Comment loaded = first.getFirst();

            assertThat(loaded.getHiddenAt())
                    .isEqualTo(
                            commentHidden ? STATE_CHANGED_AT : null
                    );

            assertThat(loaded.getPost().getHiddenAt())
                    .isEqualTo(
                            postHidden ? STATE_CHANGED_AT : null
                    );
        } else {
            assertThat(first).isEmpty();
            assertThat(after).isEmpty();
            assertThat(count).isZero();
        }
    }

    @Test
    void 숨김_항목도_최신순과_동일시각_ID_커서에_포함한다() {
        Post visiblePost = createPost(other, false, false);
        Post hiddenPost = createPost(other, true, false);

        Comment oldVisible = createComment(
                visiblePost,
                me,
                CREATED_AT.minusSeconds(60),
                false,
                false
        );

        Comment hiddenComment = createComment(
                visiblePost,
                me,
                CREATED_AT,
                true,
                false
        );

        // 같은 작성 시각이지만 나중에 생성되어 ID가 더 크다.
        Comment parentHiddenComment = createComment(
                hiddenPost,
                me,
                CREATED_AT,
                false,
                false
        );

        Long memberId = me.getId();
        Long oldId = oldVisible.getId();
        Long hiddenId = hiddenComment.getId();
        Long parentHiddenId = parentHiddenComment.getId();

        entityManager.flush();
        entityManager.clear();

        var first = commentRepository
                .findFirstNotDeletedCommentsByAuthorId(
                        memberId,
                        PageRequest.of(0, 2)
                );

        assertThat(first)
                .extracting(Comment::getId)
                .containsExactly(parentHiddenId, hiddenId);

        // 동일 시각에서는 ID가 작은 댓글로 이어진다.
        var afterParentHidden = commentRepository
                .findNotDeletedCommentsByAuthorIdAfter(
                        memberId,
                        CREATED_AT,
                        parentHiddenId,
                        PageRequest.of(0, 10)
                );

        assertThat(afterParentHidden)
                .extracting(Comment::getId)
                .containsExactly(hiddenId, oldId);

        // 숨김 댓글을 지나면 더 오래된 공개 댓글로 이어진다.
        var afterHidden = commentRepository
                .findNotDeletedCommentsByAuthorIdAfter(
                        memberId,
                        CREATED_AT,
                        hiddenId,
                        PageRequest.of(0, 10)
                );

        assertThat(afterHidden)
                .extracting(Comment::getId)
                .containsExactly(oldId);
    }

    private Member createActiveMember() {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");

        Member member = Member.createPending(
                "my-comment-" + unique + "@example.com",
                "test-only-password-hash",
                "회원" + unique.substring(0, 12)
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private Post createPost(
            Member author,
            boolean hidden,
            boolean deleted
    ) {
        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "내 댓글 조회 대상",
                "게시글 본문"
        );

        if (hidden) {
            post.hide(STATE_CHANGED_AT);
        }

        if (deleted) {
            post.delete(STATE_CHANGED_AT);
        }

        return postRepository.saveAndFlush(post);
    }

    private Comment createComment(
            Post post,
            Member author,
            Instant createdAt,
            boolean hidden,
            boolean deleted
    ) {
        Comment comment = Comment.create(
                post,
                author,
                "내 댓글 조회 검증 본문"
        );

        if (hidden) {
            comment.hide(STATE_CHANGED_AT);
        }

        if (deleted) {
            comment.delete(STATE_CHANGED_AT);
        }

        Comment saved = commentRepository.saveAndFlush(comment);

        jdbcTemplate.update(
                """
                UPDATE community_comments
                SET created_at = ?
                WHERE id = ?
                """,
                Timestamp.from(createdAt),
                saved.getId()
        );

        return saved;
    }
}