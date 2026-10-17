package io.github.takgeun.iyum.community.post.application;

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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
class PostReadServiceTest {

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
    PostReadService postReadService;

    @Autowired
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    // 실제 Repository를 실행하면서 호출 횟수와 전달값도 확인하기 위해 사용
    // 집계 결과를 가짜로 지정하지 않고 실제 Testcontainers DB에서 조회할 것
    @MockitoSpyBean
    CommentRepository commentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long authorId;
    private Long firstQuestionId;
    private Long secondQuestionId;

    @BeforeEach
    void setUp() {
        // 테스트 전용 DB: 외래키 관계에 따라 댓글부터 삭제한다.
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member author = Member.createPending(
                "post-reader@example.com",
                "test-password-hash",
                "조회작성자"
        );

        author.activeAfterEmailVerification();

        author = memberRepository.saveAndFlush(author);
        authorId = author.getId();

        firstQuestionId = savePost(
                author,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "첫 번째 질문",
                "첫 번째 줄\n두 번째 줄"
        );

        secondQuestionId = savePost(
                author,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "두 번째 질문",
                "두 번째 질문의 본문"
        );

        savePost(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "일상 이야기",
                "일상 게시글의 본문"
        );
    }

    @Test
    void 목록을_결과_객체로_변환하고_페이지_정보를_유지한다() {
        Page<PostSummaryResult> result =
                postReadService.getPosts(
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        0,
                        1
                );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();

        PostSummaryResult summary =
                result.getContent().getFirst();

        assertThat(summary.postId())
                .isIn(firstQuestionId, secondQuestionId);

        assertThat(summary.type())
                .isEqualTo(PostType.QUESTION);

        assertThat(summary.topic())
                .isEqualTo(PostTopic.HEARING_AID);

        assertThat(summary.title())
                .isIn("첫 번째 질문", "두 번째 질문");

        assertThat(summary.author().memberId())
                .isEqualTo(authorId);

        assertThat(summary.author().nickname())
                .isEqualTo("조회작성자");

        assertThat(summary.createdAt()).isNotNull();
    }

    @Test
    void 필터를_생략하고_최대_페이지_크기로_조회할_수_있다() {
        Page<PostSummaryResult> result =
                postReadService.getPosts(
                        null,
                        null,
                        0,
                        50
                );

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getSize()).isEqualTo(50);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void 서비스_호출이_끝나도_상세_결과를_읽을_수_있다() {
        assertThat(
                TransactionSynchronizationManager
                        .isActualTransactionActive()
        ).isFalse();

        PostDetailResult result =
                postReadService.getPost(firstQuestionId);

        assertThat(
                TransactionSynchronizationManager
                        .isActualTransactionActive()
        ).isFalse();

        assertThat(result.postId())
                .isEqualTo(firstQuestionId);

        assertThat(result.type())
                .isEqualTo(PostType.QUESTION);

        assertThat(result.topic())
                .isEqualTo(PostTopic.HEARING_AID);

        assertThat(result.title())
                .isEqualTo("첫 번째 질문");

        assertThat(result.content())
                .isEqualTo("첫 번째 줄\n두 번째 줄");

        assertThat(result.author().memberId())
                .isEqualTo(authorId);

        assertThat(result.author().nickname())
                .isEqualTo("조회작성자");

        assertThat(result.createdAt()).isNotNull();
        assertThat(result.updatedAt()).isNotNull();
    }

    @Test
    void 삭제된_게시글은_목록과_상세에서_조회되지_않는다() {
        jdbcTemplate.update(
                """
                        UPDATE community_posts
                        SET deleted_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                firstQuestionId
        );

        // 물리적으로 삭제된 것은 아니다.
        assertThat(
                postRepository.existsById(firstQuestionId)
        ).isTrue();

        Page<PostSummaryResult> result =
                postReadService.getPosts(
                        null,
                        null,
                        0,
                        20
                );

        assertThat(result.getTotalElements()).isEqualTo(2);

        assertThat(result.getContent())
                .extracting(PostSummaryResult::postId)
                .doesNotContain(firstQuestionId);

        assertPostNotFound(firstQuestionId);
    }

    @ParameterizedTest
    @ValueSource(longs = {
            -1L,
            0L,
            Long.MAX_VALUE
    })
    void 조회할_수_없는_ID는_게시글_없음으로_처리한다(
            long postId
    ) {
        assertPostNotFound(postId);
    }

    @Test
    void 게시글_ID가_null이면_게시글_없음으로_처리한다() {
        assertPostNotFound(null);
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, -1",
            "0, 51",
            "2147483647, 2"
    })
    void 잘못된_페이지_조건을_거절한다(
            int page,
            int size
    ) {
        assertThatThrownBy(() ->
                postReadService.getPosts(
                        null,
                        null,
                        page,
                        size
                )
        ).isInstanceOfSatisfying(
                PostReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                PostReadException.Code
                                        .INVALID_PAGE_REQUEST
                        )
        );
    }

    @Test
    void 목록에_공개_댓글_수를_연결하고_댓글이_없으면_0을_반환한다() {
        Member author = memberRepository.findById(authorId)
                .orElseThrow();

        Post firstPost = postRepository.findById(firstQuestionId)
                .orElseThrow();

        commentRepository.saveAndFlush(
                Comment.create(firstPost, author, "공개 댓글 1")
        );
        commentRepository.saveAndFlush(
                Comment.create(firstPost, author, "공개 댓글 2")
        );

        Comment deleted = Comment.create(
                firstPost,
                author,
                "집계에서 제외할 댓글"
        );
        deleted.delete(Instant.now());
        commentRepository.saveAndFlush(deleted);

        // 준비 과정의 저장·삭제 호출 기록을 제외한다. (Mockito의 호출 기록만 초기화)
        // DB 데이터는 지우지 않음.
        clearInvocations(commentRepository);

        Page<PostSummaryResult> result =
                postReadService.getPosts(
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        0,
                        20
                );

        assertThat(result.getContent())
                .extracting(
                        PostSummaryResult::postId,
                        PostSummaryResult::commentCount
                )
                .containsExactlyInAnyOrder(
                        tuple(firstQuestionId, 2L),
                        tuple(secondQuestionId, 0L)
                );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();

        // 댓글 집계 메서드는 정확히 한번 호출
        verify(commentRepository, times(1))
                .countVisibleCommentsByPostIds(anyList());

        // 필터에 맞는 현재 페이지의 게시글 ID만 전달
        verify(commentRepository)
                .countVisibleCommentsByPostIds(argThat(ids ->
                        ids.size() == 2
                                && ids.contains(firstQuestionId)
                                && ids.contains(secondQuestionId)
                ));
    }

    @Test
    void 범위를_벗어난_빈_페이지는_댓글_집계를_생략하고_전체_개수를_유지한다() {
        clearInvocations(commentRepository);

        Page<PostSummaryResult> result =
                postReadService.getPosts(
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        10,
                        1
                );

        assertThat(result.getContent()).isEmpty();

        // 요청한 페이지는 비어도 조건에 맞는 게시글은 총 2개다.
        assertThat(result.getNumber()).isEqualTo(10);
        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isFalse();

        // 빈 ID 목록으로 집계 쿼리를 실행하지 않는다.
        verifyNoInteractions(commentRepository);
    }

    private Long savePost(
            Member author,
            PostType type,
            PostTopic topic,
            String title,
            String content
    ) {
        Post post = Post.create(
                author,
                type,
                topic,
                title,
                content
        );

        return postRepository.saveAndFlush(post).getId();
    }

    private void assertPostNotFound(Long postId) {
        assertThatThrownBy(() ->
                postReadService.getPost(postId)
        ).isInstanceOfSatisfying(
                PostReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                PostReadException.Code
                                        .POST_NOT_FOUND
                        )
        );
    }
}