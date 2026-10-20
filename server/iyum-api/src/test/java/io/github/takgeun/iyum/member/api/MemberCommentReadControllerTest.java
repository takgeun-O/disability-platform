package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.comment.application.CommentCursor;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.application.MemberActivitySummaryService;
import io.github.takgeun.iyum.member.application.MemberCommentItemResult;
import io.github.takgeun.iyum.member.application.MemberCommentListResult;
import io.github.takgeun.iyum.member.application.MemberCommentPostResult;
import io.github.takgeun.iyum.member.application.MemberCommentReadException;
import io.github.takgeun.iyum.member.application.MemberCommentReadService;
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
class MemberCommentReadControllerTest {
    @MockitoBean
    MemberRepository memberRepository;


    private static final String BASE =
            "/api/v1/members/me/comments";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Long MEMBER_ID = 7L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-19T10:00:00.123456Z");

    private static final Instant UPDATED_AT =
            Instant.parse("2026-10-19T11:00:00.654321Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MemberActivitySummaryService memberActivitySummaryService;

    @MockitoBean
    MemberPostReadService memberPostReadService;

    @MockitoBean
    MemberCommentReadService memberCommentReadService;

    @Test
    void 로그인한_회원이_CSRF_없이_기본_목록을_조회한다()
            throws Exception {
        givenActiveMember();
        when(memberCommentReadService.getComments(
                MEMBER_ID, null, null, 10
        )).thenReturn(new MemberCommentListResult(
                List.of(item(104L)),
                false,
                null
        ));

        mockMvc.perform(get(BASE)
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
                .andExpect(jsonPath("$.items[0].commentId").value(104))
                .andExpect(jsonPath("$.items[0].content")
                        .value("  제가 작성한 댓글입니다.\n두 번째 줄입니다.  "))
                .andExpect(jsonPath("$.items[0].createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.items[0].updatedAt")
                        .value(UPDATED_AT.toString()))
                .andExpect(jsonPath("$.items[0].post.postId").value(31))
                .andExpect(jsonPath("$.items[0].post.title")
                        .value("보청기 적응 질문"))
                .andExpect(jsonPath("$.items[0].post.type")
                        .value("QUESTION"))
                .andExpect(jsonPath("$.items[0].post.topic")
                        .value("HEARING_AID"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        verify(memberCommentReadService).getComments(
                MEMBER_ID, null, null, 10
        );
        verifyNoInteractions(
                memberActivitySummaryService,
                memberPostReadService
        );
    }

    @Test
    void 전달받은_커서와_size를_서비스로_전달하고_다음_커서를_반환한다()
            throws Exception {
        givenActiveMember();
        Instant requestedCreatedAt =
                Instant.parse("2026-10-19T12:00:00.987654Z");

        when(memberCommentReadService.getComments(
                MEMBER_ID, requestedCreatedAt, 110L, 2
        )).thenReturn(new MemberCommentListResult(
                List.of(item(104L), item(103L)),
                true,
                new CommentCursor(CREATED_AT, 103L)
        ));

        mockMvc.perform(get(BASE)
                        .with(user(principal()))
                        .param("afterCreatedAt", requestedCreatedAt.toString())
                        .param("afterId", "110")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].commentId").value(104))
                .andExpect(jsonPath("$.items[1].commentId").value(103))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor.createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.nextCursor.commentId").value(103));

        verify(memberCommentReadService).getComments(
                MEMBER_ID, requestedCreatedAt, 110L, 2
        );
    }

    @Test
    void 댓글이_없으면_빈_목록을_반환한다()
            throws Exception {
        givenActiveMember();
        when(memberCommentReadService.getComments(
                MEMBER_ID, null, null, 10
        )).thenReturn(emptyResult());

        mockMvc.perform(get(BASE)
                        .with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        verify(memberCommentReadService).getComments(
                MEMBER_ID, null, null, 10
        );
    }

    @Test
    void 비로그인_요청은_401을_반환한다()
            throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(memberCommentReadService);
    }

    @Test
    void memberId를_임의로_전달해도_인증된_회원의_ID를_사용한다()
            throws Exception {
        givenActiveMember();
        when(memberCommentReadService.getComments(
                MEMBER_ID, null, null, 10
        )).thenReturn(emptyResult());

        mockMvc.perform(get(BASE)
                        .with(user(principal()))
                        .param("memberId", "999"))
                .andExpect(status().isOk());

        verify(memberCommentReadService).getComments(
                MEMBER_ID, null, null, 10
        );
    }

    @ParameterizedTest
    @CsvSource({
            "afterCreatedAt, not-a-date",
            "afterId, not-a-number",
            "size, not-a-number"
    })
    void 요청_파라미터의_타입이_잘못되면_400을_반환한다(
            String parameter,
            String value
    ) throws Exception {
        givenActiveMember();
        mockMvc.perform(get(BASE)
                        .with(user(principal()))
                        .param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(memberCommentReadService);
    }

    @Test
    void 커서_ID만_전달하면_서비스의_커서_오류를_400으로_반환한다()
            throws Exception {
        givenActiveMember();
        MemberCommentReadException exception =
                new MemberCommentReadException(
                        MemberCommentReadException.Code.INVALID_CURSOR
                );

        when(memberCommentReadService.getComments(
                MEMBER_ID, null, 104L, 10
        )).thenThrow(exception);

        mockMvc.perform(get(BASE)
                        .with(user(principal()))
                        .param("afterId", "104"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
                .andExpect(jsonPath("$.message")
                        .value(exception.getMessage()))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verify(memberCommentReadService).getComments(
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
    void 서비스_예외를_정해진_HTTP_응답으로_변환한다(
            String exceptionCode,
            int expectedStatus,
            String responseCode
    ) throws Exception {
        givenActiveMember();
        MemberCommentReadException exception =
                new MemberCommentReadException(
                        MemberCommentReadException.Code.valueOf(exceptionCode)
                );

        when(memberCommentReadService.getComments(
                MEMBER_ID, null, null, 10
        )).thenThrow(exception);

        String expectedMessage =
                "MEMBER_NOT_FOUND".equals(exceptionCode)
                        ? "로그인이 필요합니다."
                        : exception.getMessage();

        mockMvc.perform(get(BASE)
                        .with(user(principal())))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.code").value(responseCode))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(0));

        verify(memberCommentReadService).getComments(
                MEMBER_ID, null, null, 10
        );
    }

    @Test
    void 허용된_출처의_GET_사전요청은_로그인_없이_성공한다()
            throws Exception {
        mockMvc.perform(options(BASE)
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
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_MAX_AGE,
                        "600"
                ));

        verifyNoInteractions(memberCommentReadService);
    }

    @Test
    void 허용되지_않은_출처의_요청은_거부한다()
            throws Exception {
        mockMvc.perform(get(BASE)
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
        verifyNoInteractions(memberCommentReadService);
    }

    @Test
    void 내_댓글_조회_경로의_PUT_사전요청은_거부한다()
            throws Exception {
        mockMvc.perform(options(BASE)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                "PUT"
                        ))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(memberCommentReadService);
    }

    private static MemberPrincipal principal() {
        return new MemberPrincipal(
                MEMBER_ID,
                "member@example.com",
                "{noop}unused-password",
                "테스트회원",
                MemberStatus.ACTIVE,
                MemberRole.USER
        );
    }

    private static MemberCommentItemResult item(Long commentId) {
        return new MemberCommentItemResult(
                commentId,
                "  제가 작성한 댓글입니다.\n두 번째 줄입니다.  ",
                CREATED_AT,
                UPDATED_AT,
                new MemberCommentPostResult(
                        31L,
                        "보청기 적응 질문",
                        PostType.QUESTION,
                        PostTopic.HEARING_AID
                )
        );
    }

    private static MemberCommentListResult emptyResult() {
        return new MemberCommentListResult(
                List.of(),
                false,
                null
        );
    }

    private void givenActiveMember() {
        when(memberRepository.existsByIdAndStatus(
                MEMBER_ID, MemberStatus.ACTIVE)).thenReturn(true);
    }
}
