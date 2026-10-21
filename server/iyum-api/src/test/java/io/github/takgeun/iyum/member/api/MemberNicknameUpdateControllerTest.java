package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.application.MemberNicknameUpdateException;
import io.github.takgeun.iyum.member.application.MemberNicknameUpdateService;
import io.github.takgeun.iyum.member.application.MemberProfileResult;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MemberNicknameUpdateController.class,
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
class MemberNicknameUpdateControllerTest {

    private static final String URL =
            "/api/v1/members/me/nickname";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Long MEMBER_ID = 3L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-01T10:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MemberNicknameUpdateService nicknameUpdateService;

    @MockitoBean
    MemberRepository memberRepository;

    @Test
    void 닉네임을_변경하고_최신_프로필을_반환한다()
            throws Exception {

        givenActiveMember();
        givenSuccess("  새로운닉네임  ");

        mockMvc.perform(
                        authenticatedPut("""
                                {"nickname":"  새로운닉네임  "}
                                """)
                                .with(csrf().asHeader())
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.memberId").value(3))
                .andExpect(jsonPath("$.email")
                        .value("member@example.com"))
                .andExpect(jsonPath("$.nickname")
                        .value("새로운닉네임"))
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

        // 검증기는 유효성을 검사하고,
        // 정규화 및 저장은 서비스에 맡긴다.
        verify(nicknameUpdateService).updateNickname(
                MEMBER_ID,
                "  새로운닉네임  "
        );
        verifyNoMoreInteractions(nicknameUpdateService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"nickname\":null}",
            "{\"nickname\":\"\"}",
            "{\"nickname\":\"   \"}",
            "{\"nickname\":\"가\"}",
            "{\"nickname\":\"abcdefghijklmnopqrstu\"}",
            "{\"nickname\":\"닉 네임\"}",
            "{\"nickname\":\"닉네임!\"}",
            "{\"nickname\":\"ADMIN\"}"
    })
    void 잘못된_닉네임은_필드_오류를_반환한다(
            String requestBody
    ) throws Exception {

        // 인증 필터를 통과해야 요청 DTO의 검증까지 도달한다.
        givenActiveMember();

        mockMvc.perform(
                        authenticatedPut(requestBody)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()")
                        .value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("nickname"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .isNotEmpty());

        verifyNoInteractions(nicknameUpdateService);
    }

    @Test
    void 요청_본문이_없으면_400을_반환한다()
            throws Exception {

        givenActiveMember();

        mockMvc.perform(
                        put(URL)
                                .with(user(principal()))
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(nicknameUpdateService);
    }

    @Test
    void 중복_닉네임은_409와_닉네임_필드_오류를_반환한다()
            throws Exception {

        givenActiveMember();

        when(nicknameUpdateService.updateNickname(
                MEMBER_ID,
                "새로운닉네임"
        )).thenThrow(new MemberNicknameUpdateException(
                MemberNicknameUpdateException.Code.NICKNAME_ALREADY_EXISTS
        ));

        mockMvc.perform(
                        authenticatedPut("""
                                {"nickname":"새로운닉네임"}
                                """)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("NICKNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.fieldErrors.length()")
                        .value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("nickname"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verify(nicknameUpdateService).updateNickname(
                MEMBER_ID,
                "새로운닉네임"
        );
        verifyNoMoreInteractions(nicknameUpdateService);
    }

    @Test
    void 비로그인이면_유효한_CSRF가_있어도_401이다()
            throws Exception {

        mockMvc.perform(
                        put(URL)
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"nickname":"새로운닉네임"}
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verifyNoInteractions(
                memberRepository,
                nicknameUpdateService
        );
    }

    @Test
    void 로그인했어도_CSRF가_없으면_403이다()
            throws Exception {

        mockMvc.perform(
                        authenticatedPut("""
                                {"nickname":"새로운닉네임"}
                                """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        // 현재 필터 순서에서는 CSRF 검사에서 먼저 차단된다.
        verifyNoInteractions(
                memberRepository,
                nicknameUpdateService
        );
    }

    @Test
    void 로그인했어도_CSRF가_틀리면_403이다()
            throws Exception {

        mockMvc.perform(
                        authenticatedPut("""
                                {"nickname":"새로운닉네임"}
                                """)
                                .with(csrf().asHeader().useInvalidToken())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(
                memberRepository,
                nicknameUpdateService
        );
    }

    @Test
    void 다른_회원_ID를_전달해도_로그인_회원만_수정한다()
            throws Exception {

        givenActiveMember();
        givenSuccess("새로운닉네임");

        mockMvc.perform(
                        authenticatedPut("""
                                {"nickname":"새로운닉네임"}
                                """)
                                .with(csrf().asHeader())
                                .param("memberId", "999")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(3));

        verify(nicknameUpdateService).updateNickname(
                MEMBER_ID,
                "새로운닉네임"
        );
        verifyNoMoreInteractions(nicknameUpdateService);
    }

    @Test
    void DB에_활성_회원이_없으면_유효한_CSRF가_있어도_401이고_서비스를_호출하지_않는다()
            throws Exception {

        // 세션에는 ACTIVE인 MemberPrincipal이 남아 있지만,
        // DB 조회 결과는 더 이상 활성 회원이 아닌 상황이다.
        when(memberRepository.existsByIdAndStatusAndCredentialVersion(
                MEMBER_ID,
                MemberStatus.ACTIVE,
                0L
        )).thenReturn(false);

        mockMvc.perform(
                        authenticatedPut("""
                                {"nickname":"새로운닉네임"}
                                """)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
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
        verifyNoInteractions(nicknameUpdateService);
    }

    @Test
    void 서비스에서_회원_없음_예외가_발생하면_401을_반환한다()
            throws Exception {

        // 필터를 통과시킨 뒤 서비스 예외의 응답 변환을 검증한다.
        givenActiveMember();

        when(nicknameUpdateService.updateNickname(
                MEMBER_ID,
                "새로운닉네임"
        )).thenThrow(new MemberNicknameUpdateException(
                MemberNicknameUpdateException.Code.MEMBER_NOT_FOUND
        ));

        mockMvc.perform(
                        authenticatedPut("""
                                {"nickname":"새로운닉네임"}
                                """)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        // 필터에서 발생한 401로 우연히 통과하지 않도록 확인한다.
        verify(nicknameUpdateService).updateNickname(
                MEMBER_ID,
                "새로운닉네임"
        );
        verifyNoMoreInteractions(nicknameUpdateService);
    }

    @Test
    void 서비스에서_비활성_회원_예외가_발생하면_403을_반환한다()
            throws Exception {

        givenActiveMember();

        when(nicknameUpdateService.updateNickname(
                MEMBER_ID,
                "새로운닉네임"
        )).thenThrow(new MemberNicknameUpdateException(
                MemberNicknameUpdateException.Code.MEMBER_NOT_ACTIVE
        ));

        mockMvc.perform(
                        authenticatedPut("""
                                {"nickname":"새로운닉네임"}
                                """)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_NOT_ACTIVE"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verify(nicknameUpdateService).updateNickname(
                MEMBER_ID,
                "새로운닉네임"
        );
        verifyNoMoreInteractions(nicknameUpdateService);
    }

    @Test
    void 허용된_출처의_PUT_사전_요청을_허용한다()
            throws Exception {

        mockMvc.perform(
                        options(URL)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "PUT"
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
                        containsString("PUT")
                ));

        verifyNoInteractions(
                memberRepository,
                nicknameUpdateService
        );
    }

    @Test
    void 허용하지_않은_출처의_사전_요청을_거절한다()
            throws Exception {

        mockMvc.perform(
                        options(URL)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://untrusted.example"
                                )
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
                nicknameUpdateService
        );
    }

    private MockHttpServletRequestBuilder authenticatedPut(
            String requestBody
    ) {
        // 로그인 정보만 설정한다.
        // DB 상태 설정은 각 테스트에서 명시적으로 수행한다.
        return put(URL)
                .with(user(principal()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody);
    }

    private void givenActiveMember() {
        when(memberRepository.existsByIdAndStatusAndCredentialVersion(
                MEMBER_ID,
                MemberStatus.ACTIVE,
                0L
        )).thenReturn(true);
    }

    private void givenSuccess(String requestedNickname) {
        when(nicknameUpdateService.updateNickname(
                MEMBER_ID,
                requestedNickname
        )).thenReturn(new MemberProfileResult(
                MEMBER_ID,
                "member@example.com",
                "새로운닉네임",
                CREATED_AT
        ));
    }

    private MemberPrincipal principal() {
        return new MemberPrincipal(
                MEMBER_ID,
                "member@example.com",
                "test-only-password-hash",
                "기존닉네임",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                0L
        );
    }
}