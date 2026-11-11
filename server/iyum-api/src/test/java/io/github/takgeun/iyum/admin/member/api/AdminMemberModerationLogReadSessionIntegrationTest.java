package io.github.takgeun.iyum.admin.member.api;

import io.github.takgeun.iyum.admin.member.domain.MemberModerationLog;
import io.github.takgeun.iyum.admin.member.infrastructure.MemberModerationLogRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
@Execution(ExecutionMode.SAME_THREAD)
class AdminMemberModerationLogReadSessionIntegrationTest {

    private static final String LOG_PATH =
            "/api/v1/admin/members/{memberId}/moderation-logs";

    private static final String MODERATION_PATH =
            "/api/v1/admin/members/{memberId}/{action}";

    private static final String LOGIN_PATH =
            "/api/v1/auth/login";

    private static final String CSRF_PATH =
            "/api/v1/auth/csrf";

    private static final String PASSWORD =
            "ModerationLog123!";

    private static final long ADMIN_VERSION = 3L;

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
    MemberModerationLogRepository logRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long adminId;
    private String adminEmail;

    private Long targetId;
    private String targetEmail;

    private String passwordHash;

    /*
     * @Transactional을 붙이지 않는다.
     *
     * 각 HTTP 요청에서 커밋한 변경 사항을
     * 다음 요청의 인증 필터와 조회 서비스가 읽도록 구성한다.
     *
     * 테스트마다 고유한 회원을 생성하므로
     * 다른 테스트 데이터의 전체 삭제는 필요하지 않다.
     */
    @BeforeEach
    void setUp() {
        passwordHash = passwordEncoder.encode(PASSWORD);

        Member admin = saveActiveMember("관리");
        Member target = saveActiveMember("대상");

        adminId = admin.getId();
        adminEmail = admin.getEmail();

        targetId = target.getId();
        targetEmail = target.getEmail();

        // 실제 로그인 전에 테스트 관리자 역할과 인증 버전을 설정한다.
        int updatedRows = jdbcTemplate.update("""
                UPDATE members
                   SET role = 'ADMIN',
                       credential_version = ?
                 WHERE id = ?
                """,
                ADMIN_VERSION,
                adminId
        );

        assertThat(updatedRows).isEqualTo(1);
        assertThat(logsForTarget()).isEmpty();
    }

    @Test
    void 실제_정지와_해제_결과를_조회하고_중복_요청은_이력을_추가하지_않는다()
            throws Exception {
        MockHttpSession adminSession = login(adminEmail);

        // 이력이 없는 기존 회원은 정상적으로 빈 목록을 반환한다.
        JsonNode initial = readLogs(
                adminSession,
                targetId,
                0,
                20
        );

        assertThat(initial.path("items").size()).isZero();
        assertThat(initial.path("totalElements").asLong()).isZero();

        // 1. 최초 정지
        moderate(
                adminSession,
                targetId,
                "suspend",
                "  반복적인 이용 규칙 위반  ",
                MemberStatus.SUSPENDED,
                true
        );

        // 현재 상태가 SUSPENDED여도 이력 조회가 가능해야 한다.
        JsonNode suspended = readLogs(
                adminSession,
                targetId,
                0,
                20
        );

        assertThat(suspended.path("totalElements").asLong())
                .isEqualTo(1L);
        assertThat(
                suspended.path("items").path(0).path("reason").asString()
        ).isEqualTo("반복적인 이용 규칙 위반");

        // 2. 이미 정지 상태이므로 변경과 이력 추가가 없어야 한다.
        moderate(
                adminSession,
                targetId,
                "suspend",
                "중복 정지 요청",
                MemberStatus.SUSPENDED,
                false
        );

        // 3. 최초 정지 해제
        moderate(
                adminSession,
                targetId,
                "lift-suspension",
                "  재검토 후 정지 해제  ",
                MemberStatus.ACTIVE,
                true
        );

        // 4. 이미 정상 상태이므로 이력 추가가 없어야 한다.
        moderate(
                adminSession,
                targetId,
                "lift-suspension",
                "중복 해제 요청",
                MemberStatus.ACTIVE,
                false
        );

        List<MemberModerationLog> storedLogs = logsForTarget();

        assertThat(storedLogs).hasSize(2);

        MemberModerationLog suspendLog = storedLogs.get(0);
        MemberModerationLog restoreLog = storedLogs.get(1);

        JsonNode result = readLogs(
                adminSession,
                targetId,
                0,
                20
        );

        assertThat(result.path("items").size()).isEqualTo(2);
        assertThat(result.path("page").asInt()).isZero();
        assertThat(result.path("size").asInt()).isEqualTo(20);
        assertThat(result.path("totalElements").asLong()).isEqualTo(2L);
        assertThat(result.path("totalPages").asInt()).isEqualTo(1);
        assertThat(result.path("hasNext").asBoolean()).isFalse();
        assertThat(result.path("hasPrevious").asBoolean()).isFalse();

        // 최신 해제 기록이 먼저, 이전 정지 기록이 다음에 나온다.
        assertLogItem(result.path("items").path(0), restoreLog);
        assertLogItem(result.path("items").path(1), suspendLog);

        assertThat(result.path("items").path(0).path("action").asString())
                .isEqualTo("LIFT_SUSPENSION");
        assertThat(result.path("items").path(1).path("action").asString())
                .isEqualTo("SUSPEND");

        // 페이지 크기를 1로 줄여 실제 페이지 이동도 확인한다.
        JsonNode firstPage = readLogs(
                adminSession,
                targetId,
                0,
                1
        );

        JsonNode secondPage = readLogs(
                adminSession,
                targetId,
                1,
                1
        );

        assertThat(firstPage.path("items").size()).isEqualTo(1);
        assertLogItem(firstPage.path("items").path(0), restoreLog);
        assertThat(firstPage.path("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.path("hasNext").asBoolean()).isTrue();
        assertThat(firstPage.path("hasPrevious").asBoolean()).isFalse();

        assertThat(secondPage.path("items").size()).isEqualTo(1);
        assertLogItem(secondPage.path("items").path(0), suspendLog);
        assertThat(secondPage.path("hasNext").asBoolean()).isFalse();
        assertThat(secondPage.path("hasPrevious").asBoolean()).isTrue();

        JsonNode outOfRange = readLogs(
                adminSession,
                targetId,
                5,
                1
        );

        assertThat(outOfRange.path("items").size()).isZero();
        assertThat(outOfRange.path("page").asInt()).isEqualTo(5);
        assertThat(outOfRange.path("totalElements").asLong()).isEqualTo(2L);
        assertThat(outOfRange.path("totalPages").asInt()).isEqualTo(2);
        assertThat(outOfRange.path("hasNext").asBoolean()).isFalse();
        assertThat(outOfRange.path("hasPrevious").asBoolean()).isTrue();
    }

    @Test
    void 다른_회원의_이력은_조회_결과와_전체_개수에서_제외한다()
            throws Exception {
        MockHttpSession adminSession = login(adminEmail);
        Member otherTarget = saveActiveMember("다른");

        moderate(
                adminSession,
                targetId,
                "suspend",
                "조회 대상 회원 정지",
                MemberStatus.SUSPENDED,
                true
        );

        moderate(
                adminSession,
                otherTarget.getId(),
                "suspend",
                "다른 회원 정지",
                MemberStatus.SUSPENDED,
                true
        );

        JsonNode result = readLogs(
                adminSession,
                targetId,
                0,
                20
        );

        assertThat(result.path("items").size()).isEqualTo(1);
        assertThat(result.path("totalElements").asLong()).isEqualTo(1L);
        assertThat(result.path("totalPages").asInt()).isEqualTo(1);

        JsonNode item = result.path("items").path(0);

        assertThat(item.path("targetMemberId").asLong())
                .isEqualTo(targetId);
        assertThat(item.path("reason").asString())
                .isEqualTo("조회 대상 회원 정지");
    }

    @Test
    void 이력_없는_회원과_존재하지_않는_회원을_구분한다()
            throws Exception {
        MockHttpSession adminSession = login(adminEmail);

        JsonNode existing = readLogs(
                adminSession,
                targetId,
                0,
                20
        );

        assertThat(existing.path("items").size()).isZero();
        assertThat(existing.path("totalElements").asLong()).isZero();
        assertThat(existing.path("totalPages").asInt()).isZero();

        long missingMemberId = Long.MAX_VALUE;

        assertThat(memberRepository.existsById(missingMemberId))
                .isFalse();

        mockMvc.perform(
                        get(LOG_PATH, missingMemberId)
                                .session(adminSession)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_NOT_FOUND"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));
    }

    @Test
    void 대상_회원이_탈퇴해도_과거_조치_이력을_그대로_조회한다()
            throws Exception {
        MockHttpSession adminSession = login(adminEmail);

        moderate(
                adminSession,
                targetId,
                "suspend",
                "이용 규칙 위반",
                MemberStatus.SUSPENDED,
                true
        );

        moderate(
                adminSession,
                targetId,
                "lift-suspension",
                "재검토 후 정지 해제",
                MemberStatus.ACTIVE,
                true
        );

        JsonNode before = readLogs(
                adminSession,
                targetId,
                0,
                20
        );

        /*
         * 이 테스트는 이력 조회의 탈퇴 회원 처리 정책을 검증한다.
         * 탈퇴 API 자체의 검증은 기존 탈퇴 테스트에서 담당한다.
         */
        Member withdrawing = memberRepository.findById(targetId)
                .orElseThrow();

        withdrawing.withdraw(Instant.now());
        memberRepository.saveAndFlush(withdrawing);

        Member withdrawn = memberRepository.findById(targetId)
                .orElseThrow();

        assertThat(withdrawn.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(withdrawn.getEmail()).isNull();
        assertThat(withdrawn.getNickname()).isNull();

        JsonNode after = readLogs(
                adminSession,
                targetId,
                0,
                20
        );

        // 회원의 현재 상태가 바뀌어도 과거 이력은 변하지 않는다.
        assertThat(after.path("items"))
                .isEqualTo(before.path("items"));
        assertThat(after.path("totalElements").asLong())
                .isEqualTo(2L);

        // 마지막 조치의 afterStatus를 현재 DELETED로 덮어쓰면 안 된다.
        assertThat(after.path("items").path(0)
                .path("afterStatus").asString())
                .isEqualTo("ACTIVE");
    }

    @Test
    void 비로그인_요청은_이력을_조회할_수_없다()
            throws Exception {
        mockMvc.perform(get(LOG_PATH, targetId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.items")
                        .doesNotHaveJsonPath());
    }

    @Test
    void 실제_일반_회원_세션은_자신의_이력도_조회할_수_없다()
            throws Exception {
        MockHttpSession userSession = login(targetEmail);

        mockMvc.perform(
                        get(LOG_PATH, targetId)
                                .session(userSession)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.items")
                        .doesNotHaveJsonPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE", "VERSION", "STATUS"})
    void 로그인_후_관리자_자격이_변경되면_기존_세션으로_조회할_수_없다(
            String change
    ) throws Exception {
        MockHttpSession adminSession = login(adminEmail);

        // 변경 전에는 정상 조회된다.
        readLogs(adminSession, targetId, 0, 20);

        int updatedRows = switch (change) {
            case "ROLE" -> jdbcTemplate.update("""
                    UPDATE members
                       SET role = 'USER'
                     WHERE id = ?
                    """,
                    adminId
            );

            case "VERSION" -> jdbcTemplate.update("""
                    UPDATE members
                       SET credential_version = credential_version + 1
                     WHERE id = ?
                    """,
                    adminId
            );

            case "STATUS" -> jdbcTemplate.update("""
                    UPDATE members
                       SET status = 'SUSPENDED'
                     WHERE id = ?
                    """,
                    adminId
            );

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 테스트 조건: " + change
            );
        };

        assertThat(updatedRows).isEqualTo(1);

        mockMvc.perform(
                        get(LOG_PATH, targetId)
                                .session(adminSession)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.items")
                        .doesNotHaveJsonPath());

        assertThat(adminSession.isInvalid()).isTrue();
    }

    // ---------------------------------------------------------------------
    // 이력 조회
    // ---------------------------------------------------------------------

    private JsonNode readLogs(
            MockHttpSession session,
            Long memberId,
            int page,
            int size
    ) throws Exception {
        /*
         * 실제 로그인 세션만 전달한다.
         * GET 조회이므로 CSRF 토큰은 보내지 않는다.
         */
        MvcResult result = mockMvc.perform(
                        get(LOG_PATH, memberId)
                                .session(session)
                                .queryParam("page", Integer.toString(page))
                                .queryParam("size", Integer.toString(size))
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
                .andReturn();

        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );

        assertThat(body.path("items").isArray()).isTrue();

        for (JsonNode item : body.path("items")) {
            assertThat(item.has("email")).isFalse();
            assertThat(item.has("passwordHash")).isFalse();
            assertThat(item.has("credentialVersion")).isFalse();
        }

        return body;
    }

    private void assertLogItem(
            JsonNode item,
            MemberModerationLog storedLog
    ) {
        assertThat(item.path("logId").asLong())
                .isEqualTo(storedLog.getId());
        assertThat(item.path("actorMemberId").asLong())
                .isEqualTo(storedLog.getActorMemberId());
        assertThat(item.path("targetMemberId").asLong())
                .isEqualTo(storedLog.getTargetMemberId());
        assertThat(item.path("action").asString())
                .isEqualTo(storedLog.getAction().name());
        assertThat(item.path("beforeStatus").asString())
                .isEqualTo(storedLog.getBeforeStatus().name());
        assertThat(item.path("afterStatus").asString())
                .isEqualTo(storedLog.getAfterStatus().name());
        assertThat(item.path("reason").asString())
                .isEqualTo(storedLog.getReason());

        Instant responseTime = Instant.parse(
                item.path("createdAt").asString()
        );

        assertThat(responseTime).isEqualTo(storedLog.getCreatedAt());
    }

    private List<MemberModerationLog> logsForTarget() {
        return logRepository.findAll().stream()
                .filter(log -> targetId.equals(
                        log.getTargetMemberId()
                ))
                .sorted(Comparator.comparing(
                        MemberModerationLog::getId
                ))
                .toList();
    }

    // ---------------------------------------------------------------------
    // 실제 정지·해제 요청
    // ---------------------------------------------------------------------

    private void moderate(
            MockHttpSession adminSession,
            Long memberId,
            String action,
            String reason,
            MemberStatus expectedStatus,
            boolean expectedChanged
    ) throws Exception {
        CsrfContext csrf = fetchCsrf(adminSession);

        mockMvc.perform(
                        post(MODERATION_PATH, memberId, action)
                                .session(csrf.session())
                                .header(
                                        csrf.headerName(),
                                        csrf.token()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of("reason", reason)
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.memberId")
                        .value(is(memberId), Long.class))
                .andExpect(jsonPath("$.status")
                        .value(expectedStatus.name()))
                .andExpect(jsonPath("$.changed")
                        .value(expectedChanged));
    }

    // ---------------------------------------------------------------------
    // 실제 로그인 및 CSRF 조회
    // ---------------------------------------------------------------------

    private MockHttpSession login(String email) throws Exception {
        CsrfContext anonymous = fetchCsrf(null);

        MvcResult result = mockMvc.perform(
                        post(LOGIN_PATH)
                                .session(anonymous.session())
                                .header(
                                        anonymous.headerName(),
                                        anonymous.token()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "email", email,
                                                        "password", PASSWORD
                                                )
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticatedSession =
                (MockHttpSession) result
                        .getRequest()
                        .getSession(false);

        assertThat(authenticatedSession).isNotNull();
        assertThat(authenticatedSession.isInvalid()).isFalse();

        return authenticatedSession;
    }

    private CsrfContext fetchCsrf(
            MockHttpSession session
    ) throws Exception {
        MockHttpServletRequestBuilder request = get(CSRF_PATH);

        if (session != null) {
            request.session(session);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession actualSession =
                (MockHttpSession) result
                        .getRequest()
                        .getSession(false);

        assertThat(actualSession).isNotNull();
        assertThat(actualSession.isInvalid()).isFalse();

        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );

        String headerName = body.path("headerName").asString();
        String token = body.path("token").asString();

        assertThat(headerName).isEqualTo("X-CSRF-TOKEN");
        assertThat(token).isNotBlank();

        return new CsrfContext(
                actualSession,
                headerName,
                token
        );
    }

    // ---------------------------------------------------------------------
    // 테스트 데이터
    // ---------------------------------------------------------------------

    private Member saveActiveMember(String nicknamePrefix) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        Member member = Member.createPending(
                "moderation-log-" + suffix + "@example.com",
                passwordHash,
                nicknamePrefix + suffix
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private record CsrfContext(
            MockHttpSession session,
            String headerName,
            String token
    ) {
    }
}