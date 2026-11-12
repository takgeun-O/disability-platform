package io.github.takgeun.iyum.admin.report.api;

import io.github.takgeun.iyum.admin.report.application.AdminReportHandlingException;
import io.github.takgeun.iyum.admin.report.application.AdminReportHandlingResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportHandlingService;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
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
        controllers = AdminReportHandlingController.class,
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
class AdminReportHandlingControllerTest {

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final String UNTRUSTED_ORIGIN =
            "https://untrusted.example";

    private static final Long ACTOR_ID = 40L;
    private static final long CREDENTIAL_VERSION = 3L;
    private static final Long REPORT_ID = 100L;

    private static final String RAW_REASON =
            " \t 검토를 완료했습니다. \n";

    private static final String NORMALIZED_REASON =
            "검토를 완료했습니다.";

    private static final Instant HANDLED_AT =
            Instant.parse("2026-11-13T01:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    MemberRepository memberRepository;

    @MockitoBean
    AdminReportHandlingService reportHandlingService;

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void 관리자_요청을_서비스에_전달하고_처리_결과를_반환한다(
            String action
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        stubService(action, REPORT_ID, RAW_REASON)
                .thenReturn(successResult(action));

        mockMvc.perform(
                        request(action, body(RAW_REASON))
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
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
                .andExpect(jsonPath("$.status")
                        .value(expectedStatus(action).name()))
                .andExpect(jsonPath("$.handledByMemberId").value(40))
                .andExpect(jsonPath("$.handledAt")
                        .value(HANDLED_AT.toString()))
                .andExpect(jsonPath("$.handlingReason")
                        .value(NORMALIZED_REASON))
                .andExpect(jsonPath("$.*", hasSize(5)));

        // 인증 정보는 쿼리 파라미터가 아니라 principal에서 가져온다.
        // 사유 원문은 서비스에 전달하고 정규화는 서비스가 담당한다.
        verifyServiceCall(action, REPORT_ID, RAW_REASON);
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void 본문의_추가_필드로_관리자나_처리_결과를_지정할_수_없다(
            String action
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        stubService(action, REPORT_ID, NORMALIZED_REASON)
                .thenReturn(successResult(action));

        String requestBody = objectMapper.writeValueAsString(
                Map.of(
                        "handlingReason", NORMALIZED_REASON,
                        "actorMemberId", 999,
                        "credentialVersion", 999,
                        "handledByMemberId", 999,
                        "handledAt", "2000-01-01T00:00:00Z",
                        "status", "RECEIVED"
                )
        );

        mockMvc.perform(
                        request(action, requestBody)
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handledByMemberId").value(40))
                .andExpect(jsonPath("$.handledAt")
                        .value(HANDLED_AT.toString()))
                .andExpect(jsonPath("$.status")
                        .value(expectedStatus(action).name()));

        verifyServiceCall(action, REPORT_ID, NORMALIZED_REASON);
    }

    @ParameterizedTest
    @CsvSource({
            "resolve, AUTHENTICATION_REQUIRED, 401",
            "dismiss, AUTHENTICATION_REQUIRED, 401",
            "resolve, ADMIN_REQUIRED, 403",
            "dismiss, ADMIN_REQUIRED, 403",
            "resolve, REPORT_NOT_FOUND, 404",
            "dismiss, REPORT_NOT_FOUND, 404",
            "resolve, REPORT_ALREADY_HANDLED, 409",
            "dismiss, REPORT_ALREADY_HANDLED, 409",
            "resolve, INVALID_HANDLING_REASON, 400",
            "dismiss, INVALID_HANDLING_REASON, 400"
    })
    void 서비스_예외를_공통_HTTP_오류로_변환한다(
            String action,
            AdminReportHandlingException.Code code,
            int expectedHttpStatus
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        AdminReportHandlingException exception =
                new AdminReportHandlingException(code);

        stubService(action, REPORT_ID, RAW_REASON)
                .thenThrow(exception);

        mockMvc.perform(
                        request(action, body(RAW_REASON))
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().is(expectedHttpStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.message")
                        .value(exception.getMessage()))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ));

        verifyServiceCall(action, REPORT_ID, RAW_REASON);
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void 처리_사유_필드가_없으면_null을_서비스에_전달한다(
            String action
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        stubService(action, REPORT_ID, null)
                .thenThrow(new AdminReportHandlingException(
                        AdminReportHandlingException.Code
                                .INVALID_HANDLING_REASON
                ));

        mockMvc.perform(
                        request(action, "{}")
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_HANDLING_REASON"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verifyServiceCall(action, REPORT_ID, null);
    }

    @ParameterizedTest
    @MethodSource("invalidReasons")
    void 사유_값의_검증은_서비스에_위임한다(
            String action,
            String reason
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        stubService(action, REPORT_ID, reason)
                .thenThrow(new AdminReportHandlingException(
                        AdminReportHandlingException.Code
                                .INVALID_HANDLING_REASON
                ));

        mockMvc.perform(
                        request(action, body(reason))
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_HANDLING_REASON"));

        verifyServiceCall(action, REPORT_ID, reason);
    }

    static Stream<Arguments> invalidReasons() {
        return Stream.of("resolve", "dismiss")
                .flatMap(action ->
                        Stream.<String>of(
                                        null,
                                        "",
                                        " \t\n",
                                        "\u00A0\u2003\u3000",
                                        "가".repeat(501)
                                )
                                .map(reason ->
                                        Arguments.of(action, reason)
                                )
                );
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void 읽을_수_없는_본문은_서비스_호출_전에_거부한다(
            String action,
            String requestBody
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        mockMvc.perform(
                        request(action, requestBody)
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verifyNoInteractions(reportHandlingService);
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of("resolve", "dismiss")
                .flatMap(action ->
                        Stream.of(
                                        "",
                                        "null",
                                        "{",
                                        """
                                        {"handlingReason": {}}
                                        """,
                                        """
                                        {"handlingReason": []}
                                        """
                                )
                                .map(requestBody ->
                                        Arguments.of(action, requestBody)
                                )
                );
    }

    @ParameterizedTest
    @CsvSource({
            "resolve, abc",
            "dismiss, abc",
            "resolve, 9223372036854775808",
            "dismiss, 9223372036854775808"
    })
    void 신고_ID를_Long으로_변환할_수_없으면_400이다(
            String action,
            String reportId
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        mockMvc.perform(
                        post(path(action, reportId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(RAW_REASON))
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(reportHandlingService);
    }

    @ParameterizedTest
    @CsvSource({
            "resolve, 0",
            "dismiss, 0",
            "resolve, -1",
            "dismiss, -1"
    })
    void 숫자로_변환되는_잘못된_ID는_서비스에서_404로_처리한다(
            String action,
            Long reportId
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        stubService(action, reportId, RAW_REASON)
                .thenThrow(new AdminReportHandlingException(
                        AdminReportHandlingException.Code.REPORT_NOT_FOUND
                ));

        mockMvc.perform(
                        post(path(action, reportId.toString()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(RAW_REASON))
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_NOT_FOUND"));

        verifyServiceCall(action, reportId, RAW_REASON);
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void JSON이_아닌_ContentType은_415로_거부한다(
            String action
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        mockMvc.perform(
                        request(action, body(RAW_REASON))
                                .contentType(MediaType.TEXT_PLAIN)
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("HTTP_ERROR"));

        verifyNoInteractions(reportHandlingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void 비로그인은_유효한_CSRF가_있어도_401이다(
            String action
    ) throws Exception {
        mockMvc.perform(
                        request(action, body(RAW_REASON))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(memberRepository, reportHandlingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void 유효한_일반_회원_세션은_403이다(
            String action
    ) throws Exception {
        // 세션 자체는 유효하도록 준비해야 권한 검증까지 도달한다.
        givenCurrentMember(MemberRole.USER);

        mockMvc.perform(
                        request(action, body(RAW_REASON))
                                .with(user(principal(MemberRole.USER)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(reportHandlingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void 현재_DB_정보와_맞지_않는_관리자_세션은_401이다(
            String action
    ) throws Exception {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        MemberRole.ADMIN
                ))
                .thenReturn(false);

        mockMvc.perform(
                        request(action, body(RAW_REASON))
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
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

        verifyNoInteractions(reportHandlingService);
    }

    @ParameterizedTest
    @CsvSource({
            "resolve, false",
            "resolve, true",
            "dismiss, false",
            "dismiss, true"
    })
    void 관리자라도_CSRF가_없거나_잘못되면_403이다(
            String action,
            boolean invalidToken
    ) throws Exception {
        givenCurrentMember(MemberRole.ADMIN);

        MockHttpServletRequestBuilder builder =
                request(action, body(RAW_REASON))
                        .with(user(principal(MemberRole.ADMIN)));

        if (invalidToken) {
            builder.with(csrf().asHeader().useInvalidToken());
        }

        mockMvc.perform(builder)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(reportHandlingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void 허용된_Origin의_사전_요청을_허용한다(
            String action
    ) throws Exception {
        mockMvc.perform(
                        options(path(action, REPORT_ID.toString()))
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "content-type,x-csrf-token"
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

        verifyNoInteractions(memberRepository, reportHandlingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void 허용되지_않은_Origin의_실제_요청을_거부한다(
            String action
    ) throws Exception {
        // request()에는 Origin 기본값을 넣지 않는다.
        // 허용된 Origin과 잘못된 Origin이 중복 전송되지 않도록 한다.
        mockMvc.perform(
                        request(action, body(RAW_REASON))
                                .header(
                                        HttpHeaders.ORIGIN,
                                        UNTRUSTED_ORIGIN
                                )
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(memberRepository, reportHandlingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void 허용되지_않은_Origin의_사전_요청을_거부한다(
            String action
    ) throws Exception {
        mockMvc.perform(
                        options(path(action, REPORT_ID.toString()))
                                .header(
                                        HttpHeaders.ORIGIN,
                                        UNTRUSTED_ORIGIN
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "content-type,x-csrf-token"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(memberRepository, reportHandlingService);
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

    private String path(String action, String reportId) {
        if (!action.equals("resolve") && !action.equals("dismiss")) {
            throw new IllegalArgumentException(
                    "지원하지 않는 처리 동작: " + action
            );
        }

        return "/api/v1/admin/reports/" + reportId + "/" + action;
    }

    private String body(String reason) {
        // Map.of()는 null 값을 허용하지 않으므로 singletonMap을 사용한다.
        return objectMapper.writeValueAsString(
                Collections.singletonMap("handlingReason", reason)
        );
    }

    private MockHttpServletRequestBuilder request(
            String action,
            String requestBody
    ) {
        return post(path(action, REPORT_ID.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody);
    }

    private ContentReportStatus expectedStatus(String action) {
        return switch (action) {
            case "resolve" -> ContentReportStatus.RESOLVED;
            case "dismiss" -> ContentReportStatus.DISMISSED;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 처리 동작: " + action
            );
        };
    }

    private AdminReportHandlingResult successResult(String action) {
        return new AdminReportHandlingResult(
                REPORT_ID,
                expectedStatus(action),
                ACTOR_ID,
                HANDLED_AT,
                NORMALIZED_REASON
        );
    }

    private OngoingStubbing<AdminReportHandlingResult> stubService(
            String action,
            Long reportId,
            String reason
    ) {
        return switch (action) {
            case "resolve" -> when(reportHandlingService.resolve(
                    ACTOR_ID,
                    CREDENTIAL_VERSION,
                    reportId,
                    reason
            ));

            case "dismiss" -> when(reportHandlingService.dismiss(
                    ACTOR_ID,
                    CREDENTIAL_VERSION,
                    reportId,
                    reason
            ));

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 처리 동작: " + action
            );
        };
    }

    private void verifyServiceCall(
            String action,
            Long reportId,
            String reason
    ) {
        switch (action) {
            case "resolve" -> verify(reportHandlingService).resolve(
                    ACTOR_ID,
                    CREDENTIAL_VERSION,
                    reportId,
                    reason
            );

            case "dismiss" -> verify(reportHandlingService).dismiss(
                    ACTOR_ID,
                    CREDENTIAL_VERSION,
                    reportId,
                    reason
            );

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 처리 동작: " + action
            );
        }

        verifyNoMoreInteractions(reportHandlingService);
    }
}