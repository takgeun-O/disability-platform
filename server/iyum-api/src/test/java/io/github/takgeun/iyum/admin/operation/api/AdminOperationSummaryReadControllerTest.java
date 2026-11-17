package io.github.takgeun.iyum.admin.operation.api;

import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogReadException;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogReadService;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationSummaryCountsResult;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationSummaryReadService;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationSummaryResult;
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
import static org.hamcrest.Matchers.is;
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
                AdminOperationSummaryReadController.class,
                AdminOperationLogReadController.class
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
class AdminOperationSummaryReadControllerTest {

    private static final String PATH =
            "/api/v1/admin/operation-logs/summary";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Long ACTOR_ID = 40L;
    private static final long VERSION = 3L;

    private static final Instant NOW =
            Instant.parse("2026-11-18T03:00:00Z");

    private static final LocalDate BASE_DATE =
            LocalDate.of(2026, 11, 18);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminOperationSummaryReadService summaryReadService;

    @MockitoBean
    AdminOperationLogReadService operationLogReadService;

    @MockitoBean
    MemberRepository memberRepository;

    @ParameterizedTest
    @CsvSource({
            "20, 7, 3, 5",
            "0, 0, 0, 0",
            "9000000000, 3000000000, 2200000000, 2300000000"
    })
    void 요약_경로는_상세_조회와_구분되며_0과_long_수치를_반환한다(
            long totalOperationLogCount,
            long todayOperationLogCount,
            long todayHideCount,
            long todayRestoreCount
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminOperationSummaryResult result =
                new AdminOperationSummaryResult(
                        NOW,
                        BASE_DATE,
                        new AdminOperationSummaryCountsResult(
                                totalOperationLogCount,
                                todayOperationLogCount,
                                todayHideCount,
                                todayRestoreCount
                        )
                );

        when(summaryReadService.getSummary(
                ACTOR_ID,
                VERSION
        )).thenReturn(result);

        // GET이므로 CSRF 토큰을 전달하지 않는다.
        // 요청 파라미터로 인증된 회원 정보를 바꿀 수 없어야 한다.
        mockMvc.perform(
                        adminRequest()
                                .queryParam("actorMemberId", "999")
                                .queryParam("credentialVersion", "999")
                )
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(
                        AdminOperationSummaryReadController.class
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
                .andExpect(jsonPath("$.counts.totalOperationLogCount")
                        .isNumber())
                .andExpect(jsonPath("$.counts.totalOperationLogCount")
                        .value(is(totalOperationLogCount), Long.class))
                .andExpect(jsonPath("$.counts.todayOperationLogCount")
                        .isNumber())
                .andExpect(jsonPath("$.counts.todayOperationLogCount")
                        .value(is(todayOperationLogCount), Long.class))
                .andExpect(jsonPath("$.counts.todayHideCount")
                        .isNumber())
                .andExpect(jsonPath("$.counts.todayHideCount")
                        .value(is(todayHideCount), Long.class))
                .andExpect(jsonPath("$.counts.todayRestoreCount")
                        .isNumber())
                .andExpect(jsonPath("$.counts.todayRestoreCount")
                        .value(is(todayRestoreCount), Long.class));

        verify(summaryReadService).getSummary(
                ACTOR_ID,
                VERSION
        );
        verifyNoMoreInteractions(summaryReadService);

        // /summary가 /{memberId}로 잘못 연결되지 않았는지 확인한다.
        verifyNoInteractions(operationLogReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "AUTHENTICATION_REQUIRED, 401",
            "ADMIN_REQUIRED, 403"
    })
    void 서비스_예외를_정해진_HTTP_상태와_코드로_반환한다(
            AdminOperationLogReadException.Code code,
            int expectedStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminOperationLogReadException exception =
                new AdminOperationLogReadException(code);

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
        verifyNoInteractions(operationLogReadService);
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
                operationLogReadService
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
                operationLogReadService
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
                operationLogReadService
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
                operationLogReadService
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
                operationLogReadService
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
                operationLogReadService
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
                operationLogReadService
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

    private void givenValidSession(
            MemberRole role
    ) {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_ID,
                        MemberStatus.ACTIVE,
                        VERSION,
                        role
                ))
                .thenReturn(true);
    }

    private MemberPrincipal principal(
            MemberRole role
    ) {
        return new MemberPrincipal(
                ACTOR_ID,
                "operation-summary-admin@example.com",
                "test-only-password-hash",
                "회원요약관리자",
                MemberStatus.ACTIVE,
                role,
                VERSION
        );
    }
}