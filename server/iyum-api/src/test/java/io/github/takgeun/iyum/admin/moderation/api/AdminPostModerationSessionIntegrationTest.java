package io.github.takgeun.iyum.admin.moderation.api;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.admin.moderation.infrastructure.ContentModerationLogRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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
class AdminPostModerationSessionIntegrationTest {

    private static final String PASSWORD = "Admin-session123!";
    private static final String TITLE = "관리자 API 검증 게시글";
    private static final String CONTENT = "숨김 후에도 보존할 원본입니다.";

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
    ContentModerationLogRepository logRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long adminId;
    private String adminEmail;
    private Long postId;

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

        // 테스트 전용 DB의 계정만 관리자 역할로 변경한다.
        // 실제 로그인 전에 반영하여 principal에도 ADMIN이 담기게 한다.
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

        Post saved = postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        TITLE,
                        CONTENT
                )
        );

        postId = saved.getId();
    }

    @Test
    void 실제_관리자_세션으로_숨김과_복원을_처리하고_변경시에만_이력을_저장한다()
            throws Exception {
        MockHttpSession session = loginAdmin();
        JsonNode csrf = getCsrf(session);

        assertThat(logsForPost()).isEmpty();

        // 최초 공개 상태
        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId))
                .andExpect(status().isOk());

        // 1. 최초 숨김
        JsonNode hideResponse = moderate(
                session,
                csrf,
                "hide",
                "  개인정보 노출 확인  ",
                true,
                true
        );

        Post hidden = storedPost();

        assertThat(hidden.getHiddenAt()).isNotNull();
        assertThat(hidden.getDeletedAt()).isNull();
        assertThat(hidden.getTitle()).isEqualTo(TITLE);
        assertThat(hidden.getContent()).isEqualTo(CONTENT);

        Instant storedHiddenAt = hidden.getHiddenAt();

        // 응답 Instant와 PostgreSQL의 시간 정밀도 차이를 허용한다.
        assertThat(storedHiddenAt).isCloseTo(
                Instant.parse(hideResponse.path("hiddenAt").asString()),
                within(1, ChronoUnit.MICROS)
        );

        List<ContentModerationLog> hideLogs = logsForPost();
        assertThat(hideLogs).hasSize(1);

        ContentModerationLog hideLog = hideLogs.getFirst();

        assertThat(hideLog.getActorMemberId()).isEqualTo(adminId);
        assertThat(hideLog.getPostId()).isEqualTo(postId);
        assertThat(hideLog.getCommentId()).isNull();
        assertThat(hideLog.getAction()).isEqualTo(ModerationAction.HIDE);
        assertThat(hideLog.getReason()).isEqualTo("개인정보 노출 확인");
        assertThat(hideLog.getCreatedAt()).isEqualTo(storedHiddenAt);
        assertThat(hideLog.getReasonType())
                .isEqualTo(ModerationReasonType.OTHER);

        assertThat(hideLog.getReasonDetail())
                .isEqualTo("개인정보 노출 확인");

        // 공개 상세 조회에서는 숨김 게시글이 보이지 않는다.
        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId))
                .andExpect(status().isNotFound());

        // 2. 중복 숨김: 상태와 이력을 유지한다.
        moderate(
                session,
                csrf,
                "hide",
                "중복 숨김 요청",
                true,
                false
        );

        assertThat(storedPost().getHiddenAt()).isEqualTo(storedHiddenAt);
        assertThat(logsForPost()).hasSize(1);
        assertThat(logsForPost().getFirst().getReason())
                .isEqualTo("개인정보 노출 확인");

        // 3. 최초 복원
        moderate(
                session,
                csrf,
                "restore",
                "  재검토 결과 공개 가능  ",
                false,
                true
        );

        Post restored = storedPost();

        assertThat(restored.getHiddenAt()).isNull();
        assertThat(restored.getDeletedAt()).isNull();
        assertThat(restored.getTitle()).isEqualTo(TITLE);
        assertThat(restored.getContent()).isEqualTo(CONTENT);

        List<ContentModerationLog> restoredLogs = logsForPost();
        assertThat(restoredLogs).hasSize(2);
        assertThat(restoredLogs)
                .extracting(ContentModerationLog::getAction)
                .containsExactly(
                        ModerationAction.HIDE,
                        ModerationAction.RESTORE
                );

        ContentModerationLog restoreLog = restoredLogs.get(1);

        assertThat(restoreLog.getActorMemberId()).isEqualTo(adminId);
        assertThat(restoreLog.getPostId()).isEqualTo(postId);
        assertThat(restoreLog.getCommentId()).isNull();
        assertThat(restoreLog.getReason()).isEqualTo("재검토 결과 공개 가능");
        assertThat(restoreLog.getCreatedAt()).isNotNull();
        assertThat(restoreLog.getReasonType())
                .isEqualTo(ModerationReasonType.OTHER);

        assertThat(restoreLog.getReasonDetail())
                .isEqualTo("재검토 결과 공개 가능");

        // 복원 후 공개 상세 조회가 다시 가능하다.
        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId))
                .andExpect(status().isOk());

        // 4. 중복 복원: 이력을 추가하지 않는다.
        moderate(
                session,
                csrf,
                "restore",
                "중복 복원 요청",
                false,
                false
        );

        assertThat(storedPost().getHiddenAt()).isNull();

        List<ContentModerationLog> finalLogs = logsForPost();

        assertThat(finalLogs).hasSize(2);
        assertThat(finalLogs)
                .extracting(ContentModerationLog::getReason)
                .containsExactly(
                        "개인정보 노출 확인",
                        "재검토 결과 공개 가능"
                );
    }

    @ParameterizedTest
    @MethodSource("invalidReasonRequests")
    void 잘못된_사유는_상태와_이력을_변경하지_않는다(
            String action,
            String body
    ) throws Exception {
        // 복원 테스트는 실제 숨김 상태에서 시작한다.
        if ("restore".equals(action)) {
            Post target = storedPost();
            target.hide(Instant.parse("2026-10-30T10:00:00Z"));
            postRepository.saveAndFlush(target);
        }

        Post before = storedPost();

        assertThat(logsForPost()).isEmpty();

        MockHttpSession session = loginAdmin();
        JsonNode csrf = getCsrf(session);

        mockMvc.perform(
                        moderationRequest(
                                session,
                                csrf,
                                action,
                                body
                        )
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REASON"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        Post after = storedPost();

        assertThat(after.getHiddenAt()).isEqualTo(before.getHiddenAt());
        assertThat(after.getDeletedAt()).isEqualTo(before.getDeletedAt());
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        assertThat(after.getTitle()).isEqualTo(before.getTitle());
        assertThat(after.getContent()).isEqualTo(before.getContent());

        assertThat(logsForPost()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "hide, false",
            "hide, true",
            "restore, false",
            "restore, true"
    })
    void 삭제된_게시글은_숨김이나_복원할_수_없다(
            String action,
            boolean initiallyHidden
    ) throws Exception {
        Post target = storedPost();

        if (initiallyHidden) {
            target.hide(Instant.parse("2026-10-30T10:00:00Z"));
        }

        target.delete(Instant.parse("2026-10-30T11:00:00Z"));
        postRepository.saveAndFlush(target);

        Post before = storedPost();

        assertThat(before.getDeletedAt()).isNotNull();
        assertThat(logsForPost()).isEmpty();

        MockHttpSession session = loginAdmin();
        JsonNode csrf = getCsrf(session);

        String body = objectMapper.writeValueAsString(
                Map.of(
                        "reasonType", "OTHER",
                        "reasonDetail", "삭제된 게시글 처리 요청"
                )
        );

        mockMvc.perform(
                        moderationRequest(
                                session,
                                csrf,
                                action,
                                body
                        )
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value("POST_DELETED"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        Post after = storedPost();

        assertThat(after.getDeletedAt()).isEqualTo(before.getDeletedAt());
        assertThat(after.getHiddenAt()).isEqualTo(before.getHiddenAt());
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        assertThat(after.getTitle()).isEqualTo(before.getTitle());
        assertThat(after.getContent()).isEqualTo(before.getContent());

        assertThat(logsForPost()).isEmpty();

        // 관리자 요청이 실패한 이후에도 공개 상세 조회는 불가능하다.
        mockMvc.perform(
                        get("/api/v1/community/posts/{postId}", postId)
                )
                .andExpect(status().isNotFound());
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
                (MockHttpSession) csrfResult.getRequest().getSession(false);

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
                (MockHttpSession) loginResult.getRequest().getSession(false);

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
                        post("/api/v1/admin/posts/{postId}/{action}",
                                postId, action)
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

    private List<ContentModerationLog> logsForPost() {
        // 테스트에서 이번 게시글의 이력만 확인한다.
        return logRepository.findAll().stream()
                .filter(log -> postId.equals(log.getPostId()))
                .sorted(Comparator.comparing(ContentModerationLog::getId))
                .toList();
    }

    private MockHttpServletRequestBuilder moderationRequest(
            MockHttpSession session,
            JsonNode csrf,
            String action,
            String body
    ) {
        return post(
                "/api/v1/admin/posts/{postId}/{action}",
                postId,
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

    private static Stream<Arguments> invalidReasonRequests() {
        List<String> bodies = List.of(
                "{}",
                "{\"reason\":null}",
                "{\"reason\":\"\"}",
                "{\"reason\":\" \\t\\n \"}",
                "{\"reason\":\"" + "가".repeat(501) + "\"}",
                "{\"reason\":\"" + "😀".repeat(501) + "\"}"
        );

        return Stream.of("hide", "restore")
                .flatMap(action ->
                        bodies.stream()
                                .map(body -> Arguments.of(action, body))
                );
    }
}