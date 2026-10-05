package io.github.takgeun.iyum.auth.api;

import io.github.takgeun.iyum.auth.application.SignupService;
import io.github.takgeun.iyum.auth.application.SignupTermsPolicy;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberAgreementRepository;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 실제 JSON 변환·DTO 검증·서비스 연결을 확인한다. DB 저장·롤백은 기존 통합 테스트가 담당한다.
@WebMvcTest(controllers = {SignupController.class, CsrfController.class}, properties = "spring.config.import=")
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SignupService.class, SignupTermsPolicy.class})
class SignupControllerTest {

    private static final String PASSWORD = "Example123!";
    private static final String HASH = "{bcrypt}$2a$10$testHashMustNeverBeReturned";
    private static final String REQUEST = """
            {
              "email":"member@example.com",
              "password":"Example123!",
              "passwordConfirm":"Example123!",
              "nickname":"FirstUser",
              "agreements":[
                {"termsCode":"SERVICE_TERMS","version":"dev-v1","agreed":true},
                {"termsCode":"PRIVACY_COLLECTION_USE","version":"dev-v1","agreed":true}
              ]
            }
            """;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean MemberAgreementRepository memberAgreementRepository;
    @MockitoBean PasswordEncoder passwordEncoder;

    @BeforeEach
    void prepareRepositoryBoundary() {
        Member saved = mock(Member.class);
        when(saved.getId()).thenReturn(42L);
        when(saved.getStatus()).thenReturn(MemberStatus.PENDING);
        when(memberRepository.saveAndFlush(any(Member.class))).thenReturn(saved);
        when(passwordEncoder.encode(anyString())).thenReturn(HASH);
    }

    @Test
    void anonymousSignupReturnsOnlyPendingAndPassesNormalizedInputToService() throws Exception {
        ObjectNode request = request().put("email", " Member@Example.COM ")
                .put("nickname", " FirstUser ")
                .put("password", " " + PASSWORD + " ")
                .put("passwordConfirm", " " + PASSWORD + " ");

        perform(request).andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"status\":\"PENDING\"}"))
                .andExpect(result -> {
                    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
                    assertThat(body.propertyNames()).containsExactly("status");
                    assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD, HASH);
                    var session = result.getRequest().getSession(false);
                    if (session != null) {
                        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
                    }
                });

        verify(memberRepository).existsByEmail("member@example.com");
        verify(memberRepository).existsByNicknameIgnoreCase("FirstUser");
        verify(passwordEncoder).encode(" " + PASSWORD + " ");
        ArgumentCaptor<Member> member = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(member.capture());
        assertThat(member.getValue().getEmail()).isEqualTo("member@example.com");
        assertThat(member.getValue().getNickname()).isEqualTo("FirstUser");
    }

    @ParameterizedTest
    @CsvSource({
            "email, not-an-email",
            "password, abc1234",
            "password, abcdefgh",
            "password, 12345678",
            "nickname, ADMIN",
            "nickname, 닉!네임",
            "passwordConfirm, Different456!"
    })
    void invalidInputReturnsSafeFieldErrors(String field, String value) throws Exception {
        perform(request().put(field, value)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem(field)))
                .andExpect(result -> assertSafeError(result.getResponse().getContentAsString(), value));
        verifyNoInteractions(memberRepository, memberAgreementRepository, passwordEncoder);
    }

    @Test
    void utf8PasswordByteLimitIsEnforcedOverHttp() throws Exception {
        String password = "Ab1" + "가".repeat(24); // 27자이지만 UTF-8로 75바이트
        perform(request().put("password", password).put("passwordConfirm", password))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."))
                .andExpect(result -> assertSafeError(result.getResponse().getContentAsString(), password));
        verifyNoInteractions(memberRepository, memberAgreementRepository, passwordEncoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "[null]", "[{\"termsCode\":null,\"version\":\"\",\"agreed\":null}]"})
    void nestedAgreementsAreValidated(String agreements) throws Exception {
        ObjectNode request = request();
        request.set("agreements", objectMapper.readTree(agreements));
        perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty())
                .andExpect(result -> assertSafeError(result.getResponse().getContentAsString()));
        verifyNoInteractions(memberRepository, memberAgreementRepository, passwordEncoder);
    }

    @ParameterizedTest
    @CsvSource({
            "missing, REQUIRED_AGREEMENT_MISSING",
            "notAccepted, REQUIRED_AGREEMENT_NOT_ACCEPTED",
            "version, TERMS_VERSION_MISMATCH",
            "duplicate, DUPLICATE_AGREEMENT"
    })
    void realTermsPolicyErrorsBecome400(String scenario, String code) throws Exception {
        ObjectNode request = request();
        ArrayNode agreements = (ArrayNode) request.get("agreements");
        ObjectNode first = (ObjectNode) agreements.get(0);
        switch (scenario) {
            case "missing" -> agreements.remove(1);
            case "notAccepted" -> first.put("agreed", false);
            case "version" -> first.put("version", "dev-v0");
            case "duplicate" -> agreements.add(first.deepCopy());
            default -> throw new IllegalArgumentException(scenario);
        }
        perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(result -> assertSafeError(result.getResponse().getContentAsString()));
        verifyNoInteractions(memberRepository, memberAgreementRepository, passwordEncoder);
    }

    @Test
    void normalizedEmailConflictReturns409() throws Exception {
        when(memberRepository.existsByEmail("member@example.com")).thenReturn(true);
        perform(request().put("email", " MEMBER@Example.COM "))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(result -> assertSafeError(result.getResponse().getContentAsString()));
        verify(memberRepository).existsByEmail("member@example.com");
        verifyNoInteractions(passwordEncoder, memberAgreementRepository);
    }

    @Test
    void nicknameConflictUsesCaseInsensitiveRepositoryQueryAndReturns409() throws Exception {
        when(memberRepository.existsByNicknameIgnoreCase("FIRSTUSER")).thenReturn(true);
        perform(request().put("nickname", " FIRSTUSER "))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(result -> assertSafeError(result.getResponse().getContentAsString()));
        verify(memberRepository).existsByNicknameIgnoreCase("FIRSTUSER");
        verifyNoInteractions(passwordEncoder, memberAgreementRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{\"password\":\"Example123!\",", "{\"agreements\":\"Example123!\"}"})
    void malformedJsonOrMissingBodyReturnsSafe400(String body) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(result -> assertSafeError(result.getResponse().getContentAsString()));
        verifyNoInteractions(memberRepository, memberAgreementRepository, passwordEncoder);
    }

    @Test
    void unknownTermsCodeReturns400WithoutEchoingParserInput() throws Exception {
        ObjectNode request = request();
        ((ObjectNode) request.get("agreements").get(0)).put("termsCode", "UnknownSecret456!");
        perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(result -> assertSafeError(result.getResponse().getContentAsString(), "UnknownSecret456!"));
        verifyNoInteractions(memberRepository, memberAgreementRepository, passwordEncoder);
    }

    @Test
    void unrelatedDatabaseFailureIs500AndDoesNotExposeSqlOrHash() throws Exception {
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(
                new DataIntegrityViolationException("SQL insert into members " + PASSWORD + HASH,
                        new IllegalStateException("internal stack trace details")));
        perform(request()).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(result -> assertSafeError(result.getResponse().getContentAsString(),
                        "SQL", "insert into", "internal stack trace details", "DataIntegrityViolationException"));
    }

    @Test
    void missingCsrfTokenIsRejectedBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"))
                .andExpect(result -> assertSafeError(result.getResponse().getContentAsString()));
        verifyNoInteractions(memberRepository, memberAgreementRepository, passwordEncoder);
    }

    @Test
    void invalidCsrfTokenIsRejectedBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        verifyNoInteractions(memberRepository, memberAgreementRepository, passwordEncoder);
    }

    @Test
    void realSessionCsrfTokenSupportsAnonymousSignupButCannotBeUsedInAnotherSession() throws Exception {
        var csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty()).andReturn();
        JsonNode token = objectMapper.readTree(csrfResult.getResponse().getContentAsString());
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(post("/api/v1/auth/signup").session(session)
                        .header(token.get("headerName").asString(), token.get("token").asString())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(post("/api/v1/auth/signup").session(new MockHttpSession())
                        .header(token.get("headerName").asString(), token.get("token").asString())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        verify(memberRepository, times(1)).saveAndFlush(any(Member.class));
    }

    @Test
    void unrelatedEndpointsStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/members/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    private ObjectNode request() {
        return (ObjectNode) objectMapper.readTree(REQUEST);
    }

    private ResultActions perform(ObjectNode request) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)));
    }

    private void assertSafeError(String response, String... otherSecrets) {
        JsonNode body = objectMapper.readTree(response);
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message", "fieldErrors");
        assertThat(body.get("message").asString()).isNotBlank();
        assertThat(body.get("fieldErrors").isArray()).isTrue();
        body.get("fieldErrors").forEach(error ->
                assertThat(error.propertyNames()).containsExactlyInAnyOrder("field", "message"));
        assertThat(response).doesNotContain(PASSWORD, HASH, "rejectedValue", "passwordHash", "stackTrace");
        if (otherSecrets.length > 0) {
            assertThat(response).doesNotContain(otherSecrets);
        }
    }
}
