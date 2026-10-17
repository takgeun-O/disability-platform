package io.github.takgeun.iyum.community.comment.application;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class CommentReadServiceTest {

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
    CommentReadService commentReadService;

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
    private Long emptyPostId;
    private Long commentAuthorId;
    private Long firstCommentId;
    private Long secondCommentId;
    private Long thirdCommentId;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_TIME));

        // 이 테스트 전용 컨테이너의 데이터만 초기화한다.
        // 외래 키 때문에 댓글 → 게시글 → 회원 순서로 삭제한다.
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member postAuthor = saveMember(
                "post-author@example.com",
                "게시글작성자"
        );

        Member commentAuthor = saveMember(
                "comment-author@example.com",
                "댓글작성자"
        );
        commentAuthorId = commentAuthor.getId();

        Post post = savePost(postAuthor, "댓글 조회 대상");
        postId = post.getId();

        emptyPostId = savePost(
                postAuthor,
                "댓글 없는 게시글"
        ).getId();

        firstCommentId = saveComment(
                post,
                commentAuthor,
                "  첫 번째 댓글\n다음 줄  "
        );

        secondCommentId = saveComment(
                post,
                commentAuthor,
                "두 번째 댓글"
        );

        thirdCommentId = saveComment(
                post,
                commentAuthor,
                "세 번째 댓글"
        );
    }

    @Test
    void 첫_조회는_요청한_개수와_마지막_반환_댓글의_커서를_제공한다() {

        // 1. 서비스 호출 전에 트랜잭션이 없는지 확인한다.
        // 이 확인이 필요한 이유는 테스트 자체의 트랜잭션이 서비스를 감싸고 있으면 서비스 호출이 끝나도 트랜잭션이 계속 유지될 수 있기 떄문
        assertThat(
                TransactionSynchronizationManager
                        .isActualTransactionActive()
        ).isFalse();

        // 2. 처음 댓글 2개 요청한다.
        // 커서가 없으므로 첫 조회다. commentReadService.getComments()는 다음 댓글 존재 여부를 확인하려고 최대 3개를 조회하고,
        // 그 중 2개만 반환한다.
        // Spring Bean을 통해 호출했다면 서비스의 @Transactional(readOnly = true)가 적용되어
        // 조회와 DTO 변환이 트랜잭션 안에서 실행된다.
        CommentListResult result = commentReadService.getComments(
                postId, null, null, 2
        );

        // 3. 서비스 호출 후에도 트랜잭션이 없는지 확인한다.
        // 이후의 검증은 모두 서비스 트랜잭션 밖에서 DTO를 읽는 상황이 된다. (다만 이 검사 자체가 영속성 컨텍스트의 종료까지 직접 확인하지 않음)
        // CommentResult와 작성자 결과 객체에 필요한 값이 이미 복사해놓으므로, 컨트롤러에서도 추가 지연 로딩 없이 응답을 만들 수 있는 구조 검증
        assertThat(
                TransactionSynchronizationManager
                        .isActualTransactionActive()
        ).isFalse();

        // 4. 반환한 댓글의 개수와 순서를 확인한다.
        assertThat(result.items())
                .extracting(CommentResult::commentId)
                .containsExactly(firstCommentId, secondCommentId);

        // 5. 다음 댓글이 있고 커서가 만들어졌는지 확인한다.
        // 요청한 2개 외에 댓글이 더 있으므로 hasNext = true; nextCursor = 다음 조회에 사용할 위치
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();

        // 커서는 실제로 반환한 마지막 댓글인 두 번째 댓글을 기준으로 만들어야 한다.
        // 세 번째 댓글을 기준으로 만들면 다음 조회에서 세 번째 댓글을 건너뛰게 되기 때문
        assertThat(result.nextCursor().commentId())
                .isEqualTo(secondCommentId);
        assertThat(result.nextCursor().createdAt())
                .isEqualTo(CREATED_TIME);

        // 6. 댓글 내용과 작성자 정보, 시각이 정확히 복사됐는지 확인
        CommentResult first = result.items().getFirst();

        assertThat(first.content())
                .isEqualTo("  첫 번째 댓글\n다음 줄  ");
        assertThat(first.author().memberId())
                .isEqualTo(commentAuthorId);
        assertThat(first.author().nickname())
                .isEqualTo("댓글작성자");
        assertThat(first.createdAt()).isEqualTo(CREATED_TIME);
        assertThat(first.updatedAt()).isEqualTo(CREATED_TIME);
    }

    @Test
    void 반환받은_커서로_조회하면_다음_댓글부터_이어진다() {
        CommentListResult first = commentReadService.getComments(
                postId, null, null, 2
        );

        assertThat(first.nextCursor()).isNotNull();
        CommentCursor cursor = first.nextCursor();

        CommentListResult next = commentReadService.getComments(
                postId,
                cursor.createdAt(),
                cursor.commentId(),
                2
        );

        assertThat(next.items())
                .extracting(CommentResult::commentId)
                .containsExactly(thirdCommentId);

        assertThat(next.hasNext()).isFalse();
        assertThat(next.nextCursor()).isNull();
    }

    @Test
    void 남은_댓글이_요청_개수와_같으면_다음_구간이_없다() {
        CommentListResult result = commentReadService.getComments(
                postId, null, null, 3
        );

        assertThat(result.items())
                .extracting(CommentResult::commentId)
                .containsExactly(
                        firstCommentId,
                        secondCommentId,
                        thirdCommentId
                );

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 댓글이_없는_게시글도_정상적으로_조회한다() {
        CommentListResult result = commentReadService.getComments(
                emptyPostId, null, null, 20
        );

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 마지막_댓글_이후에는_빈_결과를_반환한다() {
        CommentListResult result = commentReadService.getComments(
                postId,
                CREATED_TIME,
                thirdCommentId,
                20
        );

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 최대_조회_개수인_오십_개를_허용한다() {
        CommentListResult result = commentReadService.getComments(
                postId, null, null, 50
        );

        assertThat(result.items()).hasSize(3);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 커서_댓글이_삭제돼도_다음_댓글을_조회한다() {
        CommentListResult first = commentReadService.getComments(
                postId, null, null, 2
        );

        assertThat(first.nextCursor()).isNotNull();
        CommentCursor cursor = first.nextCursor();

        jdbcTemplate.update("""
                UPDATE community_comments
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                cursor.commentId()
        );

        CommentListResult next = commentReadService.getComments(
                postId,
                cursor.createdAt(),
                cursor.commentId(),
                2
        );

        assertThat(next.items())
                .extracting(CommentResult::commentId)
                .containsExactly(thirdCommentId);

        assertThat(next.hasNext()).isFalse();
        assertThat(next.nextCursor()).isNull();
    }

    @Test
    void 삭제된_게시글은_빈_목록이_아닌_예외로_처리한다() {
        jdbcTemplate.update("""
                UPDATE community_posts
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                postId
        );

        assertPostNotFound(postId);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 조회할_수_없는_게시글_ID는_거부한다(Long invalidPostId) {
        assertPostNotFound(invalidPostId);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 51, Integer.MAX_VALUE})
    void 잘못된_조회_개수를_거부한다(int size) {
        assertThatThrownBy(() ->
                commentReadService.getComments(
                        postId, null, null, size
                )
        ).isInstanceOfSatisfying(
                CommentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                CommentReadException.Code
                                        .INVALID_PAGE_REQUEST
                        )
        );
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "NULL",
            value = {
                    "NULL, 1",
                    "2026-10-17T03:00:00Z, NULL",
                    "2026-10-17T03:00:00Z, 0",
                    "2026-10-17T03:00:00Z, -1"
            }
    )
    void 불완전하거나_잘못된_커서를_거부한다(
            String cursorTime,
            Long cursorId
    ) {
        Instant afterCreatedAt = cursorTime == null
                ? null
                : Instant.parse(cursorTime);

        assertThatThrownBy(() ->
                commentReadService.getComments(
                        postId,
                        afterCreatedAt,
                        cursorId,
                        20
                )
        ).isInstanceOfSatisfying(
                CommentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                CommentReadException.Code.INVALID_CURSOR
                        )
        );
    }

    private Member saveMember(String email, String nickname) {
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
                        PostTopic.HEARING_AID,
                        title,
                        "게시글 본문"
                )
        );
    }

    private Long saveComment(
            Post post,
            Member author,
            String content
    ) {
        return commentRepository.saveAndFlush(
                Comment.create(post, author, content)
        ).getId();
    }

    private void assertPostNotFound(Long targetPostId) {
        assertThatThrownBy(() ->
                commentReadService.getComments(
                        targetPostId, null, null, 20
                )
        ).isInstanceOfSatisfying(
                CommentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                CommentReadException.Code.POST_NOT_FOUND
                        )
        );
    }
}