package io.github.takgeun.iyum.admin.report.api;

import io.github.takgeun.iyum.admin.report.application.AdminReportReadException;
import io.github.takgeun.iyum.admin.report.application.AdminReportReadService;
import io.github.takgeun.iyum.admin.report.application.AdminReportSummaryCountsResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportSummaryReadService;
import io.github.takgeun.iyum.admin.report.application.AdminReportSummaryResult;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
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
        controllers = {
                AdminReportSummaryReadController.class,
                AdminReportReadController.class
        },
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
class AdminReportSummaryReadControllerTest {

    private static final String PATH =
            "/api/v1/admin/reports/summary";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Long ACTOR_ID = 40L;
    private static final long VERSION = 3L;

    private static final Instant NOW =
            Instant.parse("2026-11-17T03:00:00Z");

    private static final LocalDate BASE_DATE =
            LocalDate.of(2026, 11, 17);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminReportSummaryReadService summaryReadService;

    @MockitoBean
    AdminReportReadService reportReadService;

    @MockitoBean
    MemberRepository memberRepository;

    @ParameterizedTest
    @CsvSource({
            "7, 3, 2",
            "0, 0, 0"
    })
    void 요약_경로로_조회하고_0을_포함한_모든_필드를_반환한다(
            long pendingCount,
            long todayCount,
            long handledCount
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminReportSummaryResult result =
                new AdminReportSummaryResult(
                        NOW,
                        BASE_DATE,
                        new AdminReportSummaryCountsResult(
                                pendingCount,
                                todayCount,
                                handledCount
                        )
                );

        when(summaryReadService.getSummary(
                ACTOR_ID,
                VERSION
        )).thenReturn(result);

        // GET에는 CSRF 토큰을 전달하지 않는다.
        // 요청 파라미터로 세션의 회원 ID와 인증 버전을 바꿀 수 없다.
        mockMvc.perform(
                        adminRequest()
                                .queryParam("actorMemberId", "999")
                                .queryParam("credentialVersion", "999")
                )
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(
                        AdminReportSummaryReadController.class
                ))
                .andExpect(handler().methodName("getSummary"))
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
                ))
                .andExpect(jsonPath("$.queriedAt")
                        .value(NOW.toString()))
                .andExpect(jsonPath("$.baseDate")
                        .value(BASE_DATE.toString()))
                .andExpect(jsonPath("$.counts").isMap())
                .andExpect(jsonPath("$.counts.pendingReportCount")
                        .value((int) pendingCount))
                .andExpect(jsonPath("$.counts.todayReportCount")
                        .value((int) todayCount))
                .andExpect(jsonPath("$.counts.todayHandledReportCount")
                        .value((int) handledCount));

        verify(summaryReadService).getSummary(
                ACTOR_ID,
                VERSION
        );
        verifyNoMoreInteractions(summaryReadService);

        // summary가 기존 /{reportId} 처리로 넘어가지 않아야 한다.
        verifyNoInteractions(reportReadService);
    }

    @Test
    void 숫자_ID_경로는_기존_신고_상세_컨트롤러로_연결된다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        // 이 테스트는 상세 응답 내용이 아닌 경로 연결을 검증한다.
        when(reportReadService.getReport(
                ACTOR_ID,
                VERSION,
                100L
        )).thenThrow(new AdminReportReadException(
                AdminReportReadException.Code.REPORT_NOT_FOUND
        ));

        expectError(
                mockMvc.perform(
                        get("/api/v1/admin/reports/100")
                                .accept(MediaType.APPLICATION_JSON)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .with(user(principal(MemberRole.ADMIN)))
                ),
                404,
                "REPORT_NOT_FOUND"
        )
                .andExpect(handler().handlerType(
                        AdminReportReadController.class
                ))
                .andExpect(handler().methodName("getReport"));

        verify(reportReadService).getReport(
                ACTOR_ID,
                VERSION,
                100L
        );
        verifyNoInteractions(summaryReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "AUTHENTICATION_REQUIRED, 401",
            "ADMIN_REQUIRED, 403"
    })
    void 서비스_예외를_정해진_HTTP_상태와_코드로_반환한다(
            AdminReportReadException.Code code,
            int expectedStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminReportReadException exception =
                new AdminReportReadException(code);

        when(summaryReadService.getSummary(
                ACTOR_ID,
                VERSION
        )).thenThrow(exception);

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

        verify(summaryReadService).getSummary(
                ACTOR_ID,
                VERSION
        );
        verifyNoInteractions(reportReadService);
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
                summaryReadService,
                reportReadService
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

        verifyNoInteractions(
                summaryReadService,
                reportReadService
        );
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

        verifyNoInteractions(
                summaryReadService,
                reportReadService
        );
    }

    @Test
    void MemberPrincipal이_없는_인증은_401로_거부한다()
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
                summaryReadService,
                reportReadService
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
                ));

        verifyNoInteractions(
                memberRepository,
                summaryReadService,
                reportReadService
        );
    }

    @Test
    void 허용되지_않은_Origin의_preflight는_거부한다()
            throws Exception {
        mockMvc.perform(
                        options(PATH)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://untrusted.example"
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
                summaryReadService,
                reportReadService
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
                summaryReadService,
                reportReadService
        );
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
                "report-summary-admin@example.com",
                "test-only-password-hash",
                "신고요약관리자",
                MemberStatus.ACTIVE,
                role,
                VERSION
        );
    }
}