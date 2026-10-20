package io.github.takgeun.iyum.community.post.api;

import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
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

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
    @MockitoBean
    MemberRepository memberRepository;


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

    @MockitoBean
    PostUpdateService postUpdateService;

    @MockitoBean
    PostDeleteService postDeleteService;

    @Test
    void 인증된_회원_ID로_작성하고_201을_반환한다()
            throws Exception {
        givenActiveMember();

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

        verifyNoInteractions(memberRepository);
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

        verifyNoInteractions(memberRepository);
        verifyNoInteractions(postCreateService);
    }

    @Test
    void 잘못된_입력은_각_필드_오류와_함께_400을_반환한다()
            throws Exception {
        givenActiveMember();

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
        givenActiveMember();

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
        givenActiveMember();

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
        verify(postCreateService).create(eq(7L), any(PostCreateCommand.class));
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

    @Test
    void 피드는_로그인과_CSRF_없이_조회하고_미리보기와_댓글수를_반환한다()
            throws Exception {

        Instant latestTime =
                Instant.parse("2026-10-18T02:00:00.123456Z");

        Instant nextTime = latestTime.minusSeconds(1);

        PostFeedResult result = new PostFeedResult(
                List.of(
                        new PostFeedItemResult(
                                105L,
                                PostType.QUESTION,
                                PostTopic.HEARING_AID,
                                "첫 번째 게시글",
                                "첫 번째 본문 미리보기",
                                new PostAuthorResult(7L, "게시글작성자"),
                                latestTime,
                                3L
                        ),
                        new PostFeedItemResult(
                                104L,
                                PostType.QUESTION,
                                PostTopic.HEARING_AID,
                                "두 번째 게시글",
                                "두 번째 본문 미리보기",
                                new PostAuthorResult(7L, "게시글작성자"),
                                nextTime,
                                0L
                        )
                ),
                true,
                new PostCursor(nextTime, 104L)
        );

        when(postReadService.getFeed(
                null, null, null, null, 2
        )).thenReturn(result);

        // 로그인 사용자·세션·CSRF 토큰을 넣지 않는다.
        mockMvc.perform(
                        get(API_PATH + "/feed")
                                .param("size", "2")
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(105))
                .andExpect(jsonPath("$.items[0].type").value("QUESTION"))
                .andExpect(jsonPath("$.items[0].topic").value("HEARING_AID"))
                .andExpect(jsonPath("$.items[0].title")
                        .value("첫 번째 게시글"))
                .andExpect(jsonPath("$.items[0].contentPreview")
                        .value("첫 번째 본문 미리보기"))
                .andExpect(jsonPath("$.items[0].author.memberId").value(7))
                .andExpect(jsonPath("$.items[0].author.nickname")
                        .value("게시글작성자"))
                .andExpect(jsonPath("$.items[0].createdAt")
                        .value(latestTime.toString()))
                .andExpect(jsonPath("$.items[0].commentCount").isNumber())
                .andExpect(jsonPath("$.items[0].commentCount").value(3))
                .andExpect(jsonPath("$.items[1].commentCount").value(0))
                .andExpect(jsonPath("$.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor.createdAt")
                        .value(nextTime.toString()))
                .andExpect(jsonPath("$.nextCursor.postId").value(104))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ));

        verify(postReadService).getFeed(
                null, null, null, null, 2
        );
    }

    @Test
    void 피드의_필터와_커서를_서비스에_전달하고_마지막_목록을_반환한다()
            throws Exception {

        String cursorText = "2026-10-18T02:00:00.123456Z";
        Instant cursorTime = Instant.parse(cursorText);

        PostFeedResult result = new PostFeedResult(
                List.of(
                        new PostFeedItemResult(
                                103L,
                                PostType.QUESTION,
                                PostTopic.HEARING_AID,
                                "마지막 게시글",
                                "마지막 본문 미리보기",
                                new PostAuthorResult(7L, "게시글작성자"),
                                cursorTime.minusSeconds(1),
                                0L
                        )
                ),
                false,
                null
        );

        when(postReadService.getFeed(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                cursorTime,
                104L,
                2
        )).thenReturn(result);

        mockMvc.perform(
                        get(API_PATH + "/feed")
                                .param("type", "QUESTION")
                                .param("topic", "HEARING_AID")
                                .param("afterCreatedAt", cursorText)
                                .param("afterId", "104")
                                .param("size", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(103))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").hasJsonPath())
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        verify(postReadService).getFeed(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                cursorTime,
                104L,
                2
        );
    }

    @Test
    void 피드는_조회개수를_생략하면_20을_사용하고_빈_목록도_200이다()
            throws Exception {

        when(postReadService.getFeed(
                null, null, null, null, 20
        )).thenReturn(new PostFeedResult(
                List.of(),
                false,
                null
        ));

        mockMvc.perform(get(API_PATH + "/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").hasJsonPath())
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.page").doesNotExist())
                .andExpect(jsonPath("$.totalElements").doesNotExist())
                .andExpect(jsonPath("$.totalPages").doesNotExist());

        verify(postReadService).getFeed(
                null, null, null, null, 20
        );
    }

    @ParameterizedTest
    @CsvSource({
            "type, UNKNOWN",
            "topic, UNKNOWN",
            "afterCreatedAt, not-a-date",
            "afterId, abc",
            "afterId, 9223372036854775808",
            "size, abc",
            "size, 2147483648"
    })
    void 피드의_파라미터_형식이_잘못되면_400이다(
            String parameter,
            String value
    ) throws Exception {

        mockMvc.perform(
                        get(API_PATH + "/feed")
                                .param(parameter, value)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(postReadService);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 51})
    void 피드의_조회개수_검증_예외는_400으로_반환한다(int size)
            throws Exception {

        when(postReadService.getFeed(
                null, null, null, null, size
        )).thenThrow(new PostReadException(
                PostReadException.Code.INVALID_PAGE_REQUEST
        ));

        mockMvc.perform(
                        get(API_PATH + "/feed")
                                .param("size", String.valueOf(size))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_PAGE_REQUEST"));

        verify(postReadService).getFeed(
                null, null, null, null, size
        );
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                    "2026-10-18T02:00:00.123456Z, NULL",
                    "NULL, 104",
                    "2026-10-18T02:00:00.123456Z, 0",
                    "2026-10-18T02:00:00.123456Z, -1"
            },
            nullValues = "NULL"
    )
    void 피드의_커서_검증_예외는_400으로_반환한다(
            String timeText,
            Long afterId
    ) throws Exception {

        Instant time = timeText == null
                ? null
                : Instant.parse(timeText);

        when(postReadService.getFeed(
                null, null, time, afterId, 20
        )).thenThrow(new PostReadException(
                PostReadException.Code.INVALID_CURSOR
        ));

        MockHttpServletRequestBuilder request =
                get(API_PATH + "/feed");

        if (timeText != null) {
            request.param("afterCreatedAt", timeText);
        }

        if (afterId != null) {
            request.param("afterId", afterId.toString());
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));

        verify(postReadService).getFeed(
                null, null, time, afterId, 20
        );
    }

    @Test
    void 피드는_허용된_개발_Origin의_GET_사전요청을_허용한다()
            throws Exception {

        mockMvc.perform(
                        options(API_PATH + "/feed")
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
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("GET")
                ));

        // 사전 요청에서는 게시글을 조회하지 않는다.
        verifyNoInteractions(postReadService);
    }

    @Test
    void 피드는_허용되지_않은_Origin의_요청을_거절한다()
            throws Exception {

        mockMvc.perform(
                        get(API_PATH + "/feed")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://untrusted.example"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(postReadService);
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

    private void givenActiveMember() {
        when(memberRepository.existsByIdAndStatus(
                7L, MemberStatus.ACTIVE)).thenReturn(true);
    }
}
