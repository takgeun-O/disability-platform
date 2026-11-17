package io.github.takgeun.iyum.admin.member.api;

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
class AdminMemberSummaryReadSessionIntegrationTest {

    private static final String PATH =
            "/api/v1/admin/members/summary";

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

        // 관리자는 어제 생성하여 오늘 가입 수에서는 제외한다.
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
    void 관리자만_있으면_현재_회원은_1명이고_나머지는_0이다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        assertSummary(
                session,
                1,
                0,
                0,
                0
        );
    }

    @Test
    void 회원_인증_정지_해제_탈퇴가_커밋되면_다음_요약에_반영된다()
            throws Exception {
        // 어제 생성한 인증 대기 회원.
        saveMember(
                "과거대기",
                false,
                BEFORE
        );

        // 어제 생성한 이용 정지 회원.
        Member oldSuspended = saveMember(
                "과거정지",
                true,
                BEFORE
        );

        transactions.executeWithoutResult(transaction -> {
            Member member = entityManager.find(
                    Member.class,
                    oldSuspended.getId()
            );

            assertThat(member.suspend()).isTrue();
        });

        // 오늘 생성한 인증 대기 회원을 이후 변경 대상으로 사용한다.
        Member target = saveMember(
                "오늘가입",
                false,
                NOW
        );

        MockHttpSession session = login(admin.getEmail());

        // 관리자 + 과거 대기 + 과거 정지 + 오늘 대기.
        assertSummary(
                session,
                4,
                1,
                2,
                1
        );

        transactions.executeWithoutResult(transaction -> {
            Member member = entityManager.find(
                    Member.class,
                    target.getId()
            );

            member.activeAfterEmailVerification();
        });

        // 이메일 인증 후 현재 회원과 오늘 가입은 유지된다.
        // 인증 대기만 1명 감소한다.
        assertSummary(
                session,
                4,
                1,
                1,
                1
        );

        transactions.executeWithoutResult(transaction -> {
            Member member = entityManager.find(
                    Member.class,
                    target.getId()
            );

            assertThat(member.suspend()).isTrue();
        });

        // 이용 정지 회원도 현재 회원에 포함한다.
        assertSummary(
                session,
                4,
                1,
                1,
                2
        );

        transactions.executeWithoutResult(transaction -> {
            Member member = entityManager.find(
                    Member.class,
                    target.getId()
            );

            assertThat(member.liftSuspension()).isTrue();
        });

        assertSummary(
                session,
                4,
                1,
                1,
                1
        );

        transactions.executeWithoutResult(transaction -> {
            Member member = entityManager.find(
                    Member.class,
                    target.getId()
            );

            member.withdraw(NOW);
        });

        // 탈퇴한 회원은 현재 회원에서 제외한다.
        // 오늘 생성된 계정이라는 사실은 유지되므로 오늘 가입은 그대로다.
        assertSummary(
                session,
                3,
                1,
                1,
                1
        );
    }

    @Test
    void 로그인_후_인증_버전이_변경되면_기존_세션을_거부한다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        assertSummary(
                session,
                1,
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
            int currentMemberCount,
            int todayMemberCount,
            int pendingMemberCount,
            int suspendedMemberCount
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
                .andExpect(jsonPath("$.counts.currentMemberCount")
                        .value(currentMemberCount))
                .andExpect(jsonPath("$.counts.todayMemberCount")
                        .value(todayMemberCount))
                .andExpect(jsonPath("$.counts.pendingMemberCount")
                        .value(pendingMemberCount))
                .andExpect(jsonPath("$.counts.suspendedMemberCount")
                        .value(suspendedMemberCount));
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