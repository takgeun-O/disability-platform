package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.application.MemberProfileException;
import io.github.takgeun.iyum.member.application.MemberProfileResult;
import io.github.takgeun.iyum.member.application.MemberProfileService;
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

import java.time.Instant;

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
        controllers = MemberProfileController.class,
        properties = {
                "spring.config.import=",
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
class MemberProfileControllerTest {

    private static final String URL = "/api/v1/members/me";
    private static final String ORIGIN = "http://127.0.0.1:8443";

    private static final Long MEMBER_ID = 3L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-01T10:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MemberProfileService memberProfileService;

    @MockitoBean
    private MemberRepository memberRepository;

    @Test
    void 로그인한_회원의_최신_프로필을_CSRF_없이_조회한다()
            throws Exception {

        givenActiveMember();
        givenProfile();

        mockMvc.perform(
                        get(URL)
                                .with(user(principal()))
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.memberId").value(3))
                .andExpect(jsonPath("$.email")
                        .value("member@example.com"))
                .andExpect(jsonPath("$.nickname")
                        .value("최신닉네임"))
                .andExpect(jsonPath("$.createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
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
                ));

        verify(memberProfileService).getProfile(MEMBER_ID);
        verifyNoMoreInteractions(memberProfileService);
    }

    @Test
    void 비로그인_요청은_401이고_서비스를_호출하지_않는다()
            throws Exception {
        mockMvc.perform(
                        get(URL)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verifyNoInteractions(memberProfileService);
    }

    @Test
    void 다른_회원_ID를_파라미터로_보내도_로그인_회원만_조회한다()
            throws Exception {

        givenActiveMember();
        givenProfile();

        mockMvc.perform(
                        get(URL)
                                .with(user(principal()))
                                .param("memberId", "999")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(3))
                .andExpect(jsonPath("$.nickname")
                        .value("최신닉네임"));

        verify(memberProfileService).getProfile(MEMBER_ID);
        verifyNoMoreInteractions(memberProfileService);
    }

    @Test
    void 세션의_회원이_ACTIVE가_아니면_401이고_서비스를_호출하지_않는다()
            throws Exception {
        when(memberRepository.existsByIdAndStatus(
                MEMBER_ID,
                MemberStatus.ACTIVE
        )).thenReturn(false);

        mockMvc.perform(
                        get(URL)
                                .with(user(principal()))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verify(memberRepository).existsByIdAndStatus(
                MEMBER_ID,
                MemberStatus.ACTIVE
        );
        verifyNoInteractions(memberProfileService);
    }

    @Test
    void 서비스에서_회원_없음_예외가_발생하면_401을_반환한다()
            throws Exception {

        givenActiveMember();

        when(memberProfileService.getProfile(MEMBER_ID))
                .thenThrow(new MemberProfileException(
                        MemberProfileException.Code.MEMBER_NOT_FOUND
                ));

        mockMvc.perform(
                        get(URL)
                                .with(user(principal()))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verify(memberProfileService).getProfile(MEMBER_ID);
        verifyNoMoreInteractions(memberProfileService);
    }

    @Test
    void 서비스에서_비활성_회원_예외가_발생하면_403을_반환한다()
            throws Exception {
        givenActiveMember();

        when(memberProfileService.getProfile(MEMBER_ID))
                .thenThrow(new MemberProfileException(
                        MemberProfileException.Code.MEMBER_NOT_ACTIVE
                ));

        mockMvc.perform(
                        get(URL)
                                .with(user(principal()))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_NOT_ACTIVE"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verify(memberProfileService).getProfile(MEMBER_ID);
    }

    @Test
    void 허용된_출처의_GET_사전_요청은_로그인_없이_허용한다()
            throws Exception {
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
                        containsString("GET")
                ));

        verifyNoInteractions(memberProfileService);
    }

    @Test
    void 허용하지_않은_출처는_로그인했어도_거절한다()
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

        verifyNoInteractions(memberProfileService);
    }

    private void givenProfile() {
        when(memberProfileService.getProfile(MEMBER_ID))
                .thenReturn(new MemberProfileResult(
                        MEMBER_ID,
                        "member@example.com",
                        "최신닉네임",
                        CREATED_AT
                ));
    }

    private MemberPrincipal principal() {
        return new MemberPrincipal(
                MEMBER_ID,
                "member@example.com",
                "test-only-password-hash",
                "로그인당시닉네임",
                MemberStatus.ACTIVE,
                MemberRole.USER
        );
    }

    // 필터 검사를 통과시켜 서비스 예외 처리 경로에 도달하도록 하는 설정
    private void givenActiveMember() {
        when(memberRepository.existsByIdAndStatus(
                MEMBER_ID,
                MemberStatus.ACTIVE
        )).thenReturn(true);
    }
}