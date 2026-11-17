package io.github.takgeun.iyum.admin.dashboard.api;

import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardCountsResult;
import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardPendingReportResult;
import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardReadException;
import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardReadService;
import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetType;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminDashboardReadController.class,
        properties = {
                "spring.config.import=",
                "spring.docker.compose.enabled=false",
                "iyum.cors.allowed-origin=http://127.0.0.1:8443"
        }
)
@Import({
        SecurityConfig.class,
        DevCorsConfig.class,
        GlobalExceptionHandler.class
})
@ActiveProfiles("dev")
class AdminDashboardReadControllerTest {

    private static final String PATH =
            "/api/v1/admin/dashboard";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final String UNTRUSTED_ORIGIN =
            "https://untrusted.example";

    private static final Long ACTOR_ID = 40L;
    private static final long VERSION = 3L;

    private static final Instant NOW =
            Instant.parse("2026-11-17T03:00:00Z");

    private static final LocalDate BASE_DATE =
            LocalDate.of(2026, 11, 17);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminDashboardReadService dashboardReadService;

    @MockitoBean
    MemberRepository memberRepository;

    @Test
    void 관리자_세션으로_조회하고_요약과_신고_목록을_반환한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        Instant postReportedAt = NOW.minusSeconds(120);
        Instant commentReportedAt = NOW.minusSeconds(60);

        AdminDashboardResult result = new AdminDashboardResult(
                NOW,
                BASE_DATE,
                new AdminDashboardCountsResult(
                        7L,
                        3L,
                        4L,
                        2L
                ),
                List.of(
                        new AdminDashboardPendingReportResult(
                                100L,
                                AdminReportTargetType.POST,
                                10L,
                                ContentReportReasonType.SPAM_ADVERTISING,
                                postReportedAt
                        ),
                        new AdminDashboardPendingReportResult(
                                101L,
                                AdminReportTargetType.COMMENT,
                                20L,
                                ContentReportReasonType.ABUSE_HATE,
                                commentReportedAt
                        )
                )
        );

        when(dashboardReadService.getDashboard(ACTOR_ID, VERSION))
                .thenReturn(result);

        // GET에는 CSRF 토큰을 전달하지 않는다.
        // 요청 파라미터로 세션의 회원 ID와 인증 버전을 바꿀 수 없다.
        expectSuccess(mockMvc.perform(
                adminRequest()
                        .queryParam("actorMemberId", "999")
                        .queryParam("credentialVersion", "999")
        ))
                .andExpect(jsonPath("$.queriedAt")
                        .value(NOW.toString()))
                .andExpect(jsonPath("$.baseDate")
                        .value(BASE_DATE.toString()))
                .andExpect(jsonPath("$.counts.pendingReportCount")
                        .value(7))
                .andExpect(jsonPath("$.counts.todayReportCount")
                        .value(3))
                .andExpect(jsonPath("$.counts.todayPostCount")
                        .value(4))
                .andExpect(jsonPath("$.counts.todayMemberCount")
                        .value(2))
                .andExpect(jsonPath("$.pendingReports").isArray())
                .andExpect(jsonPath("$.pendingReports.length()")
                        .value(2))
                .andExpect(jsonPath("$.pendingReports[0].reportId")
                        .value(100))
                .andExpect(jsonPath("$.pendingReports[0].targetType")
                        .value("POST"))
                .andExpect(jsonPath("$.pendingReports[0].targetId")
                        .value(10))
                .andExpect(jsonPath("$.pendingReports[0].reasonType")
                        .value("SPAM_ADVERTISING"))
                .andExpect(jsonPath("$.pendingReports[0].createdAt")
                        .value(postReportedAt.toString()))
                .andExpect(jsonPath("$.pendingReports[1].reportId")
                        .value(101))
                .andExpect(jsonPath("$.pendingReports[1].targetType")
                        .value("COMMENT"))
                .andExpect(jsonPath("$.pendingReports[1].targetId")
                        .value(20))
                .andExpect(jsonPath("$.pendingReports[1].reasonType")
                        .value("ABUSE_HATE"))
                .andExpect(jsonPath("$.pendingReports[1].createdAt")
                        .value(commentReportedAt.toString()));

        verify(dashboardReadService)
                .getDashboard(ACTOR_ID, VERSION);

        verifyNoMoreInteractions(dashboardReadService);
    }

    @Test
    void 데이터가_없어도_0과_빈_배열을_생략하지_않는다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(dashboardReadService.getDashboard(ACTOR_ID, VERSION))
                .thenReturn(new AdminDashboardResult(
                        NOW,
                        BASE_DATE,
                        new AdminDashboardCountsResult(
                                0L,
                                0L,
                                0L,
                                0L
                        ),
                        List.of()
                ));

        expectSuccess(mockMvc.perform(adminRequest()))
                .andExpect(jsonPath("$.queriedAt")
                        .value(NOW.toString()))
                .andExpect(jsonPath("$.baseDate")
                        .value(BASE_DATE.toString()))
                .andExpect(jsonPath("$.counts.pendingReportCount")
                        .value(0))
                .andExpect(jsonPath("$.counts.todayReportCount")
                        .value(0))
                .andExpect(jsonPath("$.counts.todayPostCount")
                        .value(0))
                .andExpect(jsonPath("$.counts.todayMemberCount")
                        .value(0))
                .andExpect(jsonPath("$.pendingReports").isArray())
                .andExpect(jsonPath("$.pendingReports").isEmpty());

        verify(dashboardReadService)
                .getDashboard(ACTOR_ID, VERSION);
    }

    @ParameterizedTest
    @CsvSource({
            "AUTHENTICATION_REQUIRED, 401",
            "ADMIN_REQUIRED, 403"
    })
    void 서비스_예외를_정해진_HTTP_상태와_코드로_반환한다(
            AdminDashboardReadException.Code code,
            int expectedStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminDashboardReadException exception =
                new AdminDashboardReadException(code);

        when(dashboardReadService.getDashboard(ACTOR_ID, VERSION))
                .thenThrow(exception);

        expectError(
                mockMvc.perform(adminRequest()),
                expectedStatus,
                code.name()
        )
                .andExpect(jsonPath("$.message")
                        .value(exception.getMessage()))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));

        verify(dashboardReadService)
                .getDashboard(ACTOR_ID, VERSION);
    }

    @Test
    void 비로그인은_401이며_서비스를_호출하지_않는다()
            throws Exception {
        expectError(
                mockMvc.perform(request()),
                401,
                "AUTHENTICATION_REQUIRED"
        );

        verifyNoInteractions(
                memberRepository,
                dashboardReadService
        );
    }

    @Test
    void 유효한_일반_회원은_403으로_거부한다()
            throws Exception {
        givenValidSession(MemberRole.USER);

        expectError(
                mockMvc.perform(
                        request()
                                .with(user(principal(MemberRole.USER)))
                ),
                403,
                "ACCESS_DENIED"
        );

        verifyNoInteractions(dashboardReadService);
    }

    @Test
    void DB에서_무효로_확인된_관리자_세션은_401로_거부한다()
            throws Exception {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_ID,
                        MemberStatus.ACTIVE,
                        VERSION,
                        MemberRole.ADMIN
                ))
                .thenReturn(false);

        expectError(
                mockMvc.perform(adminRequest()),
                401,
                "AUTHENTICATION_REQUIRED"
        );

        verify(memberRepository)
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_ID,
                        MemberStatus.ACTIVE,
                        VERSION,
                        MemberRole.ADMIN
                );

        verifyNoInteractions(dashboardReadService);
    }

    @Test
    void MemberPrincipal이_없는_인증은_컨트롤러에서_거부한다()
            throws Exception {
        expectError(
                mockMvc.perform(
                        request()
                                .with(user("test-admin").roles("ADMIN"))
                ),
                401,
                "AUTHENTICATION_REQUIRED"
        );

        verifyNoInteractions(
                memberRepository,
                dashboardReadService
        );
    }

    @Test
    void 허용된_Origin의_GET_preflight는_인증_없이_성공한다()
            throws Exception {
        mockMvc.perform(
                        options(PATH)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "accept"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        "GET,OPTIONS"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        "accept"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_MAX_AGE,
                        "600"
                ));

        verifyNoInteractions(
                memberRepository,
                dashboardReadService
        );
    }

    @Test
    void 허용되지_않은_Origin의_preflight는_거부한다()
            throws Exception {
        mockMvc.perform(
                        options(PATH)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        UNTRUSTED_ORIGIN
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(
                memberRepository,
                dashboardReadService
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void 변경_메서드의_preflight는_허용하지_않는다(
            String method
    ) throws Exception {
        mockMvc.perform(
                        options(PATH)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        method
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(
                memberRepository,
                dashboardReadService
        );
    }

    @Test
    void 허용되지_않은_Origin의_실제_GET도_거부한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        get(PATH)
                                .accept(MediaType.APPLICATION_JSON)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        UNTRUSTED_ORIGIN
                                )
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(dashboardReadService);
    }

    private ResultActions expectSuccess(ResultActions response)
            throws Exception {
        return response
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ));
    }

    private ResultActions expectError(
            ResultActions response,
            int expectedStatus,
            String expectedCode
    ) throws Exception {
        return response
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ));
    }

    private MockHttpServletRequestBuilder request() {
        return get(PATH)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ORIGIN, ORIGIN);
    }

    private MockHttpServletRequestBuilder adminRequest() {
        return request()
                .with(user(principal(MemberRole.ADMIN)));
    }

    private void givenValidSession(MemberRole role) {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_ID,
                        MemberStatus.ACTIVE,
                        VERSION,
                        role
                ))
                .thenReturn(true);
    }

    private MemberPrincipal principal(MemberRole role) {
        return new MemberPrincipal(
                ACTOR_ID,
                "dashboard-admin@example.com",
                "test-only-password-hash",
                "대시보드관리자",
                MemberStatus.ACTIVE,
                role,
                VERSION
        );
    }
}