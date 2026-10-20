package io.github.takgeun.iyum.member.application;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

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
class MemberActivitySummaryServiceTest {

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
    MemberActivitySummaryService memberActivitySummaryService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    private Member me;
    private Member other;

    @BeforeEach
    void setUp() {
        // 테스트 전용 컨테이너의 데이터만 초기화한다.
        // 외래 키 관계 때문에 댓글 → 게시글 → 회원 순서로 삭제한다.
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        me = saveActiveMember(
                "activity-me@example.com",
                "내활동회원"
        );

        other = saveActiveMember(
                "activity-other@example.com",
                "다른활동회원"
        );
    }

    @Test
    void 카테고리와_관계없이_내가_작성한_게시글과_댓글만_집계한다() {
        Post myDailyPost = savePost(
                me,
                PostTopic.DAILY,
                "내 일상 게시글"
        );

        savePost(
                me,
                PostTopic.HEARING_AID,
                "내 보청기 게시글"
        );

        Post otherPost = savePost(
                other,
                PostTopic.DAILY,
                "다른 회원 게시글"
        );

        // 내가 작성한 댓글은 게시글 작성자와 관계없이 집계한다.
        saveComment(myDailyPost, me, "내 게시글에 작성한 내 댓글");
        saveComment(otherPost, me, "다른 게시글에 작성한 내 댓글");
        saveComment(otherPost, me, "다른 게시글에 작성한 추가 댓글");

        // 내 게시글에 달렸더라도 다른 회원의 댓글은 제외한다.
        saveComment(myDailyPost, other, "다른 회원이 작성한 댓글");
        saveComment(otherPost, other, "다른 회원이 작성한 추가 댓글");

        MemberActivitySummaryResult result =
                memberActivitySummaryService.getSummary(me.getId());

        assertThat(result.postCount()).isEqualTo(2L);
        assertThat(result.commentCount()).isEqualTo(3L);
    }

    @Test
    void 내_활동이_없으면_두_개수_모두_영을_반환한다() {
        // 다른 회원의 활동이 있어도 내 집계는 0이어야 한다.
        Post otherPost = savePost(
                other,
                PostTopic.DAILY,
                "다른 회원 게시글"
        );
        saveComment(otherPost, other, "다른 회원 댓글");

        MemberActivitySummaryResult result =
                memberActivitySummaryService.getSummary(me.getId());

        assertThat(result.postCount()).isZero();
        assertThat(result.commentCount()).isZero();
    }

    @Test
    void 삭제된_내_게시글은_게시글_수에서_제외한다() {
        savePost(me, PostTopic.DAILY, "공개 중인 내 게시글");

        Post deletedPost = savePost(
                me,
                PostTopic.HEARING_AID,
                "삭제할 내 게시글"
        );

        deletedPost.delete(Instant.now());
        postRepository.saveAndFlush(deletedPost);

        MemberActivitySummaryResult result =
                memberActivitySummaryService.getSummary(me.getId());

        assertThat(result.postCount()).isEqualTo(1L);
        assertThat(result.commentCount()).isZero();
    }

    @Test
    void 삭제된_내_댓글은_댓글_수에서_제외한다() {
        Post post = savePost(
                other,
                PostTopic.DAILY,
                "댓글 작성 대상 게시글"
        );

        saveComment(post, me, "공개 중인 내 댓글");

        Comment deletedComment = saveComment(
                post,
                me,
                "삭제할 내 댓글"
        );

        deletedComment.delete(Instant.now());
        commentRepository.saveAndFlush(deletedComment);

        MemberActivitySummaryResult result =
                memberActivitySummaryService.getSummary(me.getId());

        assertThat(result.postCount()).isZero();
        assertThat(result.commentCount()).isEqualTo(1L);
    }

    @Test
    void 원본_게시글이_삭제되면_삭제되지_않은_내_댓글도_제외한다() {
        Post visiblePost = savePost(
                other,
                PostTopic.DAILY,
                "공개 중인 게시글"
        );

        Post deletedPost = savePost(
                other,
                PostTopic.HEARING_AID,
                "삭제할 게시글"
        );

        saveComment(visiblePost, me, "공개 게시글에 작성한 댓글");

        Comment hiddenComment = saveComment(
                deletedPost,
                me,
                "삭제될 게시글에 작성한 댓글"
        );

        // 댓글 자체는 삭제하지 않고 원본 게시글만 삭제한다.
        deletedPost.delete(Instant.now());
        postRepository.saveAndFlush(deletedPost);

        Comment persistedComment = commentRepository
                .findById(hiddenComment.getId())
                .orElseThrow();

        assertThat(persistedComment.getDeletedAt()).isNull();

        MemberActivitySummaryResult result =
                memberActivitySummaryService.getSummary(me.getId());

        assertThat(result.postCount()).isZero();
        assertThat(result.commentCount()).isEqualTo(1L);
    }

    @Test
    void 회원_ID가_null이면_거부한다() {
        assertReadFailure(
                null,
                MemberActivityReadException.Code.MEMBER_NOT_FOUND
        );
    }

    @Test
    void 존재하지_않는_회원이면_거부한다() {
        assertReadFailure(
                Long.MAX_VALUE,
                MemberActivityReadException.Code.MEMBER_NOT_FOUND
        );
    }

    @Test
    void 활성_상태가_아닌_회원이면_거부한다() {
        Member pendingMember = memberRepository.saveAndFlush(
                Member.createPending(
                        "activity-pending@example.com",
                        "test-only-password-hash",
                        "인증대기회원"
                )
        );

        assertReadFailure(
                pendingMember.getId(),
                MemberActivityReadException.Code.MEMBER_NOT_ACTIVE
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
                        "활동 요약 테스트용 게시글 본문"
                )
        );
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

    private void assertReadFailure(
            Long memberId,
            MemberActivityReadException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                memberActivitySummaryService.getSummary(memberId)
        ).isInstanceOfSatisfying(
                MemberActivityReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );
    }
}