package io.github.takgeun.iyum.admin.operation.api;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
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
@Execution(ExecutionMode.SAME_THREAD)
class AdminOperationSummaryReadSessionIntegrationTest {

    private static final String PATH =
            "/api/v1/admin/operation-logs/summary";

    private static final String PASSWORD =
            "MemberSummaryTest123!";

    private static final Instant NOW =
            Instant.parse("2026-11-18T03:00:00Z");

    private static final Instant BEFORE =
            Instant.parse("2026-11-17T03:00:00Z");

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
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    Clock clock;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private TransactionTemplate transactions;

    private Member admin;

    private String passwordHash;

    /*
     * 클래스 전체에 @Transactional을 붙이지 않는다.
     * 회원 생성과 상태 변경이 커밋된 뒤,
     * 별도의 API 요청에서 다시 조회하도록 한다.
     */
    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);

        // 이 클래스 전용 Testcontainers DB의 데이터만 정리한다.
        jdbcTemplate.execute("""
                TRUNCATE TABLE members CASCADE
                """);

        when(clock.instant()).thenReturn(NOW);

        passwordHash = passwordEncoder.encode(PASSWORD);

        // 회원 생성 시각은 콘텐츠 운영 기록 집계와 무관하다.
        admin = saveMember(
                "관리자",
                true,
                BEFORE
        );

        int updated = jdbcTemplate.update("""
                UPDATE members
                   SET role = 'ADMIN',
                       credential_version = 3
                 WHERE id = ?
                """,
                admin.getId()
        );

        assertThat(updated).isEqualTo(1);

        setAuditingTime(NOW);
    }

    @Test
    void 기록이_없으면_실제_관리자_세션에_0건을_반환한다() throws Exception {
        assertSummary(login(admin.getEmail()), 0, 0, 0, 0);
    }

    @Test
    void 실제_숨김_복원과_중복_요청의_결과가_요약에_반영된다() throws Exception {
        Long postId = transactions.execute(transaction -> {
            var author = entityManager.find(Member.class, admin.getId());
            Post post = Post.create(
                    author,
                    PostType.GENERAL,
                    PostTopic.DAILY,
                    "검증 전용 게시글", "요약 갱신 확인"
            );
            entityManager.persist(post);
            entityManager.flush();
            return post.getId();
        });
        MockHttpSession session = login(admin.getEmail());
        JsonNode csrf = objectMapper.readTree(mockMvc.perform(
                get("/api/v1/auth/csrf").session(session)
        ).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertSummary(session, 0, 0, 0, 0);
        for (String action : new String[]{"hide", "hide", "restore", "hide"}) {
            mockMvc.perform(post("/api/v1/admin/posts/" + postId + "/" + action)
                    .session(session)
                    .header(csrf.path("headerName").asString(), csrf.path("token").asString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "reasonType", "OTHER", "reasonDetail", "검증 전용 조치"
                    ))))
                    .andExpect(status().isOk());
        }
        assertSummary(session, 3, 3, 2, 1);
        mockMvc.perform(post("/api/v1/admin/posts/" + postId + "/restore")
                .session(session)
                .header(csrf.path("headerName").asString(), csrf.path("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
        assertSummary(session, 3, 3, 2, 1);
    }

    @Test
    void 로그인_후_인증_버전이_변경되면_기존_세션을_거부한다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        assertSummary(
                session,
                0,
                0,
                0,
                0
        );

        int updated = jdbcTemplate.update("""
                UPDATE members
                   SET credential_version = credential_version + 1
                 WHERE id = ?
                """,
                admin.getId()
        );

        assertThat(updated).isEqualTo(1);

        expectError(
                mockMvc.perform(
                        get(PATH)
                                .session(session)
                                .accept(MediaType.APPLICATION_JSON)
                ),
                401,
                "AUTHENTICATION_REQUIRED"
        );

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 실제_일반_회원_로그인_세션은_403으로_거부한다()
            throws Exception {
        Member user = saveMember(
                "일반회원",
                true,
                NOW
        );

        MockHttpSession session = login(user.getEmail());

        expectError(
                mockMvc.perform(
                        get(PATH)
                                .session(session)
                                .accept(MediaType.APPLICATION_JSON)
                ),
                403,
                "ACCESS_DENIED"
        );
    }

    @Test
    void 로그인하지_않으면_401로_거부한다()
            throws Exception {
        expectError(
                mockMvc.perform(
                        get(PATH)
                                .accept(MediaType.APPLICATION_JSON)
                ),
                401,
                "AUTHENTICATION_REQUIRED"
        );
    }

    private Member saveMember(
            String label,
            boolean active,
            Instant createdAt
    ) {
        setAuditingTime(createdAt);

        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");

        Member member = Member.createPending(
                unique + "@example.com",
                passwordHash,
                label + unique.substring(0, 8)
        );

        if (active) {
            member.activeAfterEmailVerification();
        }

        Member saved = memberRepository.saveAndFlush(member);

        setAuditingTime(NOW);

        return saved;
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

        JsonNode csrf = objectMapper.readTree(
                csrfResult.getResponse().getContentAsByteArray()
        );

        String headerName = csrf.path("headerName").asString();
        String token = csrf.path("token").asString();

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

        // 로그인 과정에서 세션이 교체될 수 있으므로 다시 가져온다.
        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        assertThat(session).isNotNull();
        assertThat(session.isInvalid()).isFalse();

        return session;
    }

    private void assertSummary(
            MockHttpSession session,
            int totalOperationLogCount,
            int todayOperationLogCount,
            int todayHideCount,
            int todayRestoreCount
    ) throws Exception {
        // 조회 GET에는 CSRF 토큰을 전달하지 않는다.
        mockMvc.perform(
                        get(PATH)
                                .session(session)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.queriedAt")
                        .value(NOW.toString()))
                .andExpect(jsonPath("$.baseDate")
                        .value("2026-11-18"))
                .andExpect(jsonPath("$.counts.totalOperationLogCount")
                        .value(totalOperationLogCount))
                .andExpect(jsonPath("$.counts.todayOperationLogCount")
                        .value(todayOperationLogCount))
                .andExpect(jsonPath("$.counts.todayHideCount")
                        .value(todayHideCount))
                .andExpect(jsonPath("$.counts.todayRestoreCount")
                        .value(todayRestoreCount));
    }

    private void expectError(
            ResultActions response,
            int expectedStatus,
            String expectedCode
    ) throws Exception {
        response
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.counts").doesNotHaveJsonPath())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));
    }

    private void setAuditingTime(Instant time) {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(time));
    }
}