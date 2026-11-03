package io.github.takgeun.iyum.auth.session.api;

import io.github.takgeun.iyum.auth.security.config.AuthenticationConfig;
import io.github.takgeun.iyum.auth.security.config.PasswordConfig;
import io.github.takgeun.iyum.auth.session.application.CurrentMemberReadService;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.auth.session.config.SessionSecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = {
                LoginController.class,
                CsrfController.class,
                CurrentMemberController.class
        },
        properties = "spring.config.import="
)
@Import({
        SecurityConfig.class,
        SessionSecurityConfig.class,
        AuthenticationConfig.class,
        PasswordConfig.class,
        GlobalExceptionHandler.class,
        CurrentMemberReadService.class
})
class AuthSessionControllerTest {

    private static final String EMAIL = "member@example.com";
    private static final String PASSWORD = "Example123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MemberRepository memberRepository;

    @Test
    void 로그인하지_않으면_현재_회원_조회는_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void 로그인한_세션으로_현재_회원_정보를_조회한다() throws Exception {
        CsrfSession loggedIn = loginActiveMember();

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(loggedIn.session())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(1))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.nickname").value("타끈"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));
    }

    @Test
    void 로그아웃하면_세션이_무효화되고_204를_반환한다() throws Exception {
        CsrfSession loggedIn = loginActiveMember();

        logout(loggedIn)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        assertTrue(loggedIn.session().isInvalid());

        // 무효화된 MockHttpSession을 다시 전달하지 않는다.
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void CSRF_토큰_없는_로그아웃은_거절하고_세션을_유지한다()
            throws Exception {

        CsrfSession loggedIn = loginActiveMember();

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .session(loggedIn.session())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        assertFalse(loggedIn.session().isInvalid());

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(loggedIn.session())
                )
                .andExpect(status().isOk());
    }

    @Test
    void 로그인_전에_받은_CSRF_토큰으로는_로그아웃할_수_없다()
            throws Exception {

        prepareActiveMember();

        CsrfSession beforeLogin = csrf(null);

        login(beforeLogin)
                .andExpect(status().isOk());

        // 로그인 성공으로 이전 CSRF 토큰은 폐기된 상태다.
        logout(beforeLogin)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        assertFalse(beforeLogin.session().isInvalid());

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(beforeLogin.session())
                )
                .andExpect(status().isOk());
    }

    @Test
    void GET_요청으로는_로그아웃되지_않는다() throws Exception {
        CsrfSession loggedIn = loginActiveMember();

        mockMvc.perform(
                get("/api/v1/auth/logout")
                        .session(loggedIn.session())
        );

        assertFalse(loggedIn.session().isInvalid());

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(loggedIn.session())
                )
                .andExpect(status().isOk());
    }

    @Test
    void 비로그인_상태도_유효한_CSRF가_있으면_로그아웃은_204다()
            throws Exception {

        CsrfSession anonymous = csrf(null);

        logout(anonymous)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertTrue(anonymous.session().isInvalid());
    }

    @Test
    void 로그아웃_후에는_새_CSRF_토큰으로_다시_로그인할_수_있다()
            throws Exception {

        CsrfSession loggedIn = loginActiveMember();

        logout(loggedIn)
                .andExpect(status().isNoContent());

        CsrfSession fresh = csrf(null);

        // 새 세션에 이전 세션의 토큰을 보내면 거절되어야 한다.
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .session(fresh.session())
                                .header(
                                        fresh.headerName(),
                                        loggedIn.token()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        login(fresh)
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(fresh.session())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(1));
    }

    @Test
    void 로그인_후_닉네임이_바뀌면_같은_세션에서_최신값을_반환한다()
            throws Exception {
        CsrfSession loggedIn = loginActiveMember();

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(loggedIn.session())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("타끈"));

        // Repository가 반환하는 현재 회원 정보만 변경한다.
        // 이미 생성된 세션의 MemberPrincipal은 변경하지 않는다.
        Member currentMember = memberRepository.findById(1L)
                .orElseThrow();

        when(currentMember.getNickname()).thenReturn("변경된닉네임");

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(loggedIn.session())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(1))
                .andExpect(jsonPath("$.nickname").value("변경된닉네임"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));
    }

    private void prepareActiveMember() {
        when(memberRepository.existsByIdAndStatusAndCredentialVersionAndRole(1L, MemberStatus.ACTIVE, 0L, MemberRole.USER)).thenReturn(true);
        Member member = mock(Member.class);

        when(member.getId()).thenReturn(1L);
        when(member.getEmail()).thenReturn(EMAIL);
        when(member.getPasswordHash())
                .thenReturn(passwordEncoder.encode(PASSWORD));
        when(member.getNickname()).thenReturn("타끈");
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getRole()).thenReturn(MemberRole.USER);

        when(memberRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(member));

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
    }

    private CsrfSession loginActiveMember() throws Exception {
        prepareActiveMember();

        CsrfSession beforeLogin = csrf(null);

        login(beforeLogin)
                .andExpect(status().isOk());

        // 로그인 성공 후의 새 CSRF 토큰을 받는다.
        return csrf(beforeLogin.session());
    }

    private ResultActions login(CsrfSession csrf) throws Exception {
        return mockMvc.perform(
                post("/api/v1/auth/login")
                        .session(csrf.session())
                        .with(request -> {
                            request.setRequestedSessionId(
                                    csrf.session().getId()
                            );
                            request.setRequestedSessionIdValid(true);
                            request.setRequestedSessionIdFromCookie(true);
                            return request;
                        })
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody())
        );
    }

    private ResultActions logout(CsrfSession csrf) throws Exception {
        return mockMvc.perform(
                post("/api/v1/auth/logout")
                        .session(csrf.session())
                        .header(csrf.headerName(), csrf.token())
        );
    }

    private String loginBody() {
        return objectMapper.writeValueAsString(
                Map.of(
                        "email", EMAIL,
                        "password", PASSWORD
                )
        );
    }

    private CsrfSession csrf(MockHttpSession session) throws Exception {
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
                (MockHttpSession) result.getRequest().getSession(false),
                body.get("headerName").asString(),
                body.get("token").asString()
        );
    }

    private record CsrfSession(
            MockHttpSession session,
            String headerName,
            String token
    ) {
    }
}