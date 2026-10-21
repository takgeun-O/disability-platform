package io.github.takgeun.iyum.auth.passwordreset.api;

import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetException;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetService;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetTokenValidationService;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetTokenIssueService;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetIpRateLimiter;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = PasswordResetController.class,
        properties = {
                "spring.config.import=",
                "server.forward-headers-strategy=none",
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
class PasswordResetControllerTest {

    private static final String ISSUE_URL =
            "/api/v1/auth/password-reset/request";

    private static final String COMPLETE_URL =
            "/api/v1/auth/password-reset/complete";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final String TOKEN = "a".repeat(43);
    private static final String PASSWORD = "NewPassword123!";
    private static final String CLIENT_IP = "192.0.2.10";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    PasswordResetTokenIssueService issueService;

    @MockitoBean
    PasswordResetService resetService;

    @MockitoBean
    PasswordResetTokenValidationService validationService;

    // SecurityConfig의 MemberSessionStatusFilter 생성에 필요하다.
    // 이번 비로그인 요청에서는 조회되지 않아야 한다.
    @MockitoBean
    MemberRepository memberRepository;

    @MockitoBean
    PasswordResetIpRateLimiter ipRateLimiter;

    @Test
    void 비로그인_사전_확인은_CSRF나_세션_생성_없이_204를_반환한다() throws Exception {
        var result = mockMvc.perform(get("/api/v1/auth/password-reset/validate")
                        .param("token", TOKEN).header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(unauthenticated()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        verify(validationService).validate(TOKEN);
        verifyNoMoreInteractions(validationService);
        verifyNoInteractions(issueService, resetService, memberRepository, ipRateLimiter);
    }

    @Test
    void 무효_토큰은_공통_400과_no_store이며_개인정보를_노출하지_않는다() throws Exception {
        doThrow(PasswordResetException.invalidToken()).when(validationService).validate(TOKEN);
        var result = mockMvc.perform(get("/api/v1/auth/password-reset/validate").param("token", TOKEN)
                        .header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.memberId").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(unauthenticated()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(TOKEN);
        verify(validationService).validate(TOKEN);
        verifyNoInteractions(issueService, resetService, memberRepository, ipRateLimiter);
    }

    @Test
    void 누락과_중복_토큰은_서비스_호출_없이_거절한다() throws Exception {
        for (var request : java.util.List.of(
                get("/api/v1/auth/password-reset/validate"),
                get("/api/v1/auth/password-reset/validate").param("token", TOKEN, TOKEN))) {
            mockMvc.perform(request).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
        }
        verifyNoInteractions(validationService, issueService, resetService, memberRepository, ipRateLimiter);
    }

    @Test
    void 사전_확인_CORS는_허용_출처의_GET만_허용한다() throws Exception {
        mockMvc.perform(options("/api/v1/auth/password-reset/validate").header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
        mockMvc.perform(options("/api/v1/auth/password-reset/validate").header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/auth/password-reset/validate").param("token", TOKEN)
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(validationService, issueService, resetService, memberRepository, ipRateLimiter);
    }

    @Test
    void 비로그인_메일_요청은_이메일을_정규화하여_전달하고_202를_반환한다()
            throws Exception {

        when(ipRateLimiter.tryAcquire(CLIENT_IP)).thenReturn(true);

        mockMvc.perform(
                        jsonPost(
                                ISSUE_URL,
                                Map.of("email", " Member@Example.COM ")
                        )
                                .with(csrf().asHeader())
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.message").value(
                        "입력하신 이메일이 재설정 가능한 계정이면 "
                                + "비밀번호 재설정 안내 메일을 보내드립니다."
                ))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
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
                .andExpect(unauthenticated());

        verify(ipRateLimiter).tryAcquire(CLIENT_IP);
        verifyNoMoreInteractions(ipRateLimiter);
        verify(issueService).issue("member@example.com");
        verifyNoMoreInteractions(issueService);
        verifyNoInteractions(resetService, memberRepository);
    }

    @Test
    void IP_제한을_초과하면_429이고_메일_발급_서비스를_호출하지_않는다()
            throws Exception {
        when(ipRateLimiter.tryAcquire(CLIENT_IP)).thenReturn(false);

        mockMvc.perform(
                        jsonPost(ISSUE_URL, Map.of("email", "member@example.com"))
                                .with(csrf().asHeader())
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_RATE_LIMITED"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL, containsString("no-store")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"
                ))
                .andExpect(unauthenticated());

        verify(ipRateLimiter).tryAcquire(CLIENT_IP);
        verifyNoMoreInteractions(ipRateLimiter);
        verifyNoInteractions(issueService, resetService, memberRepository);
    }

    @Test
    void 전달_헤더가_있어도_직접_연결_IP로만_제한을_검사한다()
            throws Exception {
        when(ipRateLimiter.tryAcquire(CLIENT_IP)).thenReturn(true);

        mockMvc.perform(
                        jsonPost(ISSUE_URL, Map.of("email", "member@example.com"))
                                .with(csrf().asHeader())
                                .header("X-Forwarded-For", "198.51.100.77")
                                .header("Forwarded", "for=198.51.100.88;proto=https")
                )
                .andExpect(status().isAccepted())
                .andExpect(unauthenticated());

        verify(ipRateLimiter).tryAcquire(CLIENT_IP);
        verifyNoMoreInteractions(ipRateLimiter);
        verify(issueService).issue("member@example.com");
        verifyNoMoreInteractions(issueService);
        verifyNoInteractions(resetService, memberRepository);
    }

    @Test
    void 비로그인_재설정_완료는_원문을_전달하고_204를_반환한다()
            throws Exception {

        String rawPassword = " NewPassword123! ";

        mockMvc.perform(
                        jsonPost(
                                COMPLETE_URL,
                                completeBody(
                                        TOKEN,
                                        rawPassword,
                                        rawPassword
                                )
                        )
                                .with(csrf().asHeader())
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
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
                .andExpect(unauthenticated());

        // 확인값은 서비스에 전달하지 않는다.
        verify(resetService).reset(TOKEN, rawPassword);
        verifyNoMoreInteractions(resetService);
        verifyNoInteractions(issueService, memberRepository, ipRateLimiter);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void 입력이_잘못되면_400이고_서비스를_호출하지_않는다(
            String url,
            Map<String, String> body,
            String expectedField
    ) throws Exception {

        mockMvc.perform(
                        jsonPost(url, body)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field")
                        .value(hasItem(expectedField)))
                .andExpect(unauthenticated());

        verifyNoInteractions(
                issueService,
                resetService,
                memberRepository,
                ipRateLimiter
        );
    }

    static Stream<Arguments> invalidRequests() {
        String tooLongPassword = "Ab1" + "가".repeat(24);

        return Stream.of(
                Arguments.of(
                        ISSUE_URL,
                        Map.<String, String>of(),
                        "email"
                ),
                Arguments.of(
                        ISSUE_URL,
                        Map.of("email", "not-an-email"),
                        "email"
                ),
                Arguments.of(
                        COMPLETE_URL,
                        completeBody("", PASSWORD, PASSWORD),
                        "token"
                ),
                Arguments.of(
                        COMPLETE_URL,
                        completeBody("short-token", PASSWORD, PASSWORD),
                        "token"
                ),
                Arguments.of(
                        COMPLETE_URL,
                        completeBody(TOKEN, "Ab12345", "Ab12345"),
                        "newPassword"
                ),
                Arguments.of(
                        COMPLETE_URL,
                        completeBody(TOKEN, "abcdefgh", "abcdefgh"),
                        "newPassword"
                ),
                Arguments.of(
                        COMPLETE_URL,
                        completeBody(
                                TOKEN,
                                tooLongPassword,
                                tooLongPassword
                        ),
                        "newPassword"
                ),
                Arguments.of(
                        COMPLETE_URL,
                        completeBody(TOKEN, PASSWORD, ""),
                        "newPasswordConfirm"
                ),
                Arguments.of(
                        COMPLETE_URL,
                        completeBody(
                                TOKEN,
                                PASSWORD,
                                "DifferentPassword456!"
                        ),
                        "newPasswordConfirm"
                )
        );
    }

    @ParameterizedTest
    @MethodSource("serviceFailures")
    void 재설정_서비스_예외를_공통_오류_응답으로_변환한다(
            PasswordResetException failure,
            String expectedCode,
            String expectedField
    ) throws Exception {

        doThrow(failure)
                .when(resetService)
                .reset(TOKEN, PASSWORD);

        var actions = mockMvc.perform(
                        jsonPost(
                                COMPLETE_URL,
                                completeBody(TOKEN, PASSWORD, PASSWORD)
                        ).with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message")
                        .value(failure.getMessage()))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(unauthenticated());

        if (expectedField == null) {
            actions.andExpect(jsonPath("$.fieldErrors").isEmpty());
        } else {
            actions
                    .andExpect(jsonPath("$.fieldErrors.length()")
                            .value(1))
                    .andExpect(jsonPath("$.fieldErrors[0].field")
                            .value(expectedField))
                    .andExpect(jsonPath("$.fieldErrors[0].message")
                            .value(failure.getMessage()));
        }

        String responseBody = actions.andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody).doesNotContain(TOKEN, PASSWORD);

        verify(resetService).reset(TOKEN, PASSWORD);
        verifyNoMoreInteractions(resetService);
        verifyNoInteractions(issueService, memberRepository, ipRateLimiter);
    }

    static Stream<Arguments> serviceFailures() {
        return Stream.of(
                Arguments.of(
                        PasswordResetException.invalidToken(),
                        "PASSWORD_RESET_TOKEN_INVALID",
                        null
                ),
                Arguments.of(
                        PasswordResetException.invalidPassword(
                                "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
                        ),
                        "PASSWORD_RESET_PASSWORD_INVALID",
                        "newPassword"
                ),
                Arguments.of(
                        PasswordResetException.unchangedPassword(),
                        "PASSWORD_RESET_PASSWORD_UNCHANGED",
                        "newPassword"
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {ISSUE_URL, COMPLETE_URL})
    void 잘못된_JSON은_400이고_서비스를_호출하지_않는다(
            String url
    ) throws Exception {

        mockMvc.perform(
                        post(url)
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(
                issueService,
                resetService,
                memberRepository,
                ipRateLimiter
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {ISSUE_URL, COMPLETE_URL})
    void CSRF가_없거나_틀리면_403이고_서비스를_호출하지_않는다(
            String url
    ) throws Exception {

        mockMvc.perform(jsonPost(url, validBody(url)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        mockMvc.perform(
                        jsonPost(url, validBody(url))
                                .with(csrf().asHeader().useInvalidToken())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(
                issueService,
                resetService,
                memberRepository,
                ipRateLimiter
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {ISSUE_URL, COMPLETE_URL})
    void 개발_출처의_POST_사전_요청을_허용한다(String url)
            throws Exception {

        mockMvc.perform(
                        options(url)
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

        verifyNoInteractions(
                issueService,
                resetService,
                memberRepository,
                ipRateLimiter
        );
    }

    @ParameterizedTest
    @MethodSource("disallowedPreflights")
    void 허용하지_않은_CORS_사전_요청을_거절한다(
            String url,
            String origin,
            String method,
            String headers
    ) throws Exception {

        mockMvc.perform(
                        options(url)
                                .header(HttpHeaders.ORIGIN, origin)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        method
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        headers
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(
                issueService,
                resetService,
                memberRepository,
                ipRateLimiter
        );
    }

    static Stream<Arguments> disallowedPreflights() {
        return Stream.of(ISSUE_URL, COMPLETE_URL)
                .flatMap(url -> Stream.of(
                        Arguments.of(
                                url,
                                "https://untrusted.example",
                                "POST",
                                "content-type,x-csrf-token"
                        ),
                        Arguments.of(
                                url,
                                ORIGIN,
                                "DELETE",
                                "content-type"
                        ),
                        Arguments.of(
                                url,
                                ORIGIN,
                                "POST",
                                "x-unapproved-header"
                        )
                ));
    }

    private MockHttpServletRequestBuilder jsonPost(
            String url,
            Map<String, String> body
    ) {
        return post(url)
                .with(request -> {
                    request.setRemoteAddr(CLIENT_IP);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private static Map<String, String> completeBody(
            String token,
            String password,
            String confirmation
    ) {
        return Map.of(
                "token", token,
                "newPassword", password,
                "newPasswordConfirm", confirmation
        );
    }

    private Map<String, String> validBody(String url) {
        if (ISSUE_URL.equals(url)) {
            return Map.of("email", "member@example.com");
        }

        return completeBody(TOKEN, PASSWORD, PASSWORD);
    }
}