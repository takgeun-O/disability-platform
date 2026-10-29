package io.github.takgeun.iyum.member.api;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
class MemberPostSessionIntegrationTest {

    private static final String URL =
            "/api/v1/members/me/posts";

    private static final String PASSWORD =
            "MemberPostTest123!";

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-19T10:00:00.123456Z");

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
    ObjectMapper objectMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Member me;
    private Member other;

    private Post firstPost;
    private Post secondPost;
    private Post thirdPost;
    private Post otherPost;

    @BeforeEach
    void setUp() {
        // 테스트 전용 컨테이너의 데이터만 초기화한다.
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        String passwordHash = passwordEncoder.encode(PASSWORD);

        me = saveActiveMember(
                "my-post-session@example.com",
                "내게시글회원",
                passwordHash
        );

        other = saveActiveMember(
                "other-post-session@example.com",
                "다른게시글회원",
                passwordHash
        );

        // 생성 시각은 모두 같고, ID 내림차순으로 정렬된다.
        thirdPost = savePost(me, "세 번째 게시글");
        secondPost = savePost(me, "두 번째 게시글");
        firstPost = savePost(me, "첫 번째 게시글");

        otherPost = savePost(other, "다른 회원 게시글");

        Post deletedPost = savePost(me, "삭제된 내 게시글");
        deletedPost.delete(Instant.now());
        postRepository.saveAndFlush(deletedPost);

        // 첫 번째 게시글: 공개 댓글 2개, 삭제된 댓글 1개.
        saveComment(firstPost, me, "내 댓글");
        saveComment(firstPost, other, "다른 회원 댓글");

        Comment deletedComment = saveComment(
                firstPost,
                other,
                "삭제된 댓글"
        );
        deletedComment.delete(Instant.now());
        commentRepository.saveAndFlush(deletedComment);

        // 두 번째 게시글에는 댓글이 없다.
        saveComment(thirdPost, other, "세 번째 게시글의 댓글");
    }

    @Test
    void 실제_로그인_후_응답_커서로_다음_게시글을_조회한다()
            throws Exception {
        MockHttpSession session = login(me.getEmail());

        MvcResult firstResult = mockMvc.perform(get(URL)
                        .session(session)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id")
                        .value(is(firstPost.getId()), Long.class))
                .andExpect(jsonPath("$.items[1].id")
                        .value(is(secondPost.getId()), Long.class))
                .andExpect(jsonPath("$.items[0].author.memberId")
                        .value(is(me.getId()), Long.class))
                .andExpect(jsonPath("$.items[0].commentCount").value(2))
                .andExpect(jsonPath("$.items[1].commentCount").value(0))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor.createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.nextCursor.postId")
                        .value(is(secondPost.getId()), Long.class))
                .andReturn();

        // 실제 JSON 응답에서 커서를 꺼낸다.
        JsonNode firstBody = objectMapper.readTree(
                firstResult.getResponse().getContentAsString()
        );

        JsonNode cursor = firstBody.path("nextCursor");

        String afterCreatedAt = cursor
                .path("createdAt")
                .asString();

        long afterId = cursor
                .path("postId")
                .asLong();

        // 응답의 날짜 문자열을 가공하지 않고 그대로 전달한다.
        mockMvc.perform(get(URL)
                        .session(session)
                        .param("size", "2")
                        .param("afterCreatedAt", afterCreatedAt)
                        .param("afterId", Long.toString(afterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id")
                        .value(is(thirdPost.getId()), Long.class))
                .andExpect(jsonPath("$.items[0].author.memberId")
                        .value(is(me.getId()), Long.class))
                .andExpect(jsonPath("$.items[0].commentCount").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void 각_로그인_세션은_요청_회원_ID와_관계없이_자신의_게시글만_조회한다()
            throws Exception {
        MockHttpSession mySession = login(me.getEmail());
        MockHttpSession otherSession = login(other.getEmail());

        assertThat(mySession.getId())
                .isNotEqualTo(otherSession.getId());

        // 다른 회원 ID를 보내도 내 세션에서는 내 게시글만 반환한다.
        mockMvc.perform(get(URL)
                        .session(mySession)
                        .param("memberId", other.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].id")
                        .value(is(firstPost.getId()), Long.class))
                .andExpect(jsonPath("$.items[1].id")
                        .value(is(secondPost.getId()), Long.class))
                .andExpect(jsonPath("$.items[2].id")
                        .value(is(thirdPost.getId()), Long.class))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        // 다른 회원의 세션에서는 그 회원의 게시글만 반환한다.
        mockMvc.perform(get(URL)
                        .session(otherSession)
                        .param("memberId", me.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id")
                        .value(is(otherPost.getId()), Long.class))
                .andExpect(jsonPath("$.items[0].author.memberId")
                        .value(is(other.getId()), Long.class))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void 실제_세션으로_숨김_게시글을_조회하고_작성자만_삭제한다()
            throws Exception {
        String originalTitle = firstPost.getTitle();
        String originalContent = firstPost.getContent();

        firstPost.hide(Instant.now());
        postRepository.saveAndFlush(firstPost);

        Instant savedHiddenAt = postRepository
                .findById(firstPost.getId())
                .orElseThrow()
                .getHiddenAt();

        assertThat(savedHiddenAt).isNotNull();

        MockHttpSession authorSession = login(me.getEmail());

        MvcResult beforeResult = mockMvc.perform(
                        get(URL)
                                .session(authorSession)
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].id")
                        .value(is(firstPost.getId()), Long.class))
                .andExpect(jsonPath("$.items[0].visibility")
                        .value("HIDDEN"))
                .andExpect(jsonPath("$.items[0].createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.items[1].visibility")
                        .value("VISIBLE"))
                .andExpect(jsonPath("$.items[2].visibility")
                        .value("VISIBLE"))
                .andReturn();

        JsonNode hiddenItem = objectMapper.readTree(
                        beforeResult.getResponse().getContentAsByteArray()
                )
                .path("items")
                .path(0);

        // 필드가 생략되지 않고 명시적인 null로 반환되어야 한다.
        for (String field : List.of(
                "type",
                "topic",
                "title",
                "contentPreview",
                "author",
                "commentCount",
                "likeCount",
                "liked"
        )) {
            assertThat(hiddenItem.has(field)).isTrue();
            assertThat(hiddenItem.path(field).isNull()).isTrue();
        }

        // 실제 내 게시글 응답에서 얻은 ID를 사용한다.
        long responsePostId = hiddenItem.path("id").asLong();

        String deletePath = "/api/v1/community/posts/{postId}";

        // 다른 회원은 숨김 게시글을 삭제할 수 없다.
        MockHttpSession otherSession = login(other.getEmail());
        JsonNode otherCsrf = getCsrf(otherSession);

        mockMvc.perform(
                        delete(deletePath, responsePostId)
                                .session(otherSession)
                                .header(
                                        otherCsrf.path("headerName").asString(),
                                        otherCsrf.path("token").asString()
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        Post afterDenied = postRepository
                .findById(firstPost.getId())
                .orElseThrow();

        assertThat(afterDenied.getDeletedAt()).isNull();
        assertThat(afterDenied.getHiddenAt()).isEqualTo(savedHiddenAt);
        assertThat(afterDenied.getTitle()).isEqualTo(originalTitle);
        assertThat(afterDenied.getContent()).isEqualTo(originalContent);

        // 작성자는 숨김 게시글을 삭제할 수 있다.
        JsonNode authorCsrf = getCsrf(authorSession);

        mockMvc.perform(
                        delete(deletePath, responsePostId)
                                .session(authorSession)
                                .header(
                                        authorCsrf.path("headerName").asString(),
                                        authorCsrf.path("token").asString()
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        // 원본과 숨김 시각은 유지하고 삭제 시각만 기록한다.
        Post deleted = postRepository
                .findById(firstPost.getId())
                .orElseThrow();

        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getHiddenAt()).isEqualTo(savedHiddenAt);
        assertThat(deleted.getTitle()).isEqualTo(originalTitle);
        assertThat(deleted.getContent()).isEqualTo(originalContent);

        // 재조회하면 삭제한 게시글이 제외된다.
        mockMvc.perform(
                        get(URL)
                                .session(authorSession)
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id")
                        .value(is(secondPost.getId()), Long.class))
                .andExpect(jsonPath("$.items[1].id")
                        .value(is(thirdPost.getId()), Long.class))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        // 이미 삭제한 게시글에 다시 요청하면 404다.
        mockMvc.perform(
                        delete(deletePath, responsePostId)
                                .session(authorSession)
                                .header(
                                        authorCsrf.path("headerName").asString(),
                                        authorCsrf.path("token").asString()
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult csrfResult = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession csrfSession =
                (MockHttpSession) csrfResult
                        .getRequest()
                        .getSession(false);

        assertThat(csrfSession).isNotNull();

        JsonNode csrfBody = objectMapper.readTree(
                csrfResult.getResponse().getContentAsString()
        );

        String headerName = csrfBody
                .path("headerName")
                .asString();

        String token = csrfBody
                .path("token")
                .asString();

        assertThat(headerName).isEqualTo("X-CSRF-TOKEN");
        assertThat(token).isNotBlank();

        MvcResult loginResult = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .session(csrfSession)
                                .header(headerName, token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "email", email,
                                                "password", PASSWORD
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticatedSession =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        assertThat(authenticatedSession).isNotNull();

        return authenticatedSession;
    }

    private Member saveActiveMember(
            String email,
            String nickname,
            String passwordHash
    ) {
        Member member = Member.createPending(
                email,
                passwordHash,
                nickname
        );
        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private Post savePost(Member author, String title) {
        Post post = postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.QUESTION,
                        PostTopic.DAILY,
                        title,
                        "내 게시글 세션 통합 테스트 본문"
                )
        );

        int updatedRows = jdbcTemplate.update("""
                UPDATE community_posts
                SET created_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                Timestamp.from(CREATED_AT),
                Timestamp.from(CREATED_AT),
                post.getId()
        );

        assertThat(updatedRows).isEqualTo(1);

        return postRepository.findById(post.getId())
                .orElseThrow();
    }

    private Comment saveComment(
            Post post,
            Member author,
            String content
    ) {
        return commentRepository.saveAndFlush(
                Comment.create(post, author, content)
        );
    }

    // 로그인 후 CSRF 조회 메서드 추가
    private JsonNode getCsrf(MockHttpSession session)
            throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                                .session(session)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode csrf = objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );

        assertThat(csrf.path("headerName").asString()).isNotBlank();
        assertThat(csrf.path("token").asString()).isNotBlank();

        return csrf;
    }
}