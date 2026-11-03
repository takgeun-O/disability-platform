package io.github.takgeun.iyum.admin.content.api;

import io.github.takgeun.iyum.admin.content.application.AdminCommentAuthorResult;
import io.github.takgeun.iyum.admin.content.application.AdminCommentItemResult;
import io.github.takgeun.iyum.admin.content.application.AdminCommentListResult;
import io.github.takgeun.iyum.admin.content.application.AdminCommentReadQuery;
import io.github.takgeun.iyum.admin.content.application.AdminCommentReadService;
import io.github.takgeun.iyum.admin.content.application.AdminCommentStatus;
import io.github.takgeun.iyum.admin.content.application.AdminContentReadException;
import io.github.takgeun.iyum.admin.content.application.AdminPostStatus;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AdminCommentReadController.class,
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
class AdminCommentReadControllerTest {

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final String PATH =
            "/api/v1/admin/posts/{postId}/comments";

    private static final Long ADMIN_ID = 7L;
    private static final long CREDENTIAL_VERSION = 3L;
    private static final Long POST_ID = 123L;
    private static final Long COMMENT_ID = 456L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-30T10:00:00Z");

    private static final Instant UPDATED_AT =
            Instant.parse("2026-10-30T10:30:00Z");

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-30T11:00:00Z");

    private static final Instant DELETED_AT =
            Instant.parse("2026-10-30T12:00:00Z");

    private static final String FULL_CONTENT =
            "첫 번째 줄\n\n두 번째 줄\n" + "😀".repeat(201);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminCommentReadService commentReadService;

    @MockitoBean
    private MemberRepository memberRepository;

    @Test
    void 기본_조건과_인증된_관리자_정보로_조회한다() throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminCommentReadQuery expectedQuery =
                new AdminCommentReadQuery(
                        AdminCommentStatus.ALL,
                        null,
                        0,
                        20
                );

        AdminCommentItemResult item = commentItem(
                AdminCommentStatus.VISIBLE,
                false
        );

        when(commentReadService.getComments(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                expectedQuery
        )).thenReturn(result(
                AdminPostStatus.HIDDEN,
                List.of(item),
                0,
                20,
                1
        ));

        expectSuccess(
                mockMvc.perform(
                        request(POST_ID)
                                .with(user(principal(MemberRole.ADMIN)))
                                // 요청자가 보낸 값이 인증 정보를 대체하면 안 된다.
                                .param("memberId", "999")
                                .param("credentialVersion", "999")
                )
        )
                .andExpect(jsonPath("$.postId").value(123))
                .andExpect(jsonPath("$.postStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].commentId").value(456))
                .andExpect(jsonPath("$.items[0].content").value(FULL_CONTENT))
                .andExpect(jsonPath("$.items[0].status").value("VISIBLE"))
                .andExpect(jsonPath("$.items[0].author.memberId").value(8))
                .andExpect(jsonPath("$.items[0].author.nickname")
                        .value("댓글작성회원"))
                .andExpect(jsonPath("$.items[0].author.withdrawn")
                        .value(false))
                .andExpect(jsonPath("$.items[0].author.email")
                        .doesNotExist())
                .andExpect(jsonPath("$.items[0].author.passwordHash")
                        .doesNotExist())
                .andExpect(jsonPath("$.items[0].createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.items[0].updatedAt")
                        .value(UPDATED_AT.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(commentReadService).getComments(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                expectedQuery
        );
        verifyNoMoreInteractions(commentReadService);
    }

    @ParameterizedTest
    @EnumSource(AdminCommentStatus.class)
    void 상태와_검색어와_페이지_조건을_전달한다(
            AdminCommentStatus commentStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminCommentReadQuery expectedQuery =
                new AdminCommentReadQuery(
                        commentStatus,
                        "댓글 검색",
                        1,
                        2
                );

        when(commentReadService.getComments(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                expectedQuery
        )).thenReturn(result(
                AdminPostStatus.VISIBLE,
                List.of(
                        commentItem(AdminCommentStatus.VISIBLE, false)
                ),
                1,
                2,
                5
        ));

        expectSuccess(
                mockMvc.perform(
                        request(POST_ID)
                                .with(user(principal(MemberRole.ADMIN)))
                                .param("status", commentStatus.name())
                                .param("keyword", "  댓글 검색  ")
                                .param("page", "1")
                                .param("size", "2")
                )
        )
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(true));

        verify(commentReadService).getComments(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                expectedQuery
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\u3000"})
    void 공백_검색어는_검색_조건에서_제외한다(
            String keyword
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminCommentReadQuery expectedQuery =
                new AdminCommentReadQuery(
                        AdminCommentStatus.ALL,
                        null,
                        0,
                        20
                );

        when(commentReadService.getComments(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                expectedQuery
        )).thenReturn(result(
                AdminPostStatus.VISIBLE,
                List.of(),
                0,
                20,
                0
        ));

        expectSuccess(
                mockMvc.perform(
                        request(POST_ID)
                                .with(user(principal(MemberRole.ADMIN)))
                                .param("keyword", keyword)
                )
        ).andExpect(jsonPath("$.items").isEmpty());

        verify(commentReadService).getComments(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                expectedQuery
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = AdminCommentStatus.class,
            names = {"HIDDEN", "DELETED"}
    )
    void 숨김과_삭제_댓글의_본문과_상태를_반환한다(
            AdminCommentStatus commentStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        givenResult(result(
                AdminPostStatus.DELETED,
                List.of(commentItem(commentStatus, true)),
                0,
                20,
                1
        ));

        ResultActions response = expectSuccess(
                mockMvc.perform(
                        request(POST_ID)
                                .with(user(principal(MemberRole.ADMIN)))
                )
        )
                .andExpect(jsonPath("$.postStatus").value("DELETED"))
                .andExpect(jsonPath("$.items[0].status")
                        .value(commentStatus.name()))
                .andExpect(jsonPath("$.items[0].content")
                        .value(FULL_CONTENT))
                .andExpect(jsonPath("$.items[0].author.nickname")
                        .value("탈퇴한 회원"))
                .andExpect(jsonPath("$.items[0].author.withdrawn")
                        .value(true))
                .andExpect(jsonPath("$.items[0].hiddenAt")
                        .value(HIDDEN_AT.toString()));

        if (commentStatus == AdminCommentStatus.DELETED) {
            response.andExpect(jsonPath("$.items[0].deletedAt")
                    .value(DELETED_AT.toString()));
        }
    }

    @Test
    void 댓글이_없어도_게시글_상태와_빈_페이지를_반환한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        givenResult(result(
                AdminPostStatus.HIDDEN,
                List.of(),
                0,
                20,
                0
        ));

        expectSuccess(
                mockMvc.perform(
                        request(POST_ID)
                                .with(user(principal(MemberRole.ADMIN)))
                )
        )
                .andExpect(jsonPath("$.postId").value(123))
                .andExpect(jsonPath("$.postStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));
    }

    @Test
    void 범위를_벗어난_페이지도_전체_개수를_유지한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        givenResult(result(
                AdminPostStatus.VISIBLE,
                List.of(),
                5,
                2,
                3
        ));

        expectSuccess(
                mockMvc.perform(
                        request(POST_ID)
                                .with(user(principal(MemberRole.ADMIN)))
                                .param("page", "5")
                                .param("size", "2")
                )
        )
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(5))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));
    }

    @Test
    void 비로그인_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(request(POST_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(commentReadService);
    }

    @Test
    void 일반_회원은_403을_반환한다() throws Exception {
        givenValidSession(MemberRole.USER);

        mockMvc.perform(
                        request(POST_ID)
                                .with(user(principal(MemberRole.USER)))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(commentReadService);
    }

    @Test
    void 현재_회원_정보와_일치하지_않는_인증은_401을_반환한다()
            throws Exception {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ADMIN_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        MemberRole.ADMIN
                )
        ).thenReturn(false);

        mockMvc.perform(
                        request(POST_ID)
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(commentReadService);
    }

    @Test
    void 관리자_권한만_있고_MemberPrincipal이_없으면_401을_반환한다()
            throws Exception {
        mockMvc.perform(
                        request(POST_ID)
                                .with(user("other-principal").roles("ADMIN"))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(commentReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, -1",
            "0, 51",
            "2147483647, 2"
    })
    void 잘못된_페이지_조건은_400을_반환한다(
            String page,
            String size
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        request(POST_ID)
                                .with(user(principal(MemberRole.ADMIN)))
                                .param("page", page)
                                .param("size", size)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_PAGE_REQUEST"));

        verifyNoInteractions(commentReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 검색어가_100코드포인트를_초과하면_400을_반환한다(
            String character
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        request(POST_ID)
                                .with(user(principal(MemberRole.ADMIN)))
                                .param("keyword", character.repeat(101))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_SEARCH_KEYWORD"));

        verifyNoInteractions(commentReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "status, UNKNOWN",
            "page, abc",
            "size, abc",
            "page, 2147483648"
    })
    void 파라미터_변환에_실패하면_400을_반환한다(
            String name,
            String value
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        request(POST_ID)
                                .with(user(principal(MemberRole.ADMIN)))
                                .param(name, value)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(commentReadService);
    }

    @Test
    void 게시글_ID가_숫자가_아니면_400을_반환한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        request("abc")
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(commentReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "AUTHENTICATION_REQUIRED, 401",
            "ADMIN_REQUIRED, 403",
            "INVALID_PAGE_REQUEST, 400",
            "INVALID_SEARCH_KEYWORD, 400",
            "POST_NOT_FOUND, 404"
    })
    void 서비스_예외를_지정된_HTTP_응답으로_변환한다(
            AdminContentReadException.Code code,
            int expectedStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(commentReadService.getComments(
                eq(ADMIN_ID),
                eq(CREDENTIAL_VERSION),
                eq(POST_ID),
                any(AdminCommentReadQuery.class)
        )).thenThrow(new AdminContentReadException(code));

        mockMvc.perform(
                        request(POST_ID)
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ));
    }

    @Test
    void 허용된_출처의_GET_사전_요청을_허용한다()
            throws Exception {
        mockMvc.perform(
                        options(PATH, POST_ID)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "Accept"
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
                        containsString("GET")
                ));

        verifyNoInteractions(memberRepository, commentReadService);
    }

    @Test
    void 댓글_조회_경로의_POST_사전_요청은_거부한다()
            throws Exception {
        mockMvc.perform(
                        options(PATH, POST_ID)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(memberRepository, commentReadService);
    }

    @Test
    void 허용되지_않은_출처의_요청은_거부한다()
            throws Exception {
        // Origin을 중복으로 추가하지 않고 처음부터 다른 출처로 만든다.
        mockMvc.perform(
                        request(
                                POST_ID,
                                "https://untrusted.example"
                        ).with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(memberRepository, commentReadService);
    }

    private void givenValidSession(MemberRole role) {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ADMIN_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        role
                )
        ).thenReturn(true);
    }

    private void givenResult(AdminCommentListResult result) {
        when(commentReadService.getComments(
                eq(ADMIN_ID),
                eq(CREDENTIAL_VERSION),
                eq(POST_ID),
                any(AdminCommentReadQuery.class)
        )).thenReturn(result);
    }

    private MemberPrincipal principal(MemberRole role) {
        return new MemberPrincipal(
                ADMIN_ID,
                "admin-reader@example.com",
                "encoded-password",
                "조회검증회원",
                MemberStatus.ACTIVE,
                role,
                CREDENTIAL_VERSION
        );
    }

    private MockHttpServletRequestBuilder request(Object postId) {
        return request(postId, ORIGIN);
    }

    private MockHttpServletRequestBuilder request(
            Object postId,
            String origin
    ) {
        return get(PATH, postId)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ORIGIN, origin);
    }

    private ResultActions expectSuccess(ResultActions actions)
            throws Exception {
        return actions
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
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
    }

    private AdminCommentItemResult commentItem(
            AdminCommentStatus status,
            boolean withdrawn
    ) {
        Instant hiddenAt =
                status == AdminCommentStatus.HIDDEN
                        || status == AdminCommentStatus.DELETED
                        ? HIDDEN_AT
                        : null;

        Instant deletedAt =
                status == AdminCommentStatus.DELETED
                        ? DELETED_AT
                        : null;

        return new AdminCommentItemResult(
                COMMENT_ID,
                FULL_CONTENT,
                new AdminCommentAuthorResult(
                        8L,
                        withdrawn ? "탈퇴한 회원" : "댓글작성회원",
                        withdrawn
                ),
                status,
                CREATED_AT,
                UPDATED_AT,
                hiddenAt,
                deletedAt
        );
    }

    private AdminCommentListResult result(
            AdminPostStatus postStatus,
            List<AdminCommentItemResult> items,
            int page,
            int size,
            long total
    ) {
        Page<AdminCommentItemResult> comments =
                new PageImpl<>(
                        items,
                        PageRequest.of(page, size),
                        total
                );

        return new AdminCommentListResult(
                POST_ID,
                postStatus,
                comments
        );
    }
}