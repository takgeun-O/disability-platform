package io.github.takgeun.iyum.admin.operation.api;

import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.admin.operation.application.*;
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
import org.junit.jupiter.params.provider.NullAndEmptySource;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminOperationLogReadController.class,
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
class AdminOperationLogReadControllerTest {

    private static final String PATH =
            "/api/v1/admin/operation-logs";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Long MEMBER_ID = 7L;
    private static final long CREDENTIAL_VERSION = 3L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-04T03:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminOperationLogReadService operationLogReadService;

    @MockitoBean
    MemberRepository memberRepository;

    @Test
    void 기본_조건으로_조회하고_세션의_인증_정보를_사용한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminOperationLogItemResult item =
                new AdminOperationLogItemResult(
                        101L,
                        8L,
                        AdminOperationLogTargetType.COMMENT,
                        456L,
                        ModerationAction.HIDE,
                        ModerationReasonType.OTHER,
                        "운영 정책 위반",
                        "운영 정책 위반",
                        CREATED_AT,
                        new AdminOperationLogCurrentTargetResult(
                                new AdminOperationLogTargetAuthorResult(42L, "댓글작성자", false),
                                null,
                                "현재 댓글 본문 미리보기"
                        )
                );

        when(operationLogReadService.getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        )).thenReturn(new PageImpl<>(
                List.of(item),
                PageRequest.of(0, 20),
                1
        ));

        // GET 조회이므로 CSRF 토큰을 넣지 않는다.
        expectSuccess(mockMvc.perform(
                request()
                        .with(user(principal(MemberRole.ADMIN)))
                        // 요청자가 인증 정보를 임의로 보내도 무시해야 한다.
                        .param("memberId", "999")
                        .param("actorMemberId", "999")
                        .param("credentialVersion", "999")
        ))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].logId").value(101))
                .andExpect(jsonPath("$.items[0].actorMemberId").value(8))
                .andExpect(jsonPath("$.items[0].targetType")
                        .value("COMMENT"))
                .andExpect(jsonPath("$.items[0].targetId").value(456))
                .andExpect(jsonPath("$.items[0].action").value("HIDE"))
                .andExpect(jsonPath("$.items[0].reason")
                        .value("운영 정책 위반"))
                .andExpect(jsonPath("$.items[0].reasonType")
                        .value("OTHER"))
                .andExpect(jsonPath("$.items[0].reasonDetail")
                        .value("운영 정책 위반"))
                .andExpect(jsonPath("$.items[0].createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.items[0].currentTarget.author.memberId").value(42))
                .andExpect(jsonPath("$.items[0].currentTarget.author.nickname").value("댓글작성자"))
                .andExpect(jsonPath("$.items[0].currentTarget.author.withdrawn").value(false))
                .andExpect(jsonPath("$.items[0].currentTarget.author.email").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].currentTarget.author.passwordHash").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].currentTarget.title").hasJsonPath())
                .andExpect(jsonPath("$.items[0].currentTarget.title").value(nullValue()))
                .andExpect(jsonPath("$.items[0].currentTarget.contentPreview").value("현재 댓글 본문 미리보기"))
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].passwordHash")
                        .doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(operationLogReadService).getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        );
        verifyNoMoreInteractions(operationLogReadService);
    }

    @Test
    void 게시글_요약의_작성자와_제목과_미리보기를_반환한다() throws Exception {
        givenValidSession(MemberRole.ADMIN);
        // 서비스가 이미 만든 탈퇴 표시를 직렬화한다. 회원 상태 변환 자체를 검증하는 테스트가 아니다.
        var current = new AdminOperationLogCurrentTargetResult(
                new AdminOperationLogTargetAuthorResult(42L, "탈퇴한 회원", true),
                "현재 저장된 게시글 제목", "현재 게시글 본문 미리보기"
        );
        var item = new AdminOperationLogItemResult(
                102L, 8L, AdminOperationLogTargetType.POST, 123L,
                ModerationAction.RESTORE, ModerationReasonType.NO_VIOLATION,
                null, "기록 생성 당시 저장된 표시 문구", CREATED_AT, current
        );
        when(operationLogReadService.getLogs(MEMBER_ID, CREDENTIAL_VERSION, defaultQuery()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        expectSuccess(mockMvc.perform(request().with(user(principal(MemberRole.ADMIN)))))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].logId").value(102))
                .andExpect(jsonPath("$.items[0].actorMemberId").value(8))
                .andExpect(jsonPath("$.items[0].targetType").value("POST"))
                .andExpect(jsonPath("$.items[0].targetId").value(123))
                .andExpect(jsonPath("$.items[0].action").value("RESTORE"))
                .andExpect(jsonPath("$.items[0].reasonType").value("NO_VIOLATION"))
                .andExpect(jsonPath("$.items[0].reasonDetail").hasJsonPath())
                .andExpect(jsonPath("$.items[0].reasonDetail").value(nullValue()))
                .andExpect(jsonPath("$.items[0].reason").value("기록 생성 당시 저장된 표시 문구"))
                .andExpect(jsonPath("$.items[0].createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.items[0].currentTarget.author.memberId").value(42))
                .andExpect(jsonPath("$.items[0].currentTarget.author.nickname").value("탈퇴한 회원"))
                .andExpect(jsonPath("$.items[0].currentTarget.author.withdrawn").value(true))
                .andExpect(jsonPath("$.items[0].currentTarget.author.email").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].currentTarget.author.passwordHash").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].currentTarget.title").value("현재 저장된 게시글 제목"))
                .andExpect(jsonPath("$.items[0].currentTarget.contentPreview").value("현재 게시글 본문 미리보기"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(operationLogReadService).getLogs(MEMBER_ID, CREDENTIAL_VERSION, defaultQuery());
        verifyNoMoreInteractions(operationLogReadService);
    }

    @ParameterizedTest
    @EnumSource(value = AdminOperationLogTargetType.class, names = {"POST", "COMMENT"})
    void 대상_조회_불가여도_기록과_페이지를_유지하고_currentTarget을_null로_반환한다(
            AdminOperationLogTargetType targetType
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);
        var item = new AdminOperationLogItemResult(
                103L, 8L, targetType, 789L, ModerationAction.HIDE,
                ModerationReasonType.LEGACY, null, "기존 자유 입력 원문", CREATED_AT, null
        );
        when(operationLogReadService.getLogs(MEMBER_ID, CREDENTIAL_VERSION, defaultQuery()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 21));

        expectSuccess(mockMvc.perform(request().with(user(principal(MemberRole.ADMIN)))))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].logId").value(103))
                .andExpect(jsonPath("$.items[0].actorMemberId").value(8))
                .andExpect(jsonPath("$.items[0].targetType").value(targetType.name()))
                .andExpect(jsonPath("$.items[0].targetId").value(789))
                .andExpect(jsonPath("$.items[0].action").value("HIDE"))
                .andExpect(jsonPath("$.items[0].reasonType").value("LEGACY"))
                .andExpect(jsonPath("$.items[0].reasonDetail").hasJsonPath())
                .andExpect(jsonPath("$.items[0].reasonDetail").value(nullValue()))
                .andExpect(jsonPath("$.items[0].reason").value("기존 자유 입력 원문"))
                .andExpect(jsonPath("$.items[0].createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.items[0].currentTarget").hasJsonPath())
                .andExpect(jsonPath("$.items[0].currentTarget").value(nullValue()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(operationLogReadService).getLogs(MEMBER_ID, CREDENTIAL_VERSION, defaultQuery());
        verifyNoMoreInteractions(operationLogReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "ALL, HIDE, LATEST",
            "ALL, HIDE, OLDEST",
            "POST, RESTORE, LATEST",
            "POST, RESTORE, OLDEST",
            "COMMENT, HIDE, LATEST",
            "COMMENT, HIDE, OLDEST"
    })
    void 필터와_날짜와_페이지_조건을_서비스에_전달한다(
            AdminOperationLogTargetType targetType,
            ModerationAction action,
            AdminOperationLogSort sort
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminOperationLogReadQuery expectedQuery =
                new AdminOperationLogReadQuery(
                        targetType,
                        action,
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 4),
                        2,
                        10,
                        sort
                );

        // 전체 15건 중 2번 페이지는 범위 밖이므로 빈 목록이다.
        when(operationLogReadService.getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                expectedQuery
        )).thenReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(2, 10),
                15
        ));

        expectSuccess(mockMvc.perform(
                request()
                        .with(user(principal(MemberRole.ADMIN)))
                        .param("targetType", targetType.name())
                        .param("action", action.name())
                        .param("startDate", "2026-11-01")
                        .param("endDate", "2026-11-04")
                        .param("page", "2")
                        .param("size", "10")
                        .param("sort", sort.name())
        ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));

        verify(operationLogReadService).getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                expectedQuery
        );

        verifyNoMoreInteractions(operationLogReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"startDate", "endDate"})
    void 시작일과_종료일은_각각_단독으로_지정할_수_있다(
            String parameter
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        LocalDate date = LocalDate.of(2026, 11, 4);

        AdminOperationLogReadQuery expectedQuery =
                new AdminOperationLogReadQuery(
                        null,
                        null,
                        parameter.equals("startDate") ? date : null,
                        parameter.equals("endDate") ? date : null,
                        0,
                        20
                );

        when(operationLogReadService.getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                expectedQuery
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        expectSuccess(mockMvc.perform(
                request()
                        .with(user(principal(MemberRole.ADMIN)))
                        .param(parameter, "2026-11-04")
        ));

        verify(operationLogReadService).getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                expectedQuery
        );
    }

    @Test
    void 검색_결과가_없어도_200과_빈_목록을_반환한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(operationLogReadService.getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        expectSuccess(mockMvc.perform(
                request().with(user(principal(MemberRole.ADMIN)))
        ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(request())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(memberRepository, operationLogReadService);
    }

    @Test
    void 유효한_일반_회원은_403이다() throws Exception {
        givenValidSession(MemberRole.USER);

        mockMvc.perform(
                        request().with(user(principal(MemberRole.USER)))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(operationLogReadService);
    }

    @Test
    void 세션_검증에_실패한_관리자는_401이다() throws Exception {
        when(memberRepository
                .existsByIdAndStatusAndCredentialVersionAndRole(
                        MEMBER_ID,
                        MemberStatus.ACTIVE,
                        CREDENTIAL_VERSION,
                        MemberRole.ADMIN
                ))
                .thenReturn(false);

        mockMvc.perform(
                        request().with(user(principal(MemberRole.ADMIN)))
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

        verifyNoInteractions(operationLogReadService);
    }

    @Test
    void MemberPrincipal이_아니면_401이다() throws Exception {
        mockMvc.perform(
                        request().with(user("other-user").roles("ADMIN"))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(memberRepository, operationLogReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "page, -1",
            "size, 0",
            "size, -1",
            "size, 51",
            "page, 2147483647"
    })
    void 잘못된_페이지_조건은_400이다(
            String name,
            String value
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        request()
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

        verifyNoInteractions(operationLogReadService);
    }

    @Test
    void 시작일이_종료일보다_늦으면_400이다() throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        request()
                                .with(user(principal(MemberRole.ADMIN)))
                                .param("startDate", "2026-11-05")
                                .param("endDate", "2026-11-04")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_DATE_RANGE"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));

        verifyNoInteractions(operationLogReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "page, abc",
            "size, abc",
            "page, 2147483648",
            "targetType, UNKNOWN",
            "action, DELETE",
            "startDate, not-a-date",
            "endDate, 2026-13-01",
            "sort, UNKNOWN",
            "sort, latest",
            "sort, oldest"
    })
    void 파라미터_형식이_잘못되면_400이다(
            String name,
            String value
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        request()
                                .with(user(principal(MemberRole.ADMIN)))
                                .param(name, value)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(operationLogReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "AUTHENTICATION_REQUIRED, 401",
            "ADMIN_REQUIRED, 403",
            "INVALID_PAGE_REQUEST, 400",
            "INVALID_DATE_RANGE, 400"
    })
    void 서비스_예외를_공통_오류_응답으로_변환한다(
            AdminOperationLogReadException.Code code,
            int expectedStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminOperationLogReadException exception =
                new AdminOperationLogReadException(code);

        when(operationLogReadService.getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        )).thenThrow(exception);

        mockMvc.perform(
                        request().with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(code.name()))
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
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ));
    }

    @Test
    void 허용된_Origin의_GET_사전_요청은_인증_없이_통과한다()
            throws Exception {
        mockMvc.perform(
                        options(PATH)
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

        verifyNoInteractions(memberRepository, operationLogReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void 조회_외_메서드의_사전_요청은_거부한다(String method)
            throws Exception {
        mockMvc.perform(
                        options(PATH)
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

        verifyNoInteractions(memberRepository, operationLogReadService);
    }

    @Test
    void 허용되지_않은_Origin의_조회는_거부한다()
            throws Exception {
        // CORS에서 먼저 거부되므로 유효 세션 조회를 stub하지 않는다.
        mockMvc.perform(
                        get(PATH)
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

        verifyNoInteractions(memberRepository, operationLogReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "startDate, 0000-01-01",
            "endDate, 0000-01-01",
            "startDate, +10000-01-01",
            "endDate, +10000-01-01"
    })
    void 허용_연도를_벗어난_날짜는_400이고_서비스를_호출하지_않는다(
            String name,
            String value
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        request()
                                .with(user(principal(MemberRole.ADMIN)))
                                .param(name, value)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_DATE_RANGE"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));

        verifyNoInteractions(operationLogReadService);
    }

    @Test
    void 정렬이_빈_값이면_최신순을_적용한다() throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(operationLogReadService.getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        expectSuccess(mockMvc.perform(
                request()
                        .with(user(principal(MemberRole.ADMIN)))
                        .param("sort", "")
        ));

        verify(operationLogReadService).getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        );
    }

    @ParameterizedTest
    @EnumSource(ModerationReasonType.class)
    void 모든_사유_유형을_조회_조건으로_전달한다(
            ModerationReasonType reasonType
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminOperationLogReadQuery expectedQuery =
                new AdminOperationLogReadQuery(
                        AdminOperationLogTargetType.ALL,
                        null,
                        null,
                        null,
                        0,
                        20,
                        AdminOperationLogSort.LATEST,
                        reasonType
                );

        when(operationLogReadService.getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                expectedQuery
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        // LEGACY도 포함하며, GET 조회이므로 CSRF 토큰은 보내지 않는다.
        expectSuccess(mockMvc.perform(
                request()
                        .with(user(principal(MemberRole.ADMIN)))
                        .param("reasonType", reasonType.name())
        ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(operationLogReadService).getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                expectedQuery
        );
        verifyNoMoreInteractions(operationLogReadService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 사유_유형을_생략하거나_빈_문자열이면_전체_조회한다(
            String reasonType
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(operationLogReadService.getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        MockHttpServletRequestBuilder builder = request()
                .with(user(principal(MemberRole.ADMIN)));

        if (reasonType != null) {
            builder.param("reasonType", reasonType);
        }

        expectSuccess(mockMvc.perform(builder));

        verify(operationLogReadService).getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                defaultQuery()
        );
        verifyNoMoreInteractions(operationLogReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UNKNOWN",
            "ALL",
            "spam_advertising",
            "legacy"
    })
    void 잘못된_사유_유형은_400이고_서비스를_호출하지_않는다(
            String reasonType
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        request()
                                .with(user(principal(MemberRole.ADMIN)))
                                .param("reasonType", reasonType)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verifyNoInteractions(operationLogReadService);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, HIDE, SPAM_ADVERTISING, OLDEST",
            "COMMENT, RESTORE, LEGACY, LATEST",
            "POST, RESTORE, SPAM_ADVERTISING, OLDEST"
    })
    void 사유_유형과_기존_검색_조건을_함께_전달한다(
            AdminOperationLogTargetType targetType,
            ModerationAction action,
            ModerationReasonType reasonType,
            AdminOperationLogSort sort
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        AdminOperationLogReadQuery expectedQuery =
                new AdminOperationLogReadQuery(
                        targetType,
                        action,
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 4),
                        2,
                        10,
                        sort,
                        reasonType
                );

        when(operationLogReadService.getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                expectedQuery
        )).thenReturn(Page.empty(PageRequest.of(2, 10)));

        expectSuccess(mockMvc.perform(
                request()
                        .with(user(principal(MemberRole.ADMIN)))
                        .param("targetType", targetType.name())
                        .param("action", action.name())
                        .param("reasonType", reasonType.name())
                        .param("startDate", "2026-11-01")
                        .param("endDate", "2026-11-04")
                        .param("page", "2")
                        .param("size", "10")
                        .param("sort", sort.name())
        ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(operationLogReadService).getLogs(
                MEMBER_ID,
                CREDENTIAL_VERSION,
                expectedQuery
        );
        verifyNoMoreInteractions(operationLogReadService);
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

    private MockHttpServletRequestBuilder request() {
        return get(PATH)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ORIGIN, ORIGIN);
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
                "operation-reader@example.com",
                "test-only-password-hash",
                "기록조회회원",
                MemberStatus.ACTIVE,
                role,
                CREDENTIAL_VERSION
        );
    }

    private AdminOperationLogReadQuery defaultQuery() {
        return new AdminOperationLogReadQuery(
                null,
                null,
                null,
                null,
                0,
                20,
                AdminOperationLogSort.LATEST
        );
    }
}