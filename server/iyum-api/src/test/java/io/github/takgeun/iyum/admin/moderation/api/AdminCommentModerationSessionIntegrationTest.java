package io.github.takgeun.iyum.admin.moderation.api;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.admin.moderation.infrastructure.ContentModerationLogRepository;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class AdminCommentModerationSessionIntegrationTest {

    private static final String PASSWORD = "Admin-comment123!";
    private static final String POST_TITLE = "댓글 관리자 API 검증";
    private static final String POST_CONTENT = "부모 게시글의 원본 본문";
    private static final String COMMENT_CONTENT = "보존할 댓글 원본입니다.";

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
    PasswordEncoder passwordEncoder;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    ContentModerationLogRepository logRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long adminId;
    private String adminEmail;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        String passwordHash = passwordEncoder.encode(PASSWORD);

        Member admin = saveActiveMember(
                "admin-" + suffix + "@example.com",
                "관리검증" + suffix,
                passwordHash
        );

        adminId = admin.getId();
        adminEmail = admin.getEmail();

        // 테스트 전용 계정에 관리자 역할을 설정한다.
        int updated = jdbcTemplate.update("""
                UPDATE members
                SET role = 'ADMIN',
                    credential_version = 3
                WHERE id = ?
                """, adminId);

        assertThat(updated).isEqualTo(1);

        Member author = saveActiveMember(
                "author-" + suffix + "@example.com",
                "작성검증" + suffix,
                passwordHash
        );

        Post savedPost = postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        POST_TITLE,
                        POST_CONTENT
                )
        );

        postId = savedPost.getId();

        Comment savedComment = commentRepository.saveAndFlush(
                Comment.create(
                        savedPost,
                        author,
                        COMMENT_CONTENT
                )
        );

        commentId = savedComment.getId();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 실제_관리자_세션으로_댓글을_처리하며_부모_상태는_유지한다(
            boolean parentHidden
    ) throws Exception {
        if (parentHidden) {
            Post parent = storedPost();
            parent.hide(Instant.parse("2026-10-30T10:00:00Z"));
            postRepository.saveAndFlush(parent);
        }

        Post parentBefore = storedPost();

        MockHttpSession session = loginAdmin();
        JsonNode csrf = getCsrf(session);

        assertThat(logsForComment()).isEmpty();
        assertPublicComments(parentHidden, 1);

        // 1. 최초 숨김
        JsonNode hideResponse = moderate(
                session,
                csrf,
                "hide",
                "  댓글 개인정보 노출 확인  ",
                true,
                true
        );

        Comment hidden = storedComment();

        assertThat(hidden.getHiddenAt()).isNotNull();
        assertThat(hidden.getDeletedAt()).isNull();
        assertThat(hidden.getContent()).isEqualTo(COMMENT_CONTENT);

        Instant storedHiddenAt = hidden.getHiddenAt();

        assertThat(storedHiddenAt).isCloseTo(
                Instant.parse(hideResponse.path("hiddenAt").asString()),
                within(1, ChronoUnit.MICROS)
        );

        List<ContentModerationLog> hideLogs = logsForComment();
        assertThat(hideLogs).hasSize(1);

        ContentModerationLog hideLog = hideLogs.getFirst();

        assertThat(hideLog.getActorMemberId()).isEqualTo(adminId);
        assertThat(hideLog.getCommentId()).isEqualTo(commentId);

        // 댓글 처리 이력에는 postId를 함께 저장하지 않는다.
        assertThat(hideLog.getPostId()).isNull();
        assertThat(hideLog.getAction()).isEqualTo(ModerationAction.HIDE);
        assertThat(hideLog.getReason()).isEqualTo("댓글 개인정보 노출 확인");
        assertThat(hideLog.getCreatedAt()).isEqualTo(storedHiddenAt);
        // hideLog 검증에 추가
        assertThat(hideLog.getReasonType())
                .isEqualTo(ModerationReasonType.OTHER);
        assertThat(hideLog.getReasonDetail())
                .isEqualTo("댓글 개인정보 노출 확인");

        assertParentUnchanged(parentBefore);
        assertPublicComments(parentHidden, 0);

        // 2. 중복 숨김
        moderate(
                session,
                csrf,
                "hide",
                "중복 숨김 요청",
                true,
                false
        );

        assertThat(storedComment().getHiddenAt())
                .isEqualTo(storedHiddenAt);
        assertThat(logsForComment()).hasSize(1);
        assertThat(logsForComment().getFirst().getReason())
                .isEqualTo("댓글 개인정보 노출 확인");

        assertParentUnchanged(parentBefore);

        // 3. 최초 복원
        moderate(
                session,
                csrf,
                "restore",
                "  재검토 결과 댓글 복원  ",
                false,
                true
        );

        Comment restored = storedComment();

        assertThat(restored.getHiddenAt()).isNull();
        assertThat(restored.getDeletedAt()).isNull();
        assertThat(restored.getContent()).isEqualTo(COMMENT_CONTENT);

        List<ContentModerationLog> restoredLogs = logsForComment();

        assertThat(restoredLogs).hasSize(2);
        assertThat(restoredLogs)
                .extracting(ContentModerationLog::getAction)
                .containsExactly(
                        ModerationAction.HIDE,
                        ModerationAction.RESTORE
                );

        ContentModerationLog restoreLog = restoredLogs.get(1);

        assertThat(restoreLog.getActorMemberId()).isEqualTo(adminId);
        assertThat(restoreLog.getCommentId()).isEqualTo(commentId);
        assertThat(restoreLog.getPostId()).isNull();
        assertThat(restoreLog.getReason()).isEqualTo("재검토 결과 댓글 복원");
        assertThat(restoreLog.getCreatedAt()).isNotNull();
        // restoreLog 검증에 추가
        assertThat(restoreLog.getReasonType())
                .isEqualTo(ModerationReasonType.OTHER);
        assertThat(restoreLog.getReasonDetail())
                .isEqualTo("재검토 결과 댓글 복원");

        assertParentUnchanged(parentBefore);

        // 부모가 공개이면 댓글이 다시 나타나고,
        // 부모가 숨김이면 댓글을 복원해도 조회는 계속 거부된다.
        assertPublicComments(parentHidden, 1);

        // 4. 중복 복원
        moderate(
                session,
                csrf,
                "restore",
                "중복 복원 요청",
                false,
                false
        );

        assertThat(storedComment().getHiddenAt()).isNull();

        List<ContentModerationLog> finalLogs = logsForComment();

        assertThat(finalLogs).hasSize(2);
        assertThat(finalLogs)
                .extracting(ContentModerationLog::getReason)
                .containsExactly(
                        "댓글 개인정보 노출 확인",
                        "재검토 결과 댓글 복원"
                );

        assertParentUnchanged(parentBefore);

        // 댓글 처리 과정에서 부모 게시글의 처리 이력을 만들지 않는다.
        assertThat(logRepository.findAll().stream()
                .filter(log -> postId.equals(log.getPostId()))
                .toList())
                .isEmpty();
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

    @ParameterizedTest
    @MethodSource("invalidReasonRequests")
    void 잘못된_사유는_댓글과_부모_상태_및_이력을_변경하지_않는다(
            String action,
            String body
    ) throws Exception {
        prepareCommentForAction(action);

        Post parentBefore = storedPost();
        Comment commentBefore = storedComment();

        assertNoModerationLogsForAdmin();

        MockHttpSession session = loginAdmin();
        JsonNode csrf = getCsrf(session);

        mockMvc.perform(
                        moderationRequest(
                                session,
                                csrf,
                                postId,
                                commentId,
                                action,
                                body
                        )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REASON"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        assertParentUnchanged(parentBefore);
        assertCommentUnchanged(commentBefore);
        assertNoModerationLogsForAdmin();
    }

    @ParameterizedTest
    @CsvSource({
            "hide, POST, POST_DELETED",
            "restore, POST, POST_DELETED",
            "hide, COMMENT, COMMENT_DELETED",
            "restore, COMMENT, COMMENT_DELETED"
    })
    void 삭제된_대상은_처리할_수_없고_상태와_이력을_유지한다(
            String action,
            String deletedTarget,
            String expectedCode
    ) throws Exception {
        prepareCommentForAction(action);

        Instant deletedAt = Instant.parse("2026-10-30T11:00:00Z");

        if ("POST".equals(deletedTarget)) {
            Post parent = storedPost();
            parent.delete(deletedAt);
            postRepository.saveAndFlush(parent);
        } else {
            Comment target = storedComment();
            target.delete(deletedAt);
            commentRepository.saveAndFlush(target);
        }

        Post parentBefore = storedPost();
        Comment commentBefore = storedComment();

        assertNoModerationLogsForAdmin();

        MockHttpSession session = loginAdmin();
        JsonNode csrf = getCsrf(session);

        String body = objectMapper.writeValueAsString(
                Map.of(
                        "reasonType", "OTHER",
                        "reasonDetail", "삭제 상태 검증 요청"
                )
        );

        mockMvc.perform(
                        moderationRequest(
                                session,
                                csrf,
                                postId,
                                commentId,
                                action,
                                body
                        )
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        assertParentUnchanged(parentBefore);
        assertCommentUnchanged(commentBefore);
        assertNoModerationLogsForAdmin();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hide", "restore"})
    void 다른_게시글의_ID로_요청하면_댓글을_처리하지_않는다(
            String action
    ) throws Exception {
        prepareCommentForAction(action);

        Member anotherPostAuthor = memberRepository
                .findById(adminId)
                .orElseThrow();

        Post anotherPost = postRepository.saveAndFlush(
                Post.create(
                        anotherPostAuthor,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        "댓글이 속하지 않은 다른 게시글",
                        "게시글과 댓글 ID 불일치 검증용 본문"
                )
        );

        Long anotherPostId = anotherPost.getId();

        Post parentBefore = storedPost();
        Comment commentBefore = storedComment();

        Post anotherPostBefore = postRepository
                .findById(anotherPostId)
                .orElseThrow();

        assertNoModerationLogsForAdmin();

        MockHttpSession session = loginAdmin();
        JsonNode csrf = getCsrf(session);

        String body = objectMapper.writeValueAsString(
                Map.of(
                        "reasonType", "OTHER",
                        "reasonDetail", "게시글과 댓글 연결 검증"
                )
        );

        mockMvc.perform(
                        moderationRequest(
                                session,
                                csrf,
                                anotherPostId,
                                commentId,
                                action,
                                body
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        // 댓글과 실제 부모 게시글은 변경되지 않는다.
        assertParentUnchanged(parentBefore);
        assertCommentUnchanged(commentBefore);

        // 잘못 전달한 게시글도 변경되지 않는다.
        Post anotherPostAfter = postRepository
                .findById(anotherPostId)
                .orElseThrow();

        assertThat(anotherPostAfter.getHiddenAt())
                .isEqualTo(anotherPostBefore.getHiddenAt());
        assertThat(anotherPostAfter.getDeletedAt())
                .isEqualTo(anotherPostBefore.getDeletedAt());
        assertThat(anotherPostAfter.getUpdatedAt())
                .isEqualTo(anotherPostBefore.getUpdatedAt());
        assertThat(anotherPostAfter.getTitle())
                .isEqualTo(anotherPostBefore.getTitle());
        assertThat(anotherPostAfter.getContent())
                .isEqualTo(anotherPostBefore.getContent());

        assertNoModerationLogsForAdmin();
    }

    private JsonNode readJson(MvcResult result) {
        return objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
    }

    private MockHttpSession loginAdmin() throws Exception {
        MvcResult csrfResult = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) csrfResult.getRequest()
                        .getSession(false);

        assertThat(session).isNotNull();

        JsonNode csrf = readJson(csrfResult);

        assertThat(csrf.path("headerName").asString()).isNotBlank();
        assertThat(csrf.path("token").asString()).isNotBlank();

        MvcResult loginResult = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .session(session)
                                .header(
                                        csrf.path("headerName").asString(),
                                        csrf.path("token").asString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "email", adminEmail,
                                                "password", PASSWORD
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticatedSession =
                (MockHttpSession) loginResult.getRequest()
                        .getSession(false);

        assertThat(authenticatedSession).isNotNull();

        return authenticatedSession;
    }

    private JsonNode getCsrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/auth/csrf").session(session)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode csrf = readJson(result);

        assertThat(csrf.path("headerName").asString()).isNotBlank();
        assertThat(csrf.path("token").asString()).isNotBlank();

        return csrf;
    }

    private JsonNode moderate(
            MockHttpSession session,
            JsonNode csrf,
            String action,
            String reason,
            boolean expectedHidden,
            boolean expectedChanged
    ) throws Exception {
        var response = mockMvc.perform(
                        post(
                                "/api/v1/admin/posts/{postId}/comments/{commentId}/{action}",
                                postId,
                                commentId,
                                action
                        )
                                .session(session)
                                .header(
                                        csrf.path("headerName").asString(),
                                        csrf.path("token").asString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "reasonType", "OTHER",
                                                "reasonDetail", reason
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.postId")
                        .value(is(postId), Long.class))
                .andExpect(jsonPath("$.commentId")
                        .value(is(commentId), Long.class))
                .andExpect(jsonPath("$.hidden").value(expectedHidden))
                .andExpect(jsonPath("$.changed").value(expectedChanged))
                .andExpect(jsonPath("$.hiddenAt").hasJsonPath());

        if (expectedHidden) {
            response.andExpect(jsonPath("$.hiddenAt").isString());
        } else {
            response.andExpect(jsonPath("$.hiddenAt").value(nullValue()));
        }

        return readJson(response.andReturn());
    }

    private Post storedPost() {
        return postRepository.findById(postId).orElseThrow();
    }

    private Comment storedComment() {
        return commentRepository.findById(commentId).orElseThrow();
    }

    private List<ContentModerationLog> logsForComment() {
        return logRepository.findAll().stream()
                .filter(log -> commentId.equals(log.getCommentId()))
                .sorted(Comparator.comparing(ContentModerationLog::getId))
                .toList();
    }

    private void assertParentUnchanged(Post before) {
        Post after = storedPost();

        assertThat(after.getHiddenAt()).isEqualTo(before.getHiddenAt());
        assertThat(after.getDeletedAt()).isEqualTo(before.getDeletedAt());
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        assertThat(after.getTitle()).isEqualTo(before.getTitle());
        assertThat(after.getContent()).isEqualTo(before.getContent());
    }

    private void assertPublicComments(
            boolean parentHidden,
            int expectedCount
    ) throws Exception {
        var response = mockMvc.perform(
                get("/api/v1/community/posts/{postId}/comments", postId)
        );

        if (parentHidden) {
            response
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        } else {
            response
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()")
                            .value(expectedCount));
        }
    }

    private MockHttpServletRequestBuilder moderationRequest(
            MockHttpSession session,
            JsonNode csrf,
            Long requestedPostId,
            Long requestedCommentId,
            String action,
            String body
    ) {
        return post(
                "/api/v1/admin/posts/{postId}/comments/{commentId}/{action}",
                requestedPostId,
                requestedCommentId,
                action
        )
                .session(session)
                .header(
                        csrf.path("headerName").asString(),
                        csrf.path("token").asString()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void assertCommentUnchanged(Comment before) {
        Comment after = storedComment();

        assertThat(after.getHiddenAt()).isEqualTo(before.getHiddenAt());
        assertThat(after.getDeletedAt()).isEqualTo(before.getDeletedAt());
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        assertThat(after.getContent()).isEqualTo(before.getContent());
    }

    private void assertNoModerationLogsForAdmin() {
        // 대상 ID를 잘못 기록한 이력도 놓치지 않도록
        // 이번 테스트의 관리자 ID를 기준으로 확인한다.
        assertThat(logRepository.findAll().stream()
                .filter(log -> adminId.equals(log.getActorMemberId()))
                .toList())
                .isEmpty();
    }

    private void prepareCommentForAction(String action) {
        if ("restore".equals(action)) {
            Comment target = storedComment();
            target.hide(Instant.parse("2026-10-30T10:00:00Z"));
            commentRepository.saveAndFlush(target);
        }
    }

    private static Stream<Arguments> invalidReasonRequests() {
        List<String> bodies = List.of(
                // 필수 사유 유형 누락
                "{}",
                """
                {"reasonDetail":"사유 유형 누락 검증"}
                """,

                // 사유 유형이 명시적으로 null
                """
                {"reasonType":null,"reasonDetail":"사유 유형 검증"}
                """,

                // OTHER는 상세 사유 필수
                """
                {"reasonType":"OTHER"}
                """,
                """
                {"reasonType":"OTHER","reasonDetail":null}
                """,
                """
                {"reasonType":"OTHER","reasonDetail":""}
                """,
                "{\"reasonType\":\"OTHER\",\"reasonDetail\":\" \\t\\n \"}",

                // 코드 포인트 기준 500자 초과
                "{\"reasonType\":\"OTHER\",\"reasonDetail\":\""
                        + "가".repeat(501)
                        + "\"}",
                "{\"reasonType\":\"OTHER\",\"reasonDetail\":\""
                        + "😀".repeat(501)
                        + "\"}"
        );

        return Stream.of("hide", "restore")
                .flatMap(action ->
                        bodies.stream()
                                .map(body -> Arguments.of(action, body))
                );
    }
}