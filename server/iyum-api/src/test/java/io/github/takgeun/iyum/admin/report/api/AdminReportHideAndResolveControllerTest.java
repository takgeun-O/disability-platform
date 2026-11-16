package io.github.takgeun.iyum.admin.report.api;

import io.github.takgeun.iyum.admin.moderation.application.ContentModerationException;
import io.github.takgeun.iyum.admin.report.application.AdminReportHandlingException;
import io.github.takgeun.iyum.admin.report.application.AdminReportHandlingResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportHideAndResolveService;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.report.domain.ContentReportHandlingOutcome;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.OngoingStubbing;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminReportHideAndResolveController.class,
        properties = {
                "spring.config.import=",
                "spring.docker.compose.enabled=false",
                "iyum.cors.allowed-origin=http://127.0.0.1:8443",
                "springdoc.api-docs.enabled=false",
                "springdoc.swagger-ui.enabled=false"
        }
)
@Import({
        SecurityConfig.class,
        DevCorsConfig.class,
        GlobalExceptionHandler.class
})
@ActiveProfiles("dev")
class AdminReportHideAndResolveControllerTest {

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final String UNTRUSTED_ORIGIN =
            "https://untrusted.example";

    private static final Long ACTOR_ID = 40L;
    private static final long CREDENTIAL_VERSION = 3L;
    private static final Long REPORT_ID = 100L;

    private static final String RAW_REASON =
            " \t 운영 정책 위반을 확인했습니다. \n";

    private static final String NORMALIZED_REASON =
            "운영 정책 위반을 확인했습니다.";

    private static final Instant HANDLED_AT =
            Instant.parse("2026-11-13T07:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    MemberRepository memberRepository;

    @MockitoBean
    AdminReportHideAndResolveService hideAndResolveService;

    @ParameterizedTest
    @EnumSource(
            value = ContentReportHandlingOutcome.class,
            names = {
                    "CONTENT_HIDDEN",
                    "CONTENT_ALREADY_HIDDEN"
            }
    )
    void 관리자_요청을_서비스에_전달하고_숨김_처리_결과를_반환한다(
            ContentReportHandlingOutcome outcome
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        stubService(REPORT_ID, RAW_REASON)
                .thenReturn(successResult(outcome));

        mockMvc.perform(
                        authenticatedRequest(body(RAW_REASON))
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .param("actorMemberId", "999")
                                .param("credentialVersion", "999")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
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
                ))
                .andExpect(jsonPath("$.reportId").value(100))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.handledByMemberId").value(40))
                .andExpect(jsonPath("$.handledAt")
                        .value(HANDLED_AT.toString()))
                .andExpect(jsonPath("$.handlingReason")
                        .value(NORMALIZED_REASON))
                .andExpect(jsonPath("$.handlingOutcome")
                        .value(outcome.name()))
                .andExpect(jsonPath("$.*", hasSize(6)));

        // 관리자 정보는 쿼리 파라미터가 아니라 principal에서 가져온다.
        // 사유 원문을 전달하고 정규화는 서비스에 위임한다.
        verifyServiceCall(REPORT_ID, RAW_REASON);
    }

    @Test
    void 본문의_추가_필드로_관리자나_대상이나_처리_결과를_지정할_수_없다()
            throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        stubService(REPORT_ID, NORMALIZED_REASON)
                .thenReturn(successResult(
                        ContentReportHandlingOutcome.CONTENT_ALREADY_HIDDEN
                ));

        String requestBody = objectMapper.writeValueAsString(
                Map.of(
                        "handlingReason", NORMALIZED_REASON,
                        "actorMemberId", 999,
                        "credentialVersion", 999,
                        "reportId", 999,
                        "handledByMemberId", 999,
                        "handledAt", "2000-01-01T00:00:00Z",
                        "status", "DISMISSED",
                        "handlingOutcome", "CONTENT_HIDDEN"
                )
        );

        mockMvc.perform(authenticatedRequest(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(100))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.handledByMemberId").value(40))
                .andExpect(jsonPath("$.handledAt")
                        .value(HANDLED_AT.toString()))
                .andExpect(jsonPath("$.handlingReason")
                        .value(NORMALIZED_REASON))
                .andExpect(jsonPath("$.handlingOutcome")
                        .value("CONTENT_ALREADY_HIDDEN"))
                .andExpect(jsonPath("$.*", hasSize(6)));

        verifyServiceCall(REPORT_ID, NORMALIZED_REASON);
    }

    @ParameterizedTest
    @CsvSource({
            "AUTHENTICATION_REQUIRED, 401",
            "ADMIN_REQUIRED, 403",
            "REPORT_NOT_FOUND, 404",
            "REPORT_ALREADY_HANDLED, 409",
            "INVALID_HANDLING_REASON, 400"
    })
    void 신고_처리_예외를_공통_HTTP_오류로_변환한다(
            AdminReportHandlingException.Code code,
            int expectedHttpStatus
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        AdminReportHandlingException exception =
                new AdminReportHandlingException(code);

        stubService(REPORT_ID, RAW_REASON)
                .thenThrow(exception);

        ResultActions response = mockMvc.perform(
                authenticatedRequest(body(RAW_REASON))
                        .header(HttpHeaders.ORIGIN, ORIGIN)
        );

        assertApiError(
                response,
                expectedHttpStatus,
                code.name()
        );

        response
                .andExpect(jsonPath("$.message")
                        .value(exception.getMessage()))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ));

        verifyServiceCall(REPORT_ID, RAW_REASON);
    }

    @ParameterizedTest
    @CsvSource({
            "AUTHENTICATION_REQUIRED, 401",
            "ADMIN_REQUIRED, 403",
            "INVALID_REASON, 400",
            "POST_NOT_FOUND, 404",
            "COMMENT_NOT_FOUND, 404",
            "POST_DELETED, 409",
            "COMMENT_DELETED, 409"
    })
    void 콘텐츠_숨김_예외를_공통_HTTP_오류로_변환한다(
            ContentModerationException.Code code,
            int expectedHttpStatus
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        ContentModerationException exception =
                new ContentModerationException(code);

        stubService(REPORT_ID, RAW_REASON)
                .thenThrow(exception);

        ResultActions response = mockMvc.perform(
                authenticatedRequest(body(RAW_REASON))
                        .header(HttpHeaders.ORIGIN, ORIGIN)
        );

        assertApiError(
                response,
                expectedHttpStatus,
                code.name()
        );

        response
                .andExpect(jsonPath("$.message")
                        .value(exception.getMessage()))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ));

        verifyServiceCall(REPORT_ID, RAW_REASON);
    }

    @Test
    void 처리_사유_필드가_없으면_null을_서비스에_전달한다()
            throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        stubService(REPORT_ID, null)
                .thenThrow(new AdminReportHandlingException(
                        AdminReportHandlingException.Code
                                .INVALID_HANDLING_REASON
                ));

        assertApiError(
                mockMvc.perform(authenticatedRequest("{}")),
                400,
                "INVALID_HANDLING_REASON"
        );

        verifyServiceCall(REPORT_ID, null);
    }

    @ParameterizedTest
    @MethodSource("invalidReasons")
    void 사유_값의_검증은_서비스에_위임한다(
            String reason
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        stubService(REPORT_ID, reason)
                .thenThrow(new AdminReportHandlingException(
                        AdminReportHandlingException.Code
                                .INVALID_HANDLING_REASON
                ));

        assertApiError(
                mockMvc.perform(
                        authenticatedRequest(body(reason))
                ),
                400,
                "INVALID_HANDLING_REASON"
        );

        verifyServiceCall(REPORT_ID, reason);
    }

    static Stream<String> invalidReasons() {
        return Stream.<String>of(
                null,
                "",
                " \t\n",
                "\u00A0\u2003\u3000",
                "가".repeat(501)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void 읽을_수_없는_본문은_서비스_호출_전에_거부한다(
            String requestBody
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        assertApiError(
                mockMvc.perform(
                        authenticatedRequest(requestBody)
                ),
                400,
                "INVALID_REQUEST_BODY"
        );

        verifyNoInteractions(hideAndResolveService);
    }

    static Stream<String> invalidBodies() {
        return Stream.of(
                "",
                "null",
                "{",
                """
                {"handlingReason": {}}
                """,
                """
                {"handlingReason": []}
                """
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "9223372036854775808"
    })
    void 신고_ID를_Long으로_변환할_수_없으면_400이다(
            String reportId
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        assertApiError(
                mockMvc.perform(
                        post(path(reportId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(RAW_REASON))
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                ),
                400,
                "INVALID_REQUEST_PARAMETER"
        );

        verifyNoInteractions(hideAndResolveService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void 숫자로_변환되는_잘못된_ID는_서비스에서_404로_처리한다(
            long reportId
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        stubService(reportId, RAW_REASON)
                .thenThrow(new AdminReportHandlingException(
                        AdminReportHandlingException.Code.REPORT_NOT_FOUND
                ));

        assertApiError(
                mockMvc.perform(
                        post(path(Long.toString(reportId)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(RAW_REASON))
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                ),
                404,
                "REPORT_NOT_FOUND"
        );

        verifyServiceCall(reportId, RAW_REASON);
    }

    @Test
    void JSON이_아닌_ContentType은_415로_거부한다()
            throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        assertApiError(
                mockMvc.perform(
                        authenticatedRequest(body(RAW_REASON))
                                .contentType(MediaType.TEXT_PLAIN)
                ),
                415,
                "HTTP_ERROR"
        );

        verifyNoInteractions(hideAndResolveService);
    }

    @Test
    void 비로그인은_유효한_CSRF가_있어도_401이다()
            throws Exception {
        mockMvc.perform(
                        request(body(RAW_REASON))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(
                memberRepository,
                hideAndResolveService
        );
    }

    @Test
    void 유효한_일반_회원_세션은_403이다()
            throws Exception {
        // 세션 검증을 통과해야 관리자 권한 검사까지 도달한다.
        givenCurrentMember(MemberRole.USER);

        mockMvc.perform(
                        request(body(RAW_REASON))
                                .with(user(principal(MemberRole.USER)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("ACCESS_DENIED"));

        verifyNoInteractions(hideAndResolveService);
    }

    @Test
    void 현재_DB_정보와_맞지_않는_관리자_세션은_401이다()
            throws Exception {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        MemberRole.ADMIN
                ))
                .thenReturn(false);

        mockMvc.perform(
                        authenticatedRequest(body(RAW_REASON))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verify(memberRepository)
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        MemberRole.ADMIN
                );

        verifyNoInteractions(hideAndResolveService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 관리자라도_CSRF가_없거나_잘못되면_403이다(
            boolean invalidToken
    ) throws Exception {
        // CSRF 검사는 회원 상태 검증보다 먼저 수행된다.
        // 따라서 이 테스트에는 회원 조회 스텁이 필요하지 않다.
        MockHttpServletRequestBuilder builder =
                request(body(RAW_REASON))
                        .with(user(principal(MemberRole.ADMIN)));

        if (invalidToken) {
            builder.with(csrf().asHeader().useInvalidToken());
        }

        mockMvc.perform(builder)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(hideAndResolveService);
    }

    @Test
    void 허용된_Origin의_사전_요청을_허용한다()
            throws Exception {
        mockMvc.perform(preflight(ORIGIN))
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
                        "POST,OPTIONS"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("content-type")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("x-csrf-token")
                ));

        verifyNoInteractions(
                memberRepository,
                hideAndResolveService
        );
    }

    @Test
    void 허용되지_않은_Origin의_실제_요청을_거부한다()
            throws Exception {
        // 기본 요청에는 Origin을 넣지 않아 중복 헤더를 방지한다.
        mockMvc.perform(
                        authenticatedRequest(body(RAW_REASON))
                                .header(
                                        HttpHeaders.ORIGIN,
                                        UNTRUSTED_ORIGIN
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(
                memberRepository,
                hideAndResolveService
        );
    }

    @Test
    void 허용되지_않은_Origin의_사전_요청을_거부한다()
            throws Exception {
        mockMvc.perform(preflight(UNTRUSTED_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(
                memberRepository,
                hideAndResolveService
        );
    }

    private void givenCurrentMember(MemberRole role) {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        role
                ))
                .thenReturn(true);
    }

    private MemberPrincipal principal(MemberRole role) {
        return new MemberPrincipal(
                ACTOR_ID,
                "report-admin-test@example.com",
                "{noop}unused-password",
                "신고처리검증회원",
                MemberStatus.ACTIVE,
                role,
                CREDENTIAL_VERSION
        );
    }

    private String path(String reportId) {
        return "/api/v1/admin/reports/"
                + reportId
                + "/hide-and-resolve";
    }

    private String body(String reason) {
        // null 사유도 JSON 본문으로 전달할 수 있도록 한다.
        return objectMapper.writeValueAsString(
                Collections.singletonMap("handlingReason", reason)
        );
    }

    private MockHttpServletRequestBuilder request(
            String requestBody
    ) {
        return post(path(REPORT_ID.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody);
    }

    private MockHttpServletRequestBuilder authenticatedRequest(
            String requestBody
    ) {
        return request(requestBody)
                .with(user(principal(MemberRole.ADMIN)))
                .with(csrf().asHeader());
    }

    private MockHttpServletRequestBuilder preflight(
            String origin
    ) {
        return options(path(REPORT_ID.toString()))
                .header(HttpHeaders.ORIGIN, origin)
                .header(
                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                        "POST"
                )
                .header(
                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "content-type,x-csrf-token"
                );
    }

    private AdminReportHandlingResult successResult(
            ContentReportHandlingOutcome outcome
    ) {
        return new AdminReportHandlingResult(
                REPORT_ID,
                ContentReportStatus.RESOLVED,
                ACTOR_ID,
                HANDLED_AT,
                NORMALIZED_REASON,
                outcome
        );
    }

    private OngoingStubbing<AdminReportHandlingResult> stubService(
            Long reportId,
            String reason
    ) {
        return when(hideAndResolveService.hideAndResolve(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                reportId,
                reason
        ));
    }

    private void verifyServiceCall(
            Long reportId,
            String reason
    ) {
        verify(hideAndResolveService).hideAndResolve(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                reportId,
                reason
        );

        verifyNoMoreInteractions(hideAndResolveService);
    }

    private void assertApiError(
            ResultActions response,
            int expectedStatus,
            String expectedCode
    ) throws Exception {
        response
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value(expectedCode))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));
    }
}