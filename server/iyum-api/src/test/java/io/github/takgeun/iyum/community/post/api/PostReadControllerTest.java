package io.github.takgeun.iyum.community.post.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import io.github.takgeun.iyum.community.post.application.*;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
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
    @MockitoBean
    MemberRepository memberRepository;


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

    @MockitoBean
    PostUpdateService postUpdateService;

    @MockitoBean
    PostDeleteService postDeleteService;

    @Test
    void 비로그인으로_기본_목록을_조회할_수_있다()
            throws Exception {
        when(
                postReadService.getPosts(
                        null,
                        null,
                        null,
                        0,
                        20,
                        null
                ))
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
                .andExpect(jsonPath("$.items[0].likeCount").value(0))
                .andExpect(jsonPath("$.items[0].liked").value(false))
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
                .andExpect(jsonPath("$.items[0].matchedContentExcerpt")
                        .hasJsonPath())
                .andExpect(jsonPath("$.items[0].matchedContentExcerpt")
                        .value(nullValue()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(postReadService)
                .getPosts(
                        null,
                        null,
                        null,
                        0,
                        20,
                        null
                );
    }

    @Test
    void 필터와_페이지_조건을_서비스에_전달한다()
            throws Exception {
        when(
                postReadService.getPosts(
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        null,
                        1,
                        2,
                        null
                ))
                .thenReturn(
                        new PageImpl<>(
                                List.of(summary(123L),
                                        summary(124L)
                                ),
                                PageRequest.of(
                                        1,
                                        2
                                ),
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
                null,
                1,
                2, null
        );
    }

    @Test
    void 게시글이_없으면_빈_배열과_페이지_정보를_반환한다()
            throws Exception {
        when(
                postReadService.getPosts(
                        null,
                        null,
                        null,
                        0,
                        20,
                        null
                ))
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
        when(postReadService.getPost(123L, null))
                .thenReturn(new PostDetailResult(
                        123L,
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        "보청기 적응 질문",
                        "첫 번째 줄\n두 번째 줄",
                        new PostAuthorResult(7L, "질문작성자"),
                        CREATED_AT,
                        CREATED_AT,
                        0L,
                        false
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
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.liked").value(false))
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

        verify(postReadService).getPost(123L, null);
    }

    @Test
    void 게시글_없음_예외를_404로_반환한다()
            throws Exception {
        when(postReadService.getPost(999L, null))
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

        when(
                postReadService.getPosts(
                        null,
                        null,
                        null,
                        page,
                        size,
                        null
                ))
                .thenThrow(new PostReadException(
                        PostReadException.Code.INVALID_PAGE_REQUEST
                ));

        mockMvc.perform(get(BASE)
                        .param("page", Integer.toString(page))
                        .param("size", Integer.toString(size)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_PAGE_REQUEST"));

        verify(postReadService)
                .getPosts(
                        null,
                        null,
                        null,
                        page,
                        size,
                        null
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

    @Test
    void 목록_응답에_댓글_개수를_숫자로_포함한다()
            throws Exception {
        when(
                postReadService.getPosts(
                        null,
                        null,
                        null,
                        0,
                        20,
                        null
                ))
                .thenReturn(new PageImpl<>(
                        List.of(
                                summary(123L, 3L),
                                summary(124L, 0L)
                        ),
                        PageRequest.of(0, 20),
                        2
                ));

        // 로그인 쿠키나 CSRF 토큰 없이 공개 조회한다.
        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(123))
                .andExpect(jsonPath("$.items[0].commentCount")
                        .isNumber())
                .andExpect(jsonPath("$.items[0].commentCount")
                        .value(3))
                .andExpect(jsonPath("$.items[1].id").value(124))
                .andExpect(jsonPath("$.items[1].commentCount")
                        .isNumber())
                .andExpect(jsonPath("$.items[1].commentCount")
                        .value(0))
                .andExpect(jsonPath("$.totalElements").value(2));

        verify(postReadService)
                .getPosts(
                        null,
                        null,
                        null,
                        0,
                        20,
                        null
                );
    }

    @Test
    void 비로그인_검색에서_검색어와_필터와_페이지_조건을_서비스에_전달한다()
            throws Exception {
        String keyword = "  보청기 적응  ";

        when(postReadService.getPosts(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                keyword,
                1,
                2,
                null
        )).thenReturn(new PageImpl<>(
                List.of(summary(123L), summary(124L)),
                PageRequest.of(1, 2),
                5
        ));

        mockMvc.perform(get(BASE)
                        .param("q", keyword)
                        .param("type", "QUESTION")
                        .param("topic", "HEARING_AID")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(123))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));

        verify(postReadService).getPosts(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                keyword,
                1,
                2,
                null
        );
    }

    @Test
    void 검색어_길이_초과_예외를_400으로_반환한다()
            throws Exception {
        String keyword = "가".repeat(101);

        when(postReadService.getPosts(
                null,
                null,
                keyword,
                0,
                20,
                null
        )).thenThrow(new PostReadException(
                PostReadException.Code.INVALID_SEARCH_KEYWORD
        ));

        mockMvc.perform(get(BASE)
                        .param("q", keyword))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.code")
                        .value("INVALID_SEARCH_KEYWORD"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void 비로그인으로_주간_베스트를_조회하면_서비스의_순서대로_반환한다()
            throws Exception {
        PostAuthorResult author =
                new PostAuthorResult(7L, "질문작성자");

        PostFeedItemResult first = new PostFeedItemResult(
                120L,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "첫 보청기 상담 경험",
                "상담에서 도움이 되었던 질문입니다.",
                author,
                CREATED_AT,
                2L,
                5L,
                false
        );

        PostFeedItemResult second = new PostFeedItemResult(
                125L,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "보청기 착용 질문",
                "처음 착용할 때 궁금한 점입니다.",
                author,
                CREATED_AT,
                0L,
                1L,
                false
        );

        when(postReadService.getWeeklyBestPosts(20, null))
                .thenReturn(List.of(first, second));

//        디버그용
//        var response = mockMvc.perform(get(BASE + "/weekly-best"))
//                .andDo(print());
//
//        verify(postReadService)
//                .getWeeklyBestPosts(20, null);
//
//        response.andExpect(status().isOk())
//                .andExpect(jsonPath("$.items.length()").value(2));
//        디버그용

        mockMvc.perform(get(BASE + "/weekly-best"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(120))
                .andExpect(jsonPath("$.items[0].likeCount").value(5))
                .andExpect(jsonPath("$.items[0].liked").value(false))
                .andExpect(jsonPath("$.items[1].id").value(125));

        verify(postReadService)
                .getWeeklyBestPosts(20, null);
    }

    @Test
    void 주간_베스트가_없으면_빈_목록을_반환한다()
            throws Exception {
        when(postReadService.getWeeklyBestPosts(4, null))
                .thenReturn(List.of());

        mockMvc.perform(get(BASE + "/weekly-best")
                        .param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));

        verify(postReadService)
                .getWeeklyBestPosts(4, null);
    }

    @Test
    void 로그인한_회원의_ID를_전달하고_주간_베스트에_내_좋아요를_표시한다()
            throws Exception {
        MemberPrincipal principal = new MemberPrincipal(
                7L,
                "weekly-reader@example.com",
                "test-password-hash",
                "주간독자",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                0L
        );

        // 보안 필터가 7번 회원의 로그인 상태를 유효한 것으로 확인한다.
        when(memberRepository.existsByIdAndStatusAndCredentialVersionAndRole(
                7L,
                MemberStatus.ACTIVE,
                0L,
                MemberRole.USER
        )).thenReturn(true);

        PostFeedItemResult item = new PostFeedItemResult(
                120L,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "첫 보청기 상담 경험",
                "상담에서 도움이 되었던 질문입니다.",
                new PostAuthorResult(9L, "질문작성자"),
                CREATED_AT,
                2L,
                5L,
                true
        );

        when(postReadService.getWeeklyBestPosts(4, 7L))
                .thenReturn(List.of(item));

        mockMvc.perform(
                        get(BASE + "/weekly-best")
                                .with(user(principal))
                                .param("size", "4")
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(120))
                .andExpect(jsonPath("$.items[0].likeCount").value(5))
                .andExpect(jsonPath("$.items[0].liked").value(true));

        verify(postReadService)
                .getWeeklyBestPosts(4, 7L);
    }

    @Test
    void 주간_베스트의_조회_개수가_범위를_벗어나면_400을_반환한다()
            throws Exception {
        when(postReadService.getWeeklyBestPosts(51, null))
                .thenThrow(new PostReadException(
                        PostReadException.Code.INVALID_PAGE_REQUEST
                ));

        mockMvc.perform(
                        get(BASE + "/weekly-best")
                                .param("size", "51")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_PAGE_REQUEST"));

        verify(postReadService)
                .getWeeklyBestPosts(51, null);
    }

    @Test
    void 주간_베스트의_조회_개수가_숫자가_아니면_400을_반환한다()
            throws Exception {
        mockMvc.perform(
                        get(BASE + "/weekly-best")
                                .param("size", "abc")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(postReadService);
    }

    private PostSummaryResult summary(Long postId) {
        return summary(postId, 0L);
    }

    private PostSummaryResult summary(
            Long postId,
            long commentCount
    ) {
        return new PostSummaryResult(
                postId,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "보청기 적응 질문",
                new PostAuthorResult(7L, "질문작성자"),
                CREATED_AT,
                commentCount,
                0L,
                false,
                null
        );
    }
}