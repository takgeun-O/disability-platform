package io.github.takgeun.iyum.auth.passwordreset.api;

import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetMailRequested;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenHasher;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
        "server.forward-headers-strategy=none",

        "iyum.auth.password-reset.token-ttl=30m",
        "iyum.auth.password-reset.resend-min-interval=60s",
        "iyum.auth.password-reset.resend-max-per-hour=5",

        // 이번 테스트에서만 IP별 한도를 낮춘다.
        "iyum.auth.password-reset.request-max-per-ip-per-hour=3",

        "iyum.auth.password-reset.mail.enabled=false",
        "iyum.auth.email-verification.mail.enabled=false"
})
@AutoConfigureMockMvc(addFilters = true)
@RecordApplicationEvents
@Testcontainers
class PasswordResetIpRateLimitIntegrationTest {

    private static final String ISSUE_URL =
            "/api/v1/auth/password-reset/request";

    private static final String COMPLETE_URL =
            "/api/v1/auth/password-reset/complete";

    private static final String FIRST_IP = "192.0.2.10";
    private static final String OTHER_IP = "192.0.2.20";

    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "NewPassword456!";

    private static final Instant NOW =
            Instant.parse("2026-10-21T05:00:00Z");

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
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApplicationEvents applicationEvents;

    // 제한기·서비스·저장소는 실제 빈을 사용한다.
    // 시간을 기다리지 않고 경계를 검증하기 위해 Clock만 교체한다.
    @MockitoBean
    Clock clock;

    @Test
    void 실제_API에서_IP_제한과_토큰_보존_및_시간_경과를_검증한다()
            throws Exception {

        when(clock.instant()).thenReturn(NOW);

        Account active = createMember(true);
        Account pending = createMember(false);
        Account anotherActive = createMember(true);

        String unknownEmail =
                "unknown-" + UUID.randomUUID() + "@example.com";

        CsrfSession firstSession = createCsrfSession(FIRST_IP);

        // 1. 같은 IP에서 세 가지 이메일로 요청한다.
        assertAccepted(issue(FIRST_IP, firstSession, active.email()));
        assertAccepted(issue(FIRST_IP, firstSession, unknownEmail));
        assertAccepted(issue(FIRST_IP, firstSession, pending.email()));

        // 실제 발급은 ACTIVE 회원에게만 발생한다.
        var initialEvents = mailEvents();

        assertThat(initialEvents).hasSize(1);

        PasswordResetMailRequested firstEvent = initialEvents.getFirst();

        assertThat(firstEvent.memberId()).isEqualTo(active.memberId());

        String rawToken = firstEvent.rawToken();

        assertThat(tokenSnapshot(active.memberId())).hasSize(1);
        assertThat(tokenSnapshot(pending.memberId())).isEmpty();
        assertThat(tokenSnapshot(anotherActive.memberId())).isEmpty();

        var tokenBefore = tokenSnapshot(active.memberId());

        // 2. 회원별 재발급 간격 60초는 지난 상태로 만든다.
        // IP 제한이 없었다면 기존 토큰 폐기와 재발급이 가능해야 한다.
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));

        // 새 세션을 만들어도 같은 IP의 제한 기록은 유지되어야 한다.
        CsrfSession newSession = createCsrfSession(FIRST_IP);

        assertThat(newSession.session().getId())
                .isNotEqualTo(firstSession.session().getId());

        // 3. 기존 회원의 재발급 요청을 차단한다.
        assertRateLimited(
                issue(FIRST_IP, newSession, active.email())
        );

        // 새 토큰 생성뿐 아니라 기존 토큰의 폐기도 없어야 한다.
        assertThat(tokenSnapshot(active.memberId()))
                .isEqualTo(tokenBefore);

        // 4. 이메일과 전달 헤더를 바꿔도 같은 실제 IP이면 차단한다.
        assertRateLimited(
                mockMvc.perform(
                        issueRequest(
                                FIRST_IP,
                                newSession,
                                anotherActive.email()
                        )
                                .header("X-Forwarded-For", OTHER_IP)
                                .header(
                                        "Forwarded",
                                        "for=" + OTHER_IP + ";proto=https"
                                )
                )
        );

        assertThat(tokenSnapshot(anotherActive.memberId())).isEmpty();
        assertThat(mailEvents()).hasSize(1);

        // 5. 실제 remoteAddr가 다른 IP이면 정상 발급한다.
        CsrfSession otherSession = createCsrfSession(OTHER_IP);

        assertAccepted(
                issue(OTHER_IP, otherSession, anotherActive.email())
        );

        assertThat(tokenSnapshot(anotherActive.memberId())).hasSize(1);
        assertThat(mailEvents()).hasSize(2);

        // 6. 메일 요청 제한에 걸려도 기존 링크로 완료할 수 있다.
        // 토큰은 발급 후 61초만 지났으므로 아직 유효하다.
        mockMvc.perform(
                        post(COMPLETE_URL)
                                .with(request -> {
                                    request.setRemoteAddr(FIRST_IP);
                                    return request;
                                })
                                .session(newSession.session())
                                .header(
                                        newSession.headerName(),
                                        newSession.token()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "token", rawToken,
                                                "newPassword", NEW_PASSWORD,
                                                "newPasswordConfirm", NEW_PASSWORD
                                        )
                                ))
                )
                .andExpect(status().isNoContent())
                .andExpect(unauthenticated());

        Member changed = memberRepository.findById(active.memberId())
                .orElseThrow();

        assertThat(passwordEncoder.matches(
                NEW_PASSWORD,
                changed.getPasswordHash()
        )).isTrue();

        assertThat(changed.getCredentialVersion()).isEqualTo(1L);

        var usedToken = tokenRepository.findByTokenHash(
                tokenHasher.hash(rawToken)
        ).orElseThrow();

        assertThat(usedToken.getUsedAt())
                .isEqualTo(NOW.plusSeconds(61));
        assertThat(usedToken.getRevokedAt()).isNull();

        // 완료 API 실행은 재설정 메일 요청 이벤트를 추가하지 않는다.
        assertThat(mailEvents()).hasSize(2);

        // 7. 최초 요청들로부터 정확히 1시간이 지나면 다시 허용한다.
        when(clock.instant()).thenReturn(NOW.plusSeconds(3600));

        assertAccepted(
                issue(FIRST_IP, newSession, active.email())
        );

        assertThat(mailEvents()).hasSize(3);

        // 처음 사용한 토큰과 새로 발급한 토큰이 존재한다.
        assertThat(tokenSnapshot(active.memberId())).hasSize(2);
    }

    private Account createMember(boolean active) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "");

        String email = "ip-limit-" + suffix + "@example.com";

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

    private CsrfSession createCsrfSession(String ip) throws Exception {
        var result = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                                .with(request -> {
                                    request.setRemoteAddr(ip);
                                    return request;
                                })
                )
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

    private ResultActions issue(
            String ip,
            CsrfSession csrf,
            String email
    ) throws Exception {
        return mockMvc.perform(issueRequest(ip, csrf, email));
    }

    private MockHttpServletRequestBuilder issueRequest(
            String ip,
            CsrfSession csrf,
            String email
    ) {
        return post(ISSUE_URL)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .session(csrf.session())
                .header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("email", email)
                ));
    }

    private void assertAccepted(ResultActions actions) throws Exception {
        actions
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

    private void assertRateLimited(ResultActions actions)
            throws Exception {
        actions
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code")
                        .value("PASSWORD_RESET_RATE_LIMITED"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(unauthenticated());
    }

    private List<PasswordResetMailRequested> mailEvents() {
        return applicationEvents
                .stream(PasswordResetMailRequested.class)
                .toList();
    }

    private List<Map<String, Object>> tokenSnapshot(Long memberId) {
        return jdbcTemplate.queryForList(
                """
                SELECT id, member_id, token_hash,
                       created_at, expires_at,
                       used_at, revoked_at, version
                FROM password_reset_tokens
                WHERE member_id = ?
                ORDER BY id
                """,
                memberId
        );
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