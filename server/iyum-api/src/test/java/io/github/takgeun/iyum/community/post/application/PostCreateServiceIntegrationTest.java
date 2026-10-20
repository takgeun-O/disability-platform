package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.support.MemberStatusFixture;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
class PostCreateServiceIntegrationTest {

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
    PostCreateService postCreateService;

    @Autowired
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        // 이 테스트 전용 DB에서 게시글을 먼저 삭제한다.
        postRepository.deleteAllInBatch();

        // 게시글이 참조하던 회원을 나중에 삭제한다.
        memberRepository.deleteAllInBatch();
    }

    @Test
    void ACTIVE_회원은_게시글을_작성할_수_있다() {
        Member author = createActiveMember();

        String content = "  첫 번째 줄\n\n두 번째 줄  ";

        PostCreateResult result = postCreateService.create(
                author.getId(),
                command(
                        "  보청기 적응 질문입니다  ",
                        content
                )
        );

        assertThat(result.postId()).isNotNull();
        assertThat(postRepository.count()).isEqualTo(1);

        Post savedPost = postRepository
                .findById(result.postId())
                .orElseThrow();

        assertThat(savedPost.getType())
                .isEqualTo(PostType.QUESTION);

        assertThat(savedPost.getTopic())
                .isEqualTo(PostTopic.HEARING_AID);

        assertThat(savedPost.getTitle())
                .isEqualTo("보청기 적응 질문입니다");

        assertThat(savedPost.getContent())
                .isEqualTo(content);

        assertThat(savedPost.getCreatedAt()).isNotNull();
        assertThat(savedPost.getUpdatedAt()).isNotNull();
        assertThat(savedPost.getDeletedAt()).isNull();

        // 지연 로딩에 의존하지 않고 DB의 작성자 ID를 확인한다.
        Long savedAuthorId = jdbcTemplate.queryForObject(
                """
                SELECT author_id
                FROM community_posts
                WHERE id = ?
                """,
                Long.class,
                result.postId()
        );

        assertThat(savedAuthorId).isEqualTo(author.getId());
    }

    @ParameterizedTest
    // 상태별로 같은 테스트 반복 (ACTIVE를 제외한 다섯 상태를 차례로 전달해 작성이 거절되는지 확인)
    @EnumSource(
            value = MemberStatus.class,
            names = "ACTIVE",
            mode = EnumSource.Mode.EXCLUDE
    )
    void DB의_현재_상태가_ACTIVE가_아니면_작성을_거절한다(
            MemberStatus status
    ) {
        Member author = createActiveMember();

        // 이전에는 ACTIVE였지만 현재 DB 상태가 변경된 상황이다.
        MemberStatusFixture.setStatus(jdbcTemplate, author.getId(), status);

        assertThatThrownBy(() ->
                postCreateService.create(
                        author.getId(),
                        command("제목", "본문")
                )
        ).isInstanceOfSatisfying(
                PostCreateException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                PostCreateException.Code.AUTHOR_NOT_ACTIVE
                        )
        );

        assertThat(postRepository.count()).isZero();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = Long.MAX_VALUE)
    void 작성자_ID가_없거나_회원이_존재하지_않으면_거절한다(
            Long authorId
    ) {
        assertThatThrownBy(() ->
                postCreateService.create(
                        authorId,
                        command("제목", "본문")
                )
        ).isInstanceOfSatisfying(
                PostCreateException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                PostCreateException.Code.AUTHOR_NOT_FOUND
                        )
        );

        assertThat(postRepository.count()).isZero();
    }

    @Test
    void DTO_검증을_거치지_않아도_빈_제목은_저장되지_않는다() {
        Member author = createActiveMember();

        assertThatThrownBy(() ->
                postCreateService.create(
                        author.getId(),
                        command("   ", "본문")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("제목은 필수입니다.");

        assertThat(postRepository.count()).isZero();
    }

    @Test
    void DTO_검증을_거치지_않아도_공백_본문은_저장되지_않는다() {
        Member author = createActiveMember();

        assertThatThrownBy(() ->
                postCreateService.create(
                        author.getId(),
                        command("제목", "   ")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("본문은 필수입니다.");

        assertThat(postRepository.count()).isZero();
    }

    private Member createActiveMember() {
        Member member = Member.createPending(
                "post-writer@example.com",
                "test-only-password-hash",
                "게시글작성자"
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private PostCreateCommand command(
            String title,
            String content
    ) {
        return new PostCreateCommand(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                title,
                content
        );
    }
}