package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostLikeRepository;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class PostWriteVisibilityIntegrationTest {

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
    PostUpdateService postUpdateService;

    @Autowired
    PostLikeService postLikeService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    PostLikeRepository postLikeRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private Long authorId;
    private Long viewerId;
    private Long postId;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            Member author = createActiveMember();
            Member viewer = createActiveMember();

            Post post = postRepository.saveAndFlush(
                    Post.create(
                            author,
                            PostType.GENERAL,
                            PostTopic.DAILY,
                            "기존 제목",
                            "기존 본문"
                    )
            );

            authorId = author.getId();
            viewerId = viewer.getId();
            postId = post.getId();
        });
    }

    @Test
    void 작성자도_숨겨진_게시글을_수정할_수_없다() {
        hidePost();

        Map<String, Object> before = postSnapshot();

        PostUpdateCommand command = new PostUpdateCommand(
                PostType.GENERAL,
                PostTopic.DAILY,
                "변경 제목",
                "변경 본문"
        );

        assertThatThrownBy(() ->
                postUpdateService.update(
                        authorId,
                        postId,
                        command
                )
        ).isInstanceOfSatisfying(
                PostUpdateException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                PostUpdateException.Code.POST_NOT_FOUND
                        )
        );

        // 내용뿐 아니라 숨김·삭제 상태와 수정 시각도 유지된다.
        assertThat(postSnapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 숨겨진_게시글에는_좋아요_등록을_요청할_수_없다(
            boolean alreadyLiked
    ) {
        prepareLikeAndHide(alreadyLiked);

        Map<String, Object> postBefore = postSnapshot();
        Long likeIdBefore = findLikeId();

        assertThatThrownBy(() ->
                postLikeService.like(viewerId, postId)
        ).isInstanceOfSatisfying(
                PostLikeException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                PostLikeException.Code.POST_NOT_FOUND
                        )
        );

        assertLikeUnchanged(alreadyLiked, likeIdBefore);
        assertThat(postSnapshot()).isEqualTo(postBefore);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 숨겨진_게시글에는_좋아요_취소를_요청할_수_없다(
            boolean alreadyLiked
    ) {
        prepareLikeAndHide(alreadyLiked);

        Map<String, Object> postBefore = postSnapshot();
        Long likeIdBefore = findLikeId();

        assertThatThrownBy(() ->
                postLikeService.unlike(viewerId, postId)
        ).isInstanceOfSatisfying(
                PostLikeException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                PostLikeException.Code.POST_NOT_FOUND
                        )
        );

        assertLikeUnchanged(alreadyLiked, likeIdBefore);
        assertThat(postSnapshot()).isEqualTo(postBefore);
    }

    private Member createActiveMember() {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");

        Member member = Member.createPending(
                "write-" + unique + "@example.com",
                "test-only-password-hash",
                "회원" + unique.substring(0, 12)
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private void prepareLikeAndHide(boolean alreadyLiked) {
        // 공개 상태에서 실제 서비스를 통해 좋아요를 등록한다.
        if (alreadyLiked) {
            postLikeService.like(viewerId, postId);
        }

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(alreadyLiked ? 1L : 0L);

        hidePost();
    }

    private void hidePost() {
        jdbcTemplate.update(
                """
                UPDATE community_posts
                SET hidden_at = ?
                WHERE id = ?
                """,
                Timestamp.from(
                        Instant.parse("2026-10-30T00:00:00Z")
                ),
                postId
        );
    }

    private Long findLikeId() {
        return postLikeRepository
                .findByPost_IdAndMember_Id(postId, viewerId)
                .map(like -> like.getId())
                .orElse(null);
    }

    private void assertLikeUnchanged(
            boolean alreadyLiked,
            Long likeIdBefore
    ) {
        // 숨김 여부와 무관하게 실제 저장된 좋아요를 조회한다.
        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(alreadyLiked ? 1L : 0L);

        // 기존 좋아요가 삭제되거나 새 행으로 교체되지 않았는지 확인한다.
        assertThat(findLikeId()).isEqualTo(likeIdBefore);
    }

    private Map<String, Object> postSnapshot() {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM community_posts WHERE id = ?",
                postId
        );
    }
}