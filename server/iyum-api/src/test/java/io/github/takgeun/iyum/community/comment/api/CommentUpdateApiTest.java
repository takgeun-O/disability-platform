package io.github.takgeun.iyum.community.comment.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.comment.api.dto.CommentUpdateRequest;
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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@AutoConfigureMockMvc(addFilters = true)    // Spring Security 필터까지 포함 -> 로그인과 CSRF 오류도 검증 가능
@Testcontainers
class CommentUpdateApiTest {

    private static final String COMMENT_URL =
            "/api/v1/community/posts/{postId}/comments/{commentId}";

    private static final String ORIGINAL_CONTENT = "수정 전 댓글";

    private static final Instant CREATED_TIME =
            Instant.parse("2026-10-17T03:00:00.123456Z");

    private static final Instant UPDATED_TIME =
            Instant.parse("2026-10-17T04:00:00.123456Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

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
    private Long otherPostId;
    private Long commentId;
    private Member postAuthor;
    private Member commentAuthor;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_TIME));

        // 테스트 전용 컨테이너의 데이터만 초기화한다.
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        postAuthor = saveActiveMember(
                "post-author@example.com",
                "게시글작성자"
        );

        commentAuthor = saveActiveMember(
                "comment-author@example.com",
                "댓글작성자"
        );

        Post post = savePost(postAuthor, "댓글 수정 대상 게시글");
        postId = post.getId();

        otherPostId = savePost(postAuthor, "다른 게시글").getId();

        commentId = commentRepository.saveAndFlush(
                Comment.create(post, commentAuthor, ORIGINAL_CONTENT)
        ).getId();

        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(UPDATED_TIME));
    }

    @Test
    void 본인_댓글을_수정하면_204와_빈_본문을_반환하고_DB에_반영한다()
            throws Exception {

        String newContent = "  수정한 댓글\n두 번째 줄  ";

        mockMvc.perform(
                        authenticatedPut(
                                postId,
                                commentId,
                                jsonBody(newContent)
                        ).with(csrf().asHeader())
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(
                        header().string(HttpHeaders.CACHE_CONTROL, "no-store")
                );

        Map<String, Object> saved = readComment();

        assertThat(saved.get("id")).isEqualTo(commentId);
        assertThat(saved.get("post_id")).isEqualTo(postId);
        assertThat(saved.get("author_id")).isEqualTo(commentAuthor.getId());
        assertThat(saved.get("content")).isEqualTo(newContent);
        assertThat(saved.get("created_at"))
                .isEqualTo(Timestamp.from(CREATED_TIME));
        assertThat(saved.get("updated_at"))
                .isEqualTo(Timestamp.from(UPDATED_TIME));
        assertThat(saved.get("deleted_at")).isNull();

        assertThat(commentRepository.count()).isEqualTo(1L);
    }

    @Test
    void 동일한_본문도_204이며_기존_데이터를_유지한다()
            throws Exception {

        Map<String, Object> before = readComment();

        mockMvc.perform(
                        authenticatedPut(
                                postId,
                                commentId,
                                jsonBody(ORIGINAL_CONTENT)
                        ).with(csrf().asHeader())
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(readComment()).isEqualTo(before);
    }

    @Test
    void 비로그인은_유효한_CSRF가_있어도_401이다()
            throws Exception {

        assertRejected(
                put(COMMENT_URL, postId, commentId)
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody("수정 시도")),
                401,
                "AUTHENTICATION_REQUIRED"
        );
    }

    @Test
    void 로그인했어도_CSRF가_없으면_403이다()
            throws Exception {

        assertRejected(
                authenticatedPut(postId, commentId, jsonBody("수정 시도")),
                403,
                "CSRF_TOKEN_INVALID"
        );
    }

    @Test
    void 잘못된_CSRF_토큰이면_403이다()
            throws Exception {

        assertRejected(
                authenticatedPut(postId, commentId, jsonBody("수정 시도"))
                        .with(csrf().asHeader().useInvalidToken()),
                403,
                "CSRF_TOKEN_INVALID"
        );
    }

    @Test
    void 게시글_작성자라도_다른_회원의_댓글을_수정하면_403이다()
            throws Exception {

        assertRejected(
                authenticatedPut(
                        postAuthor,
                        postId,
                        commentId,
                        jsonBody("수정 시도")
                ).with(csrf().asHeader()),
                403,
                "ACCESS_DENIED"
        );
    }

    @Test
    void 로그인_정보가_ACTIVE여도_DB가_PENDING이면_401이다()
            throws Exception {

        MockHttpServletRequestBuilder request =
                authenticatedPut(postId, commentId, jsonBody("수정 시도"))
                        .with(csrf().asHeader());

        jdbcTemplate.update("""
                UPDATE members
                SET status = 'PENDING'
                WHERE id = ?
                """,
                commentAuthor.getId()
        );

        assertRejected(request, 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    void 로그인_정보가_있어도_DB_회원이_없으면_401이다()
            throws Exception {

        // 댓글 작성자는 FK로 참조되므로 삭제하지 않는다.
        // 게시글·댓글과 연결되지 않은 별도 회원을 사용한다.
        Member removedMember = saveActiveMember(
                "removed-member@example.com",
                "삭제대상회원"
        );

        MockHttpServletRequestBuilder request =
                authenticatedPut(
                        removedMember,
                        postId,
                        commentId,
                        jsonBody("수정 시도")
                ).with(csrf().asHeader());

        jdbcTemplate.update(
                "DELETE FROM members WHERE id = ?",
                removedMember.getId()
        );

        assertRejected(request, 401, "AUTHENTICATION_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 유효하지_않거나_없는_게시글은_404이다(
            long invalidPostId
    ) throws Exception {

        assertRejected(
                authenticatedPut(
                        invalidPostId,
                        commentId,
                        jsonBody("수정 시도")
                ).with(csrf().asHeader()),
                404,
                "POST_NOT_FOUND"
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 유효하지_않거나_없는_댓글은_404이다(
            long invalidCommentId
    ) throws Exception {

        assertRejected(
                authenticatedPut(
                        postId,
                        invalidCommentId,
                        jsonBody("수정 시도")
                ).with(csrf().asHeader()),
                404,
                "COMMENT_NOT_FOUND"
        );
    }

    @Test
    void 다른_게시글의_댓글이면_404이다()
            throws Exception {

        assertRejected(
                authenticatedPut(
                        otherPostId,
                        commentId,
                        jsonBody("수정 시도")
                ).with(csrf().asHeader()),
                404,
                "COMMENT_NOT_FOUND"
        );
    }

    @Test
    void 삭제된_게시글의_댓글은_404이다()
            throws Exception {

        jdbcTemplate.update("""
                UPDATE community_posts
                SET deleted_at = ?
                WHERE id = ?
                """,
                Timestamp.from(UPDATED_TIME),
                postId
        );

        assertRejected(
                authenticatedPut(postId, commentId, jsonBody("수정 시도"))
                        .with(csrf().asHeader()),
                404,
                "POST_NOT_FOUND"
        );
    }

    @Test
    void 삭제된_댓글은_404이다()
            throws Exception {

        jdbcTemplate.update("""
                UPDATE community_comments
                SET deleted_at = ?
                WHERE id = ?
                """,
                Timestamp.from(UPDATED_TIME),
                commentId
        );

        assertRejected(
                authenticatedPut(postId, commentId, jsonBody("수정 시도"))
                        .with(csrf().asHeader()),
                404,
                "COMMENT_NOT_FOUND"
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void 본문이_없거나_공백뿐이면_content_필드_오류와_400이다(
            String text
    ) throws Exception {

        assertValidationRejected(
                jsonBody(text),
                "댓글 내용은 필수입니다."
        );
    }

    @Test
    void 최대_길이를_초과하면_content_필드_오류와_400이다()
            throws Exception {

        assertValidationRejected(
                jsonBody("가".repeat(2_001)),
                "댓글 내용은 2,000자 이하여야 합니다."
        );
    }

    @Test
    void content_필드가_누락되어도_검증_오류로_처리한다()
            throws Exception {

        assertValidationRejected(
                "{}",
                "댓글 내용은 필수입니다."
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"{broken", "", "null"})
    void JSON이_잘못되거나_요청_본문이_없으면_400이다(
            String body
    ) throws Exception {

        assertRejected(
                authenticatedPut(postId, commentId, body)
                        .with(csrf().asHeader()),
                400,
                "INVALID_REQUEST_BODY"
        );
    }

    @Test
    void 게시글_ID가_숫자가_아니면_400이다()
            throws Exception {

        assertRejected(
                authenticatedPut("abc", commentId, jsonBody("수정 시도"))
                        .with(csrf().asHeader()),
                400,
                "INVALID_REQUEST_PARAMETER"
        );
    }

    @Test
    void 댓글_ID가_숫자가_아니면_400이다()
            throws Exception {

        assertRejected(
                authenticatedPut(postId, "abc", jsonBody("수정 시도"))
                        .with(csrf().asHeader()),
                400,
                "INVALID_REQUEST_PARAMETER"
        );
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

    private String jsonBody(String text) {
        return objectMapper.writeValueAsString(
                new CommentUpdateRequest(text)
        );
    }

    private MockHttpServletRequestBuilder authenticatedPut(
            Object targetPostId,
            Object targetCommentId,
            String body
    ) {
        return authenticatedPut(
                commentAuthor,
                targetPostId,
                targetCommentId,
                body
        );
    }

    private MockHttpServletRequestBuilder authenticatedPut(
            Member member,
            Object targetPostId,
            Object targetCommentId,
            String body
    ) {
        MemberPrincipal principal = new MemberPrincipal(
                member.getId(),
                member.getEmail(),
                member.getPasswordHash(),
                member.getNickname(),
                member.getStatus(),
                member.getRole(),
                member.getCredentialVersion()
        );

        return put(COMMENT_URL, targetPostId, targetCommentId)
                .with(user(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private Map<String, Object> readComment() {
        return jdbcTemplate.queryForMap("""
                SELECT id, post_id, author_id, content,
                       created_at, updated_at, deleted_at
                FROM community_comments
                WHERE id = ?
                """,
                commentId
        );
    }

    private ResultActions assertRejected(
            MockHttpServletRequestBuilder request,
            int expectedStatus,
            String expectedCode
    ) throws Exception {

        Map<String, Object> before = readComment();

        ResultActions result = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.code").value(expectedCode));

        // 요청 거절 후 기존 댓글의 모든 조회 컬럼이 유지되어야 한다.
        assertThat(readComment()).isEqualTo(before);
        assertThat(commentRepository.count()).isEqualTo(1L);

        return result;
    }

    private void assertValidationRejected(
            String body,
            String expectedMessage
    ) throws Exception {

        assertRejected(
                authenticatedPut(postId, commentId, body)
                        .with(csrf().asHeader()),
                400,
                "VALIDATION_FAILED"
        )
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(
                        jsonPath("$.fieldErrors[0].field").value("content")
                )
                .andExpect(
                        jsonPath("$.fieldErrors[0].message")
                                .value(expectedMessage)
                );
    }
}