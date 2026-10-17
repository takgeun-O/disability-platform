package io.github.takgeun.iyum.community.comment.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@AutoConfigureMockMvc(addFilters = true)
@Testcontainers
class CommentDeleteApiTest {

    private static final String COMMENT_URL =
            "/api/v1/community/posts/{postId}/comments/{commentId}";

    private static final String COMMENTS_URL =
            "/api/v1/community/posts/{postId}/comments";

    private static final String ORIGINAL_CONTENT = "삭제할 댓글";

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
    CommentRepository commentRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Post post;
    private Member postAuthor;
    private Member commentAuthor;

    private Long postId;
    private Long otherPostId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        // 이 테스트 전용 컨테이너의 데이터만 초기화한다.
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

        post = savePost(postAuthor, "댓글 삭제 대상 게시글");
        postId = post.getId();

        otherPostId = savePost(postAuthor, "다른 게시글").getId();

        commentId = commentRepository.saveAndFlush(
                Comment.create(post, commentAuthor, ORIGINAL_CONTENT)
        ).getId();
    }

    @Test
    void 본인_댓글을_삭제하면_204이며_DB_행은_유지된다()
            throws Exception {

        Map<String, Object> before = readComment();
        assertThat(before.get("deleted_at")).isNull();

        // 요청 본문과 Content-Type 없이 호출한다.
        mockMvc.perform(
                        authenticatedDelete(postId, commentId)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(
                        header().string(HttpHeaders.CACHE_CONTROL, "no-store")
                );

        Map<String, Object> saved = readComment();

        assertThat(commentRepository.count()).isEqualTo(1L);
        assertThat(saved.get("id")).isEqualTo(commentId);
        assertThat(saved.get("post_id")).isEqualTo(postId);
        assertThat(saved.get("author_id")).isEqualTo(commentAuthor.getId());
        assertThat(saved.get("content")).isEqualTo(ORIGINAL_CONTENT);
        assertThat(saved.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat(saved.get("updated_at")).isNotNull();
        assertThat(saved.get("deleted_at")).isInstanceOf(Timestamp.class);

        // 로그인 정보와 CSRF 없이 공개 목록을 조회한다.
        mockMvc.perform(
                        get(COMMENTS_URL, postId)
                                .param("size", "20")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void 삭제한_댓글만_목록에서_제외하고_다른_댓글은_유지한다()
            throws Exception {

        Long remainingCommentId = commentRepository.saveAndFlush(
                Comment.create(
                        post,
                        commentAuthor,
                        "남아 있어야 하는 댓글"
                )
        ).getId();

        mockMvc.perform(
                        authenticatedDelete(postId, commentId)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get(COMMENTS_URL, postId)
                                .param("size", "20")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(
                        jsonPath("$.items[0].commentId")
                                // equalTo(remainingCommentId) : 이 ID와 같은지 검사
                                // Long.class: JSON에서 읽은 값을 Long으로 변환한 뒤 비교
                                .value(equalTo(remainingCommentId), Long.class)
                )
                .andExpect(
                        jsonPath("$.items[0].content")
                                .value("남아 있어야 하는 댓글")
                )
                .andExpect(jsonPath("$.hasNext").value(false));

        assertThat(commentRepository.count()).isEqualTo(2L);

        assertThat(
                commentRepository.findById(remainingCommentId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isNull();
    }

    @Test
    void 이미_삭제한_댓글을_다시_삭제하면_404이다()
            throws Exception {

        mockMvc.perform(
                        authenticatedDelete(postId, commentId)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isNoContent());

        assertThat(readComment().get("deleted_at")).isNotNull();

        // 최초 삭제 시각을 포함한 기존 데이터가 유지되는지도 확인한다.
        assertRejected(
                authenticatedDelete(postId, commentId)
                        .with(csrf().asHeader()),
                404,
                "COMMENT_NOT_FOUND"
        );
    }

    @Test
    void 비로그인은_유효한_CSRF가_있어도_401이다()
            throws Exception {

        assertRejected(
                delete(COMMENT_URL, postId, commentId)
                        .with(csrf().asHeader()),
                401,
                "AUTHENTICATION_REQUIRED"
        );
    }

    @Test
    void 로그인했어도_CSRF가_없으면_403이다()
            throws Exception {

        assertRejected(
                authenticatedDelete(postId, commentId),
                403,
                "CSRF_TOKEN_INVALID"
        );
    }

    @Test
    void 잘못된_CSRF_토큰이면_403이다()
            throws Exception {

        assertRejected(
                authenticatedDelete(postId, commentId)
                        .with(csrf().asHeader().useInvalidToken()),
                403,
                "CSRF_TOKEN_INVALID"
        );
    }

    @Test
    void 게시글_작성자라도_다른_회원의_댓글을_삭제하면_403이다()
            throws Exception {

        assertRejected(
                authenticatedDelete(postAuthor, postId, commentId)
                        .with(csrf().asHeader()),
                403,
                "ACCESS_DENIED"
        );
    }

    @Test
    void 로그인_정보가_ACTIVE여도_DB가_PENDING이면_403이다()
            throws Exception {

        MockHttpServletRequestBuilder request =
                authenticatedDelete(postId, commentId)
                        .with(csrf().asHeader());

        jdbcTemplate.update("""
                UPDATE members
                SET status = 'PENDING'
                WHERE id = ?
                """,
                commentAuthor.getId()
        );

        assertRejected(request, 403, "COMMENT_WRITE_NOT_ALLOWED");
    }

    @Test
    void 로그인_정보가_있어도_DB_회원이_없으면_401이다()
            throws Exception {

        // 게시글·댓글에서 참조하지 않는 별도 회원을 사용한다.
        Member removedMember = saveActiveMember(
                "removed-member@example.com",
                "삭제대상회원"
        );

        MockHttpServletRequestBuilder request =
                authenticatedDelete(removedMember, postId, commentId)
                        .with(csrf().asHeader());

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
                authenticatedDelete(invalidPostId, commentId)
                        .with(csrf().asHeader()),
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
                authenticatedDelete(postId, invalidCommentId)
                        .with(csrf().asHeader()),
                404,
                "COMMENT_NOT_FOUND"
        );
    }

    @Test
    void 다른_게시글_ID로_삭제하면_404이다()
            throws Exception {

        assertRejected(
                authenticatedDelete(otherPostId, commentId)
                        .with(csrf().asHeader()),
                404,
                "COMMENT_NOT_FOUND"
        );
    }

    @Test
    void 삭제된_게시글의_댓글은_404이다()
            throws Exception {

        jdbcTemplate.update("""
                UPDATE community_posts
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                postId
        );

        assertRejected(
                authenticatedDelete(postId, commentId)
                        .with(csrf().asHeader()),
                404,
                "POST_NOT_FOUND"
        );
    }

    @Test
    void 게시글_ID가_숫자가_아니면_400이다()
            throws Exception {

        assertRejected(
                authenticatedDelete("abc", commentId)
                        .with(csrf().asHeader()),
                400,
                "INVALID_REQUEST_PARAMETER"
        );
    }

    @Test
    void 댓글_ID가_숫자가_아니면_400이다()
            throws Exception {

        assertRejected(
                authenticatedDelete(postId, "abc")
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

    private MockHttpServletRequestBuilder authenticatedDelete(
            Object targetPostId,
            Object targetCommentId
    ) {
        return authenticatedDelete(
                commentAuthor,
                targetPostId,
                targetCommentId
        );
    }

    private MockHttpServletRequestBuilder authenticatedDelete(
            Member member,
            Object targetPostId,
            Object targetCommentId
    ) {
        MemberPrincipal principal = new MemberPrincipal(
                member.getId(),
                member.getEmail(),
                member.getPasswordHash(),
                member.getNickname(),
                member.getStatus(),
                member.getRole()
        );

        return delete(COMMENT_URL, targetPostId, targetCommentId)
                .with(user(principal));
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

    private void assertRejected(
            MockHttpServletRequestBuilder request,
            int expectedStatus,
            String expectedCode
    ) throws Exception {

        Map<String, Object> before = readComment();
        long countBefore = commentRepository.count();

        mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.code").value(expectedCode));

        // 거절된 요청은 댓글의 내용·삭제 상태·시각을 바꾸면 안 된다.
        assertThat(readComment()).isEqualTo(before);
        assertThat(commentRepository.count()).isEqualTo(countBefore);
    }
}