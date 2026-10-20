package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.comment.application.CommentCursor;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;

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
class MemberCommentReadServiceTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-19T10:00:00.123456Z");

    private static final String FIRST_CONTENT =
            "  첫 번째 댓글\n다음 줄  ";

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
    MemberCommentReadService memberCommentReadService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Member me;
    private Member emptyMember;

    private Post myPost;
    private Post otherPost;

    private Comment firstComment;
    private Comment secondComment;
    private Comment thirdComment;

    @BeforeEach
    void setUp() {
        // 테스트 전용 컨테이너의 데이터만 초기화한다.
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        me = saveActiveMember(
                "my-comment-service@example.com",
                "내댓글회원"
        );

        Member other = saveActiveMember(
                "other-comment-service@example.com",
                "다른댓글회원"
        );

        emptyMember = saveActiveMember(
                "empty-comment-service@example.com",
                "댓글없는회원"
        );

        myPost = savePost(
                me,
                PostTopic.DAILY,
                "내 게시글"
        );

        otherPost = savePost(
                other,
                PostTopic.HEARING_AID,
                "다른 회원의 보청기 게시글"
        );

        Post deletedPost = savePost(
                other,
                PostTopic.DAILY,
                "삭제할 게시글"
        );

        // 생성 시각은 같고 ID 내림차순으로
        // first → second → third 순서가 된다.
        thirdComment = saveComment(
                otherPost,
                me,
                "세 번째 댓글"
        );

        secondComment = saveComment(
                myPost,
                me,
                "두 번째 댓글"
        );

        firstComment = saveComment(
                otherPost,
                me,
                FIRST_CONTENT
        );

        // 내 게시글에 달려도 다른 회원의 댓글은 제외해야 한다.
        saveComment(myPost, other, "다른 회원 댓글");

        Comment deletedComment = saveComment(
                otherPost,
                me,
                "삭제된 내 댓글"
        );
        deletedComment.delete(Instant.now());
        commentRepository.saveAndFlush(deletedComment);

        // 댓글 자체는 삭제하지 않고 원본 게시글만 삭제한다.
        saveComment(deletedPost, me, "삭제된 게시글의 내 댓글");

        deletedPost.delete(Instant.now());
        postRepository.saveAndFlush(deletedPost);
    }

    @Test
    void 첫_조회는_댓글과_원본_게시글_정보_및_다음_커서를_반환한다() {
        MemberCommentListResult result =
                memberCommentReadService.getComments(
                        me.getId(),
                        null,
                        null,
                        2
                );

        assertThat(result.items())
                .extracting(MemberCommentItemResult::commentId)
                .containsExactly(
                        firstComment.getId(),
                        secondComment.getId()
                );

        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();

        // 추가 조회한 세 번째 댓글이 아니라
        // 실제 반환한 두 번째 댓글을 커서로 사용해야 한다.
        assertThat(result.nextCursor().commentId())
                .isEqualTo(secondComment.getId());
        assertThat(result.nextCursor().createdAt())
                .isEqualTo(CREATED_AT);

        MemberCommentItemResult first = result.items().getFirst();

        // 댓글의 공백과 줄바꿈을 그대로 유지한다.
        assertThat(first.content()).isEqualTo(FIRST_CONTENT);
        assertThat(first.createdAt()).isEqualTo(CREATED_AT);
        assertThat(first.updatedAt()).isEqualTo(CREATED_AT);

        assertThat(first.post().postId())
                .isEqualTo(otherPost.getId());
        assertThat(first.post().title())
                .isEqualTo("다른 회원의 보청기 게시글");
        assertThat(first.post().type())
                .isEqualTo(PostType.QUESTION);
        assertThat(first.post().topic())
                .isEqualTo(PostTopic.HEARING_AID);

        // 두 번째 댓글에는 해당 댓글의 원본 게시글이 연결되어야 한다.
        MemberCommentItemResult second = result.items().get(1);

        assertThat(second.post().postId()).isEqualTo(myPost.getId());
        assertThat(second.post().title()).isEqualTo("내 게시글");
        assertThat(second.post().topic()).isEqualTo(PostTopic.DAILY);
    }

    @Test
    void 반환받은_커서로_조회하면_남은_댓글부터_이어진다() {
        MemberCommentListResult first =
                memberCommentReadService.getComments(
                        me.getId(), null, null, 2
                );

        assertThat(first.nextCursor()).isNotNull();
        CommentCursor cursor = first.nextCursor();

        MemberCommentListResult next =
                memberCommentReadService.getComments(
                        me.getId(),
                        cursor.createdAt(),
                        cursor.commentId(),
                        2
                );

        assertThat(next.items())
                .extracting(MemberCommentItemResult::commentId)
                .containsExactly(thirdComment.getId());

        assertThat(next.items().getFirst().post().postId())
                .isEqualTo(otherPost.getId());

        assertThat(next.hasNext()).isFalse();
        assertThat(next.nextCursor()).isNull();
    }

    @Test
    void 댓글_수가_요청_개수와_같으면_다음_커서가_없다() {
        MemberCommentListResult result =
                memberCommentReadService.getComments(
                        me.getId(), null, null, 3
                );

        assertThat(result.items())
                .extracting(MemberCommentItemResult::commentId)
                .containsExactly(
                        firstComment.getId(),
                        secondComment.getId(),
                        thirdComment.getId()
                );

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 댓글이_없는_회원은_빈_결과를_반환한다() {
        MemberCommentListResult result =
                memberCommentReadService.getComments(
                        emptyMember.getId(), null, null, 10
                );

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 마지막_댓글_이후에는_빈_결과를_반환한다() {
        MemberCommentListResult result =
                memberCommentReadService.getComments(
                        me.getId(),
                        CREATED_AT,
                        thirdComment.getId(),
                        2
                );

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 최대_조회_개수인_오십_개를_허용한다() {
        MemberCommentListResult result =
                memberCommentReadService.getComments(
                        me.getId(), null, null, 50
                );

        assertThat(result.items()).hasSize(3);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {Long.MAX_VALUE})
    void 회원을_확인할_수_없으면_거부한다(Long memberId) {
        assertFailure(
                memberId,
                null,
                null,
                10,
                MemberCommentReadException.Code.MEMBER_NOT_FOUND
        );
    }

    @Test
    void 활성_상태가_아닌_회원은_거부한다() {
        Member pendingMember = memberRepository.saveAndFlush(
                Member.createPending(
                        "pending-comment-service@example.com",
                        "test-only-password-hash",
                        "인증대기회원"
                )
        );

        assertFailure(
                pendingMember.getId(),
                null,
                null,
                10,
                MemberCommentReadException.Code.MEMBER_NOT_ACTIVE
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 51, Integer.MAX_VALUE})
    void 허용_범위를_벗어난_조회_개수는_거부한다(int size) {
        assertFailure(
                me.getId(),
                null,
                null,
                size,
                MemberCommentReadException.Code.INVALID_PAGE_REQUEST
        );
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "NULL",
            value = {
                    "NULL, 1",
                    "2026-10-19T10:00:00.123456Z, NULL",
                    "2026-10-19T10:00:00.123456Z, 0",
                    "2026-10-19T10:00:00.123456Z, -1"
            }
    )
    void 불완전하거나_잘못된_커서는_거부한다(
            String cursorTime,
            Long cursorId
    ) {
        Instant afterCreatedAt = cursorTime == null
                ? null
                : Instant.parse(cursorTime);

        assertFailure(
                me.getId(),
                afterCreatedAt,
                cursorId,
                10,
                MemberCommentReadException.Code.INVALID_CURSOR
        );
    }

    private Member saveActiveMember(
            String email,
            String nickname
    ) {
        Member member = Member.createPending(
                email,
                "test-only-password-hash",
                nickname
        );
        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private Post savePost(
            Member author,
            PostTopic topic,
            String title
    ) {
        return postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.QUESTION,
                        topic,
                        title,
                        "내 댓글 서비스 테스트용 게시글 본문"
                )
        );
    }

    private Comment saveComment(
            Post post,
            Member author,
            String content
    ) {
        Comment comment = commentRepository.saveAndFlush(
                Comment.create(post, author, content)
        );

        int updatedRows = jdbcTemplate.update("""
                UPDATE community_comments
                SET created_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                Timestamp.from(CREATED_AT),
                Timestamp.from(CREATED_AT),
                comment.getId()
        );

        assertThat(updatedRows).isEqualTo(1);

        return commentRepository.findById(comment.getId())
                .orElseThrow();
    }

    private void assertFailure(
            Long memberId,
            Instant afterCreatedAt,
            Long afterId,
            int size,
            MemberCommentReadException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                memberCommentReadService.getComments(
                        memberId,
                        afterCreatedAt,
                        afterId,
                        size
                )
        ).isInstanceOfSatisfying(
                MemberCommentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );
    }
}