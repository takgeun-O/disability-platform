package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.post.application.PostAuthorResult;
import io.github.takgeun.iyum.community.post.application.PostCursor;
import io.github.takgeun.iyum.community.post.application.PostFeedItemResult;
import io.github.takgeun.iyum.community.post.application.PostFeedResult;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.application.MemberActivitySummaryService;
import io.github.takgeun.iyum.member.application.MemberCommentReadService;
import io.github.takgeun.iyum.member.application.MemberPostReadException;
import io.github.takgeun.iyum.member.application.MemberPostReadService;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
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
class MemberPostReadControllerTest {
    @MockitoBean
    MemberRepository memberRepository;


    private static final String URL =
            "/api/v1/members/me/posts";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Long MEMBER_ID = 7L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-19T10:00:00.123456Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MemberPostReadService memberPostReadService;

    // 같은 컨트롤러의 활동 요약 API가 사용하는 의존성도 등록한다.
    @MockitoBean
    MemberActivitySummaryService memberActivitySummaryService;

    @MockitoBean
    MemberCommentReadService memberCommentReadService;

    @Test
    void 기본_요청은_CSRF_없이_열_개를_조회하고_게시글_응답을_반환한다()
            throws Exception {
        givenActiveMember();
        when(memberPostReadService.getPosts(
                MEMBER_ID, null, null, 10
        )).thenReturn(new PostFeedResult(
                List.of(item(104L)),
                false,
                null
        ));

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
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(104))
                .andExpect(jsonPath("$.items[0].title")
                        .value("내 게시글"))
                .andExpect(jsonPath("$.items[0].contentPreview")
                        .value("게시글 본문 미리보기"))
                .andExpect(jsonPath("$.items[0].type")
                        .value("QUESTION"))
                .andExpect(jsonPath("$.items[0].topic")
                        .value("DAILY"))
                .andExpect(jsonPath("$.items[0].createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.items[0].author.memberId")
                        .value(7))
                .andExpect(jsonPath("$.items[0].author.nickname")
                        .value("내게시글회원"))
                .andExpect(jsonPath("$.items[0].commentCount")
                        .value(3))
                .andExpect(jsonPath("$.items[0].author.email")
                        .doesNotExist())
                .andExpect(jsonPath("$.items[0].author.passwordHash")
                        .doesNotExist())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        verify(memberPostReadService).getPosts(
                MEMBER_ID, null, null, 10
        );
        verifyNoInteractions(memberActivitySummaryService);
    }

    @Test
    void 커서와_조회_개수를_서비스에_전달하고_다음_커서를_응답한다()
            throws Exception {
        givenActiveMember();
        when(memberPostReadService.getPosts(
                MEMBER_ID, CREATED_AT, 104L, 2
        )).thenReturn(new PostFeedResult(
                List.of(item(103L), item(102L)),
                true,
                new PostCursor(CREATED_AT, 102L)
        ));

        mockMvc.perform(get(URL)
                        .with(user(principal()))
                        .param("afterCreatedAt", CREATED_AT.toString())
                        .param("afterId", "104")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(103))
                .andExpect(jsonPath("$.items[1].id").value(102))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor.createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.nextCursor.postId").value(102));

        verify(memberPostReadService).getPosts(
                MEMBER_ID, CREATED_AT, 104L, 2
        );
    }

    @Test
    void 게시글이_없으면_빈_목록과_null_커서를_반환한다()
            throws Exception {
        givenActiveMember();
        when(memberPostReadService.getPosts(
                MEMBER_ID, null, null, 10
        )).thenReturn(new PostFeedResult(
                List.of(),
                false,
                null
        ));

        mockMvc.perform(get(URL)
                        .with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        verify(memberPostReadService).getPosts(
                MEMBER_ID, null, null, 10
        );
    }

    @Test
    void 비로그인_요청은_거절한다()
            throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verifyNoInteractions(
                memberPostReadService,
                memberActivitySummaryService
        );
    }

    @Test
    void 다른_회원_ID를_파라미터로_보내도_로그인_회원_ID를_사용한다()
            throws Exception {
        givenActiveMember();
        when(memberPostReadService.getPosts(
                MEMBER_ID, null, null, 10
        )).thenReturn(new PostFeedResult(
                List.of(),
                false,
                null
        ));

        mockMvc.perform(get(URL)
                        .with(user(principal()))
                        .param("memberId", "999"))
                .andExpect(status().isOk());

        verify(memberPostReadService).getPosts(
                MEMBER_ID, null, null, 10
        );
        verifyNoMoreInteractions(memberPostReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "size, abc",
            "afterId, abc",
            "afterCreatedAt, not-a-date"
    })
    void 파라미터_타입이_잘못되면_서비스_호출_전에_거절한다(
            String parameter,
            String value
    ) throws Exception {
        givenActiveMember();
        mockMvc.perform(get(URL)
                        .with(user(principal()))
                        .param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(memberPostReadService);
    }

    @Test
    void 커서_ID만_전달하면_서비스의_커서_검증_오류를_반환한다()
            throws Exception {
        givenActiveMember();
        when(memberPostReadService.getPosts(
                MEMBER_ID, null, 104L, 10
        )).thenThrow(new MemberPostReadException(
                MemberPostReadException.Code.INVALID_CURSOR
        ));

        mockMvc.perform(get(URL)
                        .with(user(principal()))
                        .param("afterId", "104"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CURSOR"));

        verify(memberPostReadService).getPosts(
                MEMBER_ID, null, 104L, 10
        );
    }

    @ParameterizedTest
    @CsvSource({
            "MEMBER_NOT_FOUND, 401, AUTHENTICATION_REQUIRED",
            "MEMBER_NOT_ACTIVE, 403, MEMBER_NOT_ACTIVE",
            "INVALID_PAGE_REQUEST, 400, INVALID_PAGE_REQUEST",
            "INVALID_CURSOR, 400, INVALID_CURSOR"
    })
    void 서비스_예외를_HTTP_오류_응답으로_변환한다(
            MemberPostReadException.Code exceptionCode,
            int expectedStatus,
            String expectedCode
    ) throws Exception {
        givenActiveMember();
        when(memberPostReadService.getPosts(
                MEMBER_ID, null, null, 10
        )).thenThrow(new MemberPostReadException(exceptionCode));

        mockMvc.perform(get(URL)
                        .with(user(principal())))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verify(memberPostReadService).getPosts(
                MEMBER_ID, null, null, 10
        );
    }

    @Test
    void 허용된_출처의_GET_사전_요청은_로그인_없이_허용한다()
            throws Exception {
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
                ));

        verifyNoInteractions(memberPostReadService);
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

        verifyNoInteractions(memberRepository);
        verifyNoInteractions(memberPostReadService);
    }

    @Test
    void PUT_메서드의_CORS_사전_요청은_거절한다()
            throws Exception {

        // 이 출처에서 해당 URL로 PUT 요청을 보내도 되나요?
        // DevCorsConfig에서 이 URL의 허용 메서드는 GET과 OPTION임.
        // PUT이 없으므로 사전 요청을 거절한다. -> status().isForbidden()
        // 그러면 해당 출처를 허용한다는 응답 헤더도 없어야 하고
        // 브라우저는 이 결과를 보고 실제 PUT 요청을 보내지 않는다.
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

        verifyNoInteractions(memberPostReadService);
    }

    private MemberPrincipal principal() {
        return new MemberPrincipal(
                MEMBER_ID,
                "my-posts@example.com",
                "test-only-password-hash",
                "내게시글회원",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                0L
        );
    }

    private PostFeedItemResult item(Long postId) {
        return new PostFeedItemResult(
                postId,
                PostType.QUESTION,
                PostTopic.DAILY,
                "내 게시글",
                "게시글 본문 미리보기",
                new PostAuthorResult(
                        MEMBER_ID,
                        "내게시글회원"
                ),
                CREATED_AT,
                3L
        );
    }

    private void givenActiveMember() {
        when(memberRepository.existsByIdAndStatusAndCredentialVersion(
                MEMBER_ID, MemberStatus.ACTIVE, 0L)).thenReturn(true);
    }
}
