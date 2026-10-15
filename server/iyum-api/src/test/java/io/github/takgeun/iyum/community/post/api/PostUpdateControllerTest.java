package io.github.takgeun.iyum.community.post.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.post.application.PostCreateService;
import io.github.takgeun.iyum.community.post.application.PostReadService;
import io.github.takgeun.iyum.community.post.application.PostUpdateCommand;
import io.github.takgeun.iyum.community.post.application.PostUpdateException;
import io.github.takgeun.iyum.community.post.application.PostUpdateService;
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
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class PostUpdateControllerTest {

    private static final String API_PATH =
            "/api/v1/community/posts/123";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final String VALID_BODY = """
            {
                "type": "REVIEW",
                "topic": "COCHLEAR_IMPLANT",
                "title": "  수정한 제목  ",
                "content": "수정한 본문"
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PostCreateService postCreateService;

    @MockitoBean
    PostReadService postReadService;

    @MockitoBean
    PostUpdateService postUpdateService;

    @Test
    void 로그인_회원_ID와_수정_내용을_전달하고_204를_반환한다()
            throws Exception {

        mockMvc.perform(
                        authenticatedPut(VALID_BODY)
                                .with(csrf().asHeader())
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ));

        verify(postUpdateService).update(
                7L,
                123L,
                new PostUpdateCommand(
                        PostType.REVIEW,
                        PostTopic.COCHLEAR_IMPLANT,
                        "수정한 제목",
                        "수정한 본문"
                )
        );

        verifyNoInteractions(postCreateService, postReadService);
    }

    @Test
    void 비로그인은_유효한_CSRF가_있어도_401이다()
            throws Exception {

        mockMvc.perform(
                        put(API_PATH)
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(postUpdateService);
    }

    @Test
    void 로그인했어도_CSRF가_없으면_403이다()
            throws Exception {

        mockMvc.perform(authenticatedPut(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(postUpdateService);
    }

    @Test
    void 잘못된_CSRF_토큰이면_403이다()
            throws Exception {

        mockMvc.perform(
                        authenticatedPut(VALID_BODY)
                                .with(csrf()
                                        .asHeader()
                                        .useInvalidToken())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(postUpdateService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            """
            {
                "type": null,
                "topic": null,
                "title": "   ",
                "content": "   "
            }
            """
    })
    void 필수값_누락이나_공백_입력은_필드_오류를_반환한다(
            String body
    ) throws Exception {

        mockMvc.perform(
                        authenticatedPut(body)
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

        verifyNoInteractions(postUpdateService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"UNKNOWN\"}",
            "{broken",
            "",
            "null"
    })
    void 잘못된_JSON이나_누락된_본문은_400이다(
            String body
    ) throws Exception {

        mockMvc.perform(
                        authenticatedPut(body)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(postUpdateService);
    }

    @Test
    void 게시글_ID가_숫자가_아니면_400이다()
            throws Exception {

        mockMvc.perform(
                        put("/api/v1/community/posts/abc")
                                .with(user(principal()))
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(postUpdateService);
    }

    @ParameterizedTest
    @CsvSource({
            "MEMBER_NOT_FOUND, 401, AUTHENTICATION_REQUIRED",
            "MEMBER_NOT_ACTIVE, 403, POST_WRITE_NOT_ALLOWED",
            "NOT_POST_AUTHOR, 403, ACCESS_DENIED",
            "POST_NOT_FOUND, 404, POST_NOT_FOUND"
    })
    void 서비스_예외를_정해진_HTTP_오류로_변환한다(
            PostUpdateException.Code exceptionCode,
            int expectedStatus,
            String expectedCode
    ) throws Exception {

        doThrow(new PostUpdateException(exceptionCode))
                .when(postUpdateService)
                .update(
                        eq(7L),
                        eq(123L),
                        any(PostUpdateCommand.class)
                );

        mockMvc.perform(
                        authenticatedPut(VALID_BODY)
                                .with(csrf().asHeader())
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ));
    }

    @Test
    void 허용된_개발_Origin의_PUT_사전_요청을_허용한다()
            throws Exception {

        mockMvc.perform(
                        options(API_PATH)
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

        verifyNoInteractions(postUpdateService);
    }

    @Test
    void 허용되지_않은_Origin의_PUT_요청은_거절한다()
            throws Exception {

        mockMvc.perform(
                        authenticatedPut(VALID_BODY)
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

        verifyNoInteractions(postUpdateService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/signup",
            "/api/v1/community/posts"
    })
    void 다른_경로의_CORS에는_PUT을_추가하지_않는다(
            String path
    ) throws Exception {

        mockMvc.perform(
                        options(path)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "PUT"
                                )
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(postUpdateService);
    }

    private MockHttpServletRequestBuilder authenticatedPut(
            String body
    ) {
        return put(API_PATH)
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