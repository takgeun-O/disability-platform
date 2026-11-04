package io.github.takgeun.iyum.admin.operation.api;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.infrastructure.ContentModerationLogRepository;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
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
class AdminOperationLogSessionIntegrationTest {

    private static final String PATH =
            "/api/v1/admin/operation-logs";

    private static final String PASSWORD =
            "OperationLogTest123!";

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

    private Member admin;
    private Member author;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        String passwordHash = passwordEncoder.encode(PASSWORD);

        admin = saveMember(
                "operation-admin-" + suffix + "@example.com",
                "기록관리" + suffix,
                passwordHash
        );

        author = saveMember(
                "operation-author-" + suffix + "@example.com",
                "기록작성" + suffix,
                passwordHash
        );

        // 실제 로그인 전에 DB의 역할과 인증 버전을 설정한다.
        int updated = jdbcTemplate.update("""
                UPDATE members
                SET role = 'ADMIN',
                    credential_version = 3
                WHERE id = ?
                """, admin.getId());

        assertThat(updated).isEqualTo(1);

        Post post = postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        "운영 기록 통합 테스트",
                        "게시글 본문"
                )
        );
        postId = post.getId();

        Comment comment = commentRepository.saveAndFlush(
                Comment.create(post, author, "댓글 본문")
        );
        commentId = comment.getId();
    }

    @AfterEach
    void cleanUpLogs() {
        // 이 클래스의 전용 Testcontainers DB에서
        // 이번 테스트 관리자가 만든 기록만 정리한다.
        // 전체 조회 개수가 다음 테스트에 누적되는 것을 방지한다.
        if (admin != null) {
            jdbcTemplate.update("""
                    DELETE FROM admin_content_moderation_logs
                    WHERE actor_member_id = ?
                    """, admin.getId());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 실제_숨김복원으로_저장한_기록을_조회한다(
            String targetType
    ) throws Exception {
        MockHttpSession session = login(admin.getEmail());

        String moderationPath = targetType.equals("POST")
                ? "/api/v1/admin/posts/" + postId
                : "/api/v1/admin/posts/" + postId
                  + "/comments/" + commentId;

        Long targetId = targetType.equals("POST")
                ? postId
                : commentId;

        // 로그인 시 CSRF 토큰이 갱신될 수 있으므로 다시 발급받는다.
        JsonNode csrf = getCsrf(session);

        moderate(
                session,
                csrf,
                moderationPath + "/hide",
                "  운영 정책 위반 확인  "
        );

        moderate(
                session,
                csrf,
                moderationPath + "/restore",
                "  재검토 결과 복원  "
        );

        MvcResult result = expectOk(mockMvc.perform(
                get(PATH)
                        .session(session)
                        .param("targetType", targetType)
        ))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(2L), Long.class))
                .andExpect(jsonPath("$.items[0].action").value("RESTORE"))
                .andExpect(jsonPath("$.items[0].reason")
                        .value("재검토 결과 복원"))
                .andExpect(jsonPath("$.items[1].action").value("HIDE"))
                .andExpect(jsonPath("$.items[1].reason")
                        .value("운영 정책 위반 확인"))
                .andExpect(jsonPath("$.items[0].targetType")
                        .value(targetType))
                .andExpect(jsonPath("$.items[1].targetType")
                        .value(targetType))
                .andExpect(jsonPath("$.items[0].targetId")
                        .value(is(targetId), Long.class))
                .andExpect(jsonPath("$.items[1].targetId")
                        .value(is(targetId), Long.class))
                .andExpect(jsonPath("$.items[0].actorMemberId")
                        .value(is(admin.getId()), Long.class))
                .andExpect(jsonPath("$.items[1].actorMemberId")
                        .value(is(admin.getId()), Long.class))
                .andReturn();

        // 응답 ID와 처리 시각이 실제 저장된 기록과 일치하는지 확인한다.
        JsonNode items = readJson(result).path("items");

        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);

            ContentModerationLog stored = logRepository.findById(
                    item.path("logId").asLong()
            ).orElseThrow();

            assertThat(Instant.parse(item.path("createdAt").asString()))
                    .isEqualTo(stored.getCreatedAt());
            assertThat(stored.getActorMemberId())
                    .isEqualTo(admin.getId());
        }
    }

    @Test
    void 한국_날짜와_대상과_조치_필터를_적용하고_페이지를_나눈다()
            throws Exception {
        Instant start = Instant.parse("2026-11-03T15:00:00Z");
        Instant end = Instant.parse("2026-11-04T15:00:00Z");

        // 조건에 일치하는 두 건.
        Long first = savePostLog(ModerationAction.HIDE, start);
        Long last = savePostLog(
                ModerationAction.HIDE,
                end.minusNanos(1_000)
        );

        // 날짜·조치·대상 조건에 각각 맞지 않는 기록.
        savePostLog(ModerationAction.HIDE, start.minusNanos(1_000));
        savePostLog(ModerationAction.HIDE, end);
        savePostLog(ModerationAction.RESTORE, start);

        logRepository.saveAndFlush(ContentModerationLog.forComment(
                admin.getId(),
                commentId,
                ModerationAction.HIDE,
                "댓글 숨김 기록",
                start
        ));

        MockHttpSession session = login(admin.getEmail());

        expectOk(readFilteredPage(session, 0))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].logId")
                        .value(is(last), Long.class))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(2L), Long.class))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        expectOk(readFilteredPage(session, 1))
                .andExpect(jsonPath("$.items[0].logId")
                        .value(is(first), Long.class))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(2L), Long.class))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));

        expectOk(readFilteredPage(session, 2))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements")
                        .value(is(2L), Long.class));
    }

    @Test
    void 기록이_없으면_빈_목록을_반환한다() throws Exception {
        MockHttpSession session = login(admin.getEmail());

        expectOk(mockMvc.perform(get(PATH).session(session)))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements")
                        .value(is(0L), Long.class))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void 비로그인은_운영_기록을_조회할_수_없다() throws Exception {
        savePostLog(ModerationAction.HIDE, Instant.now());

        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    @Test
    void 실제_일반_회원_세션은_403이며_로그인은_유지한다()
            throws Exception {
        savePostLog(ModerationAction.HIDE, Instant.now());

        MockHttpSession session = login(author.getEmail());

        mockMvc.perform(get(PATH).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.items").doesNotExist());

        assertThat(session.isInvalid()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE", "VERSION", "STATUS"})
    void 로그인_후_인증정보가_변경되면_기존_세션을_차단한다(
            String change
    ) throws Exception {
        savePostLog(ModerationAction.HIDE, Instant.now());

        MockHttpSession session = login(admin.getEmail());

        expectOk(mockMvc.perform(get(PATH).session(session)))
                .andExpect(jsonPath("$.items.length()").value(1));

        int updated = switch (change) {
            case "ROLE" -> jdbcTemplate.update("""
                    UPDATE members
                    SET role = 'USER'
                    WHERE id = ?
                    """, admin.getId());

            case "VERSION" -> jdbcTemplate.update("""
                    UPDATE members
                    SET credential_version = credential_version + 1
                    WHERE id = ?
                    """, admin.getId());

            case "STATUS" -> jdbcTemplate.update("""
                    UPDATE members
                    SET status = 'PENDING'
                    WHERE id = ?
                    """, admin.getId());

            default -> throw new IllegalArgumentException(change);
        };

        assertThat(updated).isEqualTo(1);

        mockMvc.perform(get(PATH).session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        assertThat(session.isInvalid()).isTrue();

        if (change.equals("ROLE")) {
            // 권한 강등 후 새로 로그인하면 유효한 USER 세션이므로 403.
            MockHttpSession newSession = login(admin.getEmail());

            mockMvc.perform(get(PATH).session(newSession))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

            assertThat(newSession.isInvalid()).isFalse();
        }
    }

    private ResultActions readFilteredPage(
            MockHttpSession session,
            int page
    ) throws Exception {
        return mockMvc.perform(
                get(PATH)
                        .session(session)
                        .param("targetType", "POST")
                        .param("action", "HIDE")
                        .param("startDate", "2026-11-04")
                        .param("endDate", "2026-11-04")
                        .param("page", Integer.toString(page))
                        .param("size", "1")
        );
    }

    private void moderate(
            MockHttpSession session,
            JsonNode csrf,
            String path,
            String reason
    ) throws Exception {
        mockMvc.perform(
                        post(path)
                                .session(session)
                                .header(
                                        csrf.path("headerName").asString(),
                                        csrf.path("token").asString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of("reason", reason)
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true));
    }

    private Long savePostLog(ModerationAction action, Instant at) {
        return logRepository.saveAndFlush(
                ContentModerationLog.forPost(
                        admin.getId(),
                        postId,
                        action,
                        "날짜 필터 검증",
                        at
                )
        ).getId();
    }

    private Member saveMember(
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

    private MockHttpSession login(String email) throws Exception {
        MvcResult csrfResult = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession csrfSession =
                (MockHttpSession) csrfResult.getRequest().getSession(false);

        assertThat(csrfSession).isNotNull();

        JsonNode csrf = readJson(csrfResult);

        assertThat(csrf.path("headerName").asString())
                .isEqualTo("X-CSRF-TOKEN");
        assertThat(csrf.path("token").asString()).isNotBlank();

        MvcResult loginResult = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .session(csrfSession)
                                .header(
                                        csrf.path("headerName").asString(),
                                        csrf.path("token").asString()
                                )
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

        MockHttpSession session =
                (MockHttpSession) loginResult.getRequest().getSession(false);

        assertThat(session).isNotNull();
        return session;
    }

    private JsonNode getCsrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/auth/csrf").session(session)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode csrf = readJson(result);

        assertThat(csrf.path("headerName").asString())
                .isEqualTo("X-CSRF-TOKEN");
        assertThat(csrf.path("token").asString()).isNotBlank();

        return csrf;
    }

    private JsonNode readJson(MvcResult result) {
        return objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
    }

    private ResultActions expectOk(ResultActions response)
            throws Exception {
        return response
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));
    }
}