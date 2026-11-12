package io.github.takgeun.iyum.community.report.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.report.application.ContentReportCreateException;
import io.github.takgeun.iyum.community.report.application.ContentReportCreateResult;
import io.github.takgeun.iyum.community.report.application.ContentReportCreateService;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = ContentReportController.class,
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
class ContentReportControllerTest {

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final String UNTRUSTED_ORIGIN =
            "https://untrusted.example";

    private static final Long REPORTER_ID = 7L;
    private static final long CREDENTIAL_VERSION = 3L;

    private static final Long POST_ID = 104L;
    private static final Long COMMENT_ID = 144L;
    private static final Long REPORT_ID = 100L;

    private static final ContentReportReasonType REASON_TYPE =
            ContentReportReasonType.SPAM_ADVERTISING;

    private static final String REASON_DETAIL =
            "  동일한 광고를 반복해서 게시하고 있습니다.  ";

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-12T10:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    MemberRepository memberRepository;

    @MockitoBean
    ContentReportCreateService reportService;

    @ParameterizedTest
    @CsvSource({
            "POST, USER",
            "COMMENT, USER",
            "POST, ADMIN",
            "COMMENT, ADMIN"
    })
    void 인증된_회원의_신고를_접수하고_201을_반환한다(
            String target,
            MemberRole role
    ) throws Exception {
        givenActiveMember(role);
        stubSuccess(target, REASON_TYPE, REASON_DETAIL);

        mockMvc.perform(
                        request(target)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .with(user(principal(role)))
                                .with(csrf().asHeader())
                                // 쿼리 파라미터로 인증 정보를 바꾸려 해도
                                // 실제 서비스에는 세션의 값이 전달되어야 한다.
                                .param("reporterMemberId", "999")
                                .param("credentialVersion", "999")
                )
                .andExpect(status().isCreated())
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
                .andExpect(jsonPath("$.reportId").value(100))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.createdAt")
                        .value(CREATED_AT.toString()))
                // 신고자·스냅샷 등 다른 정보가 추가로 노출되지 않는다.
                .andExpect(jsonPath("$.*", hasSize(3)));

        verifyServiceCall(target, REASON_TYPE, REASON_DETAIL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 상세_사유를_생략하면_null로_서비스에_전달한다(
            String target
    ) throws Exception {
        givenActiveMember(MemberRole.USER);
        stubSuccess(target, REASON_TYPE, null);

        String body = objectMapper.writeValueAsString(
                Map.of("reasonType", REASON_TYPE.name())
        );

        mockMvc.perform(
                        request(target, body)
                                .with(user(principal(MemberRole.USER)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").value(100))
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verifyServiceCall(target, REASON_TYPE, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 사유_유형_누락은_서비스의_INVALID_REASON으로_응답한다(
            String target
    ) throws Exception {
        givenActiveMember(MemberRole.USER);

        ContentReportCreateException exception =
                new ContentReportCreateException(
                        ContentReportCreateException.Code.INVALID_REASON
                );

        stubFailure(target, null, null, exception);

        mockMvc.perform(
                        request(target, "{}")
                                .with(user(principal(MemberRole.USER)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REASON"))
                .andExpect(jsonPath("$.message")
                        .value(exception.getMessage()))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verifyServiceCall(target, null, null);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, AUTHENTICATION_REQUIRED, 401",
            "COMMENT, AUTHENTICATION_REQUIRED, 401",
            "POST, INVALID_REASON, 400",
            "COMMENT, INVALID_REASON, 400",
            "POST, SELF_REPORT_NOT_ALLOWED, 403",
            "COMMENT, SELF_REPORT_NOT_ALLOWED, 403",
            "POST, POST_NOT_FOUND, 404",
            "COMMENT, POST_NOT_FOUND, 404",
            "COMMENT, COMMENT_NOT_FOUND, 404",
            "POST, REPORT_ALREADY_EXISTS, 409",
            "COMMENT, REPORT_ALREADY_EXISTS, 409"
    })
    void 서비스_예외를_HTTP_오류로_변환한다(
            String target,
            ContentReportCreateException.Code code,
            int expectedStatus
    ) throws Exception {
        givenActiveMember(MemberRole.USER);

        ContentReportCreateException exception =
                new ContentReportCreateException(code);

        stubFailure(
                target,
                REASON_TYPE,
                REASON_DETAIL,
                exception
        );

        mockMvc.perform(
                        request(target)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .with(user(principal(MemberRole.USER)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.message")
                        .value(exception.getMessage()))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ));

        verifyServiceCall(target, REASON_TYPE, REASON_DETAIL);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void 잘못된_요청_본문은_서비스_호출_전에_400으로_거부한다(
            String target,
            String body
    ) throws Exception {
        givenActiveMember(MemberRole.USER);

        mockMvc.perform(
                        request(target, body)
                                .with(user(principal(MemberRole.USER)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verifyNoInteractions(reportService);
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of("POST", "COMMENT")
                .flatMap(target -> Stream.of(
                                "",
                                "null",
                                "{",
                                """
                                {
                                  "reasonType": "UNKNOWN",
                                  "reasonDetail": "신고 상세"
                                }
                                """,
                                """
                                {
                                  "reasonType": "spam_advertising"
                                }
                                """
                        )
                        .map(body -> Arguments.of(target, body)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/community/posts/abc/reports",
            "/api/v1/community/posts/abc/comments/144/reports",
            "/api/v1/community/posts/104/comments/abc/reports"
    })
    void 경로_ID가_숫자가_아니면_400으로_거부한다(
            String path
    ) throws Exception {
        givenActiveMember(MemberRole.USER);

        mockMvc.perform(
                        post(path)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validBody())
                                .with(user(principal(MemberRole.USER)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(reportService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void JSON이_아닌_ContentType은_415로_거부한다(
            String target
    ) throws Exception {
        givenActiveMember(MemberRole.USER);

        mockMvc.perform(
                        post(path(target))
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(validBody())
                                .with(user(principal(MemberRole.USER)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("HTTP_ERROR"));

        verifyNoInteractions(reportService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 비로그인은_유효한_CSRF가_있어도_401이다(
            String target
    ) throws Exception {
        mockMvc.perform(
                        request(target)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(memberRepository, reportService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 현재_회원_상태나_인증_버전_권한이_불일치하면_401이다(
            String target
    ) throws Exception {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        REPORTER_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        MemberRole.USER
                ))
                .thenReturn(false);

        mockMvc.perform(
                        request(target)
                                .with(user(principal(MemberRole.USER)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verify(memberRepository)
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        REPORTER_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        MemberRole.USER
                );

        verifyNoInteractions(reportService);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, false",
            "POST, true",
            "COMMENT, false",
            "COMMENT, true"
    })
    void 로그인해도_CSRF가_없거나_잘못되면_403이다(
            String target,
            boolean invalidToken
    ) throws Exception {
        MockHttpServletRequestBuilder builder =
                request(target)
                        .with(user(principal(MemberRole.USER)));

        if (invalidToken) {
            builder.with(csrf().asHeader().useInvalidToken());
        }

        mockMvc.perform(builder)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(reportService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 허용된_개발_Origin의_사전_요청을_허용한다(
            String target
    ) throws Exception {
        mockMvc.perform(
                        options(path(target))
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
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        "POST,OPTIONS"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("content-type")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("x-csrf-token")
                ));

        verifyNoInteractions(memberRepository, reportService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 허용되지_않은_Origin의_실제_요청은_거부한다(
            String target
    ) throws Exception {
        // request()에는 Origin 기본값이 없다.
        // 따라서 허용된 Origin과 중복되지 않고 하나만 전송된다.
        mockMvc.perform(
                        request(target)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        UNTRUSTED_ORIGIN
                                )
                                .with(user(principal(MemberRole.USER)))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(memberRepository, reportService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 허용되지_않은_Origin의_사전_요청은_거부한다(
            String target
    ) throws Exception {
        mockMvc.perform(
                        options(path(target))
                                .header(
                                        HttpHeaders.ORIGIN,
                                        UNTRUSTED_ORIGIN
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "content-type,x-csrf-token"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(memberRepository, reportService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 추가_필드는_무시하고_신고자와_스냅샷을_클라이언트에서_받지_않는다(String target) throws Exception {
        givenActiveMember(MemberRole.USER);
        stubSuccess(target, REASON_TYPE, null);
        mockMvc.perform(request(target, """
                        {
                          "reasonType": "SPAM_ADVERTISING",
                          "reporterMemberId": 999,
                          "credentialVersion": 999,
                          "titleSnapshot": "client value",
                          "contentSnapshot": "client value",
                          "unknownField": true
                        }
                        """)
                        .with(user(principal(MemberRole.USER)))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
        verifyServiceCall(target, REASON_TYPE, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 지원하지_않는_ContentType은_공통_HTTP_ERROR다(String target) throws Exception {
        givenActiveMember(MemberRole.USER);
        mockMvc.perform(request(target)
                        .contentType(MediaType.TEXT_PLAIN)
                        .with(user(principal(MemberRole.USER)))
                        .with(csrf().asHeader()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("HTTP_ERROR"))
                .andExpect(jsonPath("$.message").value("요청을 처리할 수 없습니다."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
        verifyNoInteractions(reportService);
    }

    private void givenActiveMember(MemberRole role) {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        REPORTER_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        role
                ))
                .thenReturn(true);
    }

    private MemberPrincipal principal(MemberRole role) {
        return new MemberPrincipal(
                REPORTER_ID,
                "report-controller-test@example.com",
                "{noop}unused-password",
                "신고API검증회원",
                MemberStatus.ACTIVE,
                role,
                CREDENTIAL_VERSION
        );
    }

    private String path(String target) {
        return switch (target) {
            case "POST" ->
                    "/api/v1/community/posts/" + POST_ID + "/reports";

            case "COMMENT" ->
                    "/api/v1/community/posts/" + POST_ID
                            + "/comments/" + COMMENT_ID + "/reports";

            default ->
                    throw new IllegalArgumentException(
                            "지원하지 않는 테스트 대상: " + target
                    );
        };
    }

    private String validBody() {
        return objectMapper.writeValueAsString(
                Map.of(
                        "reasonType", REASON_TYPE.name(),
                        "reasonDetail", REASON_DETAIL
                )
        );
    }

    private MockHttpServletRequestBuilder request(String target) {
        return request(target, validBody());
    }

    private MockHttpServletRequestBuilder request(
            String target,
            String body
    ) {
        return post(path(target))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void stubSuccess(
            String target,
            ContentReportReasonType reasonType,
            String reasonDetail
    ) {
        ContentReportCreateResult result =
                new ContentReportCreateResult(
                        REPORT_ID,
                        ContentReportStatus.RECEIVED,
                        CREATED_AT
                );

        switch (target) {
            case "POST" ->
                    when(reportService.reportPost(
                            REPORTER_ID,
                            CREDENTIAL_VERSION,
                            POST_ID,
                            reasonType,
                            reasonDetail
                    )).thenReturn(result);

            case "COMMENT" ->
                    when(reportService.reportComment(
                            REPORTER_ID,
                            CREDENTIAL_VERSION,
                            POST_ID,
                            COMMENT_ID,
                            reasonType,
                            reasonDetail
                    )).thenReturn(result);

            default ->
                    throw new IllegalArgumentException(
                            "지원하지 않는 테스트 대상: " + target
                    );
        }
    }

    private void stubFailure(
            String target,
            ContentReportReasonType reasonType,
            String reasonDetail,
            ContentReportCreateException exception
    ) {
        switch (target) {
            case "POST" ->
                    when(reportService.reportPost(
                            REPORTER_ID,
                            CREDENTIAL_VERSION,
                            POST_ID,
                            reasonType,
                            reasonDetail
                    )).thenThrow(exception);

            case "COMMENT" ->
                    when(reportService.reportComment(
                            REPORTER_ID,
                            CREDENTIAL_VERSION,
                            POST_ID,
                            COMMENT_ID,
                            reasonType,
                            reasonDetail
                    )).thenThrow(exception);

            default ->
                    throw new IllegalArgumentException(
                            "지원하지 않는 테스트 대상: " + target
                    );
        }
    }

    private void verifyServiceCall(
            String target,
            ContentReportReasonType reasonType,
            String reasonDetail
    ) {
        switch (target) {
            case "POST" ->
                    verify(reportService).reportPost(
                            REPORTER_ID,
                            CREDENTIAL_VERSION,
                            POST_ID,
                            reasonType,
                            reasonDetail
                    );

            case "COMMENT" ->
                    verify(reportService).reportComment(
                            REPORTER_ID,
                            CREDENTIAL_VERSION,
                            POST_ID,
                            COMMENT_ID,
                            reasonType,
                            reasonDetail
                    );

            default ->
                    throw new IllegalArgumentException(
                            "지원하지 않는 테스트 대상: " + target
                    );
        }

        verifyNoMoreInteractions(reportService);
    }
}