package io.github.takgeun.iyum.community.comment.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.comment.application.CommentLikeException;
import io.github.takgeun.iyum.community.comment.application.CommentLikeResult;
import io.github.takgeun.iyum.community.comment.application.CommentLikeService;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = CommentLikeController.class,
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
class CommentLikeControllerTest {

    private static final String API_PATH =
            "/api/v1/community/posts/123/comments/456/like";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CommentLikeService commentLikeService;

    @MockitoBean
    MemberRepository memberRepository;

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void 인증된_회원으로_처리하고_댓글_좋아요_상태를_반환한다(
            String method
    ) throws Exception {
        givenActiveMember();

        boolean liked = method.equals("PUT");
        long likeCount = liked ? 5L : 4L;

        CommentLikeResult result =
                new CommentLikeResult(
                        123L, 456L, liked, likeCount
                );

        if (liked) {
            when(commentLikeService.like(7L, 123L, 456L))
                    .thenReturn(result);
        } else {
            when(commentLikeService.unlike(7L, 123L, 456L))
                    .thenReturn(result);
        }

        mockMvc.perform(
                        requestFor(method)
                                .with(user(principal()))
                                .with(csrf().asHeader())
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                // 요청자가 다른 ID를 보내도 인증된 회원을 사용한다.
                                .param("memberId", "999")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.postId").value(123))
                .andExpect(jsonPath("$.commentId").value(456))
                .andExpect(jsonPath("$.liked").value(liked))
                .andExpect(jsonPath("$.likeCount").value((int) likeCount))
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

        if (liked) {
            verify(commentLikeService).like(7L, 123L, 456L);
        } else {
            verify(commentLikeService).unlike(7L, 123L, 456L);
        }

        verifyNoMoreInteractions(commentLikeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void 비로그인은_유효한_CSRF가_있어도_401이다(
            String method
    ) throws Exception {
        mockMvc.perform(
                        requestFor(method)
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(commentLikeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void 로그인해도_CSRF가_없으면_403이다(
            String method
    ) throws Exception {
        mockMvc.perform(
                        requestFor(method)
                                .with(user(principal()))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(memberRepository, commentLikeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void 잘못된_CSRF는_403이다(
            String method
    ) throws Exception {
        mockMvc.perform(
                        requestFor(method)
                                .with(user(principal()))
                                .with(csrf().asHeader().useInvalidToken())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(memberRepository, commentLikeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void 회원_상태나_인증_버전이_유효하지_않으면_401이다(
            String method
    ) throws Exception {
        when(memberRepository.existsByIdAndStatusAndCredentialVersion(
                7L,
                MemberStatus.ACTIVE,
                0L
        )).thenReturn(false);

        mockMvc.perform(
                        requestFor(method)
                                .with(user(principal()))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(commentLikeService);
    }

    @ParameterizedTest
    @CsvSource({
            "PUT, abc, 456",
            "DELETE, abc, 456",
            "PUT, 123, abc",
            "DELETE, 123, abc"
    })
    void 게시글이나_댓글_ID가_숫자가_아니면_400이다(
            String method,
            String postId,
            String commentId
    ) throws Exception {
        givenActiveMember();

        mockMvc.perform(
                        request(
                                HttpMethod.valueOf(method),
                                "/api/v1/community/posts/{postId}/comments/{commentId}/like",
                                postId,
                                commentId
                        )
                                .with(user(principal()))
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(commentLikeService);
    }

    @ParameterizedTest
    @CsvSource({
            "PUT, MEMBER_NOT_FOUND, 401, AUTHENTICATION_REQUIRED",
            "DELETE, MEMBER_NOT_FOUND, 401, AUTHENTICATION_REQUIRED",
            "PUT, MEMBER_NOT_ACTIVE, 403, COMMENT_LIKE_NOT_ALLOWED",
            "DELETE, MEMBER_NOT_ACTIVE, 403, COMMENT_LIKE_NOT_ALLOWED",
            "PUT, POST_NOT_FOUND, 404, POST_NOT_FOUND",
            "DELETE, POST_NOT_FOUND, 404, POST_NOT_FOUND",
            "PUT, COMMENT_NOT_FOUND, 404, COMMENT_NOT_FOUND",
            "DELETE, COMMENT_NOT_FOUND, 404, COMMENT_NOT_FOUND"
    })
    void 서비스_예외를_HTTP_오류로_변환한다(
            String method,
            CommentLikeException.Code exceptionCode,
            int expectedStatus,
            String expectedCode
    ) throws Exception {
        givenActiveMember();

        CommentLikeException exception =
                new CommentLikeException(exceptionCode);

        if (method.equals("PUT")) {
            when(commentLikeService.like(7L, 123L, 456L))
                    .thenThrow(exception);
        } else {
            when(commentLikeService.unlike(7L, 123L, 456L))
                    .thenThrow(exception);
        }

        String expectedMessage =
                exceptionCode == CommentLikeException.Code.MEMBER_NOT_FOUND
                        ? "로그인이 필요합니다."
                        : exception.getMessage();

        mockMvc.perform(
                        requestFor(method)
                                .with(user(principal()))
                                .with(csrf().asHeader())
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void 개발_Origin의_사전_요청을_허용한다(
            String method
    ) throws Exception {
        mockMvc.perform(
                        options(API_PATH)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        method
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
                        containsString(method)
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("x-csrf-token")
                ));

        verifyNoInteractions(commentLikeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void 허용하지_않은_Origin은_거절한다(
            String method
    ) throws Exception {
        mockMvc.perform(
                        requestFor(method)
                                .with(user(principal()))
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

        verifyNoInteractions(memberRepository, commentLikeService);
    }

    private MockHttpServletRequestBuilder requestFor(String method) {
        return request(HttpMethod.valueOf(method), API_PATH);
    }

    private MemberPrincipal principal() {
        return new MemberPrincipal(
                7L,
                "member@example.com",
                "test-only-password-hash",
                "좋아요회원",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                0L
        );
    }

    private void givenActiveMember() {
        when(memberRepository.existsByIdAndStatusAndCredentialVersion(
                7L,
                MemberStatus.ACTIVE,
                0L
        )).thenReturn(true);
    }
}