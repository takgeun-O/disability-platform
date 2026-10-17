package io.github.takgeun.iyum.community.comment.application;

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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Map;
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
class CommentCreateServiceTest {

    private static final Instant CREATED_TIME =
            Instant.parse("2026-10-17T03:00:00.123456Z");

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
    CommentCreateService commentCreateService;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Long postId;
    private Long commentAuthorId;
    private Long pendingMemberId;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_TIME));

        // 이 테스트 전용 컨테이너의 데이터만 초기화한다.
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member postAuthor = saveActiveMember(
                "post-author@example.com",
                "게시글작성자"
        );

        Member commentAuthor = saveActiveMember(
                "comment-author@example.com",
                "댓글작성자"
        );
        commentAuthorId = commentAuthor.getId();

        Member pendingMember = memberRepository.saveAndFlush(
                Member.createPending(
                        "pending-member@example.com",
                        "test-only-password-hash",
                        "인증대기회원"
                )
        );
        pendingMemberId = pendingMember.getId();

        Post post = postRepository.saveAndFlush(
                Post.create(
                        postAuthor,
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        "댓글 작성 대상",
                        "게시글 본문"
                )
        );
        postId = post.getId();
    }

    @Test
    void 활성_회원은_댓글을_저장하고_생성된_ID를_받는다() {
        String content = "  첫 번째 댓글\n다음 줄  ";

        assertThat(
                TransactionSynchronizationManager
                        .isActualTransactionActive()
        ).isFalse();

        CommentCreateResult result = commentCreateService.create(
                commentAuthorId,
                postId,
                new CommentCreateCommand(content)
        );

        // 서비스 트랜잭션이 끝난 뒤 DB에 저장된 결과를 확인한다.
        assertThat(
                TransactionSynchronizationManager
                        .isActualTransactionActive()
        ).isFalse();

        assertThat(result.commentId()).isNotNull();
        assertThat(result.commentId()).isPositive();
        assertThat(commentRepository.count()).isEqualTo(1L);

        // DB에서 조회한 한 행을 컬럼 이름 -> 값 형태로 받아옴
        // 저장 결과는 JdbcTemplate으로 직접 확인
        // 이렇게 하면 JPA 연관관계의 지연 로딩에 의존하지 않고 실제 저장된 게시글 ID·작성자ID·본문을 검증할 수 있음
        Map<String, Object> saved = jdbcTemplate.queryForMap("""
                SELECT id, post_id, author_id, content,
                       created_at, updated_at, deleted_at
                FROM community_comments
                WHERE id = ?
                """,
                result.commentId()
        );

        assertThat(saved.get("id")).isEqualTo(result.commentId());
        assertThat(saved.get("post_id")).isEqualTo(postId);

        // 게시글 작성자가 아닌, 전달한 댓글 작성자로 저장되어야 한다.
        assertThat(saved.get("author_id")).isEqualTo(commentAuthorId);

        // 앞뒤 공백과 줄바꿈이 그대로 저장되어야 한다.
        assertThat(saved.get("content")).isEqualTo(content);
        assertThat(saved.get("created_at")).isNotNull();
        assertThat(saved.get("updated_at")).isNotNull();
        assertThat(saved.get("deleted_at")).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 유효하지_않거나_없는_작성자는_댓글을_작성할_수_없다(
            Long invalidAuthorId
    ) {
        assertCreateRejected(
                invalidAuthorId,
                postId,
                CommentCreateException.Code.AUTHOR_NOT_FOUND
        );
    }

    @Test
    void 인증_대기_회원은_댓글을_작성할_수_없다() {
        assertCreateRejected(
                pendingMemberId,
                postId,
                CommentCreateException.Code.AUTHOR_NOT_ACTIVE
        );
    }

    @Test
    void 작성할_때_DB의_현재_회원_상태를_확인한다() {
        // 처음에는 ACTIVE로 저장한 회원의 DB 상태를 변경한다.
        jdbcTemplate.update("""
                UPDATE members
                SET status = 'PENDING'
                WHERE id = ?
                """,
                commentAuthorId
        );

        assertCreateRejected(
                commentAuthorId,
                postId,
                CommentCreateException.Code.AUTHOR_NOT_ACTIVE
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 유효하지_않거나_없는_게시글에는_작성할_수_없다(
            Long invalidPostId
    ) {
        assertCreateRejected(
                commentAuthorId,
                invalidPostId,
                CommentCreateException.Code.POST_NOT_FOUND
        );
    }

    @Test
    void 삭제된_게시글에는_댓글을_작성할_수_없다() {
        jdbcTemplate.update("""
                UPDATE community_posts
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                postId
        );

        assertCreateRejected(
                commentAuthorId,
                postId,
                CommentCreateException.Code.POST_NOT_FOUND
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void 본문이_없거나_공백뿐이면_저장하지_않는다(
            String content
    ) {
        assertThatThrownBy(() ->
                commentCreateService.create(
                        commentAuthorId,
                        postId,
                        new CommentCreateCommand(content)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("댓글 내용은 필수입니다.");

        assertThat(commentRepository.count()).isZero();
    }

    @Test
    void 본문이_최대_길이를_초과하면_저장하지_않는다() {
        String content = "가".repeat(2_001);

        assertThatThrownBy(() ->
                commentCreateService.create(
                        commentAuthorId,
                        postId,
                        new CommentCreateCommand(content)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("댓글 내용은 2,000자 이하여야 합니다.");

        assertThat(commentRepository.count()).isZero();
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

    private void assertCreateRejected(
            Long authorId,
            Long targetPostId,
            CommentCreateException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                commentCreateService.create(
                        authorId,
                        targetPostId,
                        new CommentCreateCommand("정상 댓글")
                )
        ).isInstanceOfSatisfying(
                CommentCreateException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );

        // 예외 발생뿐 아니라 댓글이 저장되지 않은 것도 확인한다.
        assertThat(commentRepository.count()).isZero();
    }
}