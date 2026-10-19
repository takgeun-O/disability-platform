package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.application.MemberActivityReadException;
import io.github.takgeun.iyum.member.application.MemberActivitySummaryResult;
import io.github.takgeun.iyum.member.application.MemberActivitySummaryService;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    @Test
    void 로그인한_회원은_CSRF_토큰_없이_활동_요약을_조회한다()
            throws Exception {
        when(memberActivitySummaryService.getSummary(MEMBER_ID))
                .thenReturn(new MemberActivitySummaryResult(2L, 3L));

        mockMvc.perform(get(URL)
                        .with(user(principal()))
                        .header(HttpHeaders.ORIGIN, ORIGIN))
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
    }

    @Test
    void 활동이_없으면_개수를_영으로_응답한다()
            throws Exception {
        when(memberActivitySummaryService.getSummary(MEMBER_ID))
                .thenReturn(new MemberActivitySummaryResult(0L, 0L));

        mockMvc.perform(get(URL)
                        .with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postCount").value(0))
                .andExpect(jsonPath("$.commentCount").value(0));

        verify(memberActivitySummaryService).getSummary(MEMBER_ID);
    }

    @Test
    void 비로그인_요청은_거절하고_서비스를_호출하지_않는다()
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
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verifyNoInteractions(memberActivitySummaryService);
    }

    @Test
    void 회원을_찾을_수_없으면_인증_필요_응답을_반환한다()
            throws Exception {
        when(memberActivitySummaryService.getSummary(MEMBER_ID))
                .thenThrow(new MemberActivityReadException(
                        MemberActivityReadException.Code.MEMBER_NOT_FOUND
                ));

        mockMvc.perform(get(URL)
                        .with(user(principal())))
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

        verify(memberActivitySummaryService).getSummary(MEMBER_ID);
    }

    @Test
    void 회원이_활성_상태가_아니면_접근을_거절한다()
            throws Exception {
        when(memberActivitySummaryService.getSummary(MEMBER_ID))
                .thenThrow(new MemberActivityReadException(
                        MemberActivityReadException.Code.MEMBER_NOT_ACTIVE
                ));

        mockMvc.perform(get(URL)
                        .with(user(principal())))
                .andExpect(status().isForbidden())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_NOT_ACTIVE"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verify(memberActivitySummaryService).getSummary(MEMBER_ID);
    }

    @Test
    void 요청_파라미터로_다른_회원_ID를_보내도_로그인_회원_ID를_사용한다()
            throws Exception {
        when(memberActivitySummaryService.getSummary(MEMBER_ID))
                .thenReturn(new MemberActivitySummaryResult(2L, 3L));

        mockMvc.perform(get(URL)
                        .with(user(principal()))
                        .param("memberId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postCount").value(2))
                .andExpect(jsonPath("$.commentCount").value(3));

        // 요청의 999가 아니라 인증 정보의 7을 사용해야 한다.
        verify(memberActivitySummaryService).getSummary(MEMBER_ID);
        verifyNoMoreInteractions(memberActivitySummaryService);
    }

    @Test
    void 허용된_출처의_GET_사전_요청은_로그인_없이_허용한다()
            throws Exception {
        // 사전 요청은 브라우저가 실제 요청을 보내기 전에 서버의 CORS 허용 여부를 확인하는 과정
        // OPTIONS: 해당 주소에서 지원하는 통신 옵션 확인 (CORS 사전 요청이 대표적인 OPTIONS 활용 사례)
//        OPTIONS /api/v1/members/me/activity-summary
//        Origin: https://프론트엔드주소
//        Access-Control-Request-Method: GET
        mockMvc.perform(options(URL)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                "GET"
                        ))
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

        // 사전 요청만 처리했으므로, 실제 게시글·댓글 수를 조회하는 서비스는 호출되지 않아야 한다.
        verifyNoInteractions(memberActivitySummaryService);
    }

    @Test
    void 허용되지_않은_출처의_요청은_거절한다()
            throws Exception {
        mockMvc.perform(get(URL)
                        .with(user(principal()))
                        .header(
                                HttpHeaders.ORIGIN,
                                "https://untrusted.example"
                        ))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(memberActivitySummaryService);
    }

    @Test
    void PUT_메서드의_CORS_사전_요청은_거절한다()
            throws Exception {
        mockMvc.perform(options(URL)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                "PUT"
                        ))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(memberActivitySummaryService);
    }

    private MemberPrincipal principal() {
        return new MemberPrincipal(
                MEMBER_ID,
                "activity-test@example.com",
                "test-only-password-hash",
                "활동조회회원",
                MemberStatus.ACTIVE,
                MemberRole.USER
        );
    }
}