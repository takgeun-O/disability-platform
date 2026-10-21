package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.application.MemberActivityReadException;
import io.github.takgeun.iyum.member.application.MemberActivitySummaryResult;
import io.github.takgeun.iyum.member.application.MemberActivitySummaryService;
import io.github.takgeun.iyum.member.application.MemberCommentReadService;
import io.github.takgeun.iyum.member.application.MemberPostReadService;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MemberActivityController.class,
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
class MemberActivityControllerTest {

    private static final String URL =
            "/api/v1/members/me/activity-summary";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Long MEMBER_ID = 7L;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MemberActivitySummaryService memberActivitySummaryService;

    @MockitoBean
    MemberPostReadService memberPostReadService;

    @MockitoBean
    MemberCommentReadService memberCommentReadService;

    @MockitoBean
    MemberRepository memberRepository;

    @Test
    void 로그인한_회원은_CSRF_토큰_없이_활동_요약을_조회한다()
            throws Exception {

        givenActiveMember();

        when(memberActivitySummaryService.getSummary(MEMBER_ID))
                .thenReturn(new MemberActivitySummaryResult(2L, 3L));

        mockMvc.perform(
                        get(URL)
                                .with(user(principal()))
                                .header(HttpHeaders.ORIGIN, ORIGIN)
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
                .andExpect(jsonPath("$.postCount").value(2))
                .andExpect(jsonPath("$.commentCount").value(3))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(memberActivitySummaryService).getSummary(MEMBER_ID);
        verifyNoMoreInteractions(memberActivitySummaryService);

        verifyNoInteractions(
                memberPostReadService,
                memberCommentReadService
        );
    }

    @Test
    void 활동이_없으면_개수를_영으로_응답한다()
            throws Exception {

        givenActiveMember();

        when(memberActivitySummaryService.getSummary(MEMBER_ID))
                .thenReturn(new MemberActivitySummaryResult(0L, 0L));

        mockMvc.perform(
                        get(URL)
                                .with(user(principal()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postCount").value(0))
                .andExpect(jsonPath("$.commentCount").value(0));

        verify(memberActivitySummaryService).getSummary(MEMBER_ID);
        verifyNoMoreInteractions(memberActivitySummaryService);
    }

    @Test
    void 비로그인_요청은_401이고_저장소와_서비스를_호출하지_않는다()
            throws Exception {

        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verifyNoInteractions(
                memberRepository,
                memberActivitySummaryService,
                memberPostReadService,
                memberCommentReadService
        );
    }

    @Test
    void DB에_활성_회원이_없으면_401이고_서비스를_호출하지_않는다()
            throws Exception {

        // 세션에 저장된 상태는 ACTIVE지만,
        // DB 조회 결과는 활성 회원이 아닌 상황이다.
        when(memberRepository.existsByIdAndStatusAndCredentialVersion(
                MEMBER_ID,
                MemberStatus.ACTIVE,
                0L
        )).thenReturn(false);

        mockMvc.perform(
                        get(URL)
                                .with(user(principal()))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verify(memberRepository).existsByIdAndStatusAndCredentialVersion(
                MEMBER_ID,
                MemberStatus.ACTIVE,
                0L
        );

        verifyNoInteractions(
                memberActivitySummaryService,
                memberPostReadService,
                memberCommentReadService
        );
    }

    @Test
    void 서비스에서_회원_없음_예외가_발생하면_401을_반환한다()
            throws Exception {

        // 필터를 통과시킨 뒤 서비스 예외의 응답 변환을 검증한다.
        givenActiveMember();

        when(memberActivitySummaryService.getSummary(MEMBER_ID))
                .thenThrow(new MemberActivityReadException(
                        MemberActivityReadException.Code.MEMBER_NOT_FOUND
                ));

        mockMvc.perform(
                        get(URL)
                                .with(user(principal()))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        // 필터의 401로 우연히 통과하지 않도록 서비스 호출을 확인한다.
        verify(memberActivitySummaryService).getSummary(MEMBER_ID);
        verifyNoMoreInteractions(memberActivitySummaryService);
    }

    @Test
    void 서비스에서_비활성_회원_예외가_발생하면_403을_반환한다()
            throws Exception {

        givenActiveMember();

        when(memberActivitySummaryService.getSummary(MEMBER_ID))
                .thenThrow(new MemberActivityReadException(
                        MemberActivityReadException.Code.MEMBER_NOT_ACTIVE
                ));

        mockMvc.perform(
                        get(URL)
                                .with(user(principal()))
                )
                .andExpect(status().isForbidden())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_NOT_ACTIVE"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verify(memberActivitySummaryService).getSummary(MEMBER_ID);
        verifyNoMoreInteractions(memberActivitySummaryService);
    }

    @Test
    void 요청_파라미터로_다른_회원_ID를_보내도_로그인_회원_ID를_사용한다()
            throws Exception {

        givenActiveMember();

        when(memberActivitySummaryService.getSummary(MEMBER_ID))
                .thenReturn(new MemberActivitySummaryResult(2L, 3L));

        mockMvc.perform(
                        get(URL)
                                .with(user(principal()))
                                .param("memberId", "999")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postCount").value(2))
                .andExpect(jsonPath("$.commentCount").value(3));

        // 요청 파라미터의 999가 아니라 인증 정보의 7을 사용해야 한다.
        verify(memberRepository).existsByIdAndStatusAndCredentialVersion(
                MEMBER_ID,
                MemberStatus.ACTIVE,
                0L
        );
        verifyNoMoreInteractions(memberRepository);

        verify(memberActivitySummaryService).getSummary(MEMBER_ID);
        verifyNoMoreInteractions(memberActivitySummaryService);
    }

    @Test
    void 허용된_출처의_GET_사전_요청은_로그인_없이_허용한다()
            throws Exception {

        // CORS 사전 요청은 회원 상태 검사나 컨트롤러 실행 없이 처리된다.
        mockMvc.perform(
                        options(URL)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
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
                        "GET,OPTIONS"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_MAX_AGE,
                        "600"
                ));

        verifyNoInteractions(
                memberRepository,
                memberActivitySummaryService,
                memberPostReadService,
                memberCommentReadService
        );
    }

    @Test
    void 허용되지_않은_출처의_요청은_거절한다()
            throws Exception {

        mockMvc.perform(
                        get(URL)
                                .with(user(principal()))
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://untrusted.example"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        // CORS 검사에서 먼저 차단되므로 활성 회원 설정이 필요 없다.
        verifyNoInteractions(
                memberRepository,
                memberActivitySummaryService,
                memberPostReadService,
                memberCommentReadService
        );
    }

    @Test
    void PUT_메서드의_CORS_사전_요청은_거절한다()
            throws Exception {

        mockMvc.perform(
                        options(URL)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "PUT"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(
                memberRepository,
                memberActivitySummaryService,
                memberPostReadService,
                memberCommentReadService
        );
    }

    private void givenActiveMember() {
        when(memberRepository.existsByIdAndStatusAndCredentialVersion(
                MEMBER_ID,
                MemberStatus.ACTIVE,
                0L
        )).thenReturn(true);
    }

    private MemberPrincipal principal() {
        return new MemberPrincipal(
                MEMBER_ID,
                "activity-test@example.com",
                "test-only-password-hash",
                "활동조회회원",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                0L
        );
    }
}