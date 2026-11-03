package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.application.MemberWithdrawalException;
import io.github.takgeun.iyum.member.application.MemberWithdrawalService;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = MemberWithdrawalController.class,
        properties = {
                "spring.config.import=",
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
class MemberWithdrawalControllerTest {

    private static final String URL =
            "/api/v1/members/me/withdrawal";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Long MEMBER_ID = 7L;

    private static final String EMAIL =
            "withdrawal-test@example.com";

    private static final String PASSWORD =
            "Example123!";

    private static final String CONTEXT_KEY =
            HttpSessionSecurityContextRepository
                    .SPRING_SECURITY_CONTEXT_KEY;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    MemberWithdrawalService withdrawalService;

    @MockitoBean
    MemberRepository memberRepository;

    @Test
    void 탈퇴에_성공하면_204를_반환하고_현재_세션을_정리한다()
            throws Exception {

        givenActiveMember();
        MockHttpSession session = authenticatedSession();

        // 비밀번호 원문의 앞뒤 공백이 유지되는지도 확인한다.
        String rawPassword = "  Example123!  ";

        mockMvc.perform(
                        withdrawalRequest(
                                session,
                                validBody(rawPassword)
                        )
                                .with(csrf().asHeader())
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .param("memberId", "999")
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
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(unauthenticated());

        // 요청 파라미터의 999가 아닌 인증 주체의 ID를 사용한다.
        verify(withdrawalService).withdraw(
                MEMBER_ID,
                rawPassword
        );
        verifyNoMoreInteractions(withdrawalService);

        assertThat(session.isInvalid()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void 입력이_잘못되면_400이고_서비스를_호출하지_않으며_세션을_유지한다(
            String body,
            String expectedField
    ) throws Exception {

        givenActiveMember();
        MockHttpSession session = authenticatedSession();

        var result = mockMvc.perform(
                        withdrawalRequest(session, body)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()")
                        .value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value(expectedField))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .isNotEmpty())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andReturn();

        // confirmed 검증 실패 응답에도 입력한 비밀번호를 노출하지 않는다.
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(PASSWORD);

        verifyNoInteractions(withdrawalService);
        assertLoginRemains(session);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(
                        """
                        {"confirmed":true}
                        """,
                        "currentPassword"
                ),
                Arguments.of(
                        """
                        {"currentPassword":null,"confirmed":true}
                        """,
                        "currentPassword"
                ),
                Arguments.of(
                        """
                        {"currentPassword":"","confirmed":true}
                        """,
                        "currentPassword"
                ),
                Arguments.of(
                        """
                        {"currentPassword":"   ","confirmed":true}
                        """,
                        "currentPassword"
                ),
                Arguments.of(
                        """
                        {"currentPassword":"%s","confirmed":true}
                        """.formatted("a".repeat(73)),
                        "currentPassword"
                ),
                Arguments.of(
                        """
                        {"currentPassword":"%s","confirmed":true}
                        """.formatted("가".repeat(25)),
                        "currentPassword"
                ),
                Arguments.of(
                        """
                        {"currentPassword":"Example123!"}
                        """,
                        "confirmed"
                ),
                Arguments.of(
                        """
                        {"currentPassword":"Example123!","confirmed":null}
                        """,
                        "confirmed"
                ),
                Arguments.of(
                        """
                        {"currentPassword":"Example123!","confirmed":false}
                        """,
                        "confirmed"
                )
        );
    }

    @ParameterizedTest
    @MethodSource("passwordsAtByteLimit")
    void UTF8_72바이트_비밀번호는_서비스로_전달한다(
            String password
    ) throws Exception {

        givenActiveMember();
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(
                        withdrawalRequest(
                                session,
                                validBody(password)
                        )
                                .with(csrf().asHeader())
                )
                .andExpect(status().isNoContent());

        verify(withdrawalService).withdraw(MEMBER_ID, password);
    }

    static Stream<String> passwordsAtByteLimit() {
        return Stream.of(
                "a".repeat(72),
                "가".repeat(24)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "{",
            "null"
    })
    void 본문이_없거나_JSON이_잘못되면_400이고_세션을_유지한다(
            String body
    ) throws Exception {

        givenActiveMember();
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(
                        withdrawalRequest(session, body)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));

        verifyNoInteractions(withdrawalService);
        assertLoginRemains(session);
    }

    @ParameterizedTest
    @CsvSource({
            "MEMBER_NOT_FOUND, 401, AUTHENTICATION_REQUIRED",
            "MEMBER_NOT_ACTIVE, 403, MEMBER_NOT_ACTIVE",
            "INVALID_PASSWORD, 400, INVALID_PASSWORD",
            "PASSWORD_MISMATCH, 400, PASSWORD_MISMATCH"
    })
    void 서비스_예외를_정해진_HTTP_응답으로_변환한다(
            MemberWithdrawalException.Code exceptionCode,
            int expectedStatus,
            String expectedCode
    ) throws Exception {

        givenActiveMember();
        MockHttpSession session = authenticatedSession();

        doThrow(new MemberWithdrawalException(exceptionCode))
                .when(withdrawalService)
                .withdraw(MEMBER_ID, PASSWORD);

        var result = mockMvc.perform(
                        withdrawalRequest(
                                session,
                                validBody(PASSWORD)
                        )
                                .with(csrf().asHeader())
                )
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        if (exceptionCode == MemberWithdrawalException.Code.INVALID_PASSWORD
                || exceptionCode == MemberWithdrawalException.Code.PASSWORD_MISMATCH) {

            result
                    .andExpect(jsonPath("$.fieldErrors.length()")
                            .value(1))
                    .andExpect(jsonPath("$.fieldErrors[0].field")
                            .value("currentPassword"))
                    .andExpect(jsonPath("$.fieldErrors[0].message")
                            .isNotEmpty())
                    .andExpect(cookie().doesNotExist("JSESSIONID"));

            // 비밀번호 오류로 탈퇴에 실패해도 로그인은 유지한다.
            assertLoginRemains(session);
        } else {
            result.andExpect(jsonPath("$.fieldErrors").isEmpty());
        }

        assertThat(
                result.andReturn().getResponse().getContentAsString()
        ).doesNotContain(PASSWORD);

        // 필터의 401로 우연히 통과한 것이 아님을 확인한다.
        verify(withdrawalService).withdraw(MEMBER_ID, PASSWORD);
        verifyNoMoreInteractions(withdrawalService);
    }

    @Test
    void 비로그인이면_유효한_CSRF가_있어도_401이다()
            throws Exception {

        mockMvc.perform(
                        post(URL)
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validBody(PASSWORD))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(
                memberRepository,
                withdrawalService
        );
    }

    @Test
    void DB에_활성_회원이_없으면_필터가_차단하고_서비스를_호출하지_않는다()
            throws Exception {

        MockHttpSession session = authenticatedSession();

        when(memberRepository.existsByIdAndStatusAndCredentialVersionAndRole(
                MEMBER_ID,
                MemberStatus.ACTIVE,
                0L,
                MemberRole.USER
        )).thenReturn(false);

        mockMvc.perform(
                        withdrawalRequest(
                                session,
                                validBody(PASSWORD)
                        )
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(unauthenticated());

        verify(memberRepository).existsByIdAndStatusAndCredentialVersionAndRole(
                MEMBER_ID,
                MemberStatus.ACTIVE,
                0L,
                MemberRole.USER
        );
        verifyNoInteractions(withdrawalService);

        assertThat(session.isInvalid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void CSRF가_없거나_틀리면_회원상태_조회_전에_403으로_차단한다(
            boolean invalidToken
    ) throws Exception {

        MockHttpSession session = authenticatedSession();

        var request = withdrawalRequest(
                session,
                validBody(PASSWORD)
        );

        if (invalidToken) {
            request.with(csrf().asHeader().useInvalidToken());
        }

        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));

        verifyNoInteractions(
                memberRepository,
                withdrawalService
        );
        assertLoginRemains(session);
    }

    @Test
    void 허용된_출처의_POST_사전_요청은_로그인_없이_허용한다()
            throws Exception {

        mockMvc.perform(
                        options(URL)
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
                ));

        verifyNoInteractions(
                memberRepository,
                withdrawalService
        );
    }

    @ParameterizedTest
    @CsvSource({
            "https://untrusted.example, POST",
            "http://127.0.0.1:8443, GET"
    })
    void 허용하지_않은_출처나_메서드의_사전_요청은_거절한다(
            String origin,
            String method
    ) throws Exception {

        mockMvc.perform(
                        options(URL)
                                .header(HttpHeaders.ORIGIN, origin)
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
                withdrawalService
        );
    }

    private void givenActiveMember() {
        when(memberRepository.existsByIdAndStatusAndCredentialVersionAndRole(
                MEMBER_ID,
                MemberStatus.ACTIVE,
                0L,
                MemberRole.USER
        )).thenReturn(true);
    }

    private String validBody(String password) {
        return objectMapper.writeValueAsString(
                Map.of(
                        "currentPassword", password,
                        "confirmed", true
                )
        );
    }

    private MockHttpServletRequestBuilder withdrawalRequest(
            MockHttpSession session,
            String body
    ) {
        return post(URL)
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private MockHttpSession authenticatedSession() {
        MemberPrincipal principal = new MemberPrincipal(
                MEMBER_ID,
                EMAIL,
                "test-only-password-hash",
                "탈퇴테스트회원",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                0L
        );

        var authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CONTEXT_KEY, context);

        return session;
    }

    private void assertLoginRemains(MockHttpSession session) {
        assertThat(session.isInvalid()).isFalse();

        SecurityContext context =
                (SecurityContext) session.getAttribute(CONTEXT_KEY);

        assertThat(context).isNotNull();
        assertThat(context.getAuthentication()).isNotNull();
        assertThat(context.getAuthentication().isAuthenticated())
                .isTrue();

        assertThat(context.getAuthentication().getPrincipal())
                .isInstanceOfSatisfying(
                        MemberPrincipal.class,
                        principal -> assertThat(principal.getMemberId())
                                .isEqualTo(MEMBER_ID)
                );
    }
}