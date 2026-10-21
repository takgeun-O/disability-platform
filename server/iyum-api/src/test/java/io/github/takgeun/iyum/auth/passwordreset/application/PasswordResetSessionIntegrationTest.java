package io.github.takgeun.iyum.auth.passwordreset.application;

import io.github.takgeun.iyum.auth.passwordreset.domain.PasswordResetToken;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenGenerator;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenHasher;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenRepository;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
@AutoConfigureMockMvc(addFilters = true)
@Testcontainers
class PasswordResetSessionIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-10-21T05:00:00Z");

    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "NewPassword456!";

    private static final String PROFILE_URL = "/api/v1/members/me";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordResetTokenRepository tokenRepository;

    @Autowired
    PasswordResetTokenGenerator tokenGenerator;

    @Autowired
    PasswordResetTokenHasher tokenHasher;

    @Autowired
    PasswordResetService resetService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void 재설정하면_기존_두_세션을_차단하고_새_비밀번호로_로그인할_수_있다()
            throws Exception {

        Account account = createActiveMember();
        Account otherAccount = createActiveMember();

        MockHttpSession firstSession =
                login(account.email(), OLD_PASSWORD);

        MockHttpSession secondSession =
                login(account.email(), OLD_PASSWORD);

        MockHttpSession otherSession =
                login(otherAccount.email(), OLD_PASSWORD);

        assertThat(firstSession.getId())
                .isNotEqualTo(secondSession.getId());

        assertCredentialVersion(firstSession, 0L);
        assertCredentialVersion(secondSession, 0L);

        assertProfileAccessible(firstSession, account.memberId());
        assertProfileAccessible(secondSession, account.memberId());
        assertProfileAccessible(otherSession, otherAccount.memberId());

        String rawToken = createResetToken(account.memberId());

        // 실제 서비스 호출. 반환 시 트랜잭션 커밋까지 완료된다.
        resetService.reset(rawToken, NEW_PASSWORD);

        Member changed = memberRepository.findById(account.memberId())
                .orElseThrow();

        assertThat(changed.getCredentialVersion()).isEqualTo(1L);

        // 기존 세션에 저장된 로그인 당시 버전은 그대로여야 한다.
        assertCredentialVersion(firstSession, 0L);
        assertCredentialVersion(secondSession, 0L);

        // 각 세션의 다음 요청에서 인증 버전 불일치를 감지한다.
        assertSessionRejected(firstSession);
        assertSessionRejected(secondSession);

        // 다른 회원의 세션에는 영향을 주지 않는다.
        assertProfileAccessible(otherSession, otherAccount.memberId());
        assertCredentialVersion(otherSession, 0L);

        // 기존 비밀번호는 더 이상 사용할 수 없다.
        performLogin(account.email(), OLD_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(unauthenticated());

        // 새 비밀번호로 로그인하면 최신 인증 버전이 저장된다.
        MockHttpSession newSession =
                login(account.email(), NEW_PASSWORD);

        assertCredentialVersion(newSession, 1L);
        assertProfileAccessible(newSession, account.memberId());
    }

    @Test
    void 재설정_후_기존_세션으로_공개_조회하면_인증만_해제한다()
            throws Exception {

        Account account = createActiveMember();
        MockHttpSession session = login(account.email(), OLD_PASSWORD);

        assertProfileAccessible(session, account.memberId());

        resetService.reset(
                createResetToken(account.memberId()),
                NEW_PASSWORD
        );

        // 보호된 API를 거치지 않고 공개 API를 먼저 호출한다.
        mockMvc.perform(
                        get("/api/v1/community/posts")
                                .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(unauthenticated());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 재설정에_실패하면_기존_세션과_인증_버전을_유지한다()
            throws Exception {

        Account account = createActiveMember();

        MockHttpSession firstSession =
                login(account.email(), OLD_PASSWORD);

        MockHttpSession secondSession =
                login(account.email(), OLD_PASSWORD);

        String rawToken = createResetToken(account.memberId());

        // 현재와 같은 비밀번호로 변경하려는 요청은 실패한다.
        assertThatThrownBy(() ->
                resetService.reset(rawToken, OLD_PASSWORD)
        ).isInstanceOfSatisfying(
                PasswordResetException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                PasswordResetException.Code
                                        .PASSWORD_RESET_PASSWORD_UNCHANGED
                        )
        );

        Member member = memberRepository.findById(account.memberId())
                .orElseThrow();

        assertThat(member.getCredentialVersion()).isZero();

        assertProfileAccessible(firstSession, account.memberId());
        assertProfileAccessible(secondSession, account.memberId());

        assertCredentialVersion(firstSession, 0L);
        assertCredentialVersion(secondSession, 0L);
    }

    private Account createActiveMember() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "");

        String email = "reset-session-" + suffix + "@example.com";

        Member member = Member.createPending(
                email,
                passwordEncoder.encode(OLD_PASSWORD),
                "회원" + suffix.substring(0, 16)
        );

        member.activeAfterEmailVerification();

        Member saved = memberRepository.saveAndFlush(member);

        return new Account(saved.getId(), email);
    }

    private String createResetToken(Long memberId) {
        String rawToken = tokenGenerator.generate();

        tokenRepository.saveAndFlush(
                PasswordResetToken.issue(
                        memberId,
                        tokenHasher.hash(rawToken),
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(1800)
                )
        );

        return rawToken;
    }

    private MockHttpSession login(String email, String password)
            throws Exception {

        var result = performLogin(email, password)
                .andExpect(status().isOk())
                .andExpect(authenticated())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) result.getRequest().getSession(false);

        assertThat(session).isNotNull();

        return session;
    }

    private ResultActions performLogin(String email, String password)
            throws Exception {

        // 매번 새로운 익명 세션과 실제 CSRF 토큰을 사용한다.
        CsrfSession csrf = createCsrfSession();

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
                        .content(objectMapper.writeValueAsString(
                                Map.of(
                                        "email", email,
                                        "password", password
                                )
                        ))
        );
    }

    private CsrfSession createCsrfSession() throws Exception {
        var result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        var body = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        MockHttpSession session =
                (MockHttpSession) result.getRequest().getSession(false);

        assertThat(session).isNotNull();

        return new CsrfSession(
                session,
                body.get("headerName").asString(),
                body.get("token").asString()
        );
    }

    private void assertProfileAccessible(
            MockHttpSession session,
            Long expectedMemberId
    ) throws Exception {

        mockMvc.perform(get(PROFILE_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(authenticated())
                .andExpect(jsonPath("$.memberId")
                        .value(is(expectedMemberId), Long.class));

        assertThat(session.isInvalid()).isFalse();
    }

    private void assertSessionRejected(MockHttpSession session)
            throws Exception {

        mockMvc.perform(get(PROFILE_URL).session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(unauthenticated());

        assertThat(session.isInvalid()).isTrue();
    }

    private void assertCredentialVersion(
            MockHttpSession session,
            long expectedVersion
    ) {
        Object storedContext = session.getAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY
        );

        assertThat(storedContext).isInstanceOf(SecurityContext.class);

        SecurityContext context = (SecurityContext) storedContext;

        assertThat(context.getAuthentication()).isNotNull();
        assertThat(context.getAuthentication().getPrincipal())
                .isInstanceOf(MemberPrincipal.class);

        MemberPrincipal principal =
                (MemberPrincipal) context.getAuthentication().getPrincipal();

        assertThat(principal.getCredentialVersion())
                .isEqualTo(expectedVersion);
    }

    private record Account(Long memberId, String email) {
    }

    private record CsrfSession(
            MockHttpSession session,
            String headerName,
            String token
    ) {
    }
}