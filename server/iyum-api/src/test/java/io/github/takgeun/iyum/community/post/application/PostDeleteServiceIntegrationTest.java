package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
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
class PostDeleteServiceIntegrationTest {

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
    PostDeleteService postDeleteService;

    @Autowired
    PostUpdateService postUpdateService;

    @Autowired
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long authorId;
    private Long postId;

    @BeforeEach
    void setUp() {
        // Testcontainers가 만든 이 테스트 전용 DB를 정리한다.
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member author = createActiveMember(
                "delete-writer@example.com",
                "삭제작성자"
        );

        authorId = author.getId();

        Post post = postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        "기존 제목",
                        "기존 본문"
                )
        );

        postId = post.getId();
    }

    @Test
    void ACTIVE_작성자는_게시글을_논리_삭제할_수_있다() {
        Instant createdAt = findPost().getCreatedAt();

        postDeleteService.delete(authorId, postId);

        Post deletedPost = findPost();

        // 행은 남아 있고 삭제 시각이 저장된다.
        assertThat(postRepository.count()).isEqualTo(1);
        assertThat(deletedPost.getDeletedAt()).isNotNull();

        assertThat(deletedPost.getId()).isEqualTo(postId);
        assertThat(deletedPost.getCreatedAt()).isEqualTo(createdAt);
        assertThat(deletedPost.getType()).isEqualTo(PostType.QUESTION);
        assertThat(deletedPost.getTopic()).isEqualTo(PostTopic.HEARING_AID);
        assertThat(deletedPost.getTitle()).isEqualTo("기존 제목");
        assertThat(deletedPost.getContent()).isEqualTo("기존 본문");

        // 지연 로딩에 의존하지 않고 작성자 ID를 확인한다.
        Long savedAuthorId = jdbcTemplate.queryForObject(
                "SELECT author_id FROM community_posts WHERE id = ?",
                Long.class,
                postId
        );

        assertThat(savedAuthorId).isEqualTo(authorId);
    }

    @Test
    void 다른_회원은_게시글을_삭제할_수_없다() {
        Member otherMember = createActiveMember(
                "other-member@example.com",
                "다른회원"
        );

        assertDeleteRejected(
                otherMember.getId(),
                postId,
                PostDeleteException.Code.NOT_POST_AUTHOR
        );

        assertPostNotDeleted();
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = "ACTIVE",
            mode = EnumSource.Mode.EXCLUDE
    )
    void DB의_현재_상태가_ACTIVE가_아니면_삭제를_거절한다(
            MemberStatus status
    ) {
        jdbcTemplate.update(
                "UPDATE members SET status = ? WHERE id = ?",
                status.name(),
                authorId
        );

        assertDeleteRejected(
                authorId,
                postId,
                PostDeleteException.Code.MEMBER_NOT_ACTIVE
        );

        assertPostNotDeleted();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1, 0, Long.MAX_VALUE})
    void 회원_ID가_잘못되었거나_회원이_없으면_삭제를_거절한다(
            Long invalidMemberId
    ) {
        assertDeleteRejected(
                invalidMemberId,
                postId,
                PostDeleteException.Code.MEMBER_NOT_FOUND
        );

        assertPostNotDeleted();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1, 0, Long.MAX_VALUE})
    void 게시글_ID가_잘못되었거나_게시글이_없으면_삭제를_거절한다(
            Long invalidPostId
    ) {
        assertDeleteRejected(
                authorId,
                invalidPostId,
                PostDeleteException.Code.POST_NOT_FOUND
        );

        assertPostNotDeleted();
    }

    @Test
    void 이미_삭제된_게시글은_다시_삭제할_수_없다() {
        postDeleteService.delete(authorId, postId);

        Instant firstDeletedAt = findPost().getDeletedAt();

        assertDeleteRejected(
                authorId,
                postId,
                PostDeleteException.Code.POST_NOT_FOUND
        );

        assertThat(findPost().getDeletedAt())
                .isEqualTo(firstDeletedAt);

        assertThat(postRepository.count()).isEqualTo(1);
    }

    @Test
    void 삭제된_게시글은_공개_목록과_상세에서_제외된다() {
        Member author = memberRepository.findById(authorId)
                .orElseThrow();

        Post visiblePost = postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        "남아 있는 글",
                        "남아 있는 본문"
                )
        );

        postDeleteService.delete(authorId, postId);

        assertThat(postRepository.findVisiblePostById(postId))
                .isEmpty();

        Page<Post> allPosts = postRepository.findVisiblePosts(
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertThat(allPosts.getContent())
                .extracting(Post::getId)
                .containsExactly(visiblePost.getId());

        assertThat(allPosts.getTotalElements()).isEqualTo(1);

        Page<Post> filteredPosts = postRepository.findVisiblePosts(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                PageRequest.of(0, 10)
        );

        assertThat(filteredPosts.getContent())
                .extracting(Post::getId)
                .containsExactly(visiblePost.getId());

        assertThat(filteredPosts.getTotalElements()).isEqualTo(1);

        // 실제 DB에는 두 게시글 모두 남아 있다.
        assertThat(postRepository.count()).isEqualTo(2);
    }

    @Test
    void 삭제된_게시글은_수정할_수_없다() {
        postDeleteService.delete(authorId, postId);

        Instant deletedAt = findPost().getDeletedAt();

        PostUpdateCommand command = new PostUpdateCommand(
                PostType.REVIEW,
                PostTopic.COCHLEAR_IMPLANT,
                "바뀐 제목",
                "바뀐 본문"
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

        Post post = findPost();

        assertThat(post.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(post.getType()).isEqualTo(PostType.QUESTION);
        assertThat(post.getTopic()).isEqualTo(PostTopic.HEARING_AID);
        assertThat(post.getTitle()).isEqualTo("기존 제목");
        assertThat(post.getContent()).isEqualTo("기존 본문");
    }

    private Member createActiveMember(
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

    private Post findPost() {
        return postRepository.findById(postId)
                .orElseThrow();
    }

    private void assertPostNotDeleted() {
        Post post = findPost();

        assertThat(post.getDeletedAt()).isNull();
        assertThat(post.getTitle()).isEqualTo("기존 제목");
        assertThat(post.getContent()).isEqualTo("기존 본문");
        assertThat(postRepository.count()).isEqualTo(1);
    }

    private void assertDeleteRejected(
            Long memberId,
            Long targetPostId,
            PostDeleteException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                postDeleteService.delete(memberId, targetPostId)
        ).isInstanceOfSatisfying(
                PostDeleteException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );
    }
}