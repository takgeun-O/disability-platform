package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.member.application.MemberWithdrawalException;
import io.github.takgeun.iyum.member.application.MemberWithdrawalService;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@AutoConfigureMockMvc(addFilters = true)
@Testcontainers
class MemberWithdrawalSessionIntegrationTest {

    private static final String EMAIL =
            "withdrawal-session@example.com";

    private static final String OTHER_EMAIL =
            "other-session@example.com";

    private static final String PASSWORD =
            "Example123!";

    private static final String PROFILE_URL =
            "/api/v1/members/me";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );
        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    MemberWithdrawalService withdrawalService;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Long memberId;
    private Long otherMemberId;

    @BeforeEach
    void setUp() {
        // 이 클래스의 전용 Testcontainers DB만 초기화한다.
        // 이 테스트에서는 회원 외의 연관 데이터를 생성하지 않는다.
        memberRepository.deleteAllInBatch();

        memberId = createActiveMember(
                EMAIL,
                "탈퇴검증회원"
        );

        otherMemberId = createActiveMember(
                OTHER_EMAIL,
                "다른활성회원"
        );
    }

    @Test
    void 탈퇴하면_기존_두_세션은_차단되고_다른_회원의_세션은_유지된다()
            throws Exception {

        // 같은 회원이 서로 다른 브라우저에서 로그인한 상황.
        CsrfSession firstLogin = login(EMAIL);
        CsrfSession secondLogin = login(EMAIL);

        // 영향을 받지 않아야 하는 다른 회원의 세션.
        CsrfSession otherLogin = login(OTHER_EMAIL);

        assertThat(firstLogin.session())
                .isNotSameAs(secondLogin.session());

        assertThat(firstLogin.session().getId())
                .isNotEqualTo(secondLogin.session().getId());

        // 탈퇴 전에는 모든 세션이 정상 동작해야 한다.
        assertProfileAccessible(
                firstLogin.session(),
                memberId
        );
        assertProfileAccessible(
                secondLogin.session(),
                memberId
        );
        assertProfileAccessible(
                otherLogin.session(),
                otherMemberId
        );

        // 상태를 SQL로 바꾸지 않고 실제 탈퇴 서비스를 호출한다.
        // 반환 시 서비스 트랜잭션의 커밋까지 완료된다.
        withdrawalService.withdraw(memberId, PASSWORD);

        Member withdrawn = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(withdrawn.getStatus())
                .isEqualTo(MemberStatus.DELETED);
        assertThat(withdrawn.getWithdrawnAt()).isNotNull();
        assertThat(withdrawn.getEmail()).isNull();
        assertThat(withdrawn.getNickname()).isNull();
        assertThat(withdrawn.getPasswordHash()).isNull();

        // 현재 구조에서는 다음 요청 때 각각의 세션을 정리한다.
        assertSessionRejected(firstLogin.session());
        assertSessionRejected(secondLogin.session());

        // 다른 회원의 로그인 상태는 유지되어야 한다.
        assertProfileAccessible(
                otherLogin.session(),
                otherMemberId
        );

        assertThat(otherLogin.session().isInvalid()).isFalse();

        Member otherMember =
                memberRepository.findById(otherMemberId)
                        .orElseThrow();

        assertThat(otherMember.getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void 탈퇴한_세션으로_공개_게시글을_조회하면_인증만_해제하고_조회는_허용한다()
            throws Exception {

        CsrfSession loggedIn = login(EMAIL);

        assertProfileAccessible(
                loggedIn.session(),
                memberId
        );

        withdrawalService.withdraw(memberId, PASSWORD);

        // 보호된 API를 먼저 호출하지 않고,
        // 탈퇴 후 첫 요청을 공개 API로 보낸다.
        mockMvc.perform(
                        get("/api/v1/community/posts")
                                .session(loggedIn.session())
                )
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(unauthenticated());

        assertThat(loggedIn.session().isInvalid()).isTrue();
    }

    @Test
    void 비밀번호가_틀려_탈퇴에_실패하면_기존_세션들은_유지된다()
            throws Exception {

        CsrfSession firstLogin = login(EMAIL);
        CsrfSession secondLogin = login(EMAIL);

        assertThatThrownBy(() ->
                withdrawalService.withdraw(
                        memberId,
                        "WrongPassword123!"
                )
        ).isInstanceOfSatisfying(
                MemberWithdrawalException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                MemberWithdrawalException.Code.PASSWORD_MISMATCH
                        )
        );

        Member member = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(member.getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getWithdrawnAt()).isNull();
        assertThat(member.getEmail()).isEqualTo(EMAIL);

        assertProfileAccessible(
                firstLogin.session(),
                memberId
        );
        assertProfileAccessible(
                secondLogin.session(),
                memberId
        );

        assertThat(firstLogin.session().isInvalid()).isFalse();
        assertThat(secondLogin.session().isInvalid()).isFalse();
    }

    @Test
    void HTTP_탈퇴에_성공하면_DB와_현재_세션을_정리하고_다른_세션도_차단한다()
            throws Exception {

        CsrfSession currentLogin = login(EMAIL);
        CsrfSession otherDeviceLogin = login(EMAIL);
        CsrfSession otherMemberLogin = login(OTHER_EMAIL);

        assertThat(currentLogin.session().getId())
                .isNotEqualTo(otherDeviceLogin.session().getId());

        // 탈퇴 전에는 각 세션으로 정상 조회할 수 있어야 한다.
        assertProfileAccessible(
                currentLogin.session(),
                memberId
        );
        assertProfileAccessible(
                otherDeviceLogin.session(),
                memberId
        );
        assertProfileAccessible(
                otherMemberLogin.session(),
                otherMemberId
        );

        // 실제 로그인 과정에서 발급받은 세션과 CSRF 토큰으로 요청한다.
        mockMvc.perform(
                        post("/api/v1/members/me/withdrawal")
                                .session(currentLogin.session())
                                .header(
                                        currentLogin.headerName(),
                                        currentLogin.token()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "currentPassword", PASSWORD,
                                                "confirmed", true
                                        )
                                ))
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(unauthenticated());

        // 응답 시점에는 현재 세션이 이미 무효화되어야 한다.
        assertThat(currentLogin.session().isInvalid()).isTrue();

        // 실제 서비스의 트랜잭션이 완료된 DB 상태를 확인한다.
        Member withdrawn = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(withdrawn.getStatus())
                .isEqualTo(MemberStatus.DELETED);
        assertThat(withdrawn.getWithdrawnAt()).isNotNull();
        assertThat(withdrawn.getEmail()).isNull();
        assertThat(withdrawn.getNickname()).isNull();
        assertThat(withdrawn.getPasswordHash()).isNull();

        // 다른 기기의 기존 세션은 다음 요청에서 필터가 정리한다.
        assertSessionRejected(otherDeviceLogin.session());

        // 다른 회원은 영향을 받지 않아야 한다.
        assertProfileAccessible(
                otherMemberLogin.session(),
                otherMemberId
        );

        Member otherMember = memberRepository.findById(otherMemberId)
                .orElseThrow();

        assertThat(otherMember.getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
        assertThat(otherMember.getEmail()).isEqualTo(OTHER_EMAIL);
        assertThat(otherMember.getWithdrawnAt()).isNull();
    }

    @Test
    void HTTP_탈퇴의_비밀번호가_틀리면_상태를_유지하고_같은_세션으로_재시도할_수_있다()
            throws Exception {

        CsrfSession currentLogin = login(EMAIL);
        CsrfSession otherDeviceLogin = login(EMAIL);

        String sessionIdBefore = currentLogin.session().getId();

        Member before = memberRepository.findById(memberId)
                .orElseThrow();

        String emailBefore = before.getEmail();
        String nicknameBefore = before.getNickname();
        String passwordHashBefore = before.getPasswordHash();
        var updatedAtBefore = before.getUpdatedAt();

        // 잘못된 비밀번호로 실제 탈퇴 API를 호출한다.
        var failedResult = mockMvc.perform(
                        post("/api/v1/members/me/withdrawal")
                                .session(currentLogin.session())
                                .header(
                                        currentLogin.headerName(),
                                        currentLogin.token()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "currentPassword",
                                                "WrongPassword123!",
                                                "confirmed",
                                                true
                                        )
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PASSWORD_MISMATCH"))
                .andExpect(jsonPath("$.fieldErrors.length()")
                        .value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("currentPassword"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andReturn();

        assertThat(failedResult.getResponse().getContentAsString())
                .doesNotContain("WrongPassword123!");

        // 탈퇴 실패로 회원 정보가 변경되지 않아야 한다.
        Member afterFailure = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(afterFailure.getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
        assertThat(afterFailure.getWithdrawnAt()).isNull();
        assertThat(afterFailure.getEmail()).isEqualTo(emailBefore);
        assertThat(afterFailure.getNickname()).isEqualTo(nicknameBefore);
        assertThat(afterFailure.getPasswordHash())
                .isEqualTo(passwordHashBefore);
        assertThat(afterFailure.getUpdatedAt())
                .isEqualTo(updatedAtBefore);

        assertThat(currentLogin.session().isInvalid()).isFalse();
        assertThat(currentLogin.session().getId())
                .isEqualTo(sessionIdBefore);

        // 현재 기기와 다른 기기 모두 로그인을 유지한다.
        assertProfileAccessible(
                currentLogin.session(),
                memberId
        );
        assertProfileAccessible(
                otherDeviceLogin.session(),
                memberId
        );

        // 다시 로그인하거나 CSRF를 재발급받지 않고 재시도한다.
        mockMvc.perform(
                        post("/api/v1/members/me/withdrawal")
                                .session(currentLogin.session())
                                .header(
                                        currentLogin.headerName(),
                                        currentLogin.token()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "currentPassword", PASSWORD,
                                                "confirmed", true
                                        )
                                ))
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(unauthenticated());

        assertThat(currentLogin.session().isInvalid()).isTrue();

        Member withdrawn = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(withdrawn.getStatus())
                .isEqualTo(MemberStatus.DELETED);

        assertSessionRejected(otherDeviceLogin.session());
    }

    private Long createActiveMember(
            String email,
            String nickname
    ) {
        Member member = Member.createPending(
                email,
                passwordEncoder.encode(PASSWORD),
                nickname
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member).getId();
    }

    private void assertProfileAccessible(
            MockHttpSession session,
            Long expectedMemberId
    ) throws Exception {

        mockMvc.perform(
                        get(PROFILE_URL)
                                .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId")
                        .value(is(expectedMemberId), Long.class));

        assertThat(session.isInvalid()).isFalse();
    }

    private void assertSessionRejected(
            MockHttpSession session
    ) throws Exception {

        mockMvc.perform(
                        get(PROFILE_URL)
                                .session(session)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(unauthenticated());

        assertThat(session.isInvalid()).isTrue();
    }

    private CsrfSession login(String email)
            throws Exception {

        // 호출할 때마다 별도의 익명 세션으로 시작한다.
        CsrfSession beforeLogin = csrf(null);

        var result = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .session(beforeLogin.session())
                                .with(request -> {
                                    request.setRequestedSessionId(
                                            beforeLogin.session().getId()
                                    );
                                    request.setRequestedSessionIdValid(true);
                                    request.setRequestedSessionIdFromCookie(true);
                                    return request;
                                })
                                .header(
                                        beforeLogin.headerName(),
                                        beforeLogin.token()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "email", email,
                                                "password", PASSWORD
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) result.getRequest()
                        .getSession(false);

        assertThat(session).isNotNull();

        // 로그인 성공 후 새 CSRF 토큰을 발급받는다.
        return csrf(session);
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

        MockHttpSession actualSession =
                (MockHttpSession) result.getRequest()
                        .getSession(false);

        assertThat(actualSession).isNotNull();

        return new CsrfSession(
                actualSession,
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