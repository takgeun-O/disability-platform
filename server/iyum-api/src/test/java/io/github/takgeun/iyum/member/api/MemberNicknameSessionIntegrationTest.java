package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@AutoConfigureMockMvc(addFilters = true)
@Testcontainers
class MemberNicknameSessionIntegrationTest {

    private static final String EMAIL =
            "nickname-session@example.com";

    private static final String PASSWORD = "Example123!";

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
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long memberId;

    @BeforeEach
    void setUp() {
        // 이 클래스의 전용 컨테이너 DB만 초기화한다.
        // 회원 외의 연관 데이터는 만들지 않는다.
        memberRepository.deleteAllInBatch();

        Member member = Member.createPending(
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                "수정전닉네임"
        );
        member.activeAfterEmailVerification();

        memberId = memberRepository.saveAndFlush(member).getId();
    }

    @Test
    void 닉네임을_수정하면_같은_세션의_회원조회에도_반영된다()
            throws Exception {
        CsrfSession loggedIn = login();

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(loggedIn.session())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname")
                        .value("수정전닉네임"));

        mockMvc.perform(
                        put("/api/v1/members/me/nickname")
                                .session(loggedIn.session())
                                .header(
                                        loggedIn.headerName(),
                                        loggedIn.token()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "nickname",
                                                "  수정후닉네임  "
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId")
                        .value(is(memberId), Long.class))
                .andExpect(jsonPath("$.nickname")
                        .value("수정후닉네임"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        // 서비스 트랜잭션이 끝난 뒤 실제 DB 값을 확인한다.
        Member saved = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(saved.getNickname())
                .isEqualTo("수정후닉네임");

        // 다시 로그인하지 않고 기존 세션을 그대로 사용한다.
        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(loggedIn.session())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId")
                        .value(is(memberId), Long.class))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.nickname")
                        .value("수정후닉네임"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        // 마이페이지용 조회에서도 같은 값이 나와야 한다.
        mockMvc.perform(
                        get("/api/v1/members/me")
                                .session(loggedIn.session())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId")
                        .value(is(memberId), Long.class))
                .andExpect(jsonPath("$.nickname")
                        .value("수정후닉네임"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertThat(loggedIn.session().isInvalid()).isFalse();
    }

    @Test
    void 로그인_후_DB에서_회원이_없어지면_현재회원_조회는_401이다()
            throws Exception {
        CsrfSession loggedIn = login();

        // 테스트 상황 재현이며 회원 탈퇴 기능을 구현하는 것은 아니다.
        memberRepository.deleteById(memberId);

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(loggedIn.session())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));
    }

    @Test
    void 로그인_후_DB에서_비활성화되면_현재회원_조회는_403이다()
            throws Exception {
        CsrfSession loggedIn = login();

        // 현재 상태를 다시 검사하는지 확인하기 위한 테스트 데이터 변경.
        int updated = jdbcTemplate.update(
                """
                UPDATE members
                SET status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                "PENDING",
                memberId
        );

        assertThat(updated).isEqualTo(1);

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(loggedIn.session())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_NOT_ACTIVE"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));
    }

    private CsrfSession login() throws Exception {
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
                                                "email", EMAIL,
                                                "password", PASSWORD
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) result.getRequest().getSession(false);

        assertThat(session).isNotNull();

        // 로그인 성공 시 이전 CSRF 토큰이 폐기되므로 새로 발급받는다.
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