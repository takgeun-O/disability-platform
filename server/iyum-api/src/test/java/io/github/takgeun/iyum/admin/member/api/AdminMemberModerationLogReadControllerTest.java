package io.github.takgeun.iyum.admin.member.api;

import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationLogReadService;
import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationLogResult;
import io.github.takgeun.iyum.admin.member.application.AdminMemberReadException;
import io.github.takgeun.iyum.admin.member.domain.MemberModerationAction;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminMemberModerationLogReadController.class,
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
class AdminMemberModerationLogReadControllerTest {

    private static final String PATH =
            "/api/v1/admin/members/{memberId}/moderation-logs";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Long ACTOR_ID = 40L;
    private static final Long TARGET_ID = 20L;

    private static final long CREDENTIAL_VERSION = 3L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-12T00:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminMemberModerationLogReadService readService;

    @MockitoBean
    MemberRepository memberRepository;

    @Test
    void 관리자_정보로_조회하고_저장된_이력을_JSON으로_반환한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminMemberModerationLogResult restored =
                new AdminMemberModerationLogResult(
                        102L,
                        ACTOR_ID,
                        TARGET_ID,
                        MemberModerationAction.LIFT_SUSPENSION,
                        MemberStatus.SUSPENDED,
                        MemberStatus.ACTIVE,
                        "재검토 후 정지 해제",
                        CREATED_AT.plusSeconds(60)
                );

        AdminMemberModerationLogResult suspended =
                new AdminMemberModerationLogResult(
                        101L,
                        ACTOR_ID,
                        TARGET_ID,
                        MemberModerationAction.SUSPEND,
                        MemberStatus.ACTIVE,
                        MemberStatus.SUSPENDED,
                        "반복적인 이용 규칙 위반",
                        CREATED_AT
                );

        when(readService.getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                0,
                20
        )).thenReturn(
                new PageImpl<>(
                        List.of(restored, suspended),
                        PageRequest.of(0, 20),
                        2L
                )
        );

        /*
         * CSRF 토큰을 전달하지 않는다.
         * 요청 파라미터로 관리자 ID와 인증 버전을 덮어쓸 수 없어야 한다.
         */
        expectSuccess(mockMvc.perform(
                adminRequest(TARGET_ID)
                        .queryParam("actorMemberId", "999")
                        .queryParam("credentialVersion", "999")
        ))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].logId").value(102))
                .andExpect(jsonPath("$.items[0].actorMemberId").value(40))
                .andExpect(jsonPath("$.items[0].targetMemberId").value(20))
                .andExpect(jsonPath("$.items[0].action")
                        .value("LIFT_SUSPENSION"))
                .andExpect(jsonPath("$.items[0].beforeStatus")
                        .value("SUSPENDED"))
                .andExpect(jsonPath("$.items[0].afterStatus")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].reason")
                        .value("재검토 후 정지 해제"))
                .andExpect(jsonPath("$.items[0].createdAt")
                        .value(CREATED_AT.plusSeconds(60).toString()))

                .andExpect(jsonPath("$.items[1].logId").value(101))
                .andExpect(jsonPath("$.items[1].action").value("SUSPEND"))
                .andExpect(jsonPath("$.items[1].beforeStatus")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.items[1].afterStatus")
                        .value("SUSPENDED"))
                .andExpect(jsonPath("$.items[1].reason")
                        .value("반복적인 이용 규칙 위반"))
                .andExpect(jsonPath("$.items[1].createdAt")
                        .value(CREATED_AT.toString()))

                .andExpect(jsonPath("$.items[0].email")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].passwordHash")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].credentialVersion")
                        .doesNotHaveJsonPath())

                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(readService).getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                0,
                20
        );

        verifyNoMoreInteractions(readService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 페이지를_생략하거나_빈_문자열이면_기본값을_적용한다(
            boolean useEmptyParameters
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(readService.getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                0,
                20
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        MockHttpServletRequestBuilder request =
                adminRequest(TARGET_ID);

        if (useEmptyParameters) {
            request.queryParam("page", "");
            request.queryParam("size", "");
        }

        expectSuccess(mockMvc.perform(request))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(readService).getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                0,
                20
        );
    }

    @Test
    void 지정한_페이지를_전달하고_범위_밖_페이지의_전체_개수를_유지한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(readService.getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                5,
                2
        )).thenReturn(
                new PageImpl<>(
                        List.<AdminMemberModerationLogResult>of(),
                        PageRequest.of(5, 2),
                        3L
                )
        );

        expectSuccess(mockMvc.perform(
                adminRequest(TARGET_ID)
                        .queryParam("page", "5")
                        .queryParam("size", "2")
        ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(5))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));

        verify(readService).getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                5,
                2
        );
    }

    @Test
    void 비로그인_요청은_401로_거부한다() throws Exception {
        mockMvc.perform(request(TARGET_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ));

        verifyNoInteractions(memberRepository, readService);
    }

    @Test
    void 일반_회원은_403으로_거부한다() throws Exception {
        givenValidSession(MemberRole.USER);

        mockMvc.perform(
                        request(TARGET_ID)
                                .with(user(principal(MemberRole.USER)))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("ACCESS_DENIED"));

        verifyNoInteractions(readService);
    }

    @Test
    void 유효하지_않은_관리자_세션은_401로_거부한다()
            throws Exception {
        /*
         * MemberRepository의 세션 유효성 조회 결과는 기본값 false다.
         * 관리자 principal이 있어도 현재 DB와 일치하지 않으면 거부한다.
         */
        mockMvc.perform(adminRequest(TARGET_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verify(memberRepository)
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        MemberRole.ADMIN
                );

        verifyNoInteractions(readService);
    }

    @ParameterizedTest
    @CsvSource({
            "AUTHENTICATION_REQUIRED, 401",
            "ADMIN_REQUIRED, 403",
            "MEMBER_NOT_FOUND, 404",
            "INVALID_PAGE_REQUEST, 400"
    })
    void 서비스_오류를_HTTP_상태와_오류_코드로_변환한다(
            AdminMemberReadException.Code code,
            int expectedStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminMemberReadException exception =
                new AdminMemberReadException(code);

        when(readService.getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                0,
                20
        )).thenThrow(exception);

        mockMvc.perform(adminRequest(TARGET_ID))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.message")
                        .value(exception.getMessage()))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));

        verify(readService).getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                0,
                20
        );
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, 51",
            "2147483647, 2"
    })
    void 페이지_범위_검증은_서비스에_위임한다(
            int page,
            int size
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(readService.getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                page,
                size
        )).thenThrow(
                new AdminMemberReadException(
                        AdminMemberReadException.Code.INVALID_PAGE_REQUEST
                )
        );

        mockMvc.perform(
                        adminRequest(TARGET_ID)
                                .queryParam("page", Integer.toString(page))
                                .queryParam("size", Integer.toString(size))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_PAGE_REQUEST"));

        verify(readService).getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                page,
                size
        );
    }

    @ParameterizedTest
    @CsvSource({
            "page, abc",
            "page, 2147483648",
            "size, abc",
            "size, 2147483648"
    })
    void 페이지_숫자_변환_실패는_서비스_호출_전에_400으로_처리한다(
            String parameter,
            String value
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        expectInvalidParameter(mockMvc.perform(
                adminRequest(TARGET_ID)
                        .queryParam(parameter, value)
        ));

        verifyNoInteractions(readService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "9223372036854775808"
    })
    void 회원_ID를_Long으로_변환할_수_없으면_400이다(
            String memberId
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        expectInvalidParameter(
                mockMvc.perform(adminRequest(memberId))
        );

        verifyNoInteractions(readService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void 숫자로_변환되는_유효하지_않은_회원_ID는_서비스에서_404로_처리한다(
            long memberId
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(readService.getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                memberId,
                0,
                20
        )).thenThrow(
                new AdminMemberReadException(
                        AdminMemberReadException.Code.MEMBER_NOT_FOUND
                )
        );

        mockMvc.perform(adminRequest(memberId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_NOT_FOUND"));

        verify(readService).getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                memberId,
                0,
                20
        );
    }

    @Test
    void 허용된_출처의_GET_preflight는_인증_없이_통과한다()
            throws Exception {
        mockMvc.perform(
                        options(PATH, TARGET_ID)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "Accept"
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
                        containsString("GET")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsString("OPTIONS")
                ));

        verifyNoInteractions(memberRepository, readService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void 조회_경로에_변경_메서드의_preflight를_허용하지_않는다(
            String method
    ) throws Exception {
        mockMvc.perform(
                        options(PATH, TARGET_ID)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        method
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(memberRepository, readService);
    }

    @Test
    void 허용되지_않은_출처의_실제_GET은_거부한다()
            throws Exception {
        /*
         * request()에는 정상 Origin이 들어 있으므로 사용하지 않는다.
         * 허용되지 않은 Origin 하나만 가진 요청을 직접 만든다.
         */
        mockMvc.perform(
                        get(PATH, TARGET_ID)
                                .accept(MediaType.APPLICATION_JSON)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://untrusted.example"
                                )
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(memberRepository, readService);
    }

    // ---------------------------------------------------------------------
    // 공통 도우미
    // ---------------------------------------------------------------------

    private MockHttpServletRequestBuilder request(Object memberId) {
        return get(PATH, memberId)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ORIGIN, ORIGIN);
    }

    private MockHttpServletRequestBuilder adminRequest(Object memberId) {
        return request(memberId)
                .with(user(principal(MemberRole.ADMIN)));
    }

    private void givenValidSession(MemberRole role) {
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
                "admin-log-read@example.com",
                "test-only-password-hash",
                "이력조회관리자",
                MemberStatus.ACTIVE,
                role,
                CREDENTIAL_VERSION
        );
    }

    private ResultActions expectSuccess(
            ResultActions response
    ) throws Exception {
        return response
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
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
                ));
    }

    private ResultActions expectInvalidParameter(
            ResultActions response
    ) throws Exception {
        return response
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));
    }
}