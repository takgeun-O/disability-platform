package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.application.MemberPasswordChangeResult;
import io.github.takgeun.iyum.member.application.MemberPasswordChangeService;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import io.github.takgeun.iyum.member.security.MemberPasswordChangeSessionUpdateResult;
import io.github.takgeun.iyum.member.security.MemberPasswordChangeSessionUpdater;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = MemberPasswordChangeController.class,
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
class MemberPasswordChangeControllerTest {

    private static final String URL = "/api/v1/members/me/password";
    private static final String ORIGIN = "http://127.0.0.1:8443";

    private static final Instant NOW =
            Instant.parse("2026-10-24T00:00:00Z");

    // 원문 공백이 서비스까지 유지되는지도 검증한다.
    private static final String CURRENT = " OldPass123! ";
    private static final String NEW = " NewPass456! ";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    MemberPasswordChangeService service;

    @MockitoBean
    MemberPasswordChangeSessionUpdater updater;

    @MockitoBean
    MemberRepository members;

    @MockitoBean
    Clock clock;

    private MockHttpSession session;
    private Authentication authentication;
    private MemberPasswordChangeSessionUpdater.SessionSnapshot snapshot;

    @BeforeEach
    void setUp() {
        MemberPrincipal principal = new MemberPrincipal(
                7L,
                "member@example.com",
                "",
                "테스트회원",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                3L
        );

        var token = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );
        token.eraseCredentials();
        authentication = token;

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        session = new MockHttpSession();
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);

        snapshot = mock(
                MemberPasswordChangeSessionUpdater.SessionSnapshot.class
        );

        when(members.existsByIdAndStatusAndCredentialVersionAndRole(
                7L, MemberStatus.ACTIVE, 3L, MemberRole.USER
        )).thenReturn(true);

        when(updater.capture(any(), same(authentication)))
                .thenReturn(snapshot);

        when(clock.instant()).thenReturn(NOW);
    }

    @ParameterizedTest
    @CsvSource({
            "SESSION_RETAINED, false",
            "RELOGIN_REQUIRED, true"
    })
    void 변경_성공은_세션_갱신_결과와_함께_200을_반환한다(
            MemberPasswordChangeSessionUpdateResult sessionResult,
            boolean reloginRequired
    ) throws Exception {
        var changed = changedResult();

        when(service.change(7L, 3L, CURRENT, NEW))
                .thenReturn(changed);

        when(updater.update(same(snapshot), same(changed), any(), any()))
                .thenReturn(sessionResult);

        mvc.perform(validRequest()
                        .with(csrf().asHeader())
                        .param("memberId", "999")
                        .param("credentialVersion", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reloginRequired")
                        .value(reloginRequired))
                .andExpect(jsonPath("$.credentialVersion").doesNotExist())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ));

        var order = inOrder(updater, service);

        order.verify(updater).capture(any(), same(authentication));
        order.verify(service).change(7L, 3L, CURRENT, NEW);
        order.verify(updater).update(
                same(snapshot),
                same(changed),
                any(),
                any()
        );

        verifyNoMoreInteractions(service, updater);
    }

    @ParameterizedTest
    @CsvSource({
            "AUTHENTICATION_REQUIRED, 401, AUTHENTICATION_REQUIRED",
            "INVALID_CURRENT_PASSWORD, 400, PASSWORD_CHANGE_INVALID_CURRENT_PASSWORD",
            "INVALID_NEW_PASSWORD, 400, PASSWORD_CHANGE_INVALID_NEW_PASSWORD",
            "CURRENT_PASSWORD_MISMATCH, 400, PASSWORD_CHANGE_CURRENT_PASSWORD_MISMATCH",
            "PASSWORD_UNCHANGED, 400, PASSWORD_CHANGE_PASSWORD_UNCHANGED"
    })
    void 거절_사유를_HTTP_응답으로_변환하고_세션_갱신을_호출하지_않는다(
            MemberPasswordChangeResult.Reason reason,
            int expectedStatus,
            String expectedCode
    ) throws Exception {
        when(service.change(7L, 3L, CURRENT, NEW))
                .thenReturn(new MemberPasswordChangeResult.Rejected(
                        reason,
                        null
                ));

        var result = mvc.perform(validRequest().with(csrf().asHeader()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.reloginRequired").doesNotExist())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andReturn();

        String expectedField = switch (reason) {
            case INVALID_CURRENT_PASSWORD, CURRENT_PASSWORD_MISMATCH ->
                    "currentPassword";
            case INVALID_NEW_PASSWORD, PASSWORD_UNCHANGED ->
                    "newPassword";
            default -> null;
        };

        if (expectedField != null) {
            jsonPath("$.fieldErrors[0].field")
                    .value(expectedField)
                    .match(result);
        } else {
            jsonPath("$.fieldErrors").isEmpty().match(result);
        }

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(CURRENT, NEW);

        assertThat(session.isInvalid()).isFalse();

        verify(updater, never()).update(any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "1201, 2",
            "1000, 1",
            "0, 0",
            "-1000, 0"
    })
    void 제한_응답의_대기시간은_올림하고_음수가_되지_않는다(
            long remainingMillis,
            String expectedSeconds
    ) throws Exception {
        when(service.change(7L, 3L, CURRENT, NEW))
                .thenReturn(new MemberPasswordChangeResult.Rejected(
                        MemberPasswordChangeResult.Reason.RATE_LIMITED,
                        NOW.plusMillis(remainingMillis)
                ));

        mvc.perform(validRequest().with(csrf().asHeader()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code")
                        .value("PASSWORD_CHANGE_RATE_LIMITED"))
                .andExpect(header().string(
                        HttpHeaders.RETRY_AFTER,
                        expectedSeconds
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString(HttpHeaders.RETRY_AFTER)
                ));

        verify(updater, never()).update(any(), any(), any(), any());
    }

    @Test
    void 세션을_기록하지_못하면_변경_서비스를_호출하지_않는다()
            throws Exception {
        when(updater.capture(any(), same(authentication)))
                .thenReturn(null);

        mvc.perform(validRequest().with(csrf().asHeader()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(service);
        verify(updater, never()).update(any(), any(), any(), any());
    }

    @Test
    void 비로그인은_유효한_CSRF가_있어도_변경할_수_없다()
            throws Exception {
        mvc.perform(post(URL)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(NEW))
                        .with(csrf().asHeader()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(service, updater);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void CSRF가_없거나_잘못되면_컨트롤러를_호출하지_않는다(
            boolean invalidToken
    ) throws Exception {
        var request = validRequest();

        if (invalidToken) {
            request.with(csrf().asHeader().useInvalidToken());
        }

        mvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(service, updater);
    }

    @Test
    void 확인값이_다르면_DTO_검증에서_차단한다() throws Exception {
        mvc.perform(post(URL)
                        .session(session)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("DifferentPass789!"))
                        .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("newPasswordConfirm"));

        verifyNoInteractions(service, updater);
    }

    @Test
    void 갱신_중_예상하지_못한_오류를_성공으로_숨기거나_변경을_재시도하지_않는다()
            throws Exception {
        var changed = changedResult();

        when(service.change(7L, 3L, CURRENT, NEW))
                .thenReturn(changed);

        when(updater.update(same(snapshot), same(changed), any(), any()))
                .thenThrow(new IllegalStateException("test-internal-detail"));

        var result = mvc.perform(validRequest().with(csrf().asHeader()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.reloginRequired").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("test-internal-detail", CURRENT, NEW);

        verify(service, times(1)).change(7L, 3L, CURRENT, NEW);
        verify(updater, times(1)).update(
                same(snapshot),
                same(changed),
                any(),
                any()
        );
    }

    @Test
    void 개발_프론트의_사전_요청을_허용한다() throws Exception {
        mvc.perform(options(URL)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                "POST"
                        )
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Content-Type,X-CSRF-TOKEN"
                        ))
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
                        containsString("POST")
                ));

        verifyNoInteractions(service, updater, members);
    }

    private MockHttpServletRequestBuilder validRequest() throws Exception {
        return post(URL)
                .session(session)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(NEW));
    }

    private String body(String confirmation) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "currentPassword", CURRENT,
                "newPassword", NEW,
                "newPasswordConfirm", confirmation
        ));
    }

    private MemberPasswordChangeResult.Changed changedResult() {
        return new MemberPasswordChangeResult.Changed(
                7L,
                "member@example.com",
                "테스트회원",
                MemberRole.USER,
                3L,
                4L
        );
    }
}