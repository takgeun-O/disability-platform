package io.github.takgeun.iyum.community.post.api;

import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.post.application.PostCreateService;
import io.github.takgeun.iyum.community.post.application.PostDeleteException;
import io.github.takgeun.iyum.community.post.application.PostDeleteService;
import io.github.takgeun.iyum.community.post.application.PostReadService;
import io.github.takgeun.iyum.community.post.application.PostUpdateService;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import org.junit.jupiter.api.Test;
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

import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = PostController.class,
        properties = {
                "spring.config.import=",
                "spring.docker.compose.enabled=false",
                "iyum.cors.allowed-origin=http://127.0.0.1:8443"
        }
)
@Import({
        SecurityConfig.class,
        DevCorsConfig.class,
        GlobalExceptionHandler.class
})
@ActiveProfiles("dev")
class PostDeleteControllerTest {
    @MockitoBean
    MemberRepository memberRepository;


    private static final String API_PATH =
            "/api/v1/community/posts/123";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PostCreateService postCreateService;

    @MockitoBean
    PostReadService postReadService;

    @MockitoBean
    PostUpdateService postUpdateService;

    @MockitoBean
    PostDeleteService postDeleteService;

    @Test
    void 인증된_회원_ID로_삭제하고_본문_없이_204를_반환한다()
            throws Exception {
        givenActiveMember();

        mockMvc.perform(
                        authenticatedDelete()
                                .with(csrf().asHeader())    // 유효한 CSRF 토큰을 헤더로 넣기
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
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

        verify(postDeleteService).delete(7L, 123L);

        verifyNoInteractions(
                postCreateService,
                postReadService,
                postUpdateService
        );
    }

    @Test
    void 비로그인_요청은_유효한_CSRF가_있어도_401이다()
            throws Exception {

        mockMvc.perform(
                        delete(API_PATH)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(postDeleteService);
    }

    @Test
    void 로그인했어도_CSRF가_없으면_403이다()
            throws Exception {

        mockMvc.perform(authenticatedDelete())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(memberRepository);
        verifyNoInteractions(postDeleteService);
    }

    @Test
    void 잘못된_CSRF_토큰이면_403이다()
            throws Exception {

        mockMvc.perform(
                        authenticatedDelete()
                                .with(csrf()
                                        .asHeader()
                                        .useInvalidToken()) // 잘못된 토큰 넣기
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(memberRepository);
        verifyNoInteractions(postDeleteService);
    }

    @Test
    void 게시글_ID가_숫자가_아니면_400이다()
            throws Exception {
        givenActiveMember();

        mockMvc.perform(
                        delete("/api/v1/community/posts/abc")
                                .with(user(principal()))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(postDeleteService);
    }

    @ParameterizedTest
    @CsvSource({
            "MEMBER_NOT_FOUND, 401, AUTHENTICATION_REQUIRED",
            "MEMBER_NOT_ACTIVE, 403, POST_WRITE_NOT_ALLOWED",
            "NOT_POST_AUTHOR, 403, ACCESS_DENIED",
            "POST_NOT_FOUND, 404, POST_NOT_FOUND"
    })
    void 서비스_예외를_정해진_HTTP_오류로_변환한다(
            PostDeleteException.Code exceptionCode,
            int expectedStatus,
            String expectedCode
    ) throws Exception {
        givenActiveMember();

        PostDeleteException exception =
                new PostDeleteException(exceptionCode);

        // 서비스 자체가 void를 반환하므로 실패 상황은 when(...).thenThrow(...) 대신 아래처럼 설정
        doThrow(exception)
                .when(postDeleteService)
                .delete(7L, 123L);

        String expectedMessage =
                exceptionCode == PostDeleteException.Code.MEMBER_NOT_FOUND
                        ? "로그인이 필요합니다."
                        : exception.getMessage();

        mockMvc.perform(
                        authenticatedDelete()
                                .with(csrf().asHeader())
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value(expectedCode))
                .andExpect(jsonPath("$.message")
                        .value(expectedMessage))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ));

        verify(postDeleteService).delete(7L, 123L);
    }

    @Test
    void 허용된_개발_Origin의_DELETE_사전_요청을_허용한다()
            throws Exception {

        mockMvc.perform(
                        options(API_PATH)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "DELETE"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "x-csrf-token"
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
                        containsString("DELETE")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("x-csrf-token")
                ));

        verifyNoInteractions(postDeleteService);
    }

    @Test
    void 허용되지_않은_Origin의_삭제_요청은_거절한다()
            throws Exception {

        mockMvc.perform(
                        authenticatedDelete()
                                .with(csrf().asHeader())
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://other.example"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(memberRepository);
        verifyNoInteractions(postDeleteService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/signup",
            "/api/v1/community/posts"
    })
    void 다른_경로에는_DELETE_CORS_허용을_확대하지_않는다(
            String path
    ) throws Exception {

        mockMvc.perform(
                        options(path)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "DELETE"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(postDeleteService);
    }

    private MockHttpServletRequestBuilder authenticatedDelete() {
        return delete(API_PATH)
                .with(user(principal()));
    }

    private MemberPrincipal principal() {
        return new MemberPrincipal(
                7L,
                "writer@example.com",
                "test-only-password-hash",
                "게시글작성자",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                0L
        );
    }

    private void givenActiveMember() {
        when(memberRepository.existsByIdAndStatusAndCredentialVersionAndRole(
                7L, MemberStatus.ACTIVE, 0L, MemberRole.USER)).thenReturn(true);
    }
}
