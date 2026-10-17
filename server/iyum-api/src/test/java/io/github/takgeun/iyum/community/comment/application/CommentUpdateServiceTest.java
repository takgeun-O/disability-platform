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
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
class CommentUpdateServiceTest {

    private static final Instant CREATED_TIME =
            Instant.parse("2026-10-17T03:00:00.123456Z");

    private static final Instant UPDATED_TIME =
            Instant.parse("2026-10-17T04:00:00.123456Z");

    private static final String ORIGINAL_CONTENT = "수정 전 댓글";

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
    CommentUpdateService commentUpdateService;

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

        Member commentAuthor = saveActiveMember(
                "comment-author@example.com",
                "댓글작성자"
        );
        commentAuthorId = commentAuthor.getId();

        Post post = savePost(postAuthor, "댓글 수정 대상 게시글");
        postId = post.getId();

        Post otherPost = savePost(postAuthor, "다른 게시글");
        otherPostId = otherPost.getId();

        Comment comment = commentRepository.saveAndFlush(
                Comment.create(post, commentAuthor, ORIGINAL_CONTENT)
        );
        commentId = comment.getId();

        // 이후 실제 수정이 발생하면 이 시각이 기록되어야 한다.
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(UPDATED_TIME));
    }

    @Test
    void 본인_댓글을_수정하면_본문과_수정_시각만_변경된다() {
        String newContent = "  수정한 댓글\n두 번째 줄  ";

        commentUpdateService.update(
                commentAuthorId,
                postId,
                commentId,
                new CommentUpdateCommand(newContent)
        );

        // 서비스 트랜잭션이 끝난 뒤 실제 DB 결과를 조회한다.
        Map<String, Object> saved = readComment();

        assertThat(saved.get("id")).isEqualTo(commentId);
        assertThat(saved.get("post_id")).isEqualTo(postId);
        assertThat(saved.get("author_id")).isEqualTo(commentAuthorId);

        // 앞뒤 공백과 줄바꿈도 그대로 유지한다.
        assertThat(saved.get("content")).isEqualTo(newContent);

        assertThat(saved.get("created_at"))
                .isEqualTo(Timestamp.from(CREATED_TIME));
        assertThat(saved.get("updated_at"))
                .isEqualTo(Timestamp.from(UPDATED_TIME));
        assertThat(saved.get("deleted_at")).isNull();

        // 새 댓글을 생성한 것이 아니어야 한다.
        assertThat(commentRepository.count()).isEqualTo(1L);
    }

    @Test
    void 동일한_본문이면_수정_시각을_변경하지_않는다() {
        Map<String, Object> before = readComment();

        commentUpdateService.update(
                commentAuthorId,
                postId,
                commentId,
                new CommentUpdateCommand(ORIGINAL_CONTENT)
        );

        assertThat(readComment()).isEqualTo(before);
    }

    @Test
    void 게시글_작성자라도_다른_회원의_댓글은_수정할_수_없다() {
        assertUpdateRejected(
                postAuthorId,
                postId,
                commentId,
                CommentUpdateException.Code.NOT_COMMENT_AUTHOR
        );
    }

    @Test
    void 다른_일반_회원은_댓글을_수정할_수_없다() {
        Member otherMember = saveActiveMember(
                "other-member@example.com",
                "다른회원"
        );

        assertUpdateRejected(
                otherMember.getId(),
                postId,
                commentId,
                CommentUpdateException.Code.NOT_COMMENT_AUTHOR
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 유효하지_않거나_없는_회원은_수정할_수_없다(
            Long invalidMemberId
    ) {
        assertUpdateRejected(
                invalidMemberId,
                postId,
                commentId,
                CommentUpdateException.Code.MEMBER_NOT_FOUND
        );
    }

    @Test
    void 수정할_때_DB의_현재_회원_상태를_확인한다() {
        // 댓글 작성 당시 ACTIVE였더라도 현재 상태를 확인해야 한다.
        jdbcTemplate.update("""
                UPDATE members
                SET status = 'PENDING'
                WHERE id = ?
                """,
                commentAuthorId
        );

        assertUpdateRejected(
                commentAuthorId,
                postId,
                commentId,
                CommentUpdateException.Code.MEMBER_NOT_ACTIVE
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 유효하지_않거나_없는_게시글에서는_수정할_수_없다(
            Long invalidPostId
    ) {
        assertUpdateRejected(
                commentAuthorId,
                invalidPostId,
                commentId,
                CommentUpdateException.Code.POST_NOT_FOUND
        );
    }

    @Test
    void 삭제된_게시글의_댓글은_수정할_수_없다() {
        jdbcTemplate.update("""
                UPDATE community_posts
                SET deleted_at = ?
                WHERE id = ?
                """,
                Timestamp.from(UPDATED_TIME),
                postId
        );

        assertUpdateRejected(
                commentAuthorId,
                postId,
                commentId,
                CommentUpdateException.Code.POST_NOT_FOUND
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 유효하지_않거나_없는_댓글은_수정할_수_없다(
            Long invalidCommentId
    ) {
        assertUpdateRejected(
                commentAuthorId,
                postId,
                invalidCommentId,
                CommentUpdateException.Code.COMMENT_NOT_FOUND
        );
    }

    @Test
    void 다른_게시글_ID로는_댓글을_수정할_수_없다() {
        // 댓글 작성자 본인이어도 게시글과 댓글의 조합이 맞아야 한다.
        assertUpdateRejected(
                commentAuthorId,
                otherPostId,
                commentId,
                CommentUpdateException.Code.COMMENT_NOT_FOUND
        );
    }

    @Test
    void 삭제된_댓글은_수정할_수_없다() {
        jdbcTemplate.update("""
                UPDATE community_comments
                SET deleted_at = ?
                WHERE id = ?
                """,
                Timestamp.from(UPDATED_TIME),
                commentId
        );

        assertUpdateRejected(
                commentAuthorId,
                postId,
                commentId,
                CommentUpdateException.Code.COMMENT_NOT_FOUND
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void 본문이_없거나_공백뿐이면_기존_댓글을_유지한다(
            String content
    ) {
        assertInvalidContentRejected(
                content,
                "댓글 내용은 필수입니다."
        );
    }

    @Test
    void 본문이_최대_길이를_초과하면_기존_댓글을_유지한다() {
        assertInvalidContentRejected(
                "가".repeat(2_001),
                "댓글 내용은 2,000자 이하여야 합니다."
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

    private void assertUpdateRejected(
            Long memberId,
            Long targetPostId,
            Long targetCommentId,
            CommentUpdateException.Code expectedCode
    ) {
        Map<String, Object> before = readComment();

        assertThatThrownBy(() ->
                commentUpdateService.update(
                        memberId,
                        targetPostId,
                        targetCommentId,
                        new CommentUpdateCommand("수정 시도")
                )
        ).isInstanceOfSatisfying(
                CommentUpdateException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );

        // 본문뿐 아니라 작성자, 시각, 삭제 상태도 유지되어야 한다.
        assertThat(readComment()).isEqualTo(before);
    }

    private void assertInvalidContentRejected(
            String content,
            String expectedMessage
    ) {
        Map<String, Object> before = readComment();

        assertThatThrownBy(() ->
                commentUpdateService.update(
                        commentAuthorId,
                        postId,
                        commentId,
                        new CommentUpdateCommand(content)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);

        assertThat(readComment()).isEqualTo(before);
    }
}