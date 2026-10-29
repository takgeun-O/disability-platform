package io.github.takgeun.iyum.global.config;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AdminAccessSecurityTest.ProbeController.class,
        properties = "spring.config.import="
)
@Import({
        SecurityConfig.class,
        AdminAccessSecurityTest.ProbeController.class
})
class AdminAccessSecurityTest {

    private static final String PATH = "/api/v1/admin/_test";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberRepository memberRepository;

    @Test
    void 비로그인_조회는_401을_반환한다() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void 일반_회원의_조회는_403을_반환한다() throws Exception {
        givenValidSession(MemberRole.USER);

        mockMvc.perform(
                        get(PATH)
                                .with(user(principal(MemberRole.USER)))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("ACCESS_DENIED"));
    }

    @Test
    void 관리자의_조회는_허용한다() throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        get(PATH)
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isNoContent());
    }

    @Test
    void 관리자도_CSRF_없는_변경_요청은_거절한다() throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        post(PATH)
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void 관리자의_유효한_CSRF_변경_요청은_허용한다() throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        post(PATH)
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isNoContent());
    }

    @Test
    void 일반_회원은_CSRF가_유효해도_변경할_수_없다() throws Exception {
        givenValidSession(MemberRole.USER);

        mockMvc.perform(
                        post(PATH)
                                .with(user(principal(MemberRole.USER)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("ACCESS_DENIED"));
    }

    @Test
    void DB에서_세션이_무효하면_관리자도_접근할_수_없다()
            throws Exception {

        when(memberRepository.existsByIdAndStatusAndCredentialVersionAndRole(
                7L,
                MemberStatus.ACTIVE,
                0L,
                MemberRole.ADMIN
        )).thenReturn(false);

        mockMvc.perform(
                        get(PATH)
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));
    }

    private void givenValidSession(MemberRole role) {
        when(memberRepository.
                existsByIdAndStatusAndCredentialVersionAndRole(
                        7L,
                        MemberStatus.ACTIVE,
                        0L,
                        role
                )).thenReturn(true);
    }

    private MemberPrincipal principal(MemberRole role) {
        return new MemberPrincipal(
                7L,
                "admin-test@example.com",
                "unused-test-password-hash",
                "테스트회원",
                MemberStatus.ACTIVE,
                role,
                0L
        );
    }

    // 테스트에서만 등록되며 실제 서비스 API에는 추가되지 않는다.
    @RestController
    static class ProbeController {

        @GetMapping(PATH)
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void read() {
        }

        @PostMapping(PATH)
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void change() {
        }
    }
}