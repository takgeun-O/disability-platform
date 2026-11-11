package io.github.takgeun.iyum.admin.member.api;

import io.github.takgeun.iyum.admin.member.domain.MemberModerationAction;
import io.github.takgeun.iyum.admin.member.domain.MemberModerationLog;
import io.github.takgeun.iyum.admin.member.infrastructure.MemberModerationLogRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
class AdminMemberModerationSessionIntegrationTest {

    private static final String MODERATION_PATH =
            "/api/v1/admin/members/{memberId}/{action}";

    private static final String LOGIN_PATH =
            "/api/v1/auth/login";

    private static final String CSRF_PATH =
            "/api/v1/auth/csrf";

    private static final String ME_PATH =
            "/api/v1/auth/me";

    private static final String PASSWORD =
            "MemberSession123!";

    private static final long ADMIN_VERSION = 3L;
    private static final long TARGET_VERSION = 5L;

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

    /*
     * 클래스나 테스트 메서드에 @Transactional을 붙이지 않는다.
     *
     * 실제 HTTP 요청이 끝난 후 DB에 반영된 상태를
     * 다음 요청의 인증 필터가 조회하도록 구성한다.
     *
     * 테스트마다 고유한 회원을 생성하고 대상 회원 ID로 이력을
     * 구분하므로 전체 테이블을 삭제할 필요가 없다.
     */
    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        String passwordHash = passwordEncoder.encode(PASSWORD);

        Member admin = saveActiveMember(
                "moderation-admin-" + suffix + "@example.com",
                "관리검증" + suffix,
                passwordHash
        );

        adminId = admin.getId();
        adminEmail = admin.getEmail();

        Member target = saveActiveMember(
                "moderation-user-" + suffix + "@example.com",
                "회원검증" + suffix,
                passwordHash
        );

        targetId = target.getId();
        targetEmail = target.getEmail();

        /*
         * 테스트 데이터 준비용 SQL이다.
         * 실제 로그인 전에 ADMIN 역할과 인증 버전을 설정한다.
         */
        int adminRows = jdbcTemplate.update("""
                UPDATE members
                   SET role = 'ADMIN',
                       credential_version = ?
                 WHERE id = ?
                """,
                ADMIN_VERSION,
                adminId
        );

        int targetRows = jdbcTemplate.update("""
                UPDATE members
                   SET credential_version = ?
                 WHERE id = ?
                """,
                TARGET_VERSION,
                targetId
        );

        assertThat(adminRows).isEqualTo(1);
        assertThat(targetRows).isEqualTo(1);
        assertThat(logsForTarget()).isEmpty();
    }

    @Test
    void 실제_관리자_세션으로_정지와_해제를_처리하고_이력을_저장한다()
            throws Exception {
        MockHttpSession adminSession = login(adminEmail);

        expectMe(adminSession, adminId);

        moderate(
                adminSession,
                "suspend",
                "  반복적인 이용 규칙 위반  ",
                MemberStatus.SUSPENDED,
                true
        );

        assertTargetState(
                MemberStatus.SUSPENDED,
                TARGET_VERSION + 1
        );

        List<MemberModerationLog> suspendedLogs = logsForTarget();

        assertThat(suspendedLogs).hasSize(1);

        assertLog(
                suspendedLogs.get(0),
                MemberModerationAction.SUSPEND,
                MemberStatus.ACTIVE,
                MemberStatus.SUSPENDED,
                "반복적인 이용 규칙 위반"
        );

        Long suspendLogId = suspendedLogs.get(0).getId();

        // 대상 회원을 정지해도 관리자의 세션은 유지되어야 한다.
        expectMe(adminSession, adminId);

        moderate(
                adminSession,
                "lift-suspension",
                "  재검토 후 이용 정지 해제  ",
                MemberStatus.ACTIVE,
                true
        );

        assertTargetState(
                MemberStatus.ACTIVE,
                TARGET_VERSION + 2
        );

        List<MemberModerationLog> allLogs = logsForTarget();

        assertThat(allLogs).hasSize(2);

        // 기존 정지 기록을 변경하지 않고 해제 기록을 추가한다.
        assertThat(allLogs.get(0).getId())
                .isEqualTo(suspendLogId);

        assertLog(
                allLogs.get(0),
                MemberModerationAction.SUSPEND,
                MemberStatus.ACTIVE,
                MemberStatus.SUSPENDED,
                "반복적인 이용 규칙 위반"
        );

        assertLog(
                allLogs.get(1),
                MemberModerationAction.LIFT_SUSPENSION,
                MemberStatus.SUSPENDED,
                MemberStatus.ACTIVE,
                "재검토 후 이용 정지 해제"
        );

        expectMe(adminSession, adminId);

        Member storedAdmin = memberRepository.findById(adminId)
                .orElseThrow();

        assertThat(storedAdmin.getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
        assertThat(storedAdmin.getCredentialVersion())
                .isEqualTo(ADMIN_VERSION);
    }

    @Test
    void 정지된_회원의_여러_기존_세션은_다음_보호_API_요청에서_차단된다()
            throws Exception {
        MockHttpSession adminSession = login(adminEmail);

        MockHttpSession firstSession = login(targetEmail);
        MockHttpSession secondSession = login(targetEmail);

        assertThat(firstSession.getId())
                .isNotEqualTo(secondSession.getId());

        expectMe(firstSession, targetId);
        expectMe(secondSession, targetId);

        moderate(
                adminSession,
                "suspend",
                "이용 규칙 위반 확인",
                MemberStatus.SUSPENDED,
                true
        );

        /*
         * 서버가 모든 세션 객체를 즉시 찾아서 삭제하는 방식이 아니다.
         * 각 세션의 다음 요청에서 DB 상태와 인증 버전을 검증한다.
         */
        assertThat(firstSession.isInvalid()).isFalse();
        assertThat(secondSession.isInvalid()).isFalse();

        expectSessionRejected(firstSession);
        expectSessionRejected(secondSession);

        assertTargetState(
                MemberStatus.SUSPENDED,
                TARGET_VERSION + 1
        );
        assertThat(logsForTarget()).hasSize(1);

        expectMe(adminSession, adminId);
    }

    @Test
    void 정지된_회원은_올바른_비밀번호로도_새로_로그인할_수_없다()
            throws Exception {
        MockHttpSession adminSession = login(adminEmail);

        moderate(
                adminSession,
                "suspend",
                "이용 규칙 위반 확인",
                MemberStatus.SUSPENDED,
                true
        );

        // 기존 로그인 세션을 재사용하지 않고 새 익명 세션을 만든다.
        CsrfContext anonymous = fetchCsrf(null);

        performLogin(targetEmail, anonymous)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("ACCOUNT_UNAVAILABLE"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));

        // 실패한 로그인 요청이 인증 정보를 남기지 않았는지 확인한다.
        mockMvc.perform(
                        get(ME_PATH)
                                .session(anonymous.session())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        assertTargetState(
                MemberStatus.SUSPENDED,
                TARGET_VERSION + 1
        );
        assertThat(logsForTarget()).hasSize(1);

        expectMe(adminSession, adminId);
    }

    @Test
    void 정지_중_사용하지_않은_기존_세션도_해제_후_되살아나지_않는다()
            throws Exception {
        MockHttpSession adminSession = login(adminEmail);
        MockHttpSession oldSession = login(targetEmail);

        expectMe(oldSession, targetId);

        moderate(
                adminSession,
                "suspend",
                "이용 규칙 위반 확인",
                MemberStatus.SUSPENDED,
                true
        );

        /*
         * 중요:
         * 여기에서 oldSession으로 CSRF 조회나 /auth/me 등
         * 어떠한 HTTP 요청도 보내지 않는다.
         *
         * 정지 중 요청해서 이미 무효화된 세션을 검사하는 것과
         * 다른 상황을 검증하기 위함이다.
         */
        assertThat(oldSession.isInvalid()).isFalse();

        moderate(
                adminSession,
                "lift-suspension",
                "재검토 후 정지 해제",
                MemberStatus.ACTIVE,
                true
        );

        assertTargetState(
                MemberStatus.ACTIVE,
                TARGET_VERSION + 2
        );

        // 아직 사용하지 않은 세션 객체 자체는 남아 있다.
        assertThat(oldSession.isInvalid()).isFalse();

        /*
         * 회원은 ACTIVE로 돌아왔지만 인증 버전이 다르므로
         * 정지 전의 오래된 세션은 차단되어야 한다.
         */
        expectSessionRejected(oldSession);

        // 정지 해제 후 새로 로그인한 세션만 정상적으로 이용한다.
        MockHttpSession newSession = login(targetEmail);

        expectMe(newSession, targetId);
        assertThat(newSession.isInvalid()).isFalse();

        assertThat(logsForTarget()).hasSize(2);

        expectMe(adminSession, adminId);
    }

    @Test
    void 정상_회원의_정지_해제_요청은_기존_세션과_인증_버전을_유지한다()
            throws Exception {
        MockHttpSession adminSession = login(adminEmail);
        MockHttpSession targetSession = login(targetEmail);

        Member before = storedTarget();

        expectMe(targetSession, targetId);

        moderate(
                adminSession,
                "lift-suspension",
                "현재 상태 재확인",
                MemberStatus.ACTIVE,
                false
        );

        Member after = storedTarget();

        assertThat(after.getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
        assertThat(after.getCredentialVersion())
                .isEqualTo(before.getCredentialVersion());
        assertThat(after.getUpdatedAt())
                .isEqualTo(before.getUpdatedAt());

        assertThat(logsForTarget()).isEmpty();

        // 실제 변경이 없으므로 기존 로그인 세션도 계속 사용할 수 있다.
        expectMe(targetSession, targetId);
        assertThat(targetSession.isInvalid()).isFalse();

        expectMe(adminSession, adminId);
    }

    // ---------------------------------------------------------------------
    // 회원 운영 조치 HTTP 요청
    // ---------------------------------------------------------------------

    private void moderate(
            MockHttpSession adminSession,
            String action,
            String reason,
            MemberStatus expectedStatus,
            boolean expectedChanged
    ) throws Exception {
        /*
         * 로그인 성공 시 기존 CSRF 토큰이 폐기된다.
         * 로그인 전 토큰을 재사용하지 않고 현재 세션에서 조회한다.
         */
        CsrfContext csrf = fetchCsrf(adminSession);

        mockMvc.perform(
                        post(MODERATION_PATH, targetId, action)
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
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.memberId")
                        .value(is(targetId), Long.class))
                .andExpect(jsonPath("$.status")
                        .value(expectedStatus.name()))
                .andExpect(jsonPath("$.changed")
                        .value(expectedChanged))
                .andExpect(jsonPath("$.credentialVersion")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.passwordHash")
                        .doesNotHaveJsonPath());
    }

    // ---------------------------------------------------------------------
    // 실제 로그인 및 CSRF 조회
    // ---------------------------------------------------------------------

    private MockHttpSession login(String email) throws Exception {
        CsrfContext anonymous = fetchCsrf(null);

        MvcResult result = performLogin(email, anonymous)
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andReturn();

        /*
         * 로그인 과정에서 세션 ID가 변경될 수 있으므로
         * 로그인 요청 결과에서 인증된 세션을 가져온다.
         */
        MockHttpSession authenticatedSession =
                (MockHttpSession) result
                        .getRequest()
                        .getSession(false);

        assertThat(authenticatedSession).isNotNull();
        assertThat(authenticatedSession.isInvalid()).isFalse();

        return authenticatedSession;
    }

    private ResultActions performLogin(
            String email,
            CsrfContext csrf
    ) throws Exception {
        return mockMvc.perform(
                post(LOGIN_PATH)
                        .session(csrf.session())
                        .header(
                                csrf.headerName(),
                                csrf.token()
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
        );
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
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
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
    // 로그인 세션 검증
    // ---------------------------------------------------------------------

    private void expectMe(
            MockHttpSession session,
            Long expectedMemberId
    ) throws Exception {
        mockMvc.perform(
                        get(ME_PATH)
                                .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.memberId")
                        .value(is(expectedMemberId), Long.class));
    }

    private void expectSessionRejected(
            MockHttpSession session
    ) throws Exception {
        mockMvc.perform(
                        get(ME_PATH)
                                .session(session)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.email")
                        .doesNotHaveJsonPath());

        assertThat(session.isInvalid()).isTrue();
    }

    // ---------------------------------------------------------------------
    // DB 결과 검증
    // ---------------------------------------------------------------------

    private Member storedTarget() {
        return memberRepository.findById(targetId)
                .orElseThrow();
    }

    private void assertTargetState(
            MemberStatus expectedStatus,
            long expectedVersion
    ) {
        Member target = storedTarget();

        assertThat(target.getStatus())
                .isEqualTo(expectedStatus);
        assertThat(target.getCredentialVersion())
                .isEqualTo(expectedVersion);

        // 정지·해제는 탈퇴나 개인정보 삭제가 아니다.
        assertThat(target.getEmail()).isEqualTo(targetEmail);
        assertThat(target.getPasswordHash()).isNotBlank();
        assertThat(target.getWithdrawnAt()).isNull();
    }

    private List<MemberModerationLog> logsForTarget() {
        /*
         * 테스트 결과 확인용 조회다.
         * 운영용 이력 조회 API는 다음 단계에서 별도로 구현한다.
         */
        return logRepository.findAll().stream()
                .filter(log -> targetId.equals(
                        log.getTargetMemberId()
                ))
                .sorted(Comparator.comparing(
                        MemberModerationLog::getId
                ))
                .toList();
    }

    private void assertLog(
            MemberModerationLog log,
            MemberModerationAction expectedAction,
            MemberStatus expectedBefore,
            MemberStatus expectedAfter,
            String expectedReason
    ) {
        assertThat(log.getId()).isNotNull();
        assertThat(log.getActorMemberId()).isEqualTo(adminId);
        assertThat(log.getTargetMemberId()).isEqualTo(targetId);
        assertThat(log.getAction()).isEqualTo(expectedAction);
        assertThat(log.getBeforeStatus()).isEqualTo(expectedBefore);
        assertThat(log.getAfterStatus()).isEqualTo(expectedAfter);
        assertThat(log.getReason()).isEqualTo(expectedReason);
        assertThat(log.getCreatedAt()).isNotNull();
    }

    // ---------------------------------------------------------------------
    // 테스트 데이터 준비
    // ---------------------------------------------------------------------

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

    private record CsrfContext(
            MockHttpSession session,
            String headerName,
            String token
    ) {
    }
}