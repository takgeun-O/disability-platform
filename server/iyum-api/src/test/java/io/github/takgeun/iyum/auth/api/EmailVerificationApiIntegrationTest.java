package io.github.takgeun.iyum.auth.api;

import io.github.takgeun.iyum.auth.application.EmailVerificationTokenIssueService;
import io.github.takgeun.iyum.auth.domain.EmailVerificationToken;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenHasher;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberAgreementRepository;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.docker.compose.enabled=false",
        "iyum.auth.email-verification.mail.enabled=false",  // 테스트 중 SMTP 발송 비활성화
        "iyum.cors.allowed-origin=http://127.0.0.1:8443",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc   // HTTP 요청 처리를 테스트할 MockMvc 구성
@ActiveProfiles("dev")  // 개발 CORS 설정 적용
@Testcontainers         // 별도의 PostgreSQL 테스트 DB 실행
class EmailVerificationApiIntegrationTest {

    private static final String PATH =
            "/api/v1/auth/email/verify";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

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
    EmailVerificationTokenIssueService issueService;

    @Autowired
    EmailVerificationTokenRepository tokenRepository;

    @Autowired
    EmailVerificationTokenHasher tokenHasher;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    MemberAgreementRepository memberAgreementRepository;

    private Long memberId;
    private String rawToken;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAllInBatch();
        memberAgreementRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member member = Member.createPending(
                "verify-api@example.com",
                "test-only-password-hash",
                "VerifyApiUser"
        );

        memberId = memberRepository.saveAndFlush(member).getId();
        rawToken = issueService.issue(memberId).rawToken();
    }

    @Test
    void 비로그인_사용자가_인증하면_ACTIVE만_반환하고_DB를_변경한다()
            throws Exception {

        var result = performVerify(requestBody(rawToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ))
                .andExpect(unauthenticated())
                .andReturn();

        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        assertThat(body.propertyNames()).containsExactly("status");

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(rawToken, tokenHasher.hash(rawToken));

        Member member = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(storedToken().getUsedAt()).isNotNull();

        MockHttpSession session =
                (MockHttpSession) result.getRequest().getSession(false);

        assertThat(session).isNotNull();
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT"))
                .isNull();

        // 인증을 마쳤어도 로그인 상태가 된 것은 아닙니다.
        mockMvc.perform(
                        get("/api/v1/members/me")
                                .session(session)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"token\":null}",
            "{\"token\":\"\"}",
            "{\"token\":\"short\"}",
            "{\"token\":\" token \"}"
    })
    void 토큰_누락과_형식_오류는_필드_오류로_반환한다(
            String body
    ) throws Exception {

        performVerify(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("token"))
                .andExpect(jsonPath(
                        "$.fieldErrors[0].rejectedValue"
                ).doesNotExist());

        assertPendingAndUnused();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "null",
            "{\"token\":",
            "{\"token\":{}}"
    })
    void 잘못된_JSON_본문은_400으로_반환한다(
            String body
    ) throws Exception {

        performVerify(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertPendingAndUnused();
    }

    @Test
    void DB에_없는_토큰은_공통_인증_실패_응답을_반환한다()
            throws Exception {

        String unknownToken = "Z".repeat(43);

        performVerify(requestBody(unknownToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("EMAIL_VERIFICATION_INVALID"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsString())
                                .doesNotContain(unknownToken)
                );

        assertPendingAndUnused();
    }

    @Test
    void 인증한_토큰을_다시_제출하면_400을_반환한다()
            throws Exception {

        performVerify(requestBody(rawToken))
                .andExpect(status().isOk());

        Instant firstUsedAt = storedToken().getUsedAt();

        performVerify(requestBody(rawToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("EMAIL_VERIFICATION_INVALID"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(storedToken().getUsedAt()).isEqualTo(firstUsedAt);

        assertThat(
                memberRepository.findById(memberId)
                        .orElseThrow()
                        .getStatus()
        ).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void CSRF_토큰이_없으면_403이고_DB를_변경하지_않는다()
            throws Exception {

        mockMvc.perform(
                        post(PATH)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(rawToken))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        assertPendingAndUnused();
    }

    @Test
    void 다른_세션의_CSRF_토큰은_사용할_수_없다()
            throws Exception {

        CsrfSession csrf = getCsrf();   // 실제 CSRF 조회 API를 호출하여 토큰과 세션 확보

        mockMvc.perform(
                        post(PATH)
                                .session(new MockHttpSession())
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(csrf.headerName(), csrf.csrfToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(rawToken))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        assertPendingAndUnused();
    }

    @Test
    void 허용된_개발_Origin의_사전_요청을_허용한다()
            throws Exception {

        mockMvc.perform(
                        options(PATH)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "content-type,x-csrf-token"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("x-csrf-token")
                ));

        assertPendingAndUnused();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8443",
            "http://127.0.0.1:3000",
            "https://evil.example"
    })
    void 허용하지_않은_Origin의_사전_요청은_거절한다(
            String origin
    ) throws Exception {

        mockMvc.perform(
                        options(PATH)
                                .header(HttpHeaders.ORIGIN, origin)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        assertPendingAndUnused();
    }

    @Test
    void 비로그인_GET_요청으로는_인증되지_않는다()
            throws Exception {

        mockMvc.perform(
                        get(PATH).accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnauthorized());

        assertPendingAndUnused();
    }

    @Test
    void Swagger에_인증_API의_요청과_응답이_등록된다()
            throws Exception {

        var result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode document = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        JsonNode operation = document.at(
                "/paths/~1api~1v1~1auth~1email~1verify/post"
        );

        assertThat(operation.isMissingNode()).isFalse();

        assertThat(
                operation.at(
                        "/requestBody/content/application~1json/schema/$ref"
                ).asString()
        ).isEqualTo("#/components/schemas/EmailVerificationRequest");

        assertThat(
                operation.at(
                        "/responses/200/content/application~1json/schema/$ref"
                ).asString()
        ).isEqualTo("#/components/schemas/EmailVerificationResponse");

        for (String code : new String[]{"400", "403", "500"}) {
            assertThat(
                    operation.at(
                            "/responses/" + code
                                    + "/content/application~1json/schema/$ref"
                    ).asString()
            ).isEqualTo("#/components/schemas/ApiErrorResponse");
        }

        assertThat(
                operation.path("parameters").valueStream()
                        .anyMatch(parameter ->
                                "X-CSRF-TOKEN".equals(
                                        parameter.path("name").asString()
                                )
                                        && "header".equals(
                                        parameter.path("in").asString()
                                )
                                        && parameter.path("required").asBoolean()
                        )
        ).isTrue();

        JsonNode schemas = document.at("/components/schemas");

        assertThat(
                schemas.at(
                        "/EmailVerificationRequest/properties/token/writeOnly"
                ).asBoolean()
        ).isTrue();

        assertThat(
                schemas.at(
                        "/EmailVerificationResponse/properties"
                ).propertyNames()
        ).containsExactly("status");

        assertThat(
                schemas.at(
                        "/EmailVerificationResponse/properties/status/enum"
                ).valueStream().map(JsonNode::asString).toList()
        ).containsExactly("ACTIVE");
    }

    private ResultActions performVerify(String body) throws Exception {
        CsrfSession csrf = getCsrf();

        return mockMvc.perform(
                post(PATH)
                        .session(csrf.session())
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(csrf.headerName(), csrf.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body)
        );
    }

    // 실제 CSRF 조회 API를 호출하여 토큰과 세션 확보
    private CsrfSession getCsrf() throws Exception {
        var result = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andReturn();

        JsonNode body = objectMapper.readTree(
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

    private String requestBody(String token) {
        return objectMapper.writeValueAsString(
                objectMapper.createObjectNode().put("token", token)
        );
    }

    private EmailVerificationToken storedToken() {
        return tokenRepository
                .findByTokenHash(tokenHasher.hash(rawToken))
                .orElseThrow();
    }

    private void assertPendingAndUnused() {
        Member member = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.PENDING);
        assertThat(storedToken().getUsedAt()).isNull();
    }

    private record CsrfSession(
            MockHttpSession session,
            String headerName,
            String csrfToken
    ) {
    }
}