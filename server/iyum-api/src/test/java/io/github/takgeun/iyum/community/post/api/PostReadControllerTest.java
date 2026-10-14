package io.github.takgeun.iyum.community.post.api;

import io.github.takgeun.iyum.community.post.application.PostAuthorResult;
import io.github.takgeun.iyum.community.post.application.PostCreateService;
import io.github.takgeun.iyum.community.post.application.PostDetailResult;
import io.github.takgeun.iyum.community.post.application.PostReadException;
import io.github.takgeun.iyum.community.post.application.PostReadService;
import io.github.takgeun.iyum.community.post.application.PostSummaryResult;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class PostReadControllerTest {

    private static final String BASE =
            "/api/v1/community/posts";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-13T10:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PostReadService postReadService;

    @MockitoBean
    PostCreateService postCreateService;

    @Test
    void 비로그인으로_기본_목록을_조회할_수_있다()
            throws Exception {
        when(postReadService.getPosts(null, null, 0, 20))
                .thenReturn(new PageImpl<>(
                        List.of(summary(123L)),
                        PageRequest.of(0, 20),
                        1
                ));

        mockMvc.perform(get(BASE)
                        .header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(123))
                .andExpect(jsonPath("$.items[0].type")
                        .value("QUESTION"))
                .andExpect(jsonPath("$.items[0].topic")
                        .value("HEARING_AID"))
                .andExpect(jsonPath("$.items[0].title")
                        .value("보청기 적응 질문"))
                .andExpect(jsonPath("$.items[0].author.memberId")
                        .value(7))
                .andExpect(jsonPath("$.items[0].author.nickname")
                        .value("질문작성자"))
                .andExpect(jsonPath("$.items[0].author.email")
                        .doesNotExist())
                .andExpect(jsonPath("$.items[0].author.passwordHash")
                        .doesNotExist())
                .andExpect(jsonPath("$.items[0].content")
                        .doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(postReadService).getPosts(null, null, 0, 20);
    }

    @Test
    void 필터와_페이지_조건을_서비스에_전달한다()
            throws Exception {
        when(postReadService.getPosts(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                1,
                2
        )).thenReturn(new PageImpl<>(
                List.of(summary(123L), summary(124L)),
                PageRequest.of(1, 2),
                5
        ));

        mockMvc.perform(get(BASE)
                        .param("type", "QUESTION")
                        .param("topic", "HEARING_AID")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));

        verify(postReadService).getPosts(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                1,
                2
        );
    }

    @Test
    void 게시글이_없으면_빈_배열과_페이지_정보를_반환한다()
            throws Exception {
        when(postReadService.getPosts(null, null, 0, 20))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 비로그인으로_상세를_조회할_수_있다()
            throws Exception {
        when(postReadService.getPost(123L))
                .thenReturn(new PostDetailResult(
                        123L,
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        "보청기 적응 질문",
                        "첫 번째 줄\n두 번째 줄",
                        new PostAuthorResult(7L, "질문작성자"),
                        CREATED_AT,
                        CREATED_AT
                ));

        mockMvc.perform(get(BASE + "/123")
                        .header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ))
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.content")
                        .value("첫 번째 줄\n두 번째 줄"))
                .andExpect(jsonPath("$.author.memberId").value(7))
                .andExpect(jsonPath("$.author.nickname")
                        .value("질문작성자"))
                .andExpect(jsonPath("$.author.email")
                        .doesNotExist())
                .andExpect(jsonPath("$.author.passwordHash")
                        .doesNotExist())
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString());

        verify(postReadService).getPost(123L);
    }

    @Test
    void 게시글_없음_예외를_404로_반환한다()
            throws Exception {
        when(postReadService.getPost(999L))
                .thenThrow(new PostReadException(
                        PostReadException.Code.POST_NOT_FOUND
                ));

        mockMvc.perform(get(BASE + "/999")
                        .header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isNotFound())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(jsonPath("$.code")
                        .value("POST_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, 51"
    })
    void 페이지_검사_예외를_400으로_반환한다(
            int page,
            int size
    ) throws Exception {
        when(postReadService.getPosts(null, null, page, size))
                .thenThrow(new PostReadException(
                        PostReadException.Code.INVALID_PAGE_REQUEST
                ));

        mockMvc.perform(get(BASE)
                        .param("page", Integer.toString(page))
                        .param("size", Integer.toString(size)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_PAGE_REQUEST"));

        verify(postReadService).getPosts(
                null, null, page, size
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/community/posts?type=UNKNOWN",
            "/api/v1/community/posts?topic=UNKNOWN",
            "/api/v1/community/posts?page=abc",
            "/api/v1/community/posts?size=abc",
            "/api/v1/community/posts/abc"
    })
    void 형식이_잘못되면_서비스_호출_전에_400을_반환한다(
            String url
    ) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verifyNoInteractions(
                postReadService,
                postCreateService
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/community/posts",
            "/api/v1/community/posts/123"
    })
    void 조회_경로의_개발_Origin_사전_요청을_허용한다(
            String path
    ) throws Exception {
        mockMvc.perform(options(path)
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
                ));

        verifyNoInteractions(
                postReadService,
                postCreateService
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/community/posts",
            "/api/v1/community/posts/123"
    })
    void 허용하지_않은_Origin은_거절한다(
            String path
    ) throws Exception {
        mockMvc.perform(get(path)
                        .header(
                                HttpHeaders.ORIGIN,
                                "https://other.example"
                        ))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(
                postReadService,
                postCreateService
        );
    }

    @Test
    void 조회를_공개해도_작성에는_로그인이_필요하다()
            throws Exception {
        mockMvc.perform(post(BASE)
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "QUESTION",
                                  "topic": "HEARING_AID",
                                  "title": "작성 테스트",
                                  "content": "작성 테스트 본문"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(
                postReadService,
                postCreateService
        );
    }

    @Test
    void 작성_API의_CSRF_보호가_유지된다()
            throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(
                postReadService,
                postCreateService
        );
    }

    private PostSummaryResult summary(Long postId) {
        return new PostSummaryResult(
                postId,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "보청기 적응 질문",
                new PostAuthorResult(7L, "질문작성자"),
                CREATED_AT
        );
    }
}