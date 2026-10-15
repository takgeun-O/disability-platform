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
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
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
class PostUpdateServiceIntegrationTest {

    private static final Instant CREATED_TIME =
            Instant.parse("2026-10-15T03:00:00Z");

    private static final Instant UPDATED_TIME =
            Instant.parse("2026-10-15T04:00:00Z");

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
    PostUpdateService postUpdateService;

    @Autowired
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Long authorId;
    private Long postId;

    @BeforeEach
    void setUp() {
        // 이 테스트 전용 DB의 데이터를 초기화한다.
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_TIME));

        Member author = createActiveMember("writer");
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

        // 이후 발생하는 수정에는 다른 시각을 사용한다.
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(UPDATED_TIME));
    }

    @Test
    void 작성자는_게시글을_수정하고_변경사항을_DB에_저장한다() {
        String content = "  첫 번째 줄\n\n두 번째 줄  ";

        postUpdateService.update(
                authorId,
                postId,
                command("  수정한 제목  ", content)
        );

        Post found = findPost();

        assertThat(found.getId()).isEqualTo(postId);
        assertThat(found.getType()).isEqualTo(PostType.REVIEW);
        assertThat(found.getTopic())
                .isEqualTo(PostTopic.COCHLEAR_IMPLANT);
        assertThat(found.getTitle()).isEqualTo("수정한 제목");
        assertThat(found.getContent()).isEqualTo(content);

        assertThat(found.getCreatedAt()).isEqualTo(CREATED_TIME);
        assertThat(found.getUpdatedAt()).isEqualTo(UPDATED_TIME);
        assertThat(found.getDeletedAt()).isNull();

        assertThat(savedAuthorId()).isEqualTo(authorId);
        assertThat(postRepository.count()).isEqualTo(1);
    }

    @Test
    void 다른_회원은_게시글을_수정할_수_없다() {
        Member other = createActiveMember("other");

        assertUpdateRejected(
                other.getId(),
                postId,
                PostUpdateException.Code.NOT_POST_AUTHOR
        );

        assertOriginalPost();
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = "ACTIVE",
            mode = EnumSource.Mode.EXCLUDE
    )
    void DB의_현재_상태가_ACTIVE가_아니면_수정을_거절한다(
            MemberStatus status
    ) {
        jdbcTemplate.update(
                "UPDATE members SET status = ? WHERE id = ?",
                status.name(),
                authorId
        );

        assertUpdateRejected(
                authorId,
                postId,
                PostUpdateException.Code.MEMBER_NOT_ACTIVE
        );

        assertOriginalPost();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 회원_ID가_잘못되거나_회원이_없으면_거절한다(
            Long memberId
    ) {
        assertUpdateRejected(
                memberId,
                postId,
                PostUpdateException.Code.MEMBER_NOT_FOUND
        );

        assertOriginalPost();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 게시글_ID가_잘못되거나_게시글이_없으면_거절한다(
            Long targetPostId
    ) {
        assertUpdateRejected(
                authorId,
                targetPostId,
                PostUpdateException.Code.POST_NOT_FOUND
        );

        assertOriginalPost();
    }

    @Test
    void 삭제된_게시글은_작성자도_수정할_수_없다() {
        // 삭제 서비스는 아직 없으므로 테스트 데이터만 직접 변경한다.
        jdbcTemplate.update(
                """
                UPDATE community_posts
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                postId
        );

        assertUpdateRejected(
                authorId,
                postId,
                PostUpdateException.Code.POST_NOT_FOUND
        );

        assertOriginalPost();
        assertThat(findPost().getDeletedAt()).isNotNull();
    }

    @Test
    void DTO_검증을_거치지_않아도_빈_제목은_반영되지_않는다() {
        assertThatThrownBy(() ->
                postUpdateService.update(
                        authorId,
                        postId,
                        command("   ", "변경 본문")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("제목은 필수입니다.");

        assertOriginalPost();
    }

    @Test
    void DTO_검증을_거치지_않아도_빈_본문은_반영되지_않는다() {
        assertThatThrownBy(() ->
                postUpdateService.update(
                        authorId,
                        postId,
                        command("변경 제목", "   ")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("본문은 필수입니다.");

        assertOriginalPost();
    }

    private Member createActiveMember(String name) {
        Member member = Member.createPending(
                name + "@example.com",
                "test-only-password-hash",
                name
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private PostUpdateCommand command(
            String title,
            String content
    ) {
        return new PostUpdateCommand(
                PostType.REVIEW,
                PostTopic.COCHLEAR_IMPLANT,
                title,
                content
        );
    }

    private Post findPost() {
        return postRepository.findById(postId).orElseThrow();
    }

    private Long savedAuthorId() {
        return jdbcTemplate.queryForObject(
                """
                SELECT author_id
                FROM community_posts
                WHERE id = ?
                """,
                Long.class,
                postId
        );
    }

    private void assertOriginalPost() {
        Post found = findPost();

        assertThat(found.getType()).isEqualTo(PostType.QUESTION);
        assertThat(found.getTopic()).isEqualTo(PostTopic.HEARING_AID);
        assertThat(found.getTitle()).isEqualTo("기존 제목");
        assertThat(found.getContent()).isEqualTo("기존 본문");

        assertThat(found.getCreatedAt()).isEqualTo(CREATED_TIME);
        assertThat(found.getUpdatedAt()).isEqualTo(CREATED_TIME);
        assertThat(savedAuthorId()).isEqualTo(authorId);
    }

    private void assertUpdateRejected(
            Long memberId,
            Long targetPostId,
            PostUpdateException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                postUpdateService.update(
                        memberId,
                        targetPostId,
                        command("변경 제목", "변경 본문")
                )
        ).isInstanceOfSatisfying(
                PostUpdateException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );
    }
}