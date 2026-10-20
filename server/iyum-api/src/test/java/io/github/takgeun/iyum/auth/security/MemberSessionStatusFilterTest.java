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
        when(members.existsByIdAndStatus(7L, MemberStatus.ACTIVE)).thenReturn(true);
        mvc.perform(get("/test/session-protected").with(user(principal())))
                .andExpect(status().isOk());
        verify(members).existsByIdAndStatus(7L, MemberStatus.ACTIVE);
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
        verify(members).existsByIdAndStatus(7L, MemberStatus.ACTIVE);
        verifyNoInteractions(service);
    }

    @Test
    void inactiveMemberCanContinuePublicReadAsAnonymous() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/api/v1/community/posts").session(session).with(user(principal())))
                .andExpect(status().isOk()).andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
        verify(members).existsByIdAndStatus(7L, MemberStatus.ACTIVE);
        verify(service).read();
    }

    @Test
    void validCsrfStillRequiresAnActiveMemberForWrites() throws Exception {
        mvc.perform(post("/test/session-protected").with(user(principal())).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verify(members).existsByIdAndStatus(7L, MemberStatus.ACTIVE);
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

    private MemberPrincipal principal() {
        return new MemberPrincipal(7L, "fixture@example.com", "test-only-hash",
                "테스트회원", MemberStatus.ACTIVE, MemberRole.USER);
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
