package io.github.takgeun.iyum.community.post.infrastructure;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostLike;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
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
class PostLikeRepositoryTest {

    private static final Instant CREATED_TIME =
            Instant.parse("2026-10-25T03:00:00Z");

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
    PostLikeRepository postLikeRepository;

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
    void 본인_게시글의_좋아요를_저장하고_DB에서_다시_조회한다() {
        Member member = saveActiveMember(
                "writer@example.com",
                "작성회원"
        );
        Post post = savePost(member, "좋아요 테스트");

        assertThat(postLikeRepository.countByPost_Id(post.getId()))
                .isZero();

        PostLike saved = postLikeRepository.saveAndFlush(
                PostLike.create(post, member)
        );

        Long likeId = saved.getId();
        Long postId = post.getId();
        Long memberId = member.getId();

        entityManager.clear();

        PostLike found = postLikeRepository
                .findByPost_IdAndMember_Id(postId, memberId)
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(likeId);
        assertThat(found.getPost().getId()).isEqualTo(postId);
        assertThat(found.getMember().getId()).isEqualTo(memberId);
        assertThat(found.getCreatedAt()).isEqualTo(CREATED_TIME);

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(1L);
    }

    @Test
    void 같은_회원의_같은_게시글_좋아요는_DB에서_중복을_거부한다() {
        Member member = saveActiveMember(
                "duplicate@example.com",
                "중복회원"
        );
        Post post = savePost(member, "중복 테스트");

        postLikeRepository.saveAndFlush(
                PostLike.create(post, member)
        );

        assertThatThrownBy(() ->
                postLikeRepository.saveAndFlush(
                        PostLike.create(post, member)
                )
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(
                        "uk_community_post_likes_post_member"
                );
    }

    @Test
    void 다른_회원이나_다른_게시글의_좋아요는_각각_저장한다() {
        Member firstMember = saveActiveMember(
                "first@example.com",
                "첫째회원"
        );
        Member secondMember = saveActiveMember(
                "second@example.com",
                "둘째회원"
        );

        Post firstPost = savePost(firstMember, "첫 번째 게시글");
        Post secondPost = savePost(firstMember, "두 번째 게시글");

        postLikeRepository.saveAndFlush(
                PostLike.create(firstPost, firstMember)
        );
        postLikeRepository.saveAndFlush(
                PostLike.create(firstPost, secondMember)
        );
        postLikeRepository.saveAndFlush(
                PostLike.create(secondPost, firstMember)
        );

        entityManager.clear();

        assertThat(postLikeRepository.countByPost_Id(firstPost.getId()))
                .isEqualTo(2L);

        assertThat(postLikeRepository.countByPost_Id(secondPost.getId()))
                .isEqualTo(1L);

        assertThat(
                postLikeRepository.findByPost_IdAndMember_Id(
                        secondPost.getId(),
                        secondMember.getId()
                )
        ).isEmpty();
    }

    @Test
    void 좋아요를_취소하면_관계만_삭제되고_다시_등록할_수_있다() {
        Member member = saveActiveMember(
                "cancel@example.com",
                "취소회원"
        );
        Post post = savePost(member, "취소 테스트");

        PostLike saved = postLikeRepository.saveAndFlush(
                PostLike.create(post, member)
        );

        Long previousLikeId = saved.getId();
        Long postId = post.getId();
        Long memberId = member.getId();

        postLikeRepository.delete(saved);
        postLikeRepository.flush();
        entityManager.clear();

        assertThat(postLikeRepository.findById(previousLikeId))
                .isEmpty();

        assertThat(
                postLikeRepository.findByPost_IdAndMember_Id(
                        postId,
                        memberId
                )
        ).isEmpty();

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isZero();

        // 좋아요를 취소해도 게시글과 회원은 남아 있어야 한다.
        Post remainingPost = postRepository.findById(postId)
                .orElseThrow();
        Member remainingMember = memberRepository.findById(memberId)
                .orElseThrow();

        Instant likedAgainAt = CREATED_TIME.plusSeconds(60);

        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(likedAgainAt));

        PostLike likedAgain = postLikeRepository.saveAndFlush(
                PostLike.create(remainingPost, remainingMember)
        );

        Long newLikeId = likedAgain.getId();

        entityManager.clear();

        PostLike found = postLikeRepository.findById(newLikeId)
                .orElseThrow();

        assertThat(found.getId()).isNotEqualTo(previousLikeId);
        assertThat(found.getCreatedAt()).isEqualTo(likedAgainAt);

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(1L);
    }

    @Test
    void 요청한_게시글의_좋아요_수와_내_좋아요_여부를_함께_조회한다() {
        Member viewer = saveActiveMember(
                "summary-viewer@example.com",
                "조회회원"
        );

        Member other = saveActiveMember(
                "summary-other@example.com",
                "다른회원"
        );

        Post firstPost = savePost(viewer, "첫 번째 게시글");
        Post secondPost = savePost(viewer, "두 번째 게시글");
        Post emptyPost = savePost(viewer, "좋아요 없는 게시글");
        Post outsidePost = savePost(viewer, "조회 범위 밖 게시글");

        // 첫 번째 게시글: 총 2개, 조회 회원도 좋아요 등록
        postLikeRepository.saveAndFlush(
                PostLike.create(firstPost, viewer)
        );
        postLikeRepository.saveAndFlush(
                PostLike.create(firstPost, other)
        );

        // 두 번째 게시글: 총 1개, 조회 회원은 등록하지 않음
        postLikeRepository.saveAndFlush(
                PostLike.create(secondPost, other)
        );

        // 조회 대상에 포함하지 않을 게시글
        postLikeRepository.saveAndFlush(
                PostLike.create(outsidePost, viewer)
        );

        entityManager.clear();

        List<PostLikeSummary> results =
                postLikeRepository.findSummariesByPostIds(
                        List.of(
                                firstPost.getId(),
                                secondPost.getId(),
                                emptyPost.getId()
                        ),
                        viewer.getId()
                );

        assertThat(results)
                .extracting(
                        PostLikeSummary::getPostId,
                        PostLikeSummary::getLikeCount,
                        PostLikeSummary::getViewerLikeCount
                )
                .containsExactlyInAnyOrder(
                        tuple(firstPost.getId(), 2L, 1L),
                        tuple(secondPost.getId(), 1L, 0L)
                );
    }

    @Test
    void 비로그인_조회도_좋아요_수를_반환하지만_내_좋아요_수는_0이다() {
        Member member = saveActiveMember(
                "summary-anonymous@example.com",
                "공개조회회원"
        );

        Post post = savePost(member, "비로그인 조회 게시글");

        postLikeRepository.saveAndFlush(
                PostLike.create(post, member)
        );

        entityManager.clear();

        List<PostLikeSummary> results =
                postLikeRepository.findSummariesByPostIds(
                        List.of(post.getId()),
                        null
                );

        assertThat(results)
                .extracting(
                        PostLikeSummary::getPostId,
                        PostLikeSummary::getLikeCount,
                        PostLikeSummary::getViewerLikeCount
                )
                .containsExactly(
                        tuple(post.getId(), 1L, 0L)
                );
    }

    @Test
    void 좋아요가_남아_있어도_삭제된_게시글은_집계에서_제외한다() {
        Member member = saveActiveMember(
                "summary-deleted@example.com",
                "삭제집계회원"
        );

        Post visiblePost = savePost(member, "공개 게시글");
        Post deletedPost = savePost(member, "삭제할 게시글");

        postLikeRepository.saveAndFlush(
                PostLike.create(visiblePost, member)
        );

        PostLike deletedPostLike = postLikeRepository.saveAndFlush(
                PostLike.create(deletedPost, member)
        );

        Long remainingLikeId = deletedPostLike.getId();

        deletedPost.delete(CREATED_TIME.plusSeconds(60));

        entityManager.flush();
        entityManager.clear();

        List<PostLikeSummary> results =
                postLikeRepository.findSummariesByPostIds(
                        List.of(visiblePost.getId(), deletedPost.getId()),
                        member.getId()
                );

        assertThat(results)
                .extracting(
                        PostLikeSummary::getPostId,
                        PostLikeSummary::getLikeCount,
                        PostLikeSummary::getViewerLikeCount
                )
                .containsExactly(
                        tuple(visiblePost.getId(), 1L, 1L)
                );

        // 좋아요 행이 없어져서 제외된 것이 아니라,
        // 게시글의 삭제 상태를 확인해서 제외한 것이다.
        assertThat(postLikeRepository.existsById(remainingLikeId))
                .isTrue();
    }

    @Test
    void 요청한_게시글에_좋아요가_없으면_빈_집계_결과를_반환한다() {
        Member member = saveActiveMember(
                "summary-empty@example.com",
                "빈집계회원"
        );

        Post post = savePost(member, "좋아요 없는 게시글");

        entityManager.clear();

        List<PostLikeSummary> results =
                postLikeRepository.findSummariesByPostIds(
                        List.of(post.getId()),
                        member.getId()
                );

        assertThat(results).isEmpty();
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

    private Post savePost(Member author, String title) {
        return postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        title,
                        "테스트 본문"
                )
        );
    }
}