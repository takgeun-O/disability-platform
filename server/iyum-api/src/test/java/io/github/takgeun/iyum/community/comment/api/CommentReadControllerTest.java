package io.github.takgeun.iyum.community.comment.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.comment.application.*;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = CommentController.class,
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
class CommentReadControllerTest {

    private static final String API_PATH =
            "/api/v1/community/posts/123/comments";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final String TIME_TEXT =
            "2026-10-28T03:00:00.123456Z";

    private static final Instant TIME =
            Instant.parse(TIME_TEXT);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CommentReadService commentReadService;

    @MockitoBean
    CommentCreateService commentCreateService;

    @MockitoBean
    CommentUpdateService commentUpdateService;

    @MockitoBean
    CommentDeleteService commentDeleteService;

    @MockitoBean
    MemberRepository memberRepository;

    @Test
    void 비로그인_기본_요청은_등록순_첫_조회로_전달한다()
            throws Exception {
        CommentReadQuery expected = new CommentReadQuery(
                CommentSort.OLDEST,
                null,
                20
        );

        when(commentReadService.getComments(
                123L, expected, null
        )).thenReturn(emptyResult());

        mockMvc.perform(
                        get(API_PATH)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
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

        verify(commentReadService).getComments(
                123L, expected, null
        );
        verifyNoMoreInteractions(commentReadService);
    }

    @ParameterizedTest
    @EnumSource(CommentSort.class)
    void 정렬을_전달하고_정렬_커서를_직렬화한다(
            CommentSort sort
    ) throws Exception {
        CommentReadQuery expected = new CommentReadQuery(
                sort,
                null,
                2
        );

        Long cursorLikeCount =
                sort == CommentSort.POPULAR ? 5L : null;

        CommentCursor cursor = new CommentCursor(
                TIME,
                100L,
                sort,
                cursorLikeCount
        );

        CommentListResult result = new CommentListResult(
                List.of(commentResult(false)),
                true,
                cursor
        );

        when(commentReadService.getComments(
                123L, expected, null
        )).thenReturn(result);

        var response = mockMvc.perform(
                        get(API_PATH)
                                .param("sort", sort.name())
                                .param("size", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].commentId").value(100))
                .andExpect(jsonPath("$.items[0].likeCount").value(5))
                .andExpect(jsonPath("$.items[0].replyCount").value(2))
                .andExpect(jsonPath("$.items[0].liked").value(false))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor.createdAt")
                        .value(TIME_TEXT))
                .andExpect(jsonPath("$.nextCursor.commentId")
                        .value(100))
                .andExpect(jsonPath("$.nextCursor.sort")
                        .value(sort.name()));

        if (sort == CommentSort.POPULAR) {
            response.andExpect(
                    jsonPath("$.nextCursor.likeCount").value(5)
            );
        } else {
            response.andExpect(
                    jsonPath("$.nextCursor.likeCount").value(nullValue())
            );
        }

        verify(commentReadService).getComments(
                123L, expected, null
        );
        verifyNoMoreInteractions(commentReadService);
    }

    @Test
    void 공개_답글이_남은_삭제_원댓글은_답글_수와_명시적_null을_반환한다()
            throws Exception {
        CommentResult deleted = new CommentResult(
                101L, "삭제된 댓글입니다.", null, TIME, TIME,
                null, 0L, false, true, 3L
        );
        CommentReadQuery query = new CommentReadQuery(
                CommentSort.OLDEST, null, 20
        );
        when(commentReadService.getComments(123L, query, null))
                .thenReturn(new CommentListResult(List.of(deleted), false, null));

        mockMvc.perform(get(API_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].commentId").value(101))
                .andExpect(jsonPath("$.items[0].content").value("삭제된 댓글입니다."))
                .andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].replyCount").value(3))
                .andExpect(jsonPath("$.items[0].likeCount").value(0))
                .andExpect(jsonPath("$.items[0].liked").value(false))
                .andExpect(jsonPath("$.items[0].author").hasJsonPath())
                .andExpect(jsonPath("$.items[0].author").value(nullValue()))
                .andExpect(jsonPath("$.items[0].contentUpdatedAt").hasJsonPath())
                .andExpect(jsonPath("$.items[0].contentUpdatedAt").value(nullValue()));
    }

    @Test
    void 기존_등록순_커서는_afterSort_없이도_허용한다()
            throws Exception {
        CommentReadQuery expected = new CommentReadQuery(
                CommentSort.OLDEST,
                new CommentCursor(TIME, 100L),
                20
        );

        when(commentReadService.getComments(
                123L, expected, null
        )).thenReturn(emptyResult());

        mockMvc.perform(
                        get(API_PATH)
                                .param("afterCreatedAt", TIME_TEXT)
                                .param("afterId", "100")
                )
                .andExpect(status().isOk());

        verify(commentReadService).getComments(
                123L, expected, null
        );
        verifyNoMoreInteractions(commentReadService);
    }

    @ParameterizedTest
    @EnumSource(CommentSort.class)
    void 각_정렬의_후속_커서를_정확히_전달한다(
            CommentSort sort
    ) throws Exception {
        Long likeCount =
                sort == CommentSort.POPULAR ? 0L : null;

        CommentReadQuery expected = new CommentReadQuery(
                sort,
                new CommentCursor(
                        TIME,
                        100L,
                        sort,
                        likeCount
                ),
                2
        );

        when(commentReadService.getComments(
                123L, expected, null
        )).thenReturn(emptyResult());

        MockHttpServletRequestBuilder request = get(API_PATH)
                .param("sort", sort.name())
                .param("size", "2")
                .param("afterCreatedAt", TIME_TEXT)
                .param("afterId", "100")
                .param("afterSort", sort.name());

        if (sort == CommentSort.POPULAR) {
            request.param("afterLikeCount", "0");
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        verify(commentReadService).getComments(
                123L, expected, null
        );
        verifyNoMoreInteractions(commentReadService);
    }

    @Test
    void 조회_회원_ID는_요청값이_아닌_인증정보에서_결정한다()
            throws Exception {
        givenActiveMember();

        CommentReadQuery expected = new CommentReadQuery(
                CommentSort.POPULAR,
                null,
                20
        );

        when(commentReadService.getComments(
                123L, expected, 7L
        )).thenReturn(
                new CommentListResult(
                        List.of(commentResult(true)),
                        false,
                        null
                )
        );

        mockMvc.perform(
                        get(API_PATH)
                                .with(user(principal()))
                                .param("sort", "POPULAR")
                                .param("memberId", "999")
                                .param("viewerId", "999")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].liked").value(true))
                .andExpect(jsonPath("$.items[0].likeCount").value(5));

        verify(commentReadService).getComments(
                123L, expected, 7L
        );
        verifyNoMoreInteractions(commentReadService);
    }

    @ParameterizedTest
    @MethodSource("invalidCursors")
    void 잘못된_커서_조합은_서비스_호출_전에_거부한다(
            String sort,
            String createdAt,
            String id,
            String afterSort,
            String likeCount
    ) throws Exception {
        MockHttpServletRequestBuilder request = get(API_PATH)
                .param("sort", sort);

        if (createdAt != null) {
            request.param("afterCreatedAt", createdAt);
        }
        if (id != null) {
            request.param("afterId", id);
        }
        if (afterSort != null) {
            request.param("afterSort", afterSort);
        }
        if (likeCount != null) {
            request.param("afterLikeCount", likeCount);
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));

        verifyNoInteractions(commentReadService);
    }

    static Stream<Arguments> invalidCursors() {
        return Stream.of(
                // 시각 또는 ID 누락.
                Arguments.of("OLDEST", TIME_TEXT, null, null, null),
                Arguments.of("OLDEST", null, "100", null, null),

                // 유효하지 않은 ID.
                Arguments.of("OLDEST", TIME_TEXT, "0", null, null),
                Arguments.of("OLDEST", TIME_TEXT, "-1", null, null),

                // 최신순·인기순 후속 조회의 afterSort 누락.
                Arguments.of("LATEST", TIME_TEXT, "100", null, null),
                Arguments.of("POPULAR", TIME_TEXT, "100", null, "5"),

                // 요청 정렬과 커서 정렬 불일치.
                Arguments.of(
                        "LATEST", TIME_TEXT, "100", "OLDEST", null
                ),
                Arguments.of(
                        "POPULAR", TIME_TEXT, "100", "LATEST", "5"
                ),

                // 인기순 좋아요 수 누락 또는 음수.
                Arguments.of(
                        "POPULAR", TIME_TEXT, "100", "POPULAR", null
                ),
                Arguments.of(
                        "POPULAR", TIME_TEXT, "100", "POPULAR", "-1"
                ),

                // 시간순 커서에는 좋아요 수를 넣지 않는다.
                Arguments.of(
                        "OLDEST", TIME_TEXT, "100", "OLDEST", "0"
                ),
                Arguments.of(
                        "LATEST", TIME_TEXT, "100", "LATEST", "0"
                ),

                // 커서 일부만 보낸 요청을 첫 조회로 취급하면 안 된다.
                Arguments.of("OLDEST", null, null, "OLDEST", null),
                Arguments.of("POPULAR", null, null, null, "0")
        );
    }

    @ParameterizedTest
    @CsvSource({
            "sort, UNKNOWN",
            "afterSort, UNKNOWN",
            "afterCreatedAt, invalid-date",
            "afterId, abc",
            "afterLikeCount, abc",
            "size, abc"
    })
    void 파라미터_형식_오류는_400으로_처리한다(
            String parameter,
            String value
    ) throws Exception {
        mockMvc.perform(
                        get(API_PATH).param(parameter, value)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(commentReadService);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 51})
    void 조회_개수_범위_오류는_서비스_호출_전에_거부한다(
            int size
    ) throws Exception {
        mockMvc.perform(
                        get(API_PATH)
                                .param("size", Integer.toString(size))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_PAGE_REQUEST"));

        verifyNoInteractions(commentReadService);
    }

    @ParameterizedTest
    @EnumSource(CommentSort.class)
    void 게시글_없음_예외는_모든_정렬에서_404로_변환한다(
            CommentSort sort
    ) throws Exception {
        CommentReadQuery expected = new CommentReadQuery(
                sort,
                null,
                20
        );

        when(commentReadService.getComments(
                123L, expected, null
        )).thenThrow(
                new CommentReadException(
                        CommentReadException.Code.POST_NOT_FOUND
                )
        );

        mockMvc.perform(
                        get(API_PATH)
                                .param("sort", sort.name())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));

        verify(commentReadService).getComments(
                123L, expected, null
        );
        verifyNoMoreInteractions(commentReadService);
    }

    // 서비스 결과가 올바르더라도 응답 DTO에서 필드를 빠뜨릴 수 있으므로
    // HTTP 응답도 확인
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 본문_수정_시각의_null과_값을_JSON으로_반환한다(
            boolean edited
    ) throws Exception {
        Instant entityUpdatedAt = TIME.plusSeconds(120);

        Instant contentUpdatedAt =
                edited ? TIME.plusSeconds(60) : null;

        CommentReadQuery expected = new CommentReadQuery(
                CommentSort.OLDEST,
                null,
                20
        );

        CommentResult item = new CommentResult(
                100L,
                "조회 테스트 댓글",
                new CommentAuthorResult(7L, "조회회원"),
                TIME,
                entityUpdatedAt,
                contentUpdatedAt,
                5L,
                false,
                false,
                0L
        );

        when(commentReadService.getComments(
                123L,
                expected,
                null
        )).thenReturn(
                new CommentListResult(
                        List.of(item),
                        false,
                        null
                )
        );

        var response = mockMvc.perform(
                        get(API_PATH)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].commentId")
                        .value(100))
                .andExpect(jsonPath("$.items[0].createdAt")
                        .value(TIME_TEXT))
                .andExpect(jsonPath("$.items[0].updatedAt")
                        .value(entityUpdatedAt.toString()))
                // null이어도 필드 자체는 응답에 포함되어야 한다.
                .andExpect(jsonPath("$.items[0].contentUpdatedAt")
                        .hasJsonPath());

        if (edited) {
            response.andExpect(
                    jsonPath("$.items[0].contentUpdatedAt")
                            .value(contentUpdatedAt.toString())
            );
        } else {
            response.andExpect(
                    jsonPath("$.items[0].contentUpdatedAt")
                            .value(nullValue())
            );
        }

        verify(commentReadService).getComments(
                123L,
                expected,
                null
        );

        verifyNoMoreInteractions(commentReadService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 베스트_댓글은_로그인_상태에_맞는_조회자로_호출하고_서비스_순서대로_반환한다(
            boolean loggedIn
    ) throws Exception {
        Long viewerId = loggedIn ? 7L : null;

        MockHttpServletRequestBuilder request = get(API_PATH + "/best");

        if (loggedIn) {
            givenActiveMember();
            request.with(user(principal()));
        }

        CommentAuthorResult author = new CommentAuthorResult(8L, "댓글작성자");

        CommentResult first = new CommentResult(
                120L,
                "상담 전에 질문을 메모해 갔어요.",
                author,
                TIME,
                TIME,
                null,
                18L,
                loggedIn,
                false,
                3L
        );

        CommentResult second = new CommentResult(
                101L,
                "저도 미리 적어 두니 도움이 됐어요.",
                author,
                TIME,
                TIME,
                null,
                10L,
                false,
                false,
                0L
        );

        when(commentReadService.getBestComments(123L, viewerId))
                .thenReturn(List.of(
                        new BestCommentResult(first, null),
                        new BestCommentResult(second, null)
                ));

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].commentId").value(120))
                .andExpect(jsonPath("$.items[0].likeCount").value(18))
                .andExpect(jsonPath("$.items[0].replyCount").value(3))
                .andExpect(jsonPath("$.items[0].liked").value(loggedIn))
                .andExpect(jsonPath("$.items[1].commentId").value(101))
                .andExpect(jsonPath("$.items[1].likeCount").value(10))
                .andExpect(jsonPath("$.items[1].replyCount").value(0))
                .andExpect(jsonPath("$.items[1].liked").value(false));

        verify(commentReadService).getBestComments(123L, viewerId);
    }

    @Test
    void 베스트_댓글_후보가_없으면_빈_items를_반환한다() throws Exception {
        when(commentReadService.getBestComments(123L, null))
                .thenReturn(List.of());

        mockMvc.perform(get(API_PATH + "/best"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());

        verify(commentReadService).getBestComments(123L, null);
    }

    @Test
    void 조회할_수_없는_게시글의_베스트_댓글은_404를_반환한다() throws Exception {
        when(commentReadService.getBestComments(123L, null))
                .thenThrow(new CommentReadException(
                        CommentReadException.Code.POST_NOT_FOUND
                ));

        mockMvc.perform(get(API_PATH + "/best"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    private CommentListResult emptyResult() {
        return new CommentListResult(
                List.of(),
                false,
                null
        );
    }

    private CommentResult commentResult(boolean liked) {
        return new CommentResult(
                100L,
                "조회 테스트 댓글",
                new CommentAuthorResult(7L, "조회회원"),
                TIME,
                TIME,
                null,
                5L,
                liked,
                false,
                2L
        );
    }

    private MemberPrincipal principal() {
        return new MemberPrincipal(
                7L,
                "member@example.com",
                "test-only-password-hash",
                "조회회원",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                0L
        );
    }

    private void givenActiveMember() {
        when(memberRepository.existsByIdAndStatusAndCredentialVersionAndRole(
                7L,
                MemberStatus.ACTIVE,
                0L,
                MemberRole.USER
        )).thenReturn(true);
    }
}