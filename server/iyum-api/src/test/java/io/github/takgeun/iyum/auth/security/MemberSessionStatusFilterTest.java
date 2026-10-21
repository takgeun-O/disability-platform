package io.github.takgeun.iyum.auth.security;

import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MemberSessionStatusFilterTest.Endpoints.class,
        properties = "spring.config.import=")
@Import({SecurityConfig.class, MemberSessionStatusFilterTest.Endpoints.class})
class MemberSessionStatusFilterTest {
    @Autowired MockMvc mvc;
    @MockitoBean MemberRepository members;
    @MockitoBean EndpointService service;

    @Test
    void activeMemberContinuesWithTheExactPrincipalId() throws Exception {
        when(members.existsByIdAndStatusAndCredentialVersion(7L, MemberStatus.ACTIVE, 0L)).thenReturn(true);
        mvc.perform(get("/test/session-protected").with(user(principal())))
                .andExpect(status().isOk());
        verify(members).existsByIdAndStatusAndCredentialVersion(7L, MemberStatus.ACTIVE, 0L);
        verify(service).read();
    }

    @Test
    void inactiveMemberLosesSessionAndCannotReachProtectedController() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/test/session-protected").session(session).with(user(principal())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
        verify(members).existsByIdAndStatusAndCredentialVersion(7L, MemberStatus.ACTIVE, 0L);
        verifyNoInteractions(service);
    }

    @Test
    void inactiveMemberCanContinuePublicReadAsAnonymous() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/api/v1/community/posts").session(session).with(user(principal())))
                .andExpect(status().isOk()).andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
        verify(members).existsByIdAndStatusAndCredentialVersion(7L, MemberStatus.ACTIVE, 0L);
        verify(service).read();
    }

    @Test
    void validCsrfStillRequiresAnActiveMemberForWrites() throws Exception {
        mvc.perform(post("/test/session-protected").with(user(principal())).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verify(members).existsByIdAndStatusAndCredentialVersion(7L, MemberStatus.ACTIVE, 0L);
        verifyNoInteractions(service);
    }

    @Test
    void csrfRejectsBeforeMemberStatusLookup() throws Exception {
        mvc.perform(post("/test/session-protected").with(user(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(post("/test/session-protected").with(user(principal()))
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(members, service);
    }

    @Test
    void anonymousRequestsDoNotLookUpMemberStatus() throws Exception {
        mvc.perform(get("/test/session-protected")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/community/posts")).andExpect(status().isOk());
        verifyNoInteractions(members);
        verify(service).read();
    }

    @Test
    void ordinarySpringUserIsNotAMemberPrincipal() throws Exception {
        mvc.perform(get("/test/session-protected").with(user("test-user")))
                .andExpect(status().isOk());
        verifyNoInteractions(members);
        verify(service).read();
    }

    @Test
    void 인증_버전이_일치하면_로그인을_유지한다() throws Exception {
        when(members.existsByIdAndStatusAndCredentialVersion(
                7L,
                MemberStatus.ACTIVE,
                7L
        )).thenReturn(true);

        MockHttpSession session = new MockHttpSession();

        mvc.perform(
                        get("/test/session-protected")
                                .session(session)
                                .with(user(principal(7L)))
                )
                .andExpect(status().isOk())
                .andExpect(
                        org.springframework.security.test.web.servlet
                                .response.SecurityMockMvcResultMatchers
                                .authenticated()
                );

        assertThat(session.isInvalid()).isFalse();

        verify(members).existsByIdAndStatusAndCredentialVersion(
                7L,
                MemberStatus.ACTIVE,
                7L
        );
        verify(service).read();
    }

    @Test
    void 이전_인증_버전의_세션은_보호된_요청에서_로그아웃된다()
            throws Exception {

        when(members.existsByIdAndStatusAndCredentialVersion(
                7L,
                MemberStatus.ACTIVE,
                6L
        )).thenReturn(false);

        MockHttpSession session = new MockHttpSession();

        mvc.perform(
                        get("/test/session-protected")
                                .session(session)
                                .with(user(principal(6L)))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value("AUTHENTICATION_REQUIRED")
                )
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(unauthenticated());

        assertThat(session.isInvalid()).isTrue();

        verify(members).existsByIdAndStatusAndCredentialVersion(
                7L,
                MemberStatus.ACTIVE,
                6L
        );
        verifyNoInteractions(service);
    }

    @Test
    void 이전_인증_버전이라도_공개_조회는_비로그인으로_진행한다()
            throws Exception {

        when(members.existsByIdAndStatusAndCredentialVersion(
                7L,
                MemberStatus.ACTIVE,
                6L
        )).thenReturn(false);

        MockHttpSession session = new MockHttpSession();

        mvc.perform(
                        get("/api/v1/community/posts")
                                .session(session)
                                .with(user(principal(6L)))
                )
                .andExpect(status().isOk())
                .andExpect(unauthenticated())
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        assertThat(session.isInvalid()).isTrue();

        verify(members).existsByIdAndStatusAndCredentialVersion(
                7L,
                MemberStatus.ACTIVE,
                6L
        );
        verify(service).read();
    }

    private MemberPrincipal principal() {
        return principal(0L);
    }

    private MemberPrincipal principal(long credentialVersion) {
        return new MemberPrincipal(
                7L,
                "fixture@example.com",
                "test-only-hash",
                "테스트회원",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                credentialVersion
        );
    }

    interface EndpointService { void read(); }

    @RestController
    static class Endpoints {
        private final EndpointService service;
        Endpoints(EndpointService service) { this.service = service; }
        @GetMapping({"/test/session-protected", "/api/v1/community/posts"})
        String read() { service.read(); return "ok"; }
        @PostMapping("/test/session-protected")
        String write() { service.read(); return "ok"; }
    }
}
