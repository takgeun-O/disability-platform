package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.domain.CommentLike;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentLikeRepository;
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
import java.util.ArrayList;
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
class MemberCommentSessionIntegrationTest {

    private static final String BASE =
            "/api/v1/members/me/comments";

    private static final String PASSWORD =
            "Test-password123!";

    private static final Instant SAME_TIME =
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
    CommentLikeRepository commentLikeRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Member member;
    private Member otherMember;

    private Post ownPost;
    private Post otherPost;

    private Long newestCommentId;
    private Long middleCommentId;
    private Long oldestCommentId;

    private Long otherMemberCommentId;
    private Long deletedCommentId;
    private Long deletedPostCommentId;

    @BeforeEach
    void setUp() {
        // 이 클래스의 테스트 전용 DB만 초기화한다.
        commentLikeRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        member = saveActiveMember(
                "member@example.com",
                "내댓글회원"
        );
        otherMember = saveActiveMember(
                "other@example.com",
                "다른댓글회원"
        );

        ownPost = savePost(member, "내가 작성한 게시글");
        otherPost = savePost(otherMember, "다른 회원의 게시글");

        // 오래된 댓글을 먼저 저장한다.
        oldestCommentId = saveComment(
                otherPost,
                member,
                "가장 오래된 내 댓글",
                SAME_TIME.minusSeconds(60)
        );

        // 아래 두 댓글은 시각이 같다.
        // 나중에 저장한 댓글의 ID가 더 크므로 먼저 조회되어야 한다.
        middleCommentId = saveComment(
                ownPost,
                member,
                "중간 내 댓글",
                SAME_TIME
        );
        newestCommentId = saveComment(
                otherPost,
                member,
                "  최신 내 댓글\n두 번째 줄  ",
                SAME_TIME
        );

        // 내 게시글에 달린 다른 사람의 댓글: 내 댓글 목록에서 제외.
        otherMemberCommentId = saveComment(
                ownPost,
                otherMember,
                "다른 회원이 작성한 댓글",
                SAME_TIME.plusSeconds(60)
        );

        // 삭제된 내 댓글: 목록에서 제외.
        deletedCommentId = saveComment(
                ownPost,
                member,
                "삭제된 내 댓글",
                SAME_TIME.plusSeconds(120)
        );

        Comment deletedComment = commentRepository
                .findById(deletedCommentId)
                .orElseThrow();
        deletedComment.delete(SAME_TIME.plusSeconds(300));
        commentRepository.saveAndFlush(deletedComment);

        // 댓글 자체는 남아 있지만 원본 게시글이 삭제된 경우: 제외.
        Post deletedPost = savePost(
                otherMember,
                "삭제된 원본 게시글"
        );
        deletedPostCommentId = saveComment(
                deletedPost,
                member,
                "삭제된 게시글에 작성한 내 댓글",
                SAME_TIME.plusSeconds(180)
        );

        deletedPost.delete(SAME_TIME.plusSeconds(300));
        postRepository.saveAndFlush(deletedPost);
    }

    @Test
    void 실제_로그인_후_응답_커서로_다음_내_댓글을_조회한다()
            throws Exception {

        // 최신 댓글: 본인과 다른 회원이 눌러 총 2개
        saveCommentLike(newestCommentId, member);
        saveCommentLike(newestCommentId, otherMember);

        // 중간 댓글: 좋아요 없음

        // 가장 오래된 댓글: 다른 회원이 눌러 총 1개
        saveCommentLike(oldestCommentId, otherMember);

        MockHttpSession session = login(member.getEmail());
        // GET 조회에는 CSRF 토큰을 전달하지 않는다.
        MvcResult firstResult = mockMvc.perform(get(BASE)
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
                .andExpect(jsonPath("$.items[0].commentId")
                        .value(is(newestCommentId), Long.class))
                .andExpect(jsonPath("$.items[0].content")
                        .value("  최신 내 댓글\n두 번째 줄  "))
                .andExpect(jsonPath("$.items[0].post.postId")
                        .value(is(otherPost.getId()), Long.class))
                .andExpect(jsonPath("$.items[0].post.title")
                        .value("다른 회원의 게시글"))
                .andExpect(jsonPath("$.items[0].post.type")
                        .value("QUESTION"))
                .andExpect(jsonPath("$.items[0].post.topic")
                        .value("HEARING_AID"))
                .andExpect(jsonPath("$.items[1].commentId")
                        .value(is(middleCommentId), Long.class))
                .andExpect(jsonPath("$.items[1].post.postId")
                        .value(is(ownPost.getId()), Long.class))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor.createdAt")
                        .value(SAME_TIME.toString()))
                .andExpect(jsonPath("$.nextCursor.commentId")
                        .value(is(middleCommentId), Long.class))
                .andExpect(jsonPath("$.items[0].likeCount").value(2))
                .andExpect(jsonPath("$.items[1].likeCount").value(0))
                .andExpect(jsonPath("$.items[0].liked").value(true))
                .andExpect(jsonPath("$.items[1].liked").value(false))
                .andReturn();

        JsonNode first = readJson(firstResult);

        // 미리 지정한 값 대신 실제 응답의 커서를 사용한다.
        String afterCreatedAt = first.path("nextCursor")
                .path("createdAt")
                .asString();

        long afterId = first.path("nextCursor")
                .path("commentId")
                .asLong();

        MvcResult secondResult = mockMvc.perform(get(BASE)
                        .session(session)
                        .param("size", "2")
                        .param("afterCreatedAt", afterCreatedAt)
                        .param("afterId", Long.toString(afterId)))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].commentId")
                        .value(is(oldestCommentId), Long.class))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.items[0].liked").value(false))
                .andReturn();

        JsonNode second = readJson(secondResult);

        List<Long> allIds = new ArrayList<>(commentIds(first));
        allIds.addAll(commentIds(second));

        assertThat(allIds)
                .containsExactly(
                        newestCommentId,
                        middleCommentId,
                        oldestCommentId
                )
                .doesNotHaveDuplicates();

        assertThat(allIds).doesNotContain(
                otherMemberCommentId,
                deletedCommentId,
                deletedPostCommentId
        );
    }

    @Test
    void 서로_다른_로그인_세션은_각자_본인의_댓글만_조회한다()
            throws Exception {
        MockHttpSession memberSession =
                login(member.getEmail());

        MockHttpSession otherSession =
                login(otherMember.getEmail());

        assertThat(memberSession.getId())
                .isNotEqualTo(otherSession.getId());

        // 다른 회원 ID를 넣어도 로그인한 회원의 댓글만 조회한다.
        MvcResult memberResult = mockMvc.perform(get(BASE)
                        .session(memberSession)
                        .param(
                                "memberId",
                                otherMember.getId().toString()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andReturn();

        assertThat(commentIds(readJson(memberResult)))
                .containsExactly(
                        newestCommentId,
                        middleCommentId,
                        oldestCommentId
                );

        MvcResult otherResult = mockMvc.perform(get(BASE)
                        .session(otherSession)
                        .param("memberId", member.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andReturn();

        assertThat(commentIds(readJson(otherResult)))
                .containsExactly(otherMemberCommentId);

        // 앞에서 로그인했어도 세션을 전달하지 않는 요청은 비로그인이다.
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));
    }

    @ParameterizedTest
    @CsvSource({
            "false, true, HIDDEN",
            "true, false, PARENT_HIDDEN",
            "true, true, PARENT_HIDDEN"
    })
    void 실제_세션으로_숨김_댓글을_조회하고_작성자만_삭제한다(
            boolean postHidden,
            boolean commentHidden,
            String expectedVisibility
    ) throws Exception {
        Comment target = commentRepository
                .findById(newestCommentId)
                .orElseThrow();

        String originalContent = target.getContent();

        if (commentHidden) {
            target.hide(Instant.now());
            commentRepository.saveAndFlush(target);
        }

        if (postHidden) {
            otherPost.hide(Instant.now());
            postRepository.saveAndFlush(otherPost);
        }

        MockHttpSession authorSession = login(member.getEmail());

        // 실제 로그인 세션으로 내 댓글을 조회한다.
        MvcResult beforeResult = mockMvc.perform(
                        get(BASE)
                                .session(authorSession)
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].commentId")
                        .value(is(newestCommentId), Long.class))
                .andExpect(jsonPath("$.items[0].createdAt")
                        .value(SAME_TIME.toString()))
                .andExpect(jsonPath("$.items[0].visibility")
                        .value(expectedVisibility))
                .andExpect(jsonPath("$.items[0].post.postId")
                        .value(is(otherPost.getId()), Long.class))
                .andReturn();

        JsonNode hiddenItem = readJson(beforeResult)
                .path("items")
                .path(0);

        // 누락된 필드가 아니라 명시적인 null인지 확인한다.
        for (String field : List.of(
                "content", "updatedAt", "likeCount", "liked"
        )) {
            assertThat(hiddenItem.has(field)).isTrue();
            assertThat(hiddenItem.path(field).isNull()).isTrue();
        }

        JsonNode parent = hiddenItem.path("post");

        for (String field : List.of("title", "type", "topic")) {
            assertThat(parent.has(field)).isTrue();
            assertThat(parent.path(field).isNull()).isTrue();
        }

        // 미리 지정한 ID 대신 실제 응답에서 받은 ID로 삭제한다.
        long responsePostId = parent.path("postId").asLong();
        long responseCommentId = hiddenItem.path("commentId").asLong();

        String deletePath =
                "/api/v1/community/posts/{postId}/comments/{commentId}";

        // 게시글 작성자라도 다른 회원의 댓글을 삭제할 수 없다.
        MockHttpSession postAuthorSession =
                login(otherMember.getEmail());
        JsonNode postAuthorCsrf = getCsrf(postAuthorSession);

        mockMvc.perform(
                        delete(deletePath, responsePostId, responseCommentId)
                                .session(postAuthorSession)
                                .header(
                                        postAuthorCsrf.path("headerName").asString(),
                                        postAuthorCsrf.path("token").asString()
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        Comment afterDenied = commentRepository
                .findById(newestCommentId)
                .orElseThrow();

        assertThat(afterDenied.getDeletedAt()).isNull();
        assertThat(afterDenied.getContent()).isEqualTo(originalContent);

        // 댓글 작성자는 숨김 상태에서도 삭제할 수 있다.
        JsonNode authorCsrf = getCsrf(authorSession);

        mockMvc.perform(
                        delete(deletePath, responsePostId, responseCommentId)
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

        // 물리 삭제가 아닌 소프트 삭제인지 확인한다.
        Comment deleted = commentRepository
                .findById(newestCommentId)
                .orElseThrow();

        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getContent()).isEqualTo(originalContent);

        if (commentHidden) {
            assertThat(deleted.getHiddenAt()).isNotNull();
        } else {
            assertThat(deleted.getHiddenAt()).isNull();
        }

        // 같은 세션으로 재조회하면 삭제한 댓글만 빠진다.
        MvcResult afterResult = mockMvc.perform(
                        get(BASE)
                                .session(authorSession)
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andReturn();

        assertThat(commentIds(readJson(afterResult)))
                .containsExactly(middleCommentId, oldestCommentId);

        // 이미 삭제한 댓글에 다시 요청하면 404다.
        mockMvc.perform(
                        delete(deletePath, responsePostId, responseCommentId)
                                .session(authorSession)
                                .header(
                                        authorCsrf.path("headerName").asString(),
                                        authorCsrf.path("token").asString()
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    private MockHttpSession login(String email)
            throws Exception {
        MvcResult csrfResult = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession csrfSession =
                (MockHttpSession) csrfResult.getRequest()
                        .getSession(false);

        assertThat(csrfSession).isNotNull();

        JsonNode csrfBody = readJson(csrfResult);
        String headerName = csrfBody.path("headerName").asString();
        String token = csrfBody.path("token").asString();

        assertThat(headerName).isNotBlank();
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

        // 로그인 후의 세션을 반환한다.
        MockHttpSession authenticatedSession =
                (MockHttpSession) loginResult.getRequest()
                        .getSession(false);

        assertThat(authenticatedSession).isNotNull();
        return authenticatedSession;
    }

    private Member saveActiveMember(
            String email,
            String nickname
    ) {
        Member saved = Member.createPending(
                email,
                passwordEncoder.encode(PASSWORD),
                nickname
        );
        saved.activeAfterEmailVerification();
        return memberRepository.saveAndFlush(saved);
    }

    private Post savePost(Member author, String title) {
        return postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        title,
                        "테스트 게시글 본문"
                )
        );
    }

    private Long saveComment(
            Post post,
            Member author,
            String content,
            Instant createdAt
    ) {
        Comment saved = commentRepository.saveAndFlush(
                Comment.create(post, author, content)
        );

        // 실행 속도와 무관하게 정렬·커서 경계를 검증하도록 시각을 고정한다.
        jdbcTemplate.update(
                """
                update community_comments
                   set created_at = ?, updated_at = ?
                 where id = ?
                """,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                saved.getId()
        );

        return saved.getId();
    }

    private JsonNode readJson(MvcResult result) {
        return objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
    }

    private List<Long> commentIds(JsonNode response) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : response.path("items")) {
            ids.add(item.path("commentId").asLong());
        }
        return ids;
    }

    private void saveCommentLike(
            Long commentId,
            Member liker
    ) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow();

        commentLikeRepository.saveAndFlush(
                CommentLike.create(comment, liker)
        );
    }

    private JsonNode getCsrf(MockHttpSession session)
            throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                                .session(session)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode csrf = readJson(result);

        assertThat(csrf.path("headerName").asString()).isNotBlank();
        assertThat(csrf.path("token").asString()).isNotBlank();

        return csrf;
    }
}