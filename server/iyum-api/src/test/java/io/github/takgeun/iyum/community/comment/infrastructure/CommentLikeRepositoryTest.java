package io.github.takgeun.iyum.community.comment.infrastructure;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.domain.CommentLike;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.auditing.DateTimeProvider;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class CommentLikeRepositoryTest {

    private static final Instant CREATED_TIME =
            Instant.parse("2026-10-27T00:00:00Z");

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
    CommentLikeRepository commentLikeRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    EntityManager entityManager;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    @BeforeEach
    void setUpTime() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_TIME));
    }

    @Test
    void 본인_댓글의_좋아요를_저장하고_DB에서_다시_조회한다() {
        Member member = saveActiveMember("writer@example.com", "작성회원");
        Post post = savePost(member);
        Comment comment = saveComment(post, member, "내 댓글");

        Long commentId = comment.getId();
        Long memberId = member.getId();

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isZero();

        CommentLike saved = commentLikeRepository.saveAndFlush(
                CommentLike.create(comment, member)
        );
        Long likeId = saved.getId();

        entityManager.clear();

        CommentLike found = commentLikeRepository
                .findByComment_IdAndMember_Id(commentId, memberId)
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(likeId);
        assertThat(found.getComment().getId()).isEqualTo(commentId);
        assertThat(found.getMember().getId()).isEqualTo(memberId);
        assertThat(found.getCreatedAt()).isEqualTo(CREATED_TIME);

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);
    }

    @Test
    void 같은_회원의_같은_댓글_좋아요는_DB에서_중복을_거부한다() {
        Member member = saveActiveMember("duplicate@example.com", "중복회원");
        Post post = savePost(member);
        Comment comment = saveComment(post, member, "중복 검증 댓글");

        commentLikeRepository.saveAndFlush(
                CommentLike.create(comment, member)
        );

        assertThatThrownBy(() ->
                commentLikeRepository.saveAndFlush(
                        CommentLike.create(comment, member)
                )
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(
                        "uk_community_comment_likes_comment_member"
                );
    }

    @Test
    void 다른_회원이나_다른_댓글의_좋아요는_각각_저장한다() {
        Member first = saveActiveMember("first@example.com", "첫째회원");
        Member second = saveActiveMember("second@example.com", "둘째회원");

        Post post = savePost(first);
        Comment firstComment = saveComment(post, first, "첫 번째 댓글");
        Comment secondComment = saveComment(post, first, "두 번째 댓글");

        Long firstCommentId = firstComment.getId();
        Long secondCommentId = secondComment.getId();
        Long secondMemberId = second.getId();

        commentLikeRepository.saveAndFlush(
                CommentLike.create(firstComment, first)
        );
        commentLikeRepository.saveAndFlush(
                CommentLike.create(firstComment, second)
        );
        commentLikeRepository.saveAndFlush(
                CommentLike.create(secondComment, first)
        );

        entityManager.clear();

        assertThat(commentLikeRepository.countByComment_Id(firstCommentId))
                .isEqualTo(2L);
        assertThat(commentLikeRepository.countByComment_Id(secondCommentId))
                .isEqualTo(1L);

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        secondCommentId,
                        secondMemberId
                )
        ).isEmpty();
    }

    @Test
    void 좋아요를_취소해도_댓글과_회원은_남고_다시_등록할_수_있다() {
        Member member = saveActiveMember("cancel@example.com", "취소회원");
        Post post = savePost(member);
        Comment comment = saveComment(post, member, "취소 검증 댓글");

        Long commentId = comment.getId();
        Long memberId = member.getId();
        Long postId = post.getId();

        CommentLike saved = commentLikeRepository.saveAndFlush(
                CommentLike.create(comment, member)
        );
        Long previousLikeId = saved.getId();

        commentLikeRepository.delete(saved);
        commentLikeRepository.flush();
        entityManager.clear();

        assertThat(commentLikeRepository.findById(previousLikeId))
                .isEmpty();
        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId,
                        memberId
                )
        ).isEmpty();
        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isZero();

        // 좋아요만 삭제됐는지 확인
        Comment remainingComment = commentRepository.findById(commentId)
                .orElseThrow();
        Member remainingMember = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(postRepository.existsById(postId)).isTrue();

        Instant likedAgainAt = CREATED_TIME.plusSeconds(60);
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(likedAgainAt));

        CommentLike likedAgain = commentLikeRepository.saveAndFlush(
                CommentLike.create(remainingComment, remainingMember)
        );
        Long newLikeId = likedAgain.getId();

        entityManager.clear();

        CommentLike found = commentLikeRepository.findById(newLikeId)
                .orElseThrow();

        assertThat(found.getId()).isNotEqualTo(previousLikeId);
        assertThat(found.getCreatedAt()).isEqualTo(likedAgainAt);
        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);
    }

    @Test
    void 요청한_댓글의_좋아요_수와_내_좋아요_수를_함께_조회한다() {
        Member viewer = saveActiveMember(
                "summary-viewer@example.com",
                "조회회원"
        );

        Member other = saveActiveMember(
                "summary-other@example.com",
                "다른회원"
        );

        Post post = savePost(viewer);

        Comment first = saveComment(post, viewer, "첫 번째 댓글");
        Comment second = saveComment(post, other, "두 번째 댓글");
        Comment empty = saveComment(post, viewer, "좋아요 없는 댓글");
        Comment outside = saveComment(post, viewer, "조회 범위 밖 댓글");

        // 첫 번째 댓글: 전체 2개, 조회 회원의 좋아요 1개
        commentLikeRepository.save(CommentLike.create(first, viewer));
        commentLikeRepository.save(CommentLike.create(first, other));

        // 두 번째 댓글: 전체 1개, 조회 회원의 좋아요 0개
        commentLikeRepository.save(CommentLike.create(second, other));

        // 집계 대상 ID에 포함하지 않을 댓글
        commentLikeRepository.save(CommentLike.create(outside, viewer));

        commentLikeRepository.flush();
        entityManager.clear();

        List<CommentLikeSummary> results =
                commentLikeRepository.findSummariesByCommentIds(
                        List.of(
                                first.getId(),
                                second.getId(),
                                empty.getId()
                        ),
                        viewer.getId()
                );

        assertThat(results)
                .extracting(
                        CommentLikeSummary::getCommentId,
                        CommentLikeSummary::getLikeCount,
                        CommentLikeSummary::getViewerLikeCount
                )
                .containsExactlyInAnyOrder(
                        tuple(first.getId(), 2L, 1L),
                        tuple(second.getId(), 1L, 0L)
                );
    }

    @Test
    void 비로그인_조회는_전체_좋아요_수만_반환한다() {
        Member member = saveActiveMember(
                "summary-anonymous@example.com",
                "공개조회회원"
        );

        Post post = savePost(member);
        Comment comment = saveComment(post, member, "공개 조회 댓글");

        commentLikeRepository.saveAndFlush(
                CommentLike.create(comment, member)
        );

        entityManager.clear();

        List<CommentLikeSummary> results =
                commentLikeRepository.findSummariesByCommentIds(
                        List.of(comment.getId()),
                        null
                );

        assertThat(results)
                .extracting(
                        CommentLikeSummary::getCommentId,
                        CommentLikeSummary::getLikeCount,
                        CommentLikeSummary::getViewerLikeCount
                )
                .containsExactly(
                        tuple(comment.getId(), 1L, 0L)
                );
    }

    @Test
    void 삭제된_댓글과_삭제된_게시글의_댓글은_집계에서_제외한다() {
        Member member = saveActiveMember(
                "summary-deleted@example.com",
                "삭제집계회원"
        );

        Post visiblePost = savePost(member);
        Post deletedPost = savePost(member);

        Comment visibleComment =
                saveComment(visiblePost, member, "공개 댓글");

        Comment deletedComment =
                saveComment(visiblePost, member, "삭제할 댓글");

        Comment commentInDeletedPost =
                saveComment(deletedPost, member, "삭제된 게시글의 댓글");

        commentLikeRepository.save(
                CommentLike.create(visibleComment, member)
        );

        commentLikeRepository.save(
                CommentLike.create(deletedComment, member)
        );

        commentLikeRepository.save(
                CommentLike.create(commentInDeletedPost, member)
        );

        // Repository 테스트이므로 도메인 메서드로 삭제 상태를 준비한다.
        deletedComment.delete(CREATED_TIME.plusSeconds(60));
        deletedPost.delete(CREATED_TIME.plusSeconds(60));

        entityManager.flush();
        entityManager.clear();

        List<CommentLikeSummary> results =
                commentLikeRepository.findSummariesByCommentIds(
                        List.of(
                                visibleComment.getId(),
                                deletedComment.getId(),
                                commentInDeletedPost.getId()
                        ),
                        member.getId()
                );

        assertThat(results)
                .extracting(
                        CommentLikeSummary::getCommentId,
                        CommentLikeSummary::getLikeCount,
                        CommentLikeSummary::getViewerLikeCount
                )
                .containsExactly(
                        tuple(visibleComment.getId(), 1L, 1L)
                );

        // 제외된 이유가 좋아요 행의 물리 삭제 때문은 아닌지 확인한다.
        assertThat(
                commentLikeRepository.countByComment_Id(
                        deletedComment.getId()
                )
        ).isEqualTo(1L);

        assertThat(
                commentLikeRepository.countByComment_Id(
                        commentInDeletedPost.getId()
                )
        ).isEqualTo(1L);
    }

    @Test
    void 요청한_댓글에_좋아요가_없으면_빈_집계_결과를_반환한다() {
        Member member = saveActiveMember(
                "summary-empty@example.com",
                "빈집계회원"
        );

        Post post = savePost(member);
        Comment comment = saveComment(post, member, "좋아요 없는 댓글");

        entityManager.flush();
        entityManager.clear();

        List<CommentLikeSummary> results =
                commentLikeRepository.findSummariesByCommentIds(
                        List.of(comment.getId()),
                        member.getId()
                );

        assertThat(results).isEmpty();
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

    private Post savePost(Member author) {
        return postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        "댓글 좋아요 테스트",
                        "테스트 게시글 본문"
                )
        );
    }

    private Comment saveComment(Post post, Member author, String content) {
        return commentRepository.saveAndFlush(
                Comment.create(post, author, content)
        );
    }
}