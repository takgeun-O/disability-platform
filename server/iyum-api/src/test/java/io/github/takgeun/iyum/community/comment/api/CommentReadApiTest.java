package io.github.takgeun.iyum.community.comment.api;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 댓글 조회 API를 HTTP 요청 기준으로 검증
// 실제 어플리케이션의 설정과 빈을 로딩하고 MockMvc로 요청을 보낼 수 있게 준비
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@AutoConfigureMockMvc(addFilters = true)    // 필터도 테스트에 적용하겠다는 의미
@Testcontainers
class CommentReadApiTest {

    private static final String COMMENTS_URL =
            "/api/v1/community/posts/{postId}/comments";

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
    MockMvc mockMvc;

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

        // 테스트 전용 컨테이너의 데이터를 초기화한다.
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
    void 비로그인으로_댓글과_작성자_및_다음_커서를_조회한다()
            throws Exception {

        // 로그인 사용자·세션·CSRF 토큰을 추가하지 않는다.
        // 1. 해당 게시글의 댓글 조회 주소에 size=2로 GET 요청을 보낸다.
        // 2. HTTP 상태가 200인지 확인한다.
        // 3. 응답 JSON의 items에 댓글이 2개 들어 있는지 확인한다.
        mockMvc.perform(
                        get(COMMENTS_URL, postId)
                                .param("size", "2")
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.items.length()").value(2))   // $: JSON의 최상위 객체
                .andExpect(
                        jsonPath("$.items[0].commentId")
                                .value(is(firstCommentId), Long.class)
                )
                .andExpect(
                        jsonPath("$.items[1].commentId")
                                .value(is(secondCommentId), Long.class)
                )
                .andExpect(
                        jsonPath("$.items[0].content")
                                .value("  첫 번째 댓글\n다음 줄  ")
                )
                .andExpect(
                        jsonPath("$.items[0].author.memberId")
                                .value(is(commentAuthorId), Long.class)
                )
                .andExpect(
                        jsonPath("$.items[0].author.nickname")
                                .value("댓글작성자")
                )
                .andExpect(
                        jsonPath("$.items[0].createdAt")
                                .value(CREATED_TIME.toString())
                )
                .andExpect(
                        jsonPath("$.items[0].updatedAt")
                                .value(CREATED_TIME.toString())
                )
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(
                        jsonPath("$.nextCursor.commentId")  // 다음 조회용 커서의 댓글 ID
                                .value(is(secondCommentId), Long.class)
                )
                .andExpect(
                        jsonPath("$.nextCursor.createdAt")
                                .value(CREATED_TIME.toString())
                );
    }

    @Test
    void 응답의_커서를_그대로_전달하면_다음_댓글부터_조회한다()
            throws Exception {

        MvcResult first = mockMvc.perform(
                        get(COMMENTS_URL, postId)
                                .param("size", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn();

        String body = first.getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        // 서버가 실제로 반환한 JSON에서 커서를 꺼낸다.
        String cursorTime = JsonPath.read(
                body,
                "$.nextCursor.createdAt"
        );
        Number cursorId = JsonPath.read(
                body,
                "$.nextCursor.commentId"
        );

        mockMvc.perform(
                        get(COMMENTS_URL, postId)
                                .param("size", "2")
                                .param("afterCreatedAt", cursorTime)
                                .param("afterId", cursorId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(
                        jsonPath("$.items[0].commentId")
                                .value(is(thirdCommentId), Long.class)
                )
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").hasJsonPath())
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void 댓글_수가_조회_개수와_같으면_다음_커서는_null이다()
            throws Exception {

        mockMvc.perform(
                        get(COMMENTS_URL, postId)
                                .param("size", "3")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").hasJsonPath())
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void 댓글이_없는_게시글은_200과_빈_목록을_반환한다()
            throws Exception {

        mockMvc.perform(get(COMMENTS_URL, emptyPostId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").hasJsonPath())
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 조회할_수_없는_게시글은_404를_반환한다(
            long invalidPostId
    ) throws Exception {

        mockMvc.perform(get(COMMENTS_URL, invalidPostId))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code").value("POST_NOT_FOUND")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("게시글을 찾을 수 없습니다.")
                )
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void 삭제된_게시글은_404를_반환한다()
            throws Exception {

        jdbcTemplate.update("""
                UPDATE community_posts
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                postId
        );

        mockMvc.perform(get(COMMENTS_URL, postId))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code").value("POST_NOT_FOUND")
                );
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 51, Integer.MAX_VALUE})
    void 허용_범위를_벗어난_조회_개수는_400을_반환한다(
            int size
    ) throws Exception {

        mockMvc.perform(
                        get(COMMENTS_URL, postId)
                                .param("size", Integer.toString(size))
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_PAGE_REQUEST")
                );
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "NULL",
            value = {
                    "NULL, 1",
                    "2026-10-17T03:00:00.123456Z, NULL",
                    "2026-10-17T03:00:00.123456Z, 0",
                    "2026-10-17T03:00:00.123456Z, -1"
            }
    )
    void 불완전하거나_잘못된_커서는_400을_반환한다(
            String cursorTime,
            String cursorId
    ) throws Exception {

        MockHttpServletRequestBuilder request =
                get(COMMENTS_URL, postId);

        if (cursorTime != null) {
            request.param("afterCreatedAt", cursorTime);
        }

        if (cursorId != null) {
            request.param("afterId", cursorId);
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code").value("INVALID_CURSOR")
                );
    }

    @ParameterizedTest
    @CsvSource({
            "size, abc",
            "afterCreatedAt, not-a-date",
            "afterId, abc"
    })
    void 변환할_수_없는_파라미터는_400을_반환한다(
            String name,
            String value
    ) throws Exception {

        mockMvc.perform(
                        get(COMMENTS_URL, postId)
                                .param(name, value)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST_PARAMETER")
                );
    }

    @Test
    void 숫자가_아닌_게시글_ID는_400을_반환한다()
            throws Exception {

        mockMvc.perform(get(COMMENTS_URL, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST_PARAMETER")
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
}