package io.github.takgeun.iyum.auth.session.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.auth.security.config.AuthenticationConfig;
import io.github.takgeun.iyum.auth.security.config.PasswordConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                LoginController.class,
                CsrfController.class
        },
        properties = "spring.config.import="
)
@Import({
        SecurityConfig.class,
        AuthenticationConfig.class,
        PasswordConfig.class,
        GlobalExceptionHandler.class,
        LoginControllerTest.SessionProbeController.class
})
class LoginControllerTest {

    private static final String EMAIL = "member@example.com";
    private static final String PASSWORD = "Example123!";

    private static final String CONTEXT_KEY =
            HttpSessionSecurityContextRepository
                    .SPRING_SECURITY_CONTEXT_KEY;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MemberRepository memberRepository;

    @Test
    void 로그인하면_세션ID가_바뀌고_다음_요청에도_인증이_유지된다()
            throws Exception {
        givenMember(MemberStatus.ACTIVE);
        when(memberRepository.existsByIdAndStatusAndCredentialVersion(1L, MemberStatus.ACTIVE, 0L)).thenReturn(true);

        CsrfSession csrf = csrf(null);
        String previousSessionId = csrf.session().getId();

        login(csrf, EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(1))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.nickname").value("타끈"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(csrf.session().getId())
                .isNotEqualTo(previousSessionId);

        SecurityContext savedContext =
                (SecurityContext) csrf.session()
                        .getAttribute(CONTEXT_KEY);

        assertThat(savedContext).isNotNull();
        assertThat(savedContext.getAuthentication().isAuthenticated())
                .isTrue();

        assertThat(savedContext.getAuthentication().getCredentials())
                .isNull();

        MemberPrincipal principal =
                (MemberPrincipal) savedContext
                        .getAuthentication()
                        .getPrincipal();

        assertThat(principal.getPassword()).isNull();

        // 같은 세션으로 보내는 다음 요청에서도 인증되어야 한다.
        mockMvc.perform(
                        get("/test/login-session")
                                .session(csrf.session())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(1));

        // 로그인한 세션이 없으면 접근할 수 없다.
        mockMvc.perform(get("/test/login-session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void 로그인_이전_CSRF는_거절하고_새_CSRF는_허용한다()
            throws Exception {
        givenMember(MemberStatus.ACTIVE);
        when(memberRepository.existsByIdAndStatusAndCredentialVersion(1L, MemberStatus.ACTIVE, 0L)).thenReturn(true);

        CsrfSession beforeLogin = csrf(null);

        login(beforeLogin, EMAIL, PASSWORD)
                .andExpect(status().isOk());

        CsrfSession afterLogin = csrf(beforeLogin.session());

        mockMvc.perform(
                        post("/test/login-session")
                                .session(afterLogin.session())
                                .header(
                                        beforeLogin.headerName(),
                                        beforeLogin.token()
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        mockMvc.perform(
                        post("/test/login-session")
                                .session(afterLogin.session())
                                .header(
                                        afterLogin.headerName(),
                                        afterLogin.token()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(1));
    }

    @ParameterizedTest
    @CsvSource({
            "ACTIVE, Wrong123!, 401, LOGIN_FAILED",
            "PENDING, Wrong123!, 401, LOGIN_FAILED",
            "PENDING, Example123!, 403, EMAIL_VERIFICATION_REQUIRED",
            "RESTRICTED, Example123!, 403, ACCOUNT_UNAVAILABLE",
            "SUSPENDED, Example123!, 403, ACCOUNT_UNAVAILABLE",
            "WITHDRAWN, Example123!, 403, ACCOUNT_UNAVAILABLE",
            "DELETED, Example123!, 403, ACCOUNT_UNAVAILABLE"
    })
    void 인증_실패를_구분하고_인증정보를_세션에_저장하지_않는다(
            MemberStatus memberStatus,
            String password,
            int expectedStatus,
            String expectedCode
    ) throws Exception {
        givenMember(memberStatus);

        CsrfSession csrf = csrf(null);
        String previousSessionId = csrf.session().getId();

        login(csrf, EMAIL, password)
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode));

        assertThat(csrf.session().getAttribute(CONTEXT_KEY))
                .isNull();

        assertThat(csrf.session().getId())
                .isEqualTo(previousSessionId);
    }

    @Test
    void 없는_이메일도_같은_로그인_실패로_응답한다()
            throws Exception {
        when(memberRepository.findByEmail(EMAIL))
                .thenReturn(Optional.empty());

        CsrfSession csrf = csrf(null);

        login(csrf, EMAIL, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.message")
                        .value("이메일 또는 비밀번호를 확인해 주세요."));

        assertThat(csrf.session().getAttribute(CONTEXT_KEY))
                .isNull();
    }

    @Test
    void 입력_검증에_실패하면_회원을_조회하지_않는다()
            throws Exception {
        CsrfSession csrf = csrf(null);

        login(csrf, "not-an-email", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("email"));

        login(csrf, EMAIL, "가".repeat(25))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("password"));

        verifyNoInteractions(memberRepository);
    }

    @Test
    void CSRF가_없거나_잘못되면_인증을_시도하지_않는다()
            throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(EMAIL, PASSWORD))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        CsrfSession csrf = csrf(null);

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .session(csrf.session())
                                .header(csrf.headerName(), "invalid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(EMAIL, PASSWORD))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(memberRepository);
    }

    @Test
    void DB_오류를_비밀번호_오류로_응답하지_않는다()
            throws Exception {
        when(memberRepository.findByEmail(EMAIL))
                .thenThrow(
                        new DataAccessResourceFailureException(
                                "internal database detail"
                        )
                );

        CsrfSession csrf = csrf(null);

        var result = login(csrf, EMAIL, PASSWORD)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code")
                        .value("INTERNAL_SERVER_ERROR"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("internal database detail");

        assertThat(csrf.session().getAttribute(CONTEXT_KEY))
                .isNull();
    }

    private ResultActions login(
            CsrfSession csrf,
            String email,
            String password
    ) throws Exception {
        return mockMvc.perform(
                post("/api/v1/auth/login")
                        .session(csrf.session())
                        .with(request -> {
                            // 유효한 기존 세션 ID를 전달한 상황을 재현한다.
                            request.setRequestedSessionId(
                                    csrf.session().getId()
                            );
                            request.setRequestedSessionIdValid(true);
                            request.setRequestedSessionIdFromCookie(true);
                            return request;
                        })
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password))
        );
    }

    private String loginBody(
            String email,
            String password
    ) {
        return objectMapper.writeValueAsString(
                Map.of(
                        "email", email,
                        "password", password
                )
        );
    }

    private CsrfSession csrf(MockHttpSession session)
            throws Exception {
        var request = get("/api/v1/auth/csrf");

        if (session != null) {
            request.session(session);
        }

        var result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();

        var body = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        return new CsrfSession(
                (MockHttpSession) result
                        .getRequest()
                        .getSession(false),
                body.get("headerName").asString(),
                body.get("token").asString()
        );
    }

    private void givenMember(MemberStatus memberStatus) {
        Member member = mock(Member.class);

        when(member.getId()).thenReturn(1L);
        when(member.getEmail()).thenReturn(EMAIL);
        when(member.getPasswordHash())
                .thenReturn(passwordEncoder.encode(PASSWORD));
        when(member.getNickname()).thenReturn("타끈");
        when(member.getStatus()).thenReturn(memberStatus);
        when(member.getRole()).thenReturn(MemberRole.USER);

        when(memberRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(member));
    }

    private record CsrfSession(
            MockHttpSession session,
            String headerName,
            String token
    ) {
    }

    @TestComponent
    @RestController
    public static class SessionProbeController {

        @RequestMapping(
                value = "/test/login-session",
                method = {
                        RequestMethod.GET,
                        RequestMethod.POST
                }
        )
        public Map<String, Long> probe(
                @AuthenticationPrincipal MemberPrincipal principal
        ) {
            return Map.of(
                    "memberId",
                    principal.getMemberId()
            );
        }
    }
}