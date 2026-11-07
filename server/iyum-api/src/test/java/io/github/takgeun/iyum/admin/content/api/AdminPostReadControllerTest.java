package io.github.takgeun.iyum.admin.content.api;

import io.github.takgeun.iyum.admin.content.application.AdminContentReadException;
import io.github.takgeun.iyum.admin.content.application.AdminPostAuthorResult;
import io.github.takgeun.iyum.admin.content.application.AdminPostDetailResult;
import io.github.takgeun.iyum.admin.content.application.AdminPostItemResult;
import io.github.takgeun.iyum.admin.content.application.AdminPostReadQuery;
import io.github.takgeun.iyum.admin.content.application.AdminPostReadService;
import io.github.takgeun.iyum.admin.content.application.AdminPostStatus;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.global.config.DevCorsConfig;
import io.github.takgeun.iyum.global.config.SecurityConfig;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.Set;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminPostReadController.class,
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
class AdminPostReadControllerTest {

    private static final String LIST_PATH =
            "/api/v1/admin/posts";

    private static final String DETAIL_PATH =
            "/api/v1/admin/posts/123";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Long MEMBER_ID = 7L;
    private static final long CREDENTIAL_VERSION = 3L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-30T10:00:00Z");

    private static final Instant UPDATED_AT =
            Instant.parse("2026-10-30T10:30:00Z");

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-30T11:00:00Z");

    private static final Instant DELETED_AT =
            Instant.parse("2026-10-30T12:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminPostReadService adminPostReadService;

    @MockitoBean
    MemberRepository memberRepository;


    @ParameterizedTest
    @MethodSource("multipleStatusSelections")
    void 다중_상태와_정규화한_검색어를_정확히_전달한다(
            String[] statuses, Set<AdminPostStatus> expectedStatuses
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);
        AdminPostReadQuery expectedQuery = AdminPostReadQuery.ofStatuses(
                expectedStatuses, "보청기 적응", 2, 10
        );
        when(adminPostReadService.getPosts(MEMBER_ID, CREDENTIAL_VERSION, expectedQuery)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 10), 15));

        // 반복 쿼리 파라미터를 실제 컨트롤러에서 해석한다. GET에는 CSRF가 필요 없다.
        expectSuccess(mockMvc.perform(
                request(LIST_PATH)
                        .with(user(principal(MemberRole.ADMIN)))
                        .queryParam("statuses", statuses)
                        .queryParam("keyword", "  보청기 적응  ")
                        .queryParam("page", "2")
                        .queryParam("size", "10")
        ))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));

        verify(adminPostReadService).getPosts(MEMBER_ID, CREDENTIAL_VERSION, expectedQuery);
        verifyNoMoreInteractions(adminPostReadService);
    }

    static Stream<Arguments> multipleStatusSelections() {
        return Stream.of(
                Arguments.of(new String[]{"VISIBLE"}, Set.of(AdminPostStatus.VISIBLE)),
                Arguments.of(new String[]{"HIDDEN"}, Set.of(AdminPostStatus.HIDDEN)),
                Arguments.of(new String[]{"DELETED"}, Set.of(AdminPostStatus.DELETED)),
                Arguments.of(new String[]{"VISIBLE", "HIDDEN"}, Set.of(AdminPostStatus.VISIBLE, AdminPostStatus.HIDDEN)),
                Arguments.of(new String[]{"VISIBLE", "DELETED"}, Set.of(AdminPostStatus.VISIBLE, AdminPostStatus.DELETED)),
                Arguments.of(new String[]{"HIDDEN", "DELETED"}, Set.of(AdminPostStatus.HIDDEN, AdminPostStatus.DELETED)),
                Arguments.of(new String[]{"VISIBLE", "HIDDEN", "DELETED"}, Set.of(AdminPostStatus.VISIBLE, AdminPostStatus.HIDDEN, AdminPostStatus.DELETED)),
                Arguments.of(new String[]{"HIDDEN", "VISIBLE", "HIDDEN"}, Set.of(AdminPostStatus.HIDDEN, AdminPostStatus.VISIBLE))
        );
    }

    @Test
    void 빈_단일_상태는_전체_조회와_호환된다() throws Exception {
        givenValidSession(MemberRole.ADMIN);
        AdminPostReadQuery expectedQuery = AdminPostReadQuery.ofStatuses(
                Set.of(AdminPostStatus.VISIBLE, AdminPostStatus.HIDDEN, AdminPostStatus.DELETED),
                "보청기 적응", 2, 10
        );
        when(adminPostReadService.getPosts(MEMBER_ID, CREDENTIAL_VERSION, expectedQuery)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 10), 15));

        expectSuccess(mockMvc.perform(
                request(LIST_PATH)
                        .with(user(principal(MemberRole.ADMIN)))
                        .queryParam("status", "")
                        .queryParam("keyword", "  보청기 적응  ")
                        .queryParam("page", "2")
                        .queryParam("size", "10")
        ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));
        verify(adminPostReadService).getPosts(MEMBER_ID, CREDENTIAL_VERSION, expectedQuery);
        verifyNoMoreInteractions(adminPostReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "ALL", "hidden", "UNKNOWN", " HIDDEN ", "VISIBLE,HIDDEN"})
    void 잘못된_다중_상태를_거부한다(String value) throws Exception {
        givenValidSession(MemberRole.ADMIN);
        mockMvc.perform(
                request(LIST_PATH)
                        .with(user(principal(MemberRole.ADMIN)))
                        .queryParam("statuses", value)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
        verifyNoInteractions(adminPostReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "UNKNOWN", "ALL"})
    void 유효한_다중_상태에_잘못된_값이_섞이면_거부한다(String value) throws Exception {
        givenValidSession(MemberRole.ADMIN);
        mockMvc.perform(
                request(LIST_PATH)
                        .with(user(principal(MemberRole.ADMIN)))
                        .queryParam("statuses", "VISIBLE", value)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
        verifyNoInteractions(adminPostReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ALL", "HIDDEN"})
    void 단일과_다중_상태를_함께_보내면_거부한다(String value) throws Exception {
        givenValidSession(MemberRole.ADMIN);
        mockMvc.perform(
                request(LIST_PATH)
                        .with(user(principal(MemberRole.ADMIN)))
                        .queryParam("status", value)
                        .queryParam("statuses", "VISIBLE", "HIDDEN")
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
        verifyNoInteractions(adminPostReadService);
    }

    @Test
    void 단일_상태를_반복하면_거부한다() throws Exception {
        givenValidSession(MemberRole.ADMIN);
        mockMvc.perform(
                request(LIST_PATH)
                        .with(user(principal(MemberRole.ADMIN)))
                        .queryParam("status", "VISIBLE", "HIDDEN")
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
        verifyNoInteractions(adminPostReadService);
    }

    @Test
    void 기본_조건으로_목록을_조회하고_인증된_회원_정보를_사용한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminPostReadQuery query = defaultQuery();

        AdminPostItemResult item = new AdminPostItemResult(
                123L,
                PostType.GENERAL,
                PostTopic.DAILY,
                "관리자 확인용 제목",
                "게시글 미리보기",
                new AdminPostAuthorResult(
                        8L,
                        "작성회원",
                        false
                ),
                AdminPostStatus.HIDDEN,
                CREATED_AT,
                UPDATED_AT,
                HIDDEN_AT,
                null
        );

        when(adminPostReadService.getPosts(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                query
        )).thenReturn(new PageImpl<>(
                List.of(item),
                PageRequest.of(0, 20),
                1
        ));

        // GET 조회에는 CSRF 토큰을 추가하지 않는다.
        ResultActions response = mockMvc.perform(
                request(LIST_PATH)
                        .with(user(principal(MemberRole.ADMIN)))
                        // 임의의 인증 정보를 보내도 사용하면 안 된다.
                        .param("memberId", "999")
                        .param("credentialVersion", "999")
        );

        expectSuccess(response)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].postId").value(123))
                .andExpect(jsonPath("$.items[0].type").value("GENERAL"))
                .andExpect(jsonPath("$.items[0].topic").value("DAILY"))
                .andExpect(jsonPath("$.items[0].title")
                        .value("관리자 확인용 제목"))
                .andExpect(jsonPath("$.items[0].contentPreview")
                        .value("게시글 미리보기"))
                .andExpect(jsonPath("$.items[0].author.memberId").value(8))
                .andExpect(jsonPath("$.items[0].author.nickname")
                        .value("작성회원"))
                .andExpect(jsonPath("$.items[0].author.withdrawn")
                        .value(false))
                .andExpect(jsonPath("$.items[0].status").value("HIDDEN"))
                .andExpect(jsonPath("$.items[0].createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.items[0].updatedAt")
                        .value(UPDATED_AT.toString()))
                .andExpect(jsonPath("$.items[0].hiddenAt")
                        .value(HIDDEN_AT.toString()))
                .andExpect(jsonPath("$.items[0].deletedAt")
                        .value(nullValue()))
                .andExpect(jsonPath("$.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.items[0].author.email")
                        .doesNotExist())
                .andExpect(jsonPath("$.items[0].author.passwordHash")
                        .doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(adminPostReadService).getPosts(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                query
        );
        verifyNoMoreInteractions(adminPostReadService);
    }

    @ParameterizedTest
    @EnumSource(AdminPostStatus.class)
    void 상태와_정규화한_검색어와_페이지_조건을_전달한다(
            AdminPostStatus postStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminPostReadQuery expectedQuery = new AdminPostReadQuery(
                postStatus,
                "보청기 적응",
                2,
                10
        );

        when(adminPostReadService.getPosts(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                expectedQuery
        )).thenReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(2, 10),
                15
        ));

        expectSuccess(mockMvc.perform(
                request(LIST_PATH)
                        .with(user(principal(MemberRole.ADMIN)))
                        .param("status", postStatus.name())
                        .param("keyword", "  보청기 적응  ")
                        .param("page", "2")
                        .param("size", "10")
        ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));

        verify(adminPostReadService).getPosts(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                expectedQuery
        );
        verifyNoMoreInteractions(adminPostReadService);
    }

    @Test
    void 검색어가_공백뿐이면_조건을_제거하고_빈_결과도_200이다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(adminPostReadService.getPosts(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        expectSuccess(mockMvc.perform(
                request(LIST_PATH)
                        .with(user(principal(MemberRole.ADMIN)))
                        .param("keyword", " \t\n ")
        ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        verify(adminPostReadService).getPosts(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        );
    }

    @Test
    void 상세의_원문과_삭제_상태와_탈퇴_작성자를_반환한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        String originalContent = "첫 번째 줄\n\n두 번째 줄";

        AdminPostDetailResult result = new AdminPostDetailResult(
                123L,
                PostType.GENERAL,
                PostTopic.DAILY,
                "삭제된 게시글",
                originalContent,
                new AdminPostAuthorResult(
                        8L,
                        "탈퇴한 회원",
                        true
                ),
                AdminPostStatus.DELETED,
                CREATED_AT,
                UPDATED_AT,
                HIDDEN_AT,
                DELETED_AT
        );

        when(adminPostReadService.getPost(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                123L
        )).thenReturn(result);

        expectSuccess(mockMvc.perform(
                request(DETAIL_PATH)
                        .with(user(principal(MemberRole.ADMIN)))
                        .param("memberId", "999")
                        .param("credentialVersion", "999")
        ))
                .andExpect(jsonPath("$.postId").value(123))
                .andExpect(jsonPath("$.type").value("GENERAL"))
                .andExpect(jsonPath("$.topic").value("DAILY"))
                .andExpect(jsonPath("$.title").value("삭제된 게시글"))
                .andExpect(jsonPath("$.content").value(originalContent))
                .andExpect(jsonPath("$.contentPreview").doesNotExist())
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.updatedAt")
                        .value(UPDATED_AT.toString()))
                .andExpect(jsonPath("$.hiddenAt")
                        .value(HIDDEN_AT.toString()))
                .andExpect(jsonPath("$.deletedAt")
                        .value(DELETED_AT.toString()))
                .andExpect(jsonPath("$.author.memberId").value(8))
                .andExpect(jsonPath("$.author.nickname")
                        .value("탈퇴한 회원"))
                .andExpect(jsonPath("$.author.withdrawn").value(true))
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.author.passwordHash").doesNotExist());

        verify(adminPostReadService).getPost(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                123L
        );
        verifyNoMoreInteractions(adminPostReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 비로그인은_401이다(String path) throws Exception {
        mockMvc.perform(request(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(memberRepository, adminPostReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 유효한_일반_회원은_403이다(String path) throws Exception {
        givenValidSession(MemberRole.USER);

        mockMvc.perform(
                        request(path)
                                .with(user(principal(MemberRole.USER)))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(adminPostReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 세션_검증에_실패한_관리자는_401이다(String path)
            throws Exception {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        MEMBER_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        MemberRole.ADMIN
                ))
                .thenReturn(false);

        mockMvc.perform(
                        request(path)
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verify(memberRepository)
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        MEMBER_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        MemberRole.ADMIN
                );

        verifyNoInteractions(adminPostReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void MemberPrincipal이_아니면_401이다(String path)
            throws Exception {
        // 권한만 ADMIN인 일반 UserDetails로 컨트롤러 방어를 확인한다.
        mockMvc.perform(
                        request(path)
                                .with(user("other-user").roles("ADMIN"))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(memberRepository, adminPostReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "page, -1",
            "size, 0",
            "size, -1",
            "size, 51",
            "page, 2147483647"
    })
    void 페이지_조건이_유효하지_않으면_400이다(
            String name,
            String value
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        // page=2147483647은 기본 size=20과 곱하면 offset 범위 초과.
        mockMvc.perform(
                        request(LIST_PATH)
                                .with(user(principal(MemberRole.ADMIN)))
                                .param(name, value)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_PAGE_REQUEST"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));

        verifyNoInteractions(adminPostReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "page, abc",
            "size, abc",
            "page, 2147483648",
            "status, UNKNOWN"
    })
    void 파라미터_형식을_변환할_수_없으면_400이다(
            String name,
            String value
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        request(LIST_PATH)
                                .with(user(principal(MemberRole.ADMIN)))
                                .param(name, value)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(adminPostReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 검색어가_100코드포인트를_넘으면_400이다(
            String character
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        request(LIST_PATH)
                                .with(user(principal(MemberRole.ADMIN)))
                                .param("keyword", character.repeat(101))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_SEARCH_KEYWORD"));

        verifyNoInteractions(adminPostReadService);
    }

    @Test
    void 상세_ID가_숫자가_아니면_400이다() throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        request(LIST_PATH + "/abc")
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(adminPostReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "AUTHENTICATION_REQUIRED, 401",
            "ADMIN_REQUIRED, 403"
    })
    void 목록_서비스의_인증인가_예외를_HTTP_응답으로_변환한다(
            AdminContentReadException.Code code,
            int expectedStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminContentReadException exception =
                new AdminContentReadException(code);

        when(adminPostReadService.getPosts(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        )).thenThrow(exception);

        expectServiceError(
                mockMvc.perform(
                        request(LIST_PATH)
                                .with(user(principal(MemberRole.ADMIN)))
                ),
                exception,
                expectedStatus
        );
    }

    @ParameterizedTest
    @CsvSource({
            "AUTHENTICATION_REQUIRED, 401",
            "ADMIN_REQUIRED, 403",
            "POST_NOT_FOUND, 404"
    })
    void 상세_서비스_예외를_HTTP_응답으로_변환한다(
            AdminContentReadException.Code code,
            int expectedStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminContentReadException exception =
                new AdminContentReadException(code);

        when(adminPostReadService.getPost(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                123L
        )).thenThrow(exception);

        expectServiceError(
                mockMvc.perform(
                        request(DETAIL_PATH)
                                .with(user(principal(MemberRole.ADMIN)))
                ),
                exception,
                expectedStatus
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 허용된_Origin의_GET_사전_요청은_인증_없이_통과한다(
            String path
    ) throws Exception {
        mockMvc.perform(
                        options(path)
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
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsString("GET")
                ));

        verifyNoInteractions(memberRepository, adminPostReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 조회_경로의_POST_사전_요청은_거부한다(String path)
            throws Exception {
        mockMvc.perform(
                        options(path)
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

        verifyNoInteractions(memberRepository, adminPostReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 허용되지_않은_Origin의_조회는_거부한다(String path)
            throws Exception {
        mockMvc.perform(
                        request(path, "https://untrusted.example")
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(memberRepository, adminPostReadService);
    }

    private ResultActions expectSuccess(
            ResultActions response
    ) throws Exception {
        return response
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

    private void expectServiceError(
            ResultActions response,
            AdminContentReadException exception,
            int expectedStatus
    ) throws Exception {
        response
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value(exception.getCode().name()))
                .andExpect(jsonPath("$.message")
                        .value(exception.getMessage()))
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

    private MockHttpServletRequestBuilder request(String path) {
        return request(path, ORIGIN);
    }

    private MockHttpServletRequestBuilder request(
            String path,
            String origin
    ) {
        return get(path)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ORIGIN, origin);
    }

    private void givenValidSession(MemberRole role) {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        MEMBER_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        role
                ))
                .thenReturn(true);
    }

    private MemberPrincipal principal(MemberRole role) {
        return new MemberPrincipal(
                MEMBER_ID,
                "admin-read@example.com",
                "test-only-password-hash",
                "조회검증회원",
                MemberStatus.ACTIVE,
                role,
                CREDENTIAL_VERSION
        );
    }

    private AdminPostReadQuery defaultQuery() {
        return new AdminPostReadQuery(
                AdminPostStatus.ALL,
                null,
                0,
                20
        );
    }
}