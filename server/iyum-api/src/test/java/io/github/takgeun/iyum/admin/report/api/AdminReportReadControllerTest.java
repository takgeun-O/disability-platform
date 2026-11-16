package io.github.takgeun.iyum.admin.report.api;

import io.github.takgeun.iyum.admin.report.application.AdminReportCurrentTargetResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportDetailResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportItemResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportReadException;
import io.github.takgeun.iyum.admin.report.application.AdminReportReadQuery;
import io.github.takgeun.iyum.admin.report.application.AdminReportReadService;
import io.github.takgeun.iyum.admin.report.application.AdminReportSort;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetAuthorResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetType;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.report.domain.ContentReportHandlingOutcome;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
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
import java.time.LocalDate;
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
        controllers = AdminReportReadController.class,
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
class AdminReportReadControllerTest {

    private static final String LIST_PATH = "/api/v1/admin/reports";
    private static final String DETAIL_PATH = "/api/v1/admin/reports/100";
    private static final String ORIGIN = "http://127.0.0.1:8443";

    private static final Long ACTOR_ID = 40L;
    private static final long VERSION = 3L;
    private static final Long REPORT_ID = 100L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-12T03:00:00Z");

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-11-12T04:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminReportReadService reportReadService;

    @MockitoBean
    MemberRepository memberRepository;

    @Test
    void 기본_조건으로_목록을_조회하고_세션의_관리자_정보를_사용한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminReportReadQuery query = defaultQuery();

        AdminReportItemResult postItem = new AdminReportItemResult(
                100L,
                AdminReportTargetType.POST,
                104L,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                "신고 당시 제목",
                "신고 당시 게시글 미리보기",
                CREATED_AT,
                new AdminReportTargetAuthorResult(
                        11L,
                        "현재작성자",
                        false
                )
        );

        AdminReportItemResult commentItem = new AdminReportItemResult(
                99L,
                AdminReportTargetType.COMMENT,
                144L,
                ContentReportStatus.DISMISSED,
                ContentReportReasonType.OTHER,
                null,
                "신고 당시 댓글 미리보기",
                CREATED_AT.minusSeconds(60),
                null
        );

        when(reportReadService.getReports(ACTOR_ID, VERSION, query))
                .thenReturn(new PageImpl<>(
                        List.of(postItem, commentItem),
                        PageRequest.of(0, 20),
                        2
                ));

        // GET에는 CSRF 토큰을 전달하지 않는다.
        // 요청 파라미터로 인증 정보를 덮어쓸 수 없어야 한다.
        expectSuccess(mockMvc.perform(
                adminRequest(LIST_PATH)
                        .queryParam("actorMemberId", "999")
                        .queryParam("credentialVersion", "999")
        ))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].reportId").value(100))
                .andExpect(jsonPath("$.items[0].targetType").value("POST"))
                .andExpect(jsonPath("$.items[0].targetId").value(104))
                .andExpect(jsonPath("$.items[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$.items[0].reasonType")
                        .value("SPAM_ADVERTISING"))
                .andExpect(jsonPath("$.items[0].titleSnapshot")
                        .value("신고 당시 제목"))
                .andExpect(jsonPath("$.items[0].contentSnapshotPreview")
                        .value("신고 당시 게시글 미리보기"))
                .andExpect(jsonPath("$.items[0].createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.items[0].currentAuthor.memberId")
                        .value(11))
                .andExpect(jsonPath("$.items[0].currentAuthor.nickname")
                        .value("현재작성자"))
                .andExpect(jsonPath("$.items[0].currentAuthor.withdrawn")
                        .value(false))
                .andExpect(jsonPath("$.items[0].currentAuthor.email")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].currentAuthor.passwordHash")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].currentAuthor.credentialVersion")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].contentSnapshot")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].reporterMemberId")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[1].reportId").value(99))
                .andExpect(jsonPath("$.items[1].targetType").value("COMMENT"))
                .andExpect(jsonPath("$.items[1].targetId").value(144))
                .andExpect(jsonPath("$.items[1].titleSnapshot").hasJsonPath())
                .andExpect(jsonPath("$.items[1].titleSnapshot").value(nullValue()))
                .andExpect(jsonPath("$.items[1].currentAuthor").hasJsonPath())
                .andExpect(jsonPath("$.items[1].currentAuthor").value(nullValue()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(reportReadService).getReports(ACTOR_ID, VERSION, query);
        verifyNoMoreInteractions(reportReadService);
    }

    @Test
    void 모든_필터와_정렬과_페이지를_서비스에_전달한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminReportReadQuery expected = new AdminReportReadQuery(
                AdminReportTargetType.COMMENT,
                ContentReportStatus.RESOLVED,
                ContentReportReasonType.ABUSE_HATE,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 12),
                1,
                10,
                AdminReportSort.OLDEST
        );

        stubEmptyList(expected);

        expectSuccess(mockMvc.perform(
                adminRequest(LIST_PATH)
                        .queryParam("targetType", "COMMENT")
                        .queryParam("status", "RESOLVED")
                        .queryParam("reasonType", "ABUSE_HATE")
                        .queryParam("startDate", "2026-11-01")
                        .queryParam("endDate", "2026-11-12")
                        .queryParam("page", "1")
                        .queryParam("size", "10")
                        .queryParam("sort", "OLDEST")
        ));

        verify(reportReadService).getReports(ACTOR_ID, VERSION, expected);
    }

    @Test
    void 빈_파라미터는_기본값과_전체_조건으로_처리한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);
        stubEmptyList(defaultQuery());

        expectSuccess(mockMvc.perform(
                adminRequest(LIST_PATH)
                        .queryParam("targetType", "")
                        .queryParam("status", "")
                        .queryParam("reasonType", "")
                        .queryParam("startDate", "")
                        .queryParam("endDate", "")
                        .queryParam("page", "")
                        .queryParam("size", "")
                        .queryParam("sort", "")
        ));

        verify(reportReadService)
                .getReports(ACTOR_ID, VERSION, defaultQuery());
    }

    @ParameterizedTest
    @EnumSource(ContentReportStatus.class)
    void 모든_신고_상태를_조회_조건으로_받는다(
            ContentReportStatus reportStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminReportReadQuery expected = new AdminReportReadQuery(
                AdminReportTargetType.ALL,
                reportStatus,
                null,
                null,
                null,
                0,
                20,
                AdminReportSort.LATEST
        );

        stubEmptyList(expected);

        expectSuccess(mockMvc.perform(
                adminRequest(LIST_PATH)
                        .queryParam("status", reportStatus.name())
        ));

        verify(reportReadService).getReports(ACTOR_ID, VERSION, expected);
    }

    @ParameterizedTest
    @EnumSource(ContentReportReasonType.class)
    void 모든_신고_사유를_조회_조건으로_받는다(
            ContentReportReasonType reasonType
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminReportReadQuery expected = new AdminReportReadQuery(
                AdminReportTargetType.ALL,
                null,
                reasonType,
                null,
                null,
                0,
                20,
                AdminReportSort.LATEST
        );

        stubEmptyList(expected);

        expectSuccess(mockMvc.perform(
                adminRequest(LIST_PATH)
                        .queryParam("reasonType", reasonType.name())
        ));

        verify(reportReadService).getReports(ACTOR_ID, VERSION, expected);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 날짜_조건을_각각_단독으로_전달할_수_있다(
            boolean startOnly
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        LocalDate date = LocalDate.of(2026, 11, 12);

        AdminReportReadQuery expected = new AdminReportReadQuery(
                AdminReportTargetType.ALL,
                null,
                null,
                startOnly ? date : null,
                startOnly ? null : date,
                0,
                20,
                AdminReportSort.LATEST
        );

        stubEmptyList(expected);

        expectSuccess(mockMvc.perform(
                adminRequest(LIST_PATH)
                        .queryParam(
                                startOnly ? "startDate" : "endDate",
                                date.toString()
                        )
        ));

        verify(reportReadService).getReports(ACTOR_ID, VERSION, expected);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, false",
            "3, 45, 3, true"
    })
    void 빈_결과와_범위_밖_페이지도_서버_페이지_정보를_유지한다(
            int page,
            long totalElements,
            int totalPages,
            boolean hasPrevious
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminReportReadQuery query = new AdminReportReadQuery(
                AdminReportTargetType.ALL,
                null,
                null,
                null,
                null,
                page,
                20,
                AdminReportSort.LATEST
        );

        when(reportReadService.getReports(ACTOR_ID, VERSION, query))
                .thenReturn(new PageImpl<>(
                        List.of(),
                        PageRequest.of(page, 20),
                        totalElements
                ));

        expectSuccess(mockMvc.perform(
                adminRequest(LIST_PATH)
                        .queryParam("page", Integer.toString(page))
        ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(totalElements))
                .andExpect(jsonPath("$.totalPages").value(totalPages))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(hasPrevious));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 상세에서_스냅샷과_현재_대상을_구분해_반환한다(
            boolean postTarget
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        String snapshot = "신고 당시 본문\n" + "가".repeat(130);

        AdminReportCurrentTargetResult target =
                new AdminReportCurrentTargetResult(
                        7L,
                        postTarget ? null : 104L,
                        new AdminReportTargetAuthorResult(
                                11L,
                                "탈퇴한 회원",
                                true
                        ),
                        postTarget ? "현재 제목" : null,
                        "현재 본문\n수정된 내용",
                        HIDDEN_AT,
                        null
                );

        AdminReportDetailResult result = new AdminReportDetailResult(
                REPORT_ID,
                99L,
                postTarget
                        ? AdminReportTargetType.POST
                        : AdminReportTargetType.COMMENT,
                7L,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.OTHER,
                "검토 요청",
                postTarget ? "신고 당시 제목" : null,
                snapshot,
                CREATED_AT,
                target,
                null,
                null,
                null,
                null
        );

        when(reportReadService.getReport(ACTOR_ID, VERSION, REPORT_ID))
                .thenReturn(result);

        ResultActions response = expectSuccess(mockMvc.perform(
                adminRequest(DETAIL_PATH)
                        .queryParam("actorMemberId", "999")
                        .queryParam("credentialVersion", "999")
        ))
                .andExpect(jsonPath("$.reportId").value(100))
                .andExpect(jsonPath("$.reporterMemberId").value(99))
                .andExpect(jsonPath("$.targetType")
                        .value(postTarget ? "POST" : "COMMENT"))
                .andExpect(jsonPath("$.targetId").value(7))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.reasonType").value("OTHER"))
                .andExpect(jsonPath("$.reasonDetail").value("검토 요청"))
                .andExpect(jsonPath("$.contentSnapshot").value(snapshot))
                .andExpect(jsonPath("$.createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.currentTarget.targetId").value(7))
                .andExpect(jsonPath("$.currentTarget.content")
                        .value("현재 본문\n수정된 내용"))
                .andExpect(jsonPath("$.currentTarget.hiddenAt")
                        .value(HIDDEN_AT.toString()))
                .andExpect(jsonPath("$.currentTarget.deletedAt").hasJsonPath())
                .andExpect(jsonPath("$.currentTarget.deletedAt")
                        .value(nullValue()))
                .andExpect(jsonPath("$.currentTarget.author.memberId")
                        .value(11))
                .andExpect(jsonPath("$.currentTarget.author.nickname")
                        .value("탈퇴한 회원"))
                .andExpect(jsonPath("$.currentTarget.author.withdrawn")
                        .value(true))
                .andExpect(jsonPath("$.currentTarget.author.email")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.currentTarget.author.passwordHash")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.currentTarget.author.credentialVersion")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.reporterEmail")
                        .doesNotHaveJsonPath());

        if (postTarget) {
            response
                    .andExpect(jsonPath("$.titleSnapshot")
                            .value("신고 당시 제목"))
                    .andExpect(jsonPath("$.currentTarget.title")
                            .value("현재 제목"))
                    .andExpect(jsonPath("$.currentTarget.parentPostId")
                            .hasJsonPath())
                    .andExpect(jsonPath("$.currentTarget.parentPostId")
                            .value(nullValue()));
        } else {
            response
                    .andExpect(jsonPath("$.titleSnapshot").hasJsonPath())
                    .andExpect(jsonPath("$.titleSnapshot").value(nullValue()))
                    .andExpect(jsonPath("$.currentTarget.title").hasJsonPath())
                    .andExpect(jsonPath("$.currentTarget.title")
                            .value(nullValue()))
                    .andExpect(jsonPath("$.currentTarget.parentPostId")
                            .value(104));
        }

        verify(reportReadService)
                .getReport(ACTOR_ID, VERSION, REPORT_ID);
        verifyNoMoreInteractions(reportReadService);
    }

    @Test
    void 현재_대상과_상세_사유가_없어도_null_필드를_생략하지_않는다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminReportDetailResult result = new AdminReportDetailResult(
                REPORT_ID,
                99L,
                AdminReportTargetType.COMMENT,
                144L,
                ContentReportStatus.RESOLVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                null,
                null,
                "남아 있는 신고 당시 댓글",
                CREATED_AT,
                null,
                ACTOR_ID,
                CREATED_AT.plusSeconds(60),
                "검토 결과 필요한 대응을 완료했습니다.",
                ContentReportHandlingOutcome.NO_CONTENT_ACTION
        );

        when(reportReadService.getReport(ACTOR_ID, VERSION, REPORT_ID))
                .thenReturn(result);

        expectSuccess(mockMvc.perform(adminRequest(DETAIL_PATH)))
                .andExpect(jsonPath("$.contentSnapshot")
                        .value("남아 있는 신고 당시 댓글"))
                .andExpect(jsonPath("$.reasonDetail").hasJsonPath())
                .andExpect(jsonPath("$.reasonDetail").value(nullValue()))
                .andExpect(jsonPath("$.titleSnapshot").hasJsonPath())
                .andExpect(jsonPath("$.titleSnapshot").value(nullValue()))
                .andExpect(jsonPath("$.currentTarget").hasJsonPath())
                .andExpect(jsonPath("$.currentTarget").value(nullValue()));
    }

    @ParameterizedTest
    @CsvSource({
            "targetType, post",
            "targetType, UNKNOWN",
            "status, ALL",
            "status, received",
            "status, UNKNOWN",
            "reasonType, ALL",
            "reasonType, LEGACY",
            "reasonType, spam_advertising",
            "sort, latest",
            "sort, UNKNOWN",
            "page, abc",
            "page, 2147483648",
            "size, abc",
            "size, 2147483648",
            "startDate, invalid",
            "startDate, 2026-02-30",
            "endDate, 2026-13-01"
    })
    void 변환할_수_없는_파라미터는_400으로_거부한다(
            String name,
            String value
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        expectError(
                mockMvc.perform(
                        adminRequest(LIST_PATH).queryParam(name, value)
                ),
                400,
                "INVALID_REQUEST_PARAMETER"
        );

        verifyNoInteractions(reportReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "page, -1",
            "size, 0",
            "size, -1",
            "size, 51",
            "page, 2147483647"
    })
    void 페이지와_offset_범위_오류는_400으로_거부한다(
            String name,
            String value
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        expectError(
                mockMvc.perform(
                        adminRequest(LIST_PATH).queryParam(name, value)
                ),
                400,
                "INVALID_PAGE_REQUEST"
        );

        verifyNoInteractions(reportReadService);
    }

    @Test
    void 시작일이_종료일보다_늦으면_400으로_거부한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        expectError(
                mockMvc.perform(
                        adminRequest(LIST_PATH)
                                .queryParam("startDate", "2026-11-12")
                                .queryParam("endDate", "2026-11-01")
                ),
                400,
                "INVALID_DATE_RANGE"
        );

        verifyNoInteractions(reportReadService);
    }

    @Test
    void 허용_연도보다_작은_날짜는_400으로_거부한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        expectError(
                mockMvc.perform(
                        adminRequest(LIST_PATH)
                                .queryParam("startDate", "0000-01-01")
                ),
                400,
                "INVALID_DATE_RANGE"
        );

        verifyNoInteractions(reportReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "9223372036854775808"})
    void 신고_ID_변환_실패는_400으로_거부한다(String reportId)
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        expectError(
                mockMvc.perform(
                        adminRequest(LIST_PATH + "/" + reportId)
                ),
                400,
                "INVALID_REQUEST_PARAMETER"
        );

        verifyNoInteractions(reportReadService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, 999L})
    void 서비스의_신고_없음_예외를_404로_반환한다(Long reportId)
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(reportReadService.getReport(ACTOR_ID, VERSION, reportId))
                .thenThrow(new AdminReportReadException(
                        AdminReportReadException.Code.REPORT_NOT_FOUND
                ));

        expectError(
                mockMvc.perform(
                        adminRequest(LIST_PATH + "/" + reportId)
                ),
                404,
                "REPORT_NOT_FOUND"
        );

        verify(reportReadService).getReport(ACTOR_ID, VERSION, reportId);
    }

    @ParameterizedTest
    @CsvSource({
            "LIST, AUTHENTICATION_REQUIRED, 401",
            "LIST, ADMIN_REQUIRED, 403",
            "LIST, INVALID_REQUEST_PARAMETER, 400",
            "LIST, INVALID_PAGE_REQUEST, 400",
            "LIST, INVALID_DATE_RANGE, 400",
            "DETAIL, AUTHENTICATION_REQUIRED, 401",
            "DETAIL, ADMIN_REQUIRED, 403",
            "DETAIL, REPORT_NOT_FOUND, 404"
    })
    void 서비스_예외를_정해진_HTTP_상태와_코드로_반환한다(
            String endpoint,
            AdminReportReadException.Code code,
            int expectedStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminReportReadException exception =
                new AdminReportReadException(code);

        String path;

        if (endpoint.equals("LIST")) {
            path = LIST_PATH;

            when(reportReadService.getReports(
                    ACTOR_ID,
                    VERSION,
                    defaultQuery()
            )).thenThrow(exception);
        } else {
            path = DETAIL_PATH;

            when(reportReadService.getReport(
                    ACTOR_ID,
                    VERSION,
                    REPORT_ID
            )).thenThrow(exception);
        }

        expectError(
                mockMvc.perform(adminRequest(path)),
                expectedStatus,
                code.name()
        )
                .andExpect(jsonPath("$.message")
                        .value(exception.getMessage()))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 비로그인은_401이며_서비스를_호출하지_않는다(String path)
            throws Exception {
        expectError(
                mockMvc.perform(request(path)),
                401,
                "AUTHENTICATION_REQUIRED"
        );

        verifyNoInteractions(memberRepository, reportReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 유효한_일반_회원은_403으로_거부한다(String path)
            throws Exception {
        givenValidSession(MemberRole.USER);

        expectError(
                mockMvc.perform(
                        request(path)
                                .with(user(principal(MemberRole.USER)))
                ),
                403,
                "ACCESS_DENIED"
        );

        verifyNoInteractions(reportReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void DB에서_무효로_확인된_관리자_세션은_401로_거부한다(
            String path
    ) throws Exception {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_ID,
                        MemberStatus.ACTIVE,
                        VERSION,
                        MemberRole.ADMIN
                ))
                .thenReturn(false);

        expectError(
                mockMvc.perform(adminRequest(path)),
                401,
                "AUTHENTICATION_REQUIRED"
        );

        verify(memberRepository)
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        ACTOR_ID,
                        MemberStatus.ACTIVE,
                        VERSION,
                        MemberRole.ADMIN
                );

        verifyNoInteractions(reportReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void MemberPrincipal이_없는_인증은_컨트롤러에서_거부한다(
            String path
    ) throws Exception {
        expectError(
                mockMvc.perform(
                        request(path)
                                .with(user("test-admin").roles("ADMIN"))
                ),
                401,
                "AUTHENTICATION_REQUIRED"
        );

        verifyNoInteractions(memberRepository, reportReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 허용된_Origin의_GET_preflight는_인증_없이_성공한다(
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

        verifyNoInteractions(memberRepository, reportReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 허용되지_않은_Origin의_preflight는_거부한다(String path)
            throws Exception {
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
                ));

        verifyNoInteractions(memberRepository, reportReadService);
    }

    @ParameterizedTest
    @MethodSource("disallowedCorsMethods")
    void 조회_경로에는_변경_메서드의_preflight를_허용하지_않는다(
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

        verifyNoInteractions(memberRepository, reportReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LIST_PATH, DETAIL_PATH})
    void 허용되지_않은_Origin의_실제_GET도_거부한다(String path)
            throws Exception {
        // 인증 실패와 CORS 거부를 혼동하지 않도록 정상 세션을 준비한다.
        givenValidSession(MemberRole.ADMIN);

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

        verifyNoInteractions(reportReadService);
    }

    static Stream<Arguments> disallowedCorsMethods() {
        return Stream.of(LIST_PATH, DETAIL_PATH)
                .flatMap(path ->
                        Stream.of("POST", "PUT", "PATCH", "DELETE")
                                .map(method -> Arguments.of(path, method))
                );
    }

    private ResultActions expectSuccess(ResultActions response)
            throws Exception {
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

    private ResultActions expectError(
            ResultActions response,
            int expectedStatus,
            String expectedCode
    ) throws Exception {
        return response
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
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
                ));
    }

    private void stubEmptyList(AdminReportReadQuery query) {
        when(reportReadService.getReports(ACTOR_ID, VERSION, query))
                .thenReturn(Page.empty(
                        PageRequest.of(query.page(), query.size())
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
                        ACTOR_ID,
                        MemberStatus.ACTIVE,
                        VERSION,
                        role
                ))
                .thenReturn(true);
    }

    private MemberPrincipal principal(MemberRole role) {
        return new MemberPrincipal(
                ACTOR_ID,
                "admin-report-read@example.com",
                "test-only-password-hash",
                "신고조회관리자",
                MemberStatus.ACTIVE,
                role,
                VERSION
        );
    }

    private AdminReportReadQuery defaultQuery() {
        return new AdminReportReadQuery(
                AdminReportTargetType.ALL,
                null,
                null,
                null,
                null,
                0,
                20,
                AdminReportSort.LATEST
        );
    }
}