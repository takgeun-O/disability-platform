package io.github.takgeun.iyum.community.post.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.post.application.*;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class PostControllerTest {

    private static final String API_PATH =
            "/api/v1/community/posts";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final String VALID_BODY = """
            {
                "type": "QUESTION",
                "topic": "HEARING_AID",
                "title": "  보청기 적응 질문입니다  ",
                "content": "처음 착용했습니다."
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean    // 실제 서비스 대신 테스트용 Mock 주입
    PostCreateService postCreateService;

    @MockitoBean
    PostReadService postReadService;

    @Test
    void 인증된_회원_ID로_작성하고_201을_반환한다()
            throws Exception {

        when(postCreateService.create(
                eq(7L),
                any(PostCreateCommand.class)
        )).thenReturn(new PostCreateResult(123L));

        mockMvc.perform(
                        authenticatedPost(VALID_BODY)
                                .with(csrf().asHeader())
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ));

        verify(postCreateService).create(
                7L,
                new PostCreateCommand(
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        "보청기 적응 질문입니다",
                        "처음 착용했습니다."
                )
        );
    }

    @Test
    void 비로그인_요청은_유효한_CSRF가_있어도_401이다()
            throws Exception {

        mockMvc.perform(
                        post(API_PATH)
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(postCreateService);
    }

    @Test
    void 로그인했어도_CSRF가_없으면_403이다()
            throws Exception {

        mockMvc.perform(authenticatedPost(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(postCreateService);
    }

    @Test
    void 잘못된_CSRF_토큰이면_403이다()
            throws Exception {

        mockMvc.perform(
                        authenticatedPost(VALID_BODY)
                                .with(csrf()
                                        .asHeader()
                                        .useInvalidToken())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(postCreateService);
    }

    @Test
    void 잘못된_입력은_각_필드_오류와_함께_400을_반환한다()
            throws Exception {

        String body = """
                {
                    "type": null,
                    "topic": null,
                    "title": "   ",
                    "content": "   "
                }
                """;

        mockMvc.perform(
                        authenticatedPost(body)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath(
                        "$.fieldErrors[*].field",
                        containsInAnyOrder(
                                "type",
                                "topic",
                                "title",
                                "content"
                        )
                ));

        verifyNoInteractions(postCreateService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"UNKNOWN\"}",
            "{broken"
    })
    void 잘못된_열거형이나_JSON은_400을_반환한다(
            String body
    ) throws Exception {

        mockMvc.perform(
                        authenticatedPost(body)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(postCreateService);
    }

    @ParameterizedTest
    @CsvSource({
            "AUTHOR_NOT_FOUND, 401, AUTHENTICATION_REQUIRED",
            "AUTHOR_NOT_ACTIVE, 403, POST_WRITE_NOT_ALLOWED"
    })
    void 서비스_예외를_정해진_HTTP_오류로_변환한다(
            PostCreateException.Code exceptionCode,
            int expectedStatus,
            String expectedCode
    ) throws Exception {

        when(postCreateService.create(
                eq(7L),
                any(PostCreateCommand.class)
        )).thenThrow(new PostCreateException(exceptionCode));

        mockMvc.perform(
                        authenticatedPost(VALID_BODY)
                                .with(csrf().asHeader())
                )
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code")
                        .value(expectedCode))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void 허용된_개발_Origin의_사전_요청을_허용한다()
            throws Exception {

        mockMvc.perform(
                        options(API_PATH)
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
                ));

        verifyNoInteractions(postCreateService);
    }

    @Test
    void 허용되지_않은_Origin은_거절한다()
            throws Exception {

        mockMvc.perform(
                        options(API_PATH)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://other.example"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(postCreateService);
    }

    private MockHttpServletRequestBuilder authenticatedPost(
            String body
    ) {
        return post(API_PATH)
                .with(user(principal()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private MemberPrincipal principal() {
        return new MemberPrincipal(
                7L,
                "writer@example.com",
                "test-only-password-hash",
                "게시글작성자",
                MemberStatus.ACTIVE,
                MemberRole.USER
        );
    }
}