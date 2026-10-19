package io.github.takgeun.iyum.member.infrastructure;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
class MemberCommentCursorRepositoryTest {

    private static final Instant BASE_TIME =
            Instant.parse("2026-10-19T10:00:00.123456Z");

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
    MemberRepository memberRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Member me;
    private Member emptyMember;

    private Post otherPost;

    private Comment newest;
    private Comment sameTimeLowerId;
    private Comment sameTimeHigherId;
    private Comment oldest;

    @BeforeEach
    void setUp() {
        // 테스트 전용 컨테이너의 데이터만 초기화한다.
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        me = saveMember(
                "my-comments@example.com",
                "내댓글회원"
        );

        Member other = saveMember(
                "other-comments@example.com",
                "다른댓글회원"
        );

        emptyMember = saveMember(
                "empty-comments@example.com",
                "댓글없는회원"
        );

        Post myPost = savePost(me, "내 게시글");

        otherPost = savePost(
                other,
                "다른 회원 게시글"
        );

        Post deletedPost = savePost(
                other,
                "삭제할 게시글"
        );

        // 최신 댓글을 먼저 저장한다.
        // ID만 내림차순으로 정렬하는 잘못된 구현도 잡아낸다.
        newest = saveComment(
                otherPost,
                me,
                "다른 게시글에 작성한 최신 내 댓글",
                BASE_TIME.plusSeconds(120)
        );

        oldest = saveComment(
                myPost,
                me,
                "가장 오래된 내 댓글",
                BASE_TIME
        );

        sameTimeLowerId = saveComment(
                otherPost,
                me,
                "같은 시각의 작은 ID 댓글",
                BASE_TIME.plusSeconds(60)
        );

        sameTimeHigherId = saveComment(
                myPost,
                me,
                "같은 시각의 큰 ID 댓글",
                BASE_TIME.plusSeconds(60)
        );

        // 내 게시글에 달렸어도 다른 회원의 댓글은 제외해야 한다.
        saveComment(
                myPost,
                other,
                "다른 회원이 작성한 댓글",
                BASE_TIME.plusSeconds(90)
        );

        Comment deletedComment = saveComment(
                otherPost,
                me,
                "삭제된 내 댓글",
                BASE_TIME.plusSeconds(80)
        );
        softDeleteComment(deletedComment.getId());

        // 댓글은 삭제하지 않고 원본 게시글만 삭제한다.
        saveComment(
                deletedPost,
                me,
                "삭제된 게시글에 달린 내 댓글",
                BASE_TIME.plusSeconds(70)
        );

        deletedPost.delete(Instant.now());
        postRepository.saveAndFlush(deletedPost);
    }

    @Test
    void 첫_조회는_게시글_작성자와_관계없이_내_공개_댓글만_최신순으로_반환한다() {
        List<Comment> comments =
                commentRepository.findFirstVisibleCommentsByAuthorId(
                        me.getId(),
                        PageRequest.of(0, 10)
                );

        assertThat(comments)
                .extracting(Comment::getId)
                .containsExactly(
                        newest.getId(),
                        sameTimeHigherId.getId(),
                        sameTimeLowerId.getId(),
                        oldest.getId()
                );
    }

    @Test
    void 첫_조회는_지정한_조회_개수를_넘지_않는다() {
        List<Comment> comments =
                commentRepository.findFirstVisibleCommentsByAuthorId(
                        me.getId(),
                        PageRequest.of(0, 2)
                );

        assertThat(comments)
                .extracting(Comment::getId)
                .containsExactly(
                        newest.getId(),
                        sameTimeHigherId.getId()
                );
    }

    @Test
    void 후속_조회에서도_다른_회원의_댓글과_삭제된_항목을_제외한다() {
        List<Comment> comments =
                commentRepository.findVisibleCommentsByAuthorIdAfter(
                        me.getId(),
                        newest.getCreatedAt(),
                        newest.getId(),
                        PageRequest.of(0, 10)
                );

        assertThat(comments)
                .extracting(Comment::getId)
                .containsExactly(
                        sameTimeHigherId.getId(),
                        sameTimeLowerId.getId(),
                        oldest.getId()
                );
    }

    @Test
    void 동일한_생성_시각의_경계에서도_중복이나_누락_없이_이어진다() {
        List<Comment> first =
                commentRepository.findFirstVisibleCommentsByAuthorId(
                        me.getId(),
                        PageRequest.of(0, 2)
                );

        assertThat(first)
                .extracting(Comment::getId)
                .containsExactly(
                        newest.getId(),
                        sameTimeHigherId.getId()
                );

        Comment cursorComment = first.getLast();

        List<Comment> next =
                commentRepository.findVisibleCommentsByAuthorIdAfter(
                        me.getId(),
                        cursorComment.getCreatedAt(),
                        cursorComment.getId(),
                        PageRequest.of(0, 2)
                );

        assertThat(next)
                .extracting(Comment::getId)
                .containsExactly(
                        sameTimeLowerId.getId(),
                        oldest.getId()
                );
    }

    @Test
    void 커서에_해당하는_댓글이_삭제되어도_다음_목록을_조회한다() {
        List<Comment> first =
                commentRepository.findFirstVisibleCommentsByAuthorId(
                        me.getId(),
                        PageRequest.of(0, 2)
                );

        Comment cursorComment = first.getLast();

        // 클라이언트가 커서를 받은 뒤 댓글이 삭제된 상황.
        softDeleteComment(cursorComment.getId());

        List<Comment> next =
                commentRepository.findVisibleCommentsByAuthorIdAfter(
                        me.getId(),
                        cursorComment.getCreatedAt(),
                        cursorComment.getId(),
                        PageRequest.of(0, 2)
                );

        assertThat(next)
                .extracting(Comment::getId)
                .containsExactly(
                        sameTimeLowerId.getId(),
                        oldest.getId()
                );
    }

    @Test
    void 마지막_댓글_이후에는_빈_목록을_반환한다() {
        List<Comment> comments =
                commentRepository.findVisibleCommentsByAuthorIdAfter(
                        me.getId(),
                        oldest.getCreatedAt(),
                        oldest.getId(),
                        PageRequest.of(0, 10)
                );

        assertThat(comments).isEmpty();
    }

    @Test
    void 작성한_댓글이_없는_회원은_빈_목록을_반환한다() {
        List<Comment> comments =
                commentRepository.findFirstVisibleCommentsByAuthorId(
                        emptyMember.getId(),
                        PageRequest.of(0, 10)
                );

        assertThat(comments).isEmpty();
    }

    @Test
    void 조회가_끝난_후에도_원본_게시글_정보를_읽을_수_있다() {
        List<Comment> comments =
                commentRepository.findFirstVisibleCommentsByAuthorId(
                        me.getId(),
                        PageRequest.of(0, 1)
                );

        // 현재 테스트를 실행하는 스레드에 활성 트랜잭션이 없어야 한다.
        // 테스트 자체에 트랜잭션이 걸려있으면 연관 데이터를 미리 가져오지 않았어도
        // 나중에 지연 로딩으로 추가 조회해서 테스트가 통과될 수 있기 때문임. (fetch join 검증 불가)
        assertThat(
                TransactionSynchronizationManager
                        .isActualTransactionActive()
        ).isFalse();

        assertThat(comments).hasSize(1);

        // fetch join으로 원본 게시글이 함께 로딩되어 있어야 한다.
        Post post = comments.getFirst().getPost();

        assertThat(post.getId()).isEqualTo(otherPost.getId());
        assertThat(post.getTitle()).isEqualTo("다른 회원 게시글");
        assertThat(post.getType()).isEqualTo(PostType.QUESTION);
        assertThat(post.getTopic()).isEqualTo(PostTopic.DAILY);
    }

    private Member saveMember(
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

    private Post savePost(Member author, String title) {
        return postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.QUESTION,
                        PostTopic.DAILY,
                        title,
                        "내 댓글 조회 테스트용 게시글 본문"
                )
        );
    }

    private Comment saveComment(
            Post post,
            Member author,
            String content,
            Instant createdAt
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
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                comment.getId()
        );

        assertThat(updatedRows).isEqualTo(1);

        // JDBC로 변경한 시각을 반영한 엔티티를 다시 읽는다.
        return commentRepository.findById(comment.getId())
                .orElseThrow();
    }

    private void softDeleteComment(Long commentId) {
        int updatedRows = jdbcTemplate.update("""
                UPDATE community_comments
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                commentId
        );

        assertThat(updatedRows).isEqualTo(1);
    }
}