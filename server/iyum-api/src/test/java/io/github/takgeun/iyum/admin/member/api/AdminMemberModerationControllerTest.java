package io.github.takgeun.iyum.admin.member.api;

import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationException;
import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationResult;
import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationService;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminMemberModerationController.class,
        properties = {
                "spring.config.import=",
                "spring.docker.compose.enabled=false",
                "iyum.cors.allowed-origin=http://127.0.0.1:8443",
                "springdoc.api-docs.enabled=false",
                "springdoc.swagger-ui.enabled=false"
        }
)
@Import({
        SecurityConfig.class,
        DevCorsConfig.class,
        GlobalExceptionHandler.class
})
@ActiveProfiles("dev")
class AdminMemberModerationControllerTest {

    private static final String BASE =
            "/api/v1/admin/members/{memberId}/{action}";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Long ACTOR_ID = 40L;
    private static final Long TARGET_ID = 20L;
    private static final long CREDENTIAL_VERSION = 3L;

    private static final String REASON =
            "  운영 정책 검토 결과입니다.  ";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    MemberRepository memberRepository;

    @MockitoBean
    AdminMemberModerationService moderationService;

    @ParameterizedTest
    @CsvSource({
            "suspend, SUSPENDED, true",
            "suspend, SUSPENDED, false",
            "lift-suspension, ACTIVE, true",
            "lift-suspension, ACTIVE, false"
    })
    void 관리자_인증정보로_처리하고_현재_상태를_반환한다(
            String action,
            MemberStatus expectedStatus,
            boolean changed
    ) throws Exception {
        givenActiveMember(MemberRole.ADMIN);

        stubResult(
                action,
                new AdminMemberModerationResult(
                        TARGET_ID,
                        expectedStatus,
                        changed
                )
        );

        mockMvc.perform(
                        request(action)
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                                // 요청자가 보낸 관리자 정보는 사용하지 않는다.
                                .param("actorMemberId", "999")
                                .param("credentialVersion", "999")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ))
                .andExpect(jsonPath("$.memberId").value(20))
                .andExpect(jsonPath("$.status").value(expectedStatus.name()))
                .andExpect(jsonPath("$.changed").value(changed))
                .andExpect(jsonPath("$.credentialVersion").doesNotExist());

        verifyServiceCall(action, TARGET_ID, REASON);
    }

    @ParameterizedTest
    @ValueSource(strings = {"suspend", "lift-suspension"})
    void 비로그인_요청은_401을_반환한다(String action)
            throws Exception {
        mockMvc.perform(
                        request(action)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(moderationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"suspend", "lift-suspension"})
    void 일반_회원은_403을_반환한다(String action)
            throws Exception {
        givenActiveMember(MemberRole.USER);

        mockMvc.perform(
                        request(action)
                                .with(user(principal(MemberRole.USER)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(moderationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"suspend", "lift-suspension"})
    void 현재_세션_조건이_일치하지_않으면_401을_반환한다(
            String action
    ) throws Exception {
        // 저장소 Mock의 기본 반환값 false를 사용한다.
        // 현재 회원 상태·인증 버전·권한이 세션과 일치하지 않는 상황이다.
        mockMvc.perform(
                        request(action)
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(moderationService);
    }

    @ParameterizedTest
    @CsvSource({
            "suspend, false",
            "suspend, true",
            "lift-suspension, false",
            "lift-suspension, true"
    })
    void 관리자라도_CSRF가_없거나_잘못되면_거부한다(
            String action,
            boolean invalidToken
    ) throws Exception {
        var builder = request(action)
                .with(user(principal(MemberRole.ADMIN)));

        if (invalidToken) {
            builder.with(csrf().asHeader().useInvalidToken());
        }

        mockMvc.perform(builder)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(moderationService);
    }

    @ParameterizedTest
    @CsvSource({
            "suspend, AUTHENTICATION_REQUIRED, 401",
            "lift-suspension, AUTHENTICATION_REQUIRED, 401",
            "suspend, ADMIN_REQUIRED, 403",
            "lift-suspension, ADMIN_REQUIRED, 403",
            "suspend, INVALID_REASON, 400",
            "lift-suspension, INVALID_REASON, 400",
            "suspend, MEMBER_NOT_FOUND, 404",
            "lift-suspension, MEMBER_NOT_FOUND, 404",
            "suspend, SELF_MODERATION_NOT_ALLOWED, 403",
            "lift-suspension, SELF_MODERATION_NOT_ALLOWED, 403",
            "suspend, ADMIN_TARGET_NOT_ALLOWED, 403",
            "lift-suspension, ADMIN_TARGET_NOT_ALLOWED, 403",
            "suspend, MEMBER_STATUS_NOT_ALLOWED, 409",
            "lift-suspension, MEMBER_STATUS_NOT_ALLOWED, 409"
    })
    void 서비스_예외를_HTTP_응답으로_변환한다(
            String action,
            AdminMemberModerationException.Code code,
            int expectedStatus
    ) throws Exception {
        givenActiveMember(MemberRole.ADMIN);

        AdminMemberModerationException exception =
                new AdminMemberModerationException(code);

        stubFailure(action, TARGET_ID, REASON, exception);

        mockMvc.perform(
                        request(action)
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.message")
                        .value(exception.getMessage()))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verifyServiceCall(action, TARGET_ID, REASON);
    }

    @ParameterizedTest
    @CsvSource({
            "suspend, false",
            "suspend, true",
            "lift-suspension, false",
            "lift-suspension, true"
    })
    void 사유_생략과_null은_서비스_검증으로_거부한다(
            String action,
            boolean explicitNull
    ) throws Exception {
        givenActiveMember(MemberRole.ADMIN);

        String body = explicitNull
                ? "{\"reason\":null}"
                : "{}";

        stubFailure(
                action,
                TARGET_ID,
                null,
                new AdminMemberModerationException(
                        AdminMemberModerationException.Code.INVALID_REASON
                )
        );

        mockMvc.perform(
                        request(action)
                                .content(body)
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REASON"));

        verifyServiceCall(action, TARGET_ID, null);
    }

    @ParameterizedTest
    @CsvSource({
            "suspend, EMPTY",
            "suspend, NULL",
            "suspend, MALFORMED",
            "lift-suspension, EMPTY",
            "lift-suspension, NULL",
            "lift-suspension, MALFORMED"
    })
    void 본문이_없거나_JSON이_잘못되면_거부한다(
            String action,
            String scenario
    ) throws Exception {
        givenActiveMember(MemberRole.ADMIN);

        String body = switch (scenario) {
            case "EMPTY" -> "";
            case "NULL" -> "null";
            case "MALFORMED" -> "{\"reason\":";
            default -> throw new IllegalArgumentException(scenario);
        };

        mockMvc.perform(
                        request(action)
                                .content(body)
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(moderationService);
    }

    @ParameterizedTest
    @CsvSource({
            "suspend, abc",
            "suspend, 9223372036854775808",
            "lift-suspension, abc",
            "lift-suspension, 9223372036854775808"
    })
    void 회원_ID를_Long으로_변환할_수_없으면_거부한다(
            String action,
            String targetId
    ) throws Exception {
        givenActiveMember(MemberRole.ADMIN);

        mockMvc.perform(
                        post(BASE, targetId, action)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of("reason", REASON)
                                ))
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(moderationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"suspend", "lift-suspension"})
    void 허용된_출처의_POST_사전요청은_로그인_없이_성공한다(
            String action
    ) throws Exception {
        mockMvc.perform(
                        options(BASE, TARGET_ID, action)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "Content-Type,X-CSRF-TOKEN"
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
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        "POST,OPTIONS"
                ));

        verifyNoInteractions(memberRepository, moderationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"suspend", "lift-suspension"})
    void 허용되지_않은_출처의_요청은_거부한다(
            String action
    ) throws Exception {
        mockMvc.perform(
                        post(BASE, TARGET_ID, action)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://untrusted.example"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of("reason", REASON)
                                        )
                                )
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                        )
                );

        verifyNoInteractions(memberRepository, moderationService);
    }

    private void givenActiveMember(MemberRole role) {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        role
                ))
                .thenReturn(true);
    }

    private MemberPrincipal principal(MemberRole role) {
        return new MemberPrincipal(
                ACTOR_ID,
                "admin-api-test@example.com",
                "{noop}unused-password",
                "관리자API검증",
                MemberStatus.ACTIVE,
                role,
                CREDENTIAL_VERSION
        );
    }

    private MockHttpServletRequestBuilder request(String action) {
        return post(BASE, TARGET_ID, action)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("reason", REASON)
                ));
    }

    private void stubResult(
            String action,
            AdminMemberModerationResult result
    ) {
        if ("suspend".equals(action)) {
            when(moderationService.suspend(
                    ACTOR_ID,
                    CREDENTIAL_VERSION,
                    TARGET_ID,
                    REASON
            )).thenReturn(result);
        } else {
            when(moderationService.liftSuspension(
                    ACTOR_ID,
                    CREDENTIAL_VERSION,
                    TARGET_ID,
                    REASON
            )).thenReturn(result);
        }
    }

    private void stubFailure(
            String action,
            Long targetId,
            String reason,
            AdminMemberModerationException exception
    ) {
        if ("suspend".equals(action)) {
            when(moderationService.suspend(
                    ACTOR_ID,
                    CREDENTIAL_VERSION,
                    targetId,
                    reason
            )).thenThrow(exception);
        } else {
            when(moderationService.liftSuspension(
                    ACTOR_ID,
                    CREDENTIAL_VERSION,
                    targetId,
                    reason
            )).thenThrow(exception);
        }
    }

    private void verifyServiceCall(
            String action,
            Long targetId,
            String reason
    ) {
        if ("suspend".equals(action)) {
            verify(moderationService).suspend(
                    ACTOR_ID,
                    CREDENTIAL_VERSION,
                    targetId,
                    reason
            );
        } else {
            verify(moderationService).liftSuspension(
                    ACTOR_ID,
                    CREDENTIAL_VERSION,
                    targetId,
                    reason
            );
        }

        verifyNoMoreInteractions(moderationService);
    }
}