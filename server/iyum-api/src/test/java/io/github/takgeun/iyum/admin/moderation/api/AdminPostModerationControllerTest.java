package io.github.takgeun.iyum.admin.moderation.api;

import io.github.takgeun.iyum.admin.moderation.application.ContentModerationException;
import io.github.takgeun.iyum.admin.moderation.application.PostModerationResult;
import io.github.takgeun.iyum.admin.moderation.application.PostModerationService;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
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

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminPostModerationController.class,
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
class AdminPostModerationControllerTest {

    private static final String BASE =
            "/api/v1/admin/posts/{postId}/{action}";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Long MEMBER_ID = 7L;
    private static final long CREDENTIAL_VERSION = 3L;
    private static final Long POST_ID = 31L;

    // 컨트롤러는 사유를 가공하지 않고 서비스로 전달한다.
    // 공백 제거와 입력 정책 검증은 서비스에서 수행한다.
    private static final String REASON =
            "  운영 정책에 따른 처리입니다.  ";

    // OTHER는 숨김·복원 모두 사용할 수 있다.
    // 기존 공통 요청 테스트에는 이 유형을 사용한다.
    private static final ModerationReasonType REASON_TYPE =
            ModerationReasonType.OTHER;

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-31T00:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    MemberRepository memberRepository;

    @MockitoBean
    PostModerationService postModerationService;

    @ParameterizedTest
    @CsvSource({
            "hide, true",
            "hide, false",
            "restore, true",
            "restore, false"
    })
    void 관리자의_인증정보로_처리하고_현재_상태를_반환한다(
            String action,
            boolean changed
    ) throws Exception {
        givenActiveMember(MemberRole.ADMIN);

        boolean hidden = "hide".equals(action);

        stubResult(
                action,
                new PostModerationResult(
                        POST_ID,
                        hidden,
                        hidden ? HIDDEN_AT : null,
                        changed
                )
        );

        var response = mockMvc.perform(
                        request(action)
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                                // 요청자가 다른 값을 보내도 사용하지 않는다.
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
                .andExpect(jsonPath("$.postId").value(31))
                .andExpect(jsonPath("$.hidden").value(hidden))
                .andExpect(jsonPath("$.changed").value(changed))
                .andExpect(jsonPath("$.hiddenAt").hasJsonPath());

        if (hidden) {
            response.andExpect(jsonPath("$.hiddenAt")
                    .value(HIDDEN_AT.toString()));
        } else {
            response.andExpect(jsonPath("$.hiddenAt")
                    .value(nullValue()));
        }

        verifyServiceCall(action);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hide", "restore"})
    void 비로그인_요청은_401을_반환한다(String action)
            throws Exception {
        mockMvc.perform(
                        request(action)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(postModerationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hide", "restore"})
    void 일반_회원은_403을_반환한다(String action)
            throws Exception {
        givenActiveMember(MemberRole.USER);

        mockMvc.perform(
                        request(action)
                                .with(user(principal(MemberRole.USER)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("ACCESS_DENIED"));

        verifyNoInteractions(postModerationService);
    }

    @ParameterizedTest
    @CsvSource({
            "hide, false",
            "hide, true",
            "restore, false",
            "restore, true"
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
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(postModerationService);
    }

    @ParameterizedTest
    @CsvSource({
            "hide, AUTHENTICATION_REQUIRED, 401",
            "restore, AUTHENTICATION_REQUIRED, 401",
            "hide, ADMIN_REQUIRED, 403",
            "restore, ADMIN_REQUIRED, 403",
            "hide, INVALID_REASON, 400",
            "restore, INVALID_REASON, 400",
            "hide, POST_NOT_FOUND, 404",
            "restore, POST_NOT_FOUND, 404",
            "hide, POST_DELETED, 409",
            "restore, POST_DELETED, 409"
    })
    void 서비스_예외를_HTTP_응답으로_변환한다(
            String action,
            String code,
            int expectedStatus
    ) throws Exception {
        givenActiveMember(MemberRole.ADMIN);

        ContentModerationException exception =
                new ContentModerationException(
                        ContentModerationException.Code.valueOf(code)
                );

        if ("hide".equals(action)) {
            when(postModerationService.hide(
                    MEMBER_ID, CREDENTIAL_VERSION, POST_ID, REASON_TYPE, REASON
            )).thenThrow(exception);
        } else {
            when(postModerationService.restore(
                    MEMBER_ID, CREDENTIAL_VERSION, POST_ID, REASON_TYPE, REASON
            )).thenThrow(exception);
        }

        mockMvc.perform(
                        request(action)
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message")
                        .value(exception.getMessage()))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verifyServiceCall(action);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hide", "restore"})
    void 허용된_출처의_POST_사전요청은_로그인_없이_성공한다(
            String action
    ) throws Exception {
        mockMvc.perform(
                        options(BASE, POST_ID, action)
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

        verifyNoInteractions(postModerationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hide", "restore"})
    void 허용되지_않은_출처의_요청은_거부한다(String action)
            throws Exception {
        mockMvc.perform(
                        request(action, "https://untrusted.example")
                                .with(user(principal(MemberRole.ADMIN)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        // CORS 단계에서 차단되어 회원 조회와 서비스까지 도달하지 않는다.
        verifyNoInteractions(
                memberRepository,
                postModerationService
        );
    }

    @ParameterizedTest
    @CsvSource({
            "hide, SPAM_ADVERTISING, false",
            "hide, SPAM_ADVERTISING, true",
            "restore, NO_VIOLATION, false",
            "restore, NO_VIOLATION, true"
    })
    void 상세_사유_생략과_null을_서비스에_그대로_전달한다(
            String action,
            ModerationReasonType reasonType,
            boolean explicitNull
    ) throws Exception {
        givenActiveMember(MemberRole.ADMIN);
        boolean hidden = "hide".equals(action);
        var result = new PostModerationResult(POST_ID, hidden, hidden ? HIDDEN_AT : null, true);
        if (hidden) {
            when(postModerationService.hide(MEMBER_ID, CREDENTIAL_VERSION, POST_ID, reasonType, null)).thenReturn(result);
        } else {
            when(postModerationService.restore(MEMBER_ID, CREDENTIAL_VERSION, POST_ID, reasonType, null)).thenReturn(result);
        }
        // 명시적 null과 필드 생략을 서로 다른 JSON 요청으로 검증한다.
        String body = "{\"reasonType\":\"" + reasonType.name() + "\""
                + (explicitNull ? ",\"reasonDetail\":null" : "") + "}";

        mockMvc.perform(request(action)
                        .content(body)
                        .with(user(principal(MemberRole.ADMIN)))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(POST_ID))
                .andExpect(jsonPath("$.hidden").value(hidden))
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")));

        if (hidden) {
            verify(postModerationService).hide(MEMBER_ID, CREDENTIAL_VERSION, POST_ID, reasonType, null);
        } else {
            verify(postModerationService).restore(MEMBER_ID, CREDENTIAL_VERSION, POST_ID, reasonType, null);
        }
        verifyNoMoreInteractions(postModerationService);
    }

    @ParameterizedTest
    @CsvSource({
            "hide, UNKNOWN_REASON",
            "restore, UNKNOWN_REASON",
            "hide, spam_advertising",
            "restore, no_violation"
    })
    void 알_수_없거나_소문자인_사유_유형은_요청_본문_오류로_거부한다(
            String action,
            String reasonType
    ) throws Exception {
        givenActiveMember(MemberRole.ADMIN);

        mockMvc.perform(request(action)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reasonType", reasonType,
                                "reasonDetail", REASON
                        )))
                        .with(user(principal(MemberRole.ADMIN)))
                        .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(postModerationService);
    }

    private void givenActiveMember(MemberRole role) {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        MEMBER_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        role
                ))
                .thenReturn(true);
    }

    private MemberPrincipal principal(MemberRole role) {
        return new MemberPrincipal(
                MEMBER_ID,
                "moderation-test@example.com",
                "{noop}unused-password",
                "처리검증회원",
                MemberStatus.ACTIVE,
                role,
                CREDENTIAL_VERSION
        );
    }

    private MockHttpServletRequestBuilder request(String action) {
        return request(action, ORIGIN);
    }

    private MockHttpServletRequestBuilder request(
            String action,
            String origin
    ) {
        return post(BASE, POST_ID, action)
                .header(HttpHeaders.ORIGIN, origin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("reasonType", REASON_TYPE.name(),
                                "reasonDetail", REASON
                        )
                ));
    }

    private void stubResult(
            String action,
            PostModerationResult result
    ) {
        if ("hide".equals(action)) {
            when(postModerationService.hide(
                    MEMBER_ID, CREDENTIAL_VERSION, POST_ID, REASON_TYPE, REASON
            )).thenReturn(result);
        } else {
            when(postModerationService.restore(
                    MEMBER_ID, CREDENTIAL_VERSION, POST_ID, REASON_TYPE, REASON
            )).thenReturn(result);
        }
    }

    private void verifyServiceCall(String action) {
        if ("hide".equals(action)) {
            verify(postModerationService).hide(
                    MEMBER_ID, CREDENTIAL_VERSION, POST_ID, REASON_TYPE, REASON
            );
        } else {
            verify(postModerationService).restore(
                    MEMBER_ID, CREDENTIAL_VERSION, POST_ID, REASON_TYPE, REASON
            );
        }

        verifyNoMoreInteractions(postModerationService);
    }
}