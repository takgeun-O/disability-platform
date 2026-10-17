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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
class CommentDeleteServiceTest {

    private static final Instant CREATED_TIME =
            Instant.parse("2026-10-17T03:00:00.123456Z");

    private static final Instant UPDATED_TIME =
            Instant.parse("2026-10-17T04:00:00.123456Z");

    private static final String ORIGINAL_CONTENT = "삭제할 댓글";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    CommentDeleteService commentDeleteService;

    @Autowired
    CommentReadService commentReadService;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Post post;
    private Member commentAuthor;

    private Long postId;
    private Long otherPostId;
    private Long commentId;
    private Long postAuthorId;
    private Long commentAuthorId;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_TIME));

        // 이 테스트 전용 컨테이너의 데이터만 초기화한다.
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member postAuthor = saveActiveMember(
                "post-author@example.com",
                "게시글작성자"
        );
        postAuthorId = postAuthor.getId();

        commentAuthor = saveActiveMember(
                "comment-author@example.com",
                "댓글작성자"
        );
        commentAuthorId = commentAuthor.getId();

        post = savePost(postAuthor, "댓글 삭제 대상 게시글");
        postId = post.getId();

        otherPostId = savePost(postAuthor, "다른 게시글").getId();

        commentId = commentRepository.saveAndFlush(
                Comment.create(post, commentAuthor, ORIGINAL_CONTENT)
        ).getId();

        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(UPDATED_TIME));
    }

    @Test
    void 본인_댓글을_삭제하면_행을_유지하고_삭제_시각을_기록한다() {
        Instant beforeDelete = Instant.now();

        commentDeleteService.delete(
                commentAuthorId,
                postId,
                commentId
        );

        Instant afterDelete = Instant.now();

        Map<String, Object> saved = readComment();

        // 물리적으로 삭제되지 않아야 한다.
        assertThat(commentRepository.count()).isEqualTo(1L);

        assertThat(saved.get("id")).isEqualTo(commentId);
        assertThat(saved.get("post_id")).isEqualTo(postId);
        assertThat(saved.get("author_id")).isEqualTo(commentAuthorId);
        assertThat(saved.get("content")).isEqualTo(ORIGINAL_CONTENT);
        assertThat(saved.get("created_at"))
                .isEqualTo(Timestamp.from(CREATED_TIME));

        // 삭제 상태 변경도 엔티티 수정이므로 Auditing이 적용된다.
        assertThat(saved.get("updated_at"))
                .isEqualTo(Timestamp.from(UPDATED_TIME));

        assertThat(saved.get("deleted_at"))
                .isInstanceOf(Timestamp.class);

        Instant deletedAt =
                ((Timestamp) saved.get("deleted_at")).toInstant();

        // PostgreSQL 저장 시 시각 정밀도 차이를 고려해 1ms 여유를 둔다.
        assertThat(deletedAt).isBetween(
                beforeDelete.minusMillis(1),
                afterDelete.plusMillis(1)
        );
    }

    @Test
    void 삭제한_댓글만_목록에서_제외하고_다른_댓글은_유지한다() {
        Long remainingCommentId = commentRepository.saveAndFlush(
                Comment.create(
                        post,
                        commentAuthor,
                        "목록에 남아 있어야 하는 댓글"
                )
        ).getId();

        // 삭제 전에는 두 댓글이 모두 조회된다.
        assertThat(
                commentReadService.getComments(
                        postId,
                        null,
                        null,
                        20
                ).items()
        )
                .extracting(CommentResult::commentId)
                .containsExactly(commentId, remainingCommentId);

        commentDeleteService.delete(
                commentAuthorId,
                postId,
                commentId
        );

        // 삭제 후에는 남은 댓글만 조회된다.
        assertThat(
                commentReadService.getComments(
                        postId,
                        null,
                        null,
                        20
                ).items()
        )
                .extracting(CommentResult::commentId)
                .containsExactly(remainingCommentId);

        // 실제 DB에는 두 행 모두 남아 있다.
        assertThat(commentRepository.count()).isEqualTo(2L);

        assertThat(
                commentRepository.findById(remainingCommentId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isNull();
    }

    @Test
    void 이미_삭제한_댓글은_다시_삭제할_수_없다() {
        commentDeleteService.delete(
                commentAuthorId,
                postId,
                commentId
        );

        assertThat(readComment().get("deleted_at")).isNotNull();

        // 재요청은 거절되고 최초 삭제 시각도 유지되어야 한다.
        assertDeleteRejected(
                commentAuthorId,
                postId,
                commentId,
                CommentDeleteException.Code.COMMENT_NOT_FOUND
        );
    }

    @Test
    void 게시글_작성자라도_다른_회원의_댓글은_삭제할_수_없다() {
        assertDeleteRejected(
                postAuthorId,
                postId,
                commentId,
                CommentDeleteException.Code.NOT_COMMENT_AUTHOR
        );
    }

    @Test
    void 다른_일반_회원은_댓글을_삭제할_수_없다() {
        Member otherMember = saveActiveMember(
                "other-member@example.com",
                "다른회원"
        );

        assertDeleteRejected(
                otherMember.getId(),
                postId,
                commentId,
                CommentDeleteException.Code.NOT_COMMENT_AUTHOR
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 유효하지_않거나_없는_회원은_삭제할_수_없다(
            Long invalidMemberId
    ) {
        assertDeleteRejected(
                invalidMemberId,
                postId,
                commentId,
                CommentDeleteException.Code.MEMBER_NOT_FOUND
        );
    }

    @Test
    void 삭제할_때_DB의_현재_회원_상태를_확인한다() {
        jdbcTemplate.update("""
                UPDATE members
                SET status = 'PENDING'
                WHERE id = ?
                """,
                commentAuthorId
        );

        assertDeleteRejected(
                commentAuthorId,
                postId,
                commentId,
                CommentDeleteException.Code.MEMBER_NOT_ACTIVE
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 유효하지_않거나_없는_게시글에서는_삭제할_수_없다(
            Long invalidPostId
    ) {
        assertDeleteRejected(
                commentAuthorId,
                invalidPostId,
                commentId,
                CommentDeleteException.Code.POST_NOT_FOUND
        );
    }

    @Test
    void 삭제된_게시글의_댓글은_삭제할_수_없다() {
        jdbcTemplate.update("""
                UPDATE community_posts
                SET deleted_at = ?
                WHERE id = ?
                """,
                Timestamp.from(UPDATED_TIME),
                postId
        );

        assertDeleteRejected(
                commentAuthorId,
                postId,
                commentId,
                CommentDeleteException.Code.POST_NOT_FOUND
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 유효하지_않거나_없는_댓글은_삭제할_수_없다(
            Long invalidCommentId
    ) {
        assertDeleteRejected(
                commentAuthorId,
                postId,
                invalidCommentId,
                CommentDeleteException.Code.COMMENT_NOT_FOUND
        );
    }

    @Test
    void 다른_게시글_ID로는_댓글을_삭제할_수_없다() {
        assertDeleteRejected(
                commentAuthorId,
                otherPostId,
                commentId,
                CommentDeleteException.Code.COMMENT_NOT_FOUND
        );
    }

    private Member saveActiveMember(String email, String nickname) {
        Member member = Member.createPending(
                email,
                "test-only-password-hash",
                nickname
        );
        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private Post savePost(Member author, String title) {
        return postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        title,
                        "게시글 본문"
                )
        );
    }

    private Map<String, Object> readComment() {
        return jdbcTemplate.queryForMap("""
                SELECT id, post_id, author_id, content,
                       created_at, updated_at, deleted_at
                FROM community_comments
                WHERE id = ?
                """,
                commentId
        );
    }

    private void assertDeleteRejected(
            Long memberId,
            Long targetPostId,
            Long targetCommentId,
            CommentDeleteException.Code expectedCode
    ) {
        Map<String, Object> before = readComment();
        long countBefore = commentRepository.count();

        assertThatThrownBy(() ->
                commentDeleteService.delete(
                        memberId,
                        targetPostId,
                        targetCommentId
                )
        ).isInstanceOfSatisfying(
                CommentDeleteException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );

        // 본문·작성자·생성/수정/삭제 시각이 모두 유지되어야 한다.
        assertThat(readComment()).isEqualTo(before);
        assertThat(commentRepository.count()).isEqualTo(countBefore);
    }
}