package io.github.takgeun.iyum.admin.member.api;

import io.github.takgeun.iyum.admin.member.application.AdminMemberDetailResult;
import io.github.takgeun.iyum.admin.member.application.AdminMemberItemResult;
import io.github.takgeun.iyum.admin.member.application.AdminMemberReadException;
import io.github.takgeun.iyum.admin.member.application.AdminMemberReadQuery;
import io.github.takgeun.iyum.admin.member.application.AdminMemberReadService;
import io.github.takgeun.iyum.admin.member.application.AdminMemberSearchType;
import io.github.takgeun.iyum.admin.member.application.AdminMemberSort;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
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
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;

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
        controllers = AdminMemberReadController.class,
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
class AdminMemberReadControllerTest {

    private static final String LIST_PATH =
            "/api/v1/admin/members";

    private static final String DETAIL_PATH =
            "/api/v1/admin/members/123";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Long ACTOR_MEMBER_ID = 7L;
    private static final long CREDENTIAL_VERSION = 3L;
    private static final Long TARGET_MEMBER_ID = 123L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-30T10:00:00Z");

    private static final Instant UPDATED_AT =
            Instant.parse("2026-11-01T11:00:00Z");

    private static final Instant WITHDRAWN_AT =
            Instant.parse("2026-11-02T12:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminMemberReadService adminMemberReadService;

    @MockitoBean
    MemberRepository memberRepository;

    // ---------------------------------------------------------------------
    // 목록 조회
    // ---------------------------------------------------------------------

    @Test
    void 기본_조건으로_목록을_조회하고_인증된_관리자_정보를_사용한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminMemberReadQuery query = defaultQuery();

        AdminMemberItemResult item = new AdminMemberItemResult(
                TARGET_MEMBER_ID,
                "조회대상회원",
                "target@example.com",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREATED_AT
        );

        when(adminMemberReadService.getMembers(
                ACTOR_MEMBER_ID,
                CREDENTIAL_VERSION,
                query
        )).thenReturn(new PageImpl<>(
                List.of(item),
                PageRequest.of(0, 20),
                1
        ));

        // GET에는 CSRF 토큰을 전달하지 않는다.
        // 임의의 요청 파라미터를 인증된 관리자 정보로 사용하면 안 된다.
        expectSuccess(mockMvc.perform(
                adminRequest(LIST_PATH)
                        .queryParam("actorMemberId", "999")
                        .queryParam("credentialVersion", "999")
        ))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].memberId").value(123))
                .andExpect(jsonPath("$.items[0].nickname")
                        .value("조회대상회원"))
                .andExpect(jsonPath("$.items[0].email")
                        .value("target@example.com"))
                .andExpect(jsonPath("$.items[0].status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].role")
                        .value("USER"))
                .andExpect(jsonPath("$.items[0].createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.items[0].passwordHash")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].credentialVersion")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(adminMemberReadService).getMembers(
                ACTOR_MEMBER_ID,
                CREDENTIAL_VERSION,
                query
        );
        verifyNoMoreInteractions(adminMemberReadService);
    }

    @ParameterizedTest
    @MethodSource("normalizedSearchKeywords")
    void 검색어를_정규화하고_필터와_정렬과_페이지를_전달한다(
            AdminMemberSearchType searchType,
            String rawKeyword,
            String expectedKeyword
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminMemberReadQuery expectedQuery = new AdminMemberReadQuery(
                1,
                10,
                searchType,
                expectedKeyword,
                MemberStatus.ACTIVE,
                MemberRole.USER,
                AdminMemberSort.OLDEST
        );

        when(adminMemberReadService.getMembers(
                ACTOR_MEMBER_ID,
                CREDENTIAL_VERSION,
                expectedQuery
        )).thenReturn(Page.empty(PageRequest.of(1, 10)));

        expectSuccess(mockMvc.perform(
                adminRequest(LIST_PATH)
                        .queryParam("searchType", searchType.name())
                        .queryParam("keyword", rawKeyword)
                        .queryParam("status", "ACTIVE")
                        .queryParam("role", "USER")
                        .queryParam("sort", "OLDEST")
                        .queryParam("page", "1")
                        .queryParam("size", "10")
        ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10));

        verify(adminMemberReadService).getMembers(
                ACTOR_MEMBER_ID,
                CREDENTIAL_VERSION,
                expectedQuery
        );
        verifyNoMoreInteractions(adminMemberReadService);
    }

    static Stream<Arguments> normalizedSearchKeywords() {
        return Stream.of(
                Arguments.of(
                        AdminMemberSearchType.MEMBER_ID,
                        "  00123  ",
                        "123"
                ),
                Arguments.of(
                        AdminMemberSearchType.NICKNAME,
                        "  조회 회원  ",
                        "조회 회원"
                ),
                Arguments.of(
                        AdminMemberSearchType.NICKNAME,
                        "  A%_!회원  ",
                        "A%_!회원"
                ),
                Arguments.of(
                        AdminMemberSearchType.EMAIL,
                        "  TARGET@EXAMPLE.COM  ",
                        "target@example.com"
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \t\n "})
    void 검색어가_비어_있으면_검색_유형도_제거한다(
            String keyword
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(adminMemberReadService.getMembers(
                ACTOR_MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        expectSuccess(mockMvc.perform(
                adminRequest(LIST_PATH)
                        .queryParam("searchType", "EMAIL")
                        .queryParam("keyword", keyword)
        ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(adminMemberReadService).getMembers(
                ACTOR_MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        );
    }

    @Test
    void 정렬이_빈_문자열이면_최신순을_적용한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(adminMemberReadService.getMembers(
                ACTOR_MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        expectSuccess(mockMvc.perform(
                adminRequest(LIST_PATH)
                        .queryParam("sort", "")
        ));

        verify(adminMemberReadService).getMembers(
                ACTOR_MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        );
    }

    @Test
    void 범위_밖_페이지에서도_전체_개수를_유지한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminMemberReadQuery query = new AdminMemberReadQuery(
                2,
                10,
                null,
                null,
                null,
                null,
                AdminMemberSort.LATEST
        );

        when(adminMemberReadService.getMembers(
                ACTOR_MEMBER_ID,
                CREDENTIAL_VERSION,
                query
        )).thenReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(2, 10),
                15
        ));

        expectSuccess(mockMvc.perform(
                adminRequest(LIST_PATH)
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

        verify(adminMemberReadService).getMembers(
                ACTOR_MEMBER_ID,
                CREDENTIAL_VERSION,
                query
        );
    }

    @Test
    void 목록에서_삭제_회원의_null_이메일을_생략하지_않는다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        // 삭제 회원의 표시 정책 자체는 서비스/결과 객체 테스트에서 검증한다.
        // 여기서는 서비스가 반환한 값의 JSON 변환을 검증한다.
        AdminMemberItemResult item = new AdminMemberItemResult(
                TARGET_MEMBER_ID,
                "탈퇴한 회원",
                null,
                MemberStatus.DELETED,
                MemberRole.USER,
                CREATED_AT
        );

        when(adminMemberReadService.getMembers(
                ACTOR_MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        )).thenReturn(new PageImpl<>(
                List.of(item),
                PageRequest.of(0, 20),
                1
        ));

        expectSuccess(mockMvc.perform(adminRequest(LIST_PATH)))
                .andExpect(jsonPath("$.items[0].nickname")
                        .value("탈퇴한 회원"))
                .andExpect(jsonPath("$.items[0].status")
                        .value("DELETED"))
                .andExpect(jsonPath("$.items[0].email").hasJsonPath())
                .andExpect(jsonPath("$.items[0].email")
                        .value(nullValue()))
                .andExpect(jsonPath("$.items[0].passwordHash")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].credentialVersion")
                        .doesNotHaveJsonPath());
    }

    // ---------------------------------------------------------------------
    // 상세 조회
    // ---------------------------------------------------------------------

    @Test
    void 상세_조회는_회원_정보와_시각을_반환한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminMemberDetailResult result = new AdminMemberDetailResult(
                TARGET_MEMBER_ID,
                "조회대상회원",
                "target@example.com",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREATED_AT,
                UPDATED_AT,
                null
        );

        when(adminMemberReadService.getMember(
                ACTOR_MEMBER_ID,
                CREDENTIAL_VERSION,
                TARGET_MEMBER_ID
        )).thenReturn(result);

        expectSuccess(mockMvc.perform(
                adminRequest(DETAIL_PATH)
                        .queryParam("actorMemberId", "999")
                        .queryParam("credentialVersion", "999")
        ))
                .andExpect(jsonPath("$.memberId").value(123))
                .andExpect(jsonPath("$.nickname").value("조회대상회원"))
                .andExpect(jsonPath("$.email").value("target@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.updatedAt")
                        .value(UPDATED_AT.toString()))
                .andExpect(jsonPath("$.withdrawnAt").hasJsonPath())
                .andExpect(jsonPath("$.withdrawnAt").value(nullValue()))
                .andExpect(jsonPath("$.passwordHash")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.credentialVersion")
                        .doesNotHaveJsonPath());

        verify(adminMemberReadService).getMember(
                ACTOR_MEMBER_ID,
                CREDENTIAL_VERSION,
                TARGET_MEMBER_ID
        );
        verifyNoMoreInteractions(adminMemberReadService);
    }

    @Test
    void 삭제_회원_상세의_null_이메일과_탈퇴_시각을_반환한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminMemberDetailResult result = new AdminMemberDetailResult(
                TARGET_MEMBER_ID,
                "탈퇴한 회원",
                null,
                MemberStatus.DELETED,
                MemberRole.USER,
                CREATED_AT,
                UPDATED_AT,
                WITHDRAWN_AT
        );

        when(adminMemberReadService.getMember(
                ACTOR_MEMBER_ID,
                CREDENTIAL_VERSION,
                TARGET_MEMBER_ID
        )).thenReturn(result);

        expectSuccess(mockMvc.perform(adminRequest(DETAIL_PATH)))
                .andExpect(jsonPath("$.memberId").value(123))
                .andExpect(jsonPath("$.nickname").value("탈퇴한 회원"))
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.email").hasJsonPath())
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.withdrawnAt").hasJsonPath())
                .andExpect(jsonPath("$.withdrawnAt")
                        .value(WITHDRAWN_AT.toString()))
                .andExpect(jsonPath("$.passwordHash")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.credentialVersion")
                        .doesNotHaveJsonPath());
    }

    // ---------------------------------------------------------------------
    // 요청 파라미터 검증
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "searchType, UNKNOWN",
            "searchType, email",
            "status, ALL",
            "status, active",
            "role, ALL",
            "role, admin",
            "sort, UNKNOWN",
            "sort, latest",
            "page, abc",
            "page, 2147483648",
            "size, abc"
    })
    void 변환할_수_없는_요청_파라미터는_400이다(
            String parameter,
            String value
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        expectBadRequest(
                mockMvc.perform(
                        adminRequest(LIST_PATH)
                                .queryParam(parameter, value)
                ),
                "INVALID_REQUEST_PARAMETER"
        );

        verifyNoInteractions(adminMemberReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, -1",
            "0, 51",
            "107374183, 20"
    })
    void 페이지_또는_offset_범위가_잘못되면_400이다(
            int page,
            int size
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        expectBadRequest(
                mockMvc.perform(
                        adminRequest(LIST_PATH)
                                .queryParam("page", String.valueOf(page))
                                .queryParam("size", String.valueOf(size))
                ),
                "INVALID_PAGE_REQUEST"
        );

        verifyNoInteractions(adminMemberReadService);
    }

    @Test
    void 검색어가_있는데_검색_유형이_없으면_400이다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        expectBadRequest(
                mockMvc.perform(
                        adminRequest(LIST_PATH)
                                .queryParam("keyword", "조회회원")
                ),
                "INVALID_REQUEST_PARAMETER"
        );

        verifyNoInteractions(adminMemberReadService);
    }

    @ParameterizedTest
    @MethodSource("invalidSearchKeywords")
    void 검색_유형에_맞지_않는_검색어는_400이다(
            AdminMemberSearchType searchType,
            String keyword
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        expectBadRequest(
                mockMvc.perform(
                        adminRequest(LIST_PATH)
                                .queryParam("searchType", searchType.name())
                                .queryParam("keyword", keyword)
                ),
                "INVALID_SEARCH_KEYWORD"
        );

        verifyNoInteractions(adminMemberReadService);
    }

    static Stream<Arguments> invalidSearchKeywords() {
        return Stream.of(
                Arguments.of(AdminMemberSearchType.MEMBER_ID, "0"),
                Arguments.of(AdminMemberSearchType.MEMBER_ID, "-1"),
                Arguments.of(AdminMemberSearchType.MEMBER_ID, "+123"),
                Arguments.of(AdminMemberSearchType.MEMBER_ID, "1.5"),
                Arguments.of(AdminMemberSearchType.MEMBER_ID, "abc"),
                Arguments.of(
                        AdminMemberSearchType.MEMBER_ID,
                        "9223372036854775808"
                ),
                Arguments.of(
                        AdminMemberSearchType.MEMBER_ID,
                        "1".repeat(20)
                ),
                Arguments.of(
                        AdminMemberSearchType.NICKNAME,
                        "가".repeat(101)
                ),
                Arguments.of(
                        AdminMemberSearchType.EMAIL,
                        "a".repeat(255)
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "9223372036854775808"})
    void 상세_회원_ID를_Long으로_변환할_수_없으면_400이다(
            String memberId
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        expectBadRequest(
                mockMvc.perform(
                        adminRequest(LIST_PATH + "/" + memberId)
                ),
                "INVALID_REQUEST_PARAMETER"
        );

        verifyNoInteractions(adminMemberReadService);
    }

    // ---------------------------------------------------------------------
    // 서비스 예외의 HTTP 응답 변환
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("serviceErrors")
    void 서비스_예외를_정해진_HTTP_상태와_오류_코드로_변환한다(
            String path,
            AdminMemberReadException.Code code,
            int expectedStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminMemberReadException exception =
                new AdminMemberReadException(code);

        if (LIST_PATH.equals(path)) {
            when(adminMemberReadService.getMembers(
                    ACTOR_MEMBER_ID,
                    CREDENTIAL_VERSION,
                    defaultQuery()
            )).thenThrow(exception);
        } else {
            when(adminMemberReadService.getMember(
                    ACTOR_MEMBER_ID,
                    CREDENTIAL_VERSION,
                    TARGET_MEMBER_ID
            )).thenThrow(exception);
        }

        mockMvc.perform(adminRequest(path))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.message")
                        .value(exception.getMessage()))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ));

        if (LIST_PATH.equals(path)) {
            verify(adminMemberReadService).getMembers(
                    ACTOR_MEMBER_ID,
                    CREDENTIAL_VERSION,
                    defaultQuery()
            );
        } else {
            verify(adminMemberReadService).getMember(
                    ACTOR_MEMBER_ID,
                    CREDENTIAL_VERSION,
                    TARGET_MEMBER_ID
            );
        }

        verifyNoMoreInteractions(adminMemberReadService);
    }

    static Stream<Arguments> serviceErrors() {
        return Stream.of(
                Arguments.of(
                        LIST_PATH,
                        AdminMemberReadException.Code.AUTHENTICATION_REQUIRED,
                        401
                ),
                Arguments.of(
                        DETAIL_PATH,
                        AdminMemberReadException.Code.AUTHENTICATION_REQUIRED,
                        401
                ),
                Arguments.of(
                        LIST_PATH,
                        AdminMemberReadException.Code.ADMIN_REQUIRED,
                        403
                ),
                Arguments.of(
                        DETAIL_PATH,
                        AdminMemberReadException.Code.ADMIN_REQUIRED,
                        403
                ),
                Arguments.of(
                        LIST_PATH,
                        AdminMemberReadException.Code.INVALID_REQUEST_PARAMETER,
                        400
                ),
                Arguments.of(
                        LIST_PATH,
                        AdminMemberReadException.Code.INVALID_PAGE_REQUEST,
                        400
                ),
                Arguments.of(
                        LIST_PATH,
                        AdminMemberReadException.Code.INVALID_SEARCH_KEYWORD,
                        400
                ),
                Arguments.of(
                        DETAIL_PATH,
                        AdminMemberReadException.Code.MEMBER_NOT_FOUND,
                        404
                )
        );
    }

    // ---------------------------------------------------------------------
    // 인증 및 인가
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 비로그인은_401이다(String path) throws Exception {
        mockMvc.perform(request(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(memberRepository, adminMemberReadService);
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

        verifyNoInteractions(adminMemberReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 세션_검증에_실패한_관리자는_401이다(String path)
            throws Exception {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_MEMBER_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        MemberRole.ADMIN
                ))
                .thenReturn(false);

        mockMvc.perform(adminRequest(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verify(memberRepository)
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_MEMBER_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        MemberRole.ADMIN
                );

        verifyNoInteractions(adminMemberReadService);
    }

    // ---------------------------------------------------------------------
    // Dev CORS
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 허용_Origin의_GET_preflight는_인증_없이_성공한다(
            String path
    ) throws Exception {
        mockMvc.perform(
                        options(path)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "accept"
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
                        "GET,OPTIONS"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        "accept"
                ));

        verifyNoInteractions(memberRepository, adminMemberReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 허용하지_않은_Origin의_preflight는_거부한다(
            String path
    ) throws Exception {
        mockMvc.perform(
                        options(path)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://untrusted.example"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ))
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS
                ));

        verifyNoInteractions(memberRepository, adminMemberReadService);
    }

    @ParameterizedTest
    @MethodSource("disallowedCorsMethods")
    void 조회_경로에_변경_메서드의_preflight를_허용하지_않는다(
            String path,
            String method
    ) throws Exception {
        mockMvc.perform(
                        options(path)
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        method
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(memberRepository, adminMemberReadService);
    }

    static Stream<Arguments> disallowedCorsMethods() {
        return Stream.of(LIST_PATH, DETAIL_PATH)
                .flatMap(path ->
                        Stream.of("POST", "PUT", "PATCH", "DELETE")
                                .map(method -> Arguments.of(path, method))
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 허용하지_않은_Origin의_실제_GET도_거부한다(
            String path
    ) throws Exception {
        mockMvc.perform(
                        get(path)
                                .accept(MediaType.APPLICATION_JSON)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://untrusted.example"
                                )
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        verifyNoInteractions(adminMemberReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void CORS가_허용되어도_비로그인_GET은_401이다(
            String path
    ) throws Exception {
        mockMvc.perform(request(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ));

        verifyNoInteractions(memberRepository, adminMemberReadService);
    }

    // ---------------------------------------------------------------------
    // 공통 도우미
    // ---------------------------------------------------------------------

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

    private ResultActions expectBadRequest(
            ResultActions response,
            String expectedCode
    ) throws Exception {
        return response
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(expectedCode))
                // 타입 변환 오류는 Security의 기본 캐시 헤더가 적용될 수 있다.
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));
    }

    private MockHttpServletRequestBuilder request(String path) {
        return get(path)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ORIGIN, ORIGIN);
    }

    private MockHttpServletRequestBuilder adminRequest(String path) {
        return request(path)
                .with(user(principal(MemberRole.ADMIN)));
    }

    private void givenValidSession(MemberRole role) {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_MEMBER_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        role
                ))
                .thenReturn(true);
    }

    private MemberPrincipal principal(MemberRole role) {
        return new MemberPrincipal(
                ACTOR_MEMBER_ID,
                "admin-read@example.com",
                "test-only-password-hash",
                "조회검증관리자",
                MemberStatus.ACTIVE,
                role,
                CREDENTIAL_VERSION
        );
    }

    private AdminMemberReadQuery defaultQuery() {
        return new AdminMemberReadQuery(
                0,
                20,
                null,
                null,
                null,
                null,
                AdminMemberSort.LATEST
        );
    }
}