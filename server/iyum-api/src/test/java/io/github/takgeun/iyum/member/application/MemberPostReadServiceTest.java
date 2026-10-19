package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.application.PostCursor;
import io.github.takgeun.iyum.community.post.application.PostFeedItemResult;
import io.github.takgeun.iyum.community.post.application.PostFeedResult;
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
class MemberPostReadServiceTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-19T10:00:00.123456Z");

    private static final String CONTENT =
            "내 게시글 조회 테스트 본문";

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
    MemberPostReadService memberPostReadService;

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

    private Post firstPost;
    private Post secondPost;
    private Post thirdPost;

    @BeforeEach
    void setUp() {
        // 테스트 전용 컨테이너의 데이터만 초기화한다.
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        me = saveActiveMember(
                "my-post-service@example.com",
                "내게시글회원"
        );

        Member other = saveActiveMember(
                "other-post-service@example.com",
                "다른게시글회원"
        );

        emptyMember = saveActiveMember(
                "empty-post-service@example.com",
                "게시글없는회원"
        );

        // 모든 게시글의 생성 시각을 같게 지정한다.
        // ID 내림차순으로 first → second → third 순서가 된다.
        thirdPost = savePost(
                me,
                PostTopic.DAILY,
                "세 번째 게시글"
        );

        secondPost = savePost(
                me,
                PostTopic.HEARING_AID,
                "두 번째 게시글"
        );

        firstPost = savePost(
                me,
                PostTopic.DAILY,
                "첫 번째 게시글"
        );

        // ID가 더 커도 다른 회원의 글은 제외되어야 한다.
        savePost(
                other,
                PostTopic.DAILY,
                "다른 회원 게시글"
        );

        // ID가 더 커도 삭제된 내 글은 제외되어야 한다.
        Post deletedPost = savePost(
                me,
                PostTopic.DAILY,
                "삭제된 내 게시글"
        );
        deletedPost.delete(Instant.now());
        postRepository.saveAndFlush(deletedPost);

        // 첫 번째 게시글: 공개 댓글 2개, 삭제된 댓글 1개.
        saveComment(firstPost, me, "내가 작성한 댓글");
        saveComment(firstPost, other, "다른 회원이 작성한 댓글");

        Comment deletedComment = saveComment(
                firstPost,
                other,
                "삭제된 댓글"
        );
        deletedComment.delete(Instant.now());
        commentRepository.saveAndFlush(deletedComment);

        // 두 번째 게시글: 댓글 없음.
        // 세 번째 게시글: 공개 댓글 1개.
        saveComment(thirdPost, other, "세 번째 게시글의 댓글");
    }

    @Test
    void 첫_조회는_요청한_개수와_댓글_수_및_다음_커서를_반환한다() {
        PostFeedResult result = memberPostReadService.getPosts(
                me.getId(),
                null,
                null,
                2
        );

        assertThat(result.items())
                .extracting(PostFeedItemResult::postId)
                .containsExactly(
                        firstPost.getId(),
                        secondPost.getId()
                );

        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();

        // 추가 조회한 세 번째 게시글이 아니라
        // 실제 반환한 두 번째 게시글이 커서여야 한다.
        assertThat(result.nextCursor().postId())
                .isEqualTo(secondPost.getId());
        assertThat(result.nextCursor().createdAt())
                .isEqualTo(CREATED_AT);

        PostFeedItemResult first = result.items().getFirst();

        assertThat(first.title()).isEqualTo("첫 번째 게시글");
        assertThat(first.contentPreview()).isEqualTo(CONTENT);
        assertThat(first.type()).isEqualTo(PostType.QUESTION);
        assertThat(first.topic()).isEqualTo(PostTopic.DAILY);
        assertThat(first.createdAt()).isEqualTo(CREATED_AT);
        assertThat(first.author().memberId()).isEqualTo(me.getId());
        assertThat(first.author().nickname()).isEqualTo("내게시글회원");

        // 작성자와 관계없이 공개 댓글 2개를 집계한다.
        // 삭제된 댓글은 포함하지 않는다.
        assertThat(first.commentCount()).isEqualTo(2L);

        // 댓글이 없는 게시글은 0으로 반환한다.
        assertThat(result.items().get(1).commentCount()).isZero();
    }

    @Test
    void 반환받은_커서로_조회하면_남은_게시글부터_이어진다() {
        PostFeedResult first = memberPostReadService.getPosts(
                me.getId(),
                null,
                null,
                2
        );

        assertThat(first.nextCursor()).isNotNull();
        PostCursor cursor = first.nextCursor();

        PostFeedResult next = memberPostReadService.getPosts(
                me.getId(),
                cursor.createdAt(),
                cursor.postId(),
                2
        );

        assertThat(next.items())
                .extracting(PostFeedItemResult::postId)
                .containsExactly(thirdPost.getId());

        assertThat(next.items().getFirst().commentCount())
                .isEqualTo(1L);

        assertThat(next.hasNext()).isFalse();
        assertThat(next.nextCursor()).isNull();
    }

    @Test
    void 게시글_수가_요청_개수와_같으면_다음_커서가_없다() {
        PostFeedResult result = memberPostReadService.getPosts(
                me.getId(),
                null,
                null,
                3
        );

        assertThat(result.items())
                .extracting(PostFeedItemResult::postId)
                .containsExactly(
                        firstPost.getId(),
                        secondPost.getId(),
                        thirdPost.getId()
                );

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 게시글이_없는_회원은_빈_결과를_반환한다() {
        PostFeedResult result = memberPostReadService.getPosts(
                emptyMember.getId(),
                null,
                null,
                10
        );

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 마지막_게시글_이후에는_빈_결과를_반환한다() {
        PostFeedResult result = memberPostReadService.getPosts(
                me.getId(),
                CREATED_AT,
                thirdPost.getId(),
                2
        );

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 최대_조회_개수인_오십_개를_허용한다() {
        PostFeedResult result = memberPostReadService.getPosts(
                me.getId(),
                null,
                null,
                50
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
                MemberPostReadException.Code.MEMBER_NOT_FOUND
        );
    }

    @Test
    void 활성_상태가_아닌_회원은_거부한다() {
        Member pendingMember = memberRepository.saveAndFlush(
                Member.createPending(
                        "pending-post-service@example.com",
                        "test-only-password-hash",
                        "인증대기회원"
                )
        );

        assertFailure(
                pendingMember.getId(),
                null,
                null,
                10,
                MemberPostReadException.Code.MEMBER_NOT_ACTIVE
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
                MemberPostReadException.Code.INVALID_PAGE_REQUEST
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
                MemberPostReadException.Code.INVALID_CURSOR
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
        Post post = postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.QUESTION,
                        topic,
                        title,
                        CONTENT
                )
        );

        int updatedRows = jdbcTemplate.update("""
                UPDATE community_posts
                SET created_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                Timestamp.from(CREATED_AT),
                Timestamp.from(CREATED_AT),
                post.getId()
        );

        assertThat(updatedRows).isEqualTo(1);

        return postRepository.findById(post.getId())
                .orElseThrow();
    }

    private Comment saveComment(
            Post post,
            Member author,
            String content
    ) {
        return commentRepository.saveAndFlush(
                Comment.create(post, author, content)
        );
    }

    private void assertFailure(
            Long memberId,
            Instant afterCreatedAt,
            Long afterId,
            int size,
            MemberPostReadException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                memberPostReadService.getPosts(
                        memberId,
                        afterCreatedAt,
                        afterId,
                        size
                )
        ).isInstanceOfSatisfying(
                MemberPostReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );
    }
}