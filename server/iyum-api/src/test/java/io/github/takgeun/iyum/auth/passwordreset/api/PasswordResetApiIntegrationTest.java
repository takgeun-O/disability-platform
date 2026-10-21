package io.github.takgeun.iyum.auth.passwordreset.api;

import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetMailRequested;
import io.github.takgeun.iyum.auth.passwordreset.domain.PasswordResetToken;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenHasher;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true",

        "iyum.auth.password-reset.token-ttl=30m",
        "iyum.auth.password-reset.resend-min-interval=60s",
        "iyum.auth.password-reset.resend-max-per-hour=5",

        "iyum.auth.password-reset.mail.enabled=false",
        "iyum.auth.email-verification.mail.enabled=false"
})
@AutoConfigureMockMvc(addFilters = true)
@RecordApplicationEvents
@Testcontainers
class PasswordResetApiIntegrationTest {

    private static final String ISSUE_URL =
            "/api/v1/auth/password-reset/request";

    private static final String COMPLETE_URL =
            "/api/v1/auth/password-reset/complete";

    private static final Instant NOW =
            Instant.parse("2026-10-21T05:00:00Z");

    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "NewPassword456!";

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
    PasswordResetTokenHasher tokenHasher;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ApplicationEvents applicationEvents;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void 메일_요청으로_발급한_토큰으로_재설정하고_재사용을_거절한다()
            throws Exception {

        Account account = createMember(true);
        CsrfSession csrf = createCsrfSession();

        String rawToken = issueAndGetToken(account, csrf);

        PasswordResetToken issuedToken = findToken(rawToken);

        assertThat(issuedToken.getMemberId())
                .isEqualTo(account.memberId());
        assertThat(issuedToken.getUsedAt()).isNull();
        assertThat(issuedToken.getRevokedAt()).isNull();
        assertThat(issuedToken.isUsable(NOW)).isTrue();

        // 사전 확인 GET은 쿠키/CSRF 없이 가능하며 토큰을 소비하지 않는다.
        for (int i = 0; i < 2; i++) {
            var checked = mockMvc.perform(get("/api/v1/auth/password-reset/validate").param("token", rawToken))
                    .andExpect(status().isNoContent()).andExpect(content().string(""))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                    .andExpect(unauthenticated()).andReturn();
            assertThat(checked.getRequest().getSession(false)).isNull();
        }
        assertThat(findToken(rawToken).getUsedAt()).isNull();
        assertThat(findToken(rawToken).getRevokedAt()).isNull();
        assertThat(findMember(account.memberId()).getCredentialVersion()).isZero();

        // 실제 완료 API를 호출한다.
        complete(
                csrf,
                rawToken,
                NEW_PASSWORD,
                NEW_PASSWORD
        )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(unauthenticated());

        Member changed = findMember(account.memberId());

        assertThat(passwordEncoder.matches(
                NEW_PASSWORD,
                changed.getPasswordHash()
        )).isTrue();

        assertThat(passwordEncoder.matches(
                OLD_PASSWORD,
                changed.getPasswordHash()
        )).isFalse();

        assertThat(changed.getCredentialVersion()).isEqualTo(1L);

        PasswordResetToken usedToken = findToken(rawToken);

        assertThat(usedToken.getUsedAt()).isEqualTo(NOW);
        assertThat(usedToken.getRevokedAt()).isNull();
        assertThat(usedToken.isUsable(NOW)).isFalse();

        mockMvc.perform(get("/api/v1/auth/password-reset/validate").param("token", rawToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        String changedHash = changed.getPasswordHash();

        // 같은 토큰으로 다른 비밀번호를 설정하려고 시도한다.
        complete(
                csrf,
                rawToken,
                "AnotherPassword789!",
                "AnotherPassword789!"
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PASSWORD_RESET_TOKEN_INVALID"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        Member afterRetry = findMember(account.memberId());

        assertThat(afterRetry.getPasswordHash()).isEqualTo(changedHash);
        assertThat(afterRetry.getCredentialVersion()).isEqualTo(1L);
        assertThat(findToken(rawToken).getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void 확인값이_틀리면_토큰을_유지하고_수정한_요청으로_재시도할_수_있다()
            throws Exception {

        Account account = createMember(true);
        CsrfSession csrf = createCsrfSession();

        String rawToken = issueAndGetToken(account, csrf);

        String oldHash = findMember(account.memberId()).getPasswordHash();

        complete(
                csrf,
                rawToken,
                NEW_PASSWORD,
                "DifferentPassword789!"
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field")
                        .value(hasItem("newPasswordConfirm")));

        Member unchanged = findMember(account.memberId());

        assertThat(unchanged.getPasswordHash()).isEqualTo(oldHash);
        assertThat(unchanged.getCredentialVersion()).isZero();

        PasswordResetToken token = findToken(rawToken);

        assertThat(token.getUsedAt()).isNull();
        assertThat(token.getRevokedAt()).isNull();
        assertThat(token.isUsable(NOW)).isTrue();

        // 토큰 재발급 없이 확인값만 수정해서 재시도한다.
        complete(
                csrf,
                rawToken,
                NEW_PASSWORD,
                NEW_PASSWORD
        )
                .andExpect(status().isNoContent())
                .andExpect(unauthenticated());

        Member changed = findMember(account.memberId());

        assertThat(passwordEncoder.matches(
                NEW_PASSWORD,
                changed.getPasswordHash()
        )).isTrue();

        assertThat(changed.getCredentialVersion()).isEqualTo(1L);
        assertThat(findToken(rawToken).getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void 가입_여부와_회원_상태와_재요청_제한에_관계없이_같은_안내를_반환한다()
            throws Exception {

        Account active = createMember(true);
        Account pending = createMember(false);

        String unknownEmail =
                "unknown-" + UUID.randomUUID() + "@example.com";

        CsrfSession csrf = createCsrfSession();

        String activeResponse = issue(csrf, active.email())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String unknownResponse = issue(csrf, unknownEmail)
                .andReturn()
                .getResponse()
                .getContentAsString();

        String pendingResponse = issue(csrf, pending.email())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Clock이 동일하므로 재발급 최소 간격에 걸린다.
        String limitedResponse = issue(csrf, active.email())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(unknownResponse).isEqualTo(activeResponse);
        assertThat(pendingResponse).isEqualTo(activeResponse);
        assertThat(limitedResponse).isEqualTo(activeResponse);

        var mailEvents = applicationEvents
                .stream(PasswordResetMailRequested.class)
                .toList();

        // 처음 요청한 ACTIVE 회원에 대해서만 발급한다.
        assertThat(mailEvents).hasSize(1);

        PasswordResetMailRequested event = mailEvents.getFirst();

        assertThat(event.memberId()).isEqualTo(active.memberId());

        assertThat(tokenRepository.countIssuedInWindow(
                active.memberId(),
                NOW.minusSeconds(3600),
                NOW
        )).isEqualTo(1L);

        assertThat(tokenRepository.findLatestIssuedAt(
                pending.memberId()
        )).isEmpty();

        // 제한된 재요청이 기존 링크를 폐기하지 않아야 한다.
        PasswordResetToken token = findToken(event.rawToken());

        assertThat(token.getRevokedAt()).isNull();
        assertThat(token.isUsable(NOW)).isTrue();

        assertThat(activeResponse).doesNotContain(
                active.email(),
                pending.email(),
                unknownEmail,
                event.rawToken()
        );
    }

    private Account createMember(boolean active) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "");

        String email = "reset-api-" + suffix + "@example.com";

        Member member = Member.createPending(
                email,
                passwordEncoder.encode(OLD_PASSWORD),
                "회원" + suffix.substring(0, 16)
        );

        if (active) {
            member.activeAfterEmailVerification();
        }

        Member saved = memberRepository.saveAndFlush(member);

        return new Account(saved.getId(), email);
    }

    private String issueAndGetToken(
            Account account,
            CsrfSession csrf
    ) throws Exception {
        issue(csrf, account.email());

        var events = applicationEvents
                .stream(PasswordResetMailRequested.class)
                .toList();

        assertThat(events).hasSize(1);

        PasswordResetMailRequested event = events.getFirst();

        assertThat(event.memberId()).isEqualTo(account.memberId());
        assertThat(event.email()).isEqualTo(account.email());
        assertThat(event.rawToken())
                .matches("[A-Za-z0-9_-]{43}");
        assertThat(event.expiresAt())
                .isEqualTo(NOW.plusSeconds(1800));

        return event.rawToken();
    }

    private ResultActions issue(
            CsrfSession csrf,
            String email
    ) throws Exception {
        return mockMvc.perform(
                        jsonPost(
                                ISSUE_URL,
                                csrf,
                                Map.of("email", email)
                        )
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "입력하신 이메일이 재설정 가능한 계정이면 "
                                + "비밀번호 재설정 안내 메일을 보내드립니다."
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(unauthenticated());
    }

    private ResultActions complete(
            CsrfSession csrf,
            String token,
            String password,
            String confirmation
    ) throws Exception {
        return mockMvc.perform(
                jsonPost(
                        COMPLETE_URL,
                        csrf,
                        Map.of(
                                "token", token,
                                "newPassword", password,
                                "newPasswordConfirm", confirmation
                        )
                )
        );
    }

    private MockHttpServletRequestBuilder jsonPost(
            String url,
            CsrfSession csrf,
            Map<String, String> body
    ) {
        return post(url)
                .session(csrf.session())
                .header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
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

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow();
    }

    private PasswordResetToken findToken(String rawToken) {
        return tokenRepository.findByTokenHash(
                tokenHasher.hash(rawToken)
        ).orElseThrow();
    }

    private record Account(Long memberId, String email) {
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