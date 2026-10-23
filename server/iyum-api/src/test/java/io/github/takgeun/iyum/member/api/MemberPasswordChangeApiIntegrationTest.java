package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.HttpSessionMutexListener;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true",
        "iyum.auth.password-reset.mail.enabled=false",
        "iyum.auth.email-verification.mail.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class MemberPasswordChangeApiIntegrationTest {

    private static final String PASSWORD_URL =
            "/api/v1/members/me/password";

    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "NewPassword456!";
    private static final String WRONG_PASSWORD = "WrongPassword789!";

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ServletContext servletContext;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void 변경한_세션은_유지하고_다른_세션은_다음_요청에서_차단한다()
            throws Exception {

        Member member = createActiveMember();

        // 같은 회원으로 서로 다른 두 세션에 로그인한다.
        CsrfSession current = login(member.getEmail(), OLD_PASSWORD);
        CsrfSession other = login(member.getEmail(), OLD_PASSWORD);

        assertThat(current.session()).isNotSameAs(other.session());

        assertMe(current.session(), member.getId());
        assertMe(other.session(), member.getId());

        String previousSessionId = current.session().getId();
        SecurityContext previousContext = context(current.session());
        var previousAuthentication = previousContext.getAuthentication();
        MemberPrincipal previousPrincipal =
                (MemberPrincipal) previousAuthentication.getPrincipal();

        assertThat(previousPrincipal.getCredentialVersion()).isZero();
        assertThat(principal(other.session()).getCredentialVersion())
                .isZero();

        change(current, OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reloginRequired").value(false))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        // DB 변경이 실제로 커밋됐는지 확인한다.
        assertPasswordState(member.getId(), NEW_PASSWORD, 1L);

        assertThat(passwordEncoder.matches(
                OLD_PASSWORD,
                findMember(member.getId()).getPasswordHash()
        )).isFalse();

        // 현재 세션은 유지되지만 ID와 인증 객체는 교체된다.
        assertThat(current.session().isInvalid()).isFalse();
        assertThat(current.session().getId())
                .isNotEqualTo(previousSessionId);

        SecurityContext updatedContext = context(current.session());

        assertThat(updatedContext).isNotSameAs(previousContext);
        assertThat(updatedContext.getAuthentication())
                .isNotSameAs(previousAuthentication);
        assertThat(principal(current.session()).getCredentialVersion())
                .isEqualTo(1L);

        // 기존 인증 객체를 직접 수정하지 않았는지도 확인한다.
        assertThat(previousPrincipal.getCredentialVersion()).isZero();

        // 다음 요청에서도 새 인증으로 로그인 상태가 유지된다.
        assertMe(current.session(), member.getId());

        // 다른 세션은 즉시 찾아서 제거하는 구조가 아니다.
        // 아직 요청하지 않았으므로 이전 버전이 남아 있다.
        assertThat(other.session().isInvalid()).isFalse();
        assertThat(principal(other.session()).getCredentialVersion())
                .isZero();

        mockMvc.perform(withSession(
                        get("/api/v1/auth/me"),
                        other.session()
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        assertThat(other.session().isInvalid()).isTrue();

        // 다른 세션 차단이 현재 세션에 영향을 주면 안 된다.
        assertMe(current.session(), member.getId());
    }

    @Test
    void 변경_전_CSRF는_거절하고_새로_조회한_CSRF는_사용할_수_있다()
            throws Exception {

        Member member = createActiveMember();
        CsrfSession beforeChange =
                login(member.getEmail(), OLD_PASSWORD);

        change(beforeChange, OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reloginRequired").value(false));

        // 세션은 유지되더라도 변경 전에 받았던 CSRF 토큰은 폐기된다.
        change(beforeChange, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        assertThat(beforeChange.session().isInvalid()).isFalse();
        assertMe(beforeChange.session(), member.getId());

        CsrfSession refreshed = csrf(beforeChange.session());

        // 새 토큰으로 보낸 요청은 CSRF 검증을 통과해서
        // 서비스의 '현재 비밀번호와 동일함' 검사까지 도달한다.
        change(refreshed, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PASSWORD_CHANGE_PASSWORD_UNCHANGED"));

        // 검증 요청 때문에 비밀번호나 버전이 다시 변경되면 안 된다.
        assertPasswordState(member.getId(), NEW_PASSWORD, 1L);
        assertMe(refreshed.session(), member.getId());
    }

    @Test
    void 현재_비밀번호가_틀리면_세션과_CSRF를_유지한다()
            throws Exception {

        Member member = createActiveMember();
        CsrfSession current = login(member.getEmail(), OLD_PASSWORD);
        CsrfSession other = login(member.getEmail(), OLD_PASSWORD);

        String previousSessionId = current.session().getId();
        SecurityContext previousContext = context(current.session());
        String previousHash = findMember(member.getId()).getPasswordHash();

        change(current, WRONG_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PASSWORD_CHANGE_CURRENT_PASSWORD_MISMATCH"));

        Member unchanged = findMember(member.getId());

        assertThat(unchanged.getPasswordHash()).isEqualTo(previousHash);
        assertThat(unchanged.getCredentialVersion()).isZero();

        assertThat(current.session().isInvalid()).isFalse();
        assertThat(current.session().getId()).isEqualTo(previousSessionId);
        assertThat(context(current.session())).isSameAs(previousContext);

        assertMe(current.session(), member.getId());
        assertMe(other.session(), member.getId());

        // 실패 전과 같은 CSRF 토큰으로 다시 시도할 수 있어야 한다.
        change(current, OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reloginRequired").value(false));

        assertPasswordState(member.getId(), NEW_PASSWORD, 1L);
    }

    private Member createActiveMember() {
        String suffix = UUID.randomUUID().toString().replace("-", "");

        Member member = Member.createPending(
                "change-api-" + suffix + "@example.com",
                passwordEncoder.encode(OLD_PASSWORD),
                "회원" + suffix.substring(0, 12)
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private CsrfSession login(String email, String password)
            throws Exception {

        CsrfSession anonymous = csrf(null);

        mockMvc.perform(withSession(
                        post("/api/v1/auth/login"),
                        anonymous.session()
                )
                        .header(anonymous.headerName(), anonymous.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of(
                                        "email", email,
                                        "password", password
                                )
                        )))
                .andExpect(status().isOk());

        // 로그인 성공도 기존 CSRF 토큰을 폐기하므로 다시 조회한다.
        return csrf(anonymous.session());
    }

    private CsrfSession csrf(MockHttpSession existingSession)
            throws Exception {

        MockHttpSession session = existingSession;

        if (session == null) {
            session = new MockHttpSession(servletContext);

            // MockMvc에서는 실제 서블릿 컨테이너의 세션 생성
            // 리스너 호출을 테스트에서 직접 재현한다.
            new HttpSessionMutexListener().sessionCreated(
                    new HttpSessionEvent(session)
            );
        }

        var result = mockMvc.perform(withSession(
                        get("/api/v1/auth/csrf"),
                        session
                ))
                .andExpect(status().isOk())
                .andReturn();

        var body = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        assertThat(result.getRequest().getSession(false))
                .isSameAs(session);

        return new CsrfSession(
                session,
                body.get("headerName").asString(),
                body.get("token").asString()
        );
    }

    private ResultActions change(
            CsrfSession csrf,
            String currentPassword,
            String newPassword
    ) throws Exception {

        return mockMvc.perform(withSession(
                post(PASSWORD_URL),
                csrf.session()
        )
                .header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of(
                                "currentPassword", currentPassword,
                                "newPassword", newPassword,
                                "newPasswordConfirm", newPassword
                        )
                )));
    }

    private MockHttpServletRequestBuilder withSession(
            MockHttpServletRequestBuilder builder,
            MockHttpSession session
    ) {
        return builder.session(session)
                .with(request -> {
                    request.setRequestedSessionId(session.getId());
                    request.setRequestedSessionIdValid(true);
                    request.setRequestedSessionIdFromCookie(true);
                    return request;
                });
    }

    private void assertMe(MockHttpSession session, Long memberId)
            throws Exception {

        mockMvc.perform(withSession(
                        get("/api/v1/auth/me"),
                        session
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId")
                        .value(memberId.intValue()));
    }

    private SecurityContext context(MockHttpSession session) {
        Object stored = session.getAttribute(SPRING_SECURITY_CONTEXT_KEY);

        assertThat(stored).isInstanceOf(SecurityContext.class);

        return (SecurityContext) stored;
    }

    private MemberPrincipal principal(MockHttpSession session) {
        Object principal = context(session)
                .getAuthentication()
                .getPrincipal();

        assertThat(principal).isInstanceOf(MemberPrincipal.class);

        return (MemberPrincipal) principal;
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow();
    }

    private void assertPasswordState(
            Long memberId,
            String expectedPassword,
            long expectedVersion
    ) {
        Member member = findMember(memberId);

        assertThat(member.getCredentialVersion())
                .isEqualTo(expectedVersion);

        assertThat(passwordEncoder.matches(
                expectedPassword,
                member.getPasswordHash()
        )).isTrue();
    }

    private record CsrfSession(
            MockHttpSession session,
            String headerName,
            String token
    ) {
        @Override
        public String toString() {
            return "CsrfSession[REDACTED]";
        }
    }
}