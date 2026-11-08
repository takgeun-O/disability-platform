package io.github.takgeun.iyum.admin.content.api;

import io.github.takgeun.iyum.admin.content.application.AdminCommentAuthorResult;
import io.github.takgeun.iyum.admin.content.application.AdminCommentDetailResult;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

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
        controllers = AdminCommentDetailReadController.class,
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
class AdminCommentDetailReadControllerTest {

    private static final String PATH =
            "/api/v1/admin/comments/{commentId}";

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final Long ADMIN_ID = 7L;
    private static final long CREDENTIAL_VERSION = 3L;
    private static final Long POST_ID = 123L;
    private static final Long COMMENT_ID = 456L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-30T10:00:00Z");

    private static final Instant CONTENT_UPDATED_AT =
            Instant.parse("2026-10-30T10:15:00Z");

    private static final Instant UPDATED_AT =
            Instant.parse("2026-10-30T12:00:00Z");

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-30T11:00:00Z");

    private static final Instant DELETED_AT =
            Instant.parse("2026-10-30T12:00:00Z");

    private static final String FULL_CONTENT =
            "첫 번째 줄\n\n두 번째 줄\n" + "😀".repeat(201);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminCommentReadService commentReadService;

    // 실제 회원 세션 상태 필터가 사용하는 저장소.
    @MockitoBean
    MemberRepository memberRepository;

    @Test
    void 관리자_인증_정보로_조회하고_전체_상세_필드를_반환한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(commentReadService.getComment(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                COMMENT_ID
        )).thenReturn(detail(
                AdminPostStatus.HIDDEN,
                AdminCommentStatus.VISIBLE,
                true,
                false
        ));

        // GET에는 CSRF 토큰을 첨부하지 않는다.
        expectSuccess(mockMvc.perform(
                request(COMMENT_ID)
                        .with(user(principal(MemberRole.ADMIN)))
                        // 요청 파라미터로 인증 정보를 덮어쓸 수 없어야 한다.
                        .param("memberId", "999")
                        .param("credentialVersion", "999")
        ))
                .andExpect(jsonPath("$.commentId").value(456))
                .andExpect(jsonPath("$.postId").value(123))
                .andExpect(jsonPath("$.postStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.status").value("VISIBLE"))
                .andExpect(jsonPath("$.content").value(FULL_CONTENT))
                .andExpect(jsonPath("$.author.memberId").value(8))
                .andExpect(jsonPath("$.author.nickname")
                        .value("댓글작성회원"))
                .andExpect(jsonPath("$.author.withdrawn").value(false))
                .andExpect(jsonPath("$.author.email")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.author.passwordHash")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.updatedAt")
                        .value(UPDATED_AT.toString()))
                .andExpect(jsonPath("$.contentUpdatedAt")
                        .value(CONTENT_UPDATED_AT.toString()));

        verify(commentReadService).getComment(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                COMMENT_ID
        );
        verifyNoMoreInteractions(commentReadService);
    }

    @Test
    void 없는_시각_필드도_생략하지_않고_null로_반환한다()
            throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(commentReadService.getComment(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                COMMENT_ID
        )).thenReturn(detail(
                AdminPostStatus.VISIBLE,
                AdminCommentStatus.VISIBLE,
                false,
                false
        ));

        ResultActions response = expectSuccess(mockMvc.perform(
                request(COMMENT_ID)
                        .with(user(principal(MemberRole.ADMIN)))
        ));

        for (String field : new String[]{
                "contentUpdatedAt", "hiddenAt", "deletedAt"
        }) {
            // nullValue만 확인하면 필드 누락을 놓칠 수 있으므로
            // 필드 존재 여부도 함께 확인한다.
            response
                    .andExpect(jsonPath("$." + field).hasJsonPath())
                    .andExpect(jsonPath("$." + field).value(nullValue()));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "HIDDEN, false",
            "HIDDEN, true",
            "DELETED, false",
            "DELETED, true"
    })
    void 숨김과_삭제_댓글도_본문과_탈퇴_작성자_표시를_반환한다(
            AdminCommentStatus commentStatus,
            boolean edited
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(commentReadService.getComment(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                COMMENT_ID
        )).thenReturn(detail(
                AdminPostStatus.DELETED,
                commentStatus,
                edited,
                true
        ));

        ResultActions response = expectSuccess(mockMvc.perform(
                request(COMMENT_ID)
                        .with(user(principal(MemberRole.ADMIN)))
        ))
                .andExpect(jsonPath("$.postStatus").value("DELETED"))
                .andExpect(jsonPath("$.status").value(commentStatus.name()))
                .andExpect(jsonPath("$.content").value(FULL_CONTENT))
                .andExpect(jsonPath("$.author.memberId").value(8))
                .andExpect(jsonPath("$.author.nickname").value("탈퇴한 회원"))
                .andExpect(jsonPath("$.author.withdrawn").value(true))
                .andExpect(jsonPath("$.hiddenAt")
                        .value(HIDDEN_AT.toString()))
                .andExpect(jsonPath("$.contentUpdatedAt").hasJsonPath())
                .andExpect(jsonPath("$.deletedAt").hasJsonPath());

        if (edited) {
            response.andExpect(jsonPath("$.contentUpdatedAt")
                    .value(CONTENT_UPDATED_AT.toString()));
        } else {
            response.andExpect(jsonPath("$.contentUpdatedAt")
                    .value(nullValue()));
        }

        if (commentStatus == AdminCommentStatus.DELETED) {
            response.andExpect(jsonPath("$.deletedAt")
                    .value(DELETED_AT.toString()));
        } else {
            response.andExpect(jsonPath("$.deletedAt")
                    .value(nullValue()));
        }
    }

    @Test
    void 비로그인_요청은_401이고_서비스를_호출하지_않는다()
            throws Exception {
        mockMvc.perform(request(COMMENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verifyNoInteractions(commentReadService);
    }

    @Test
    void 일반_회원은_403이고_서비스를_호출하지_않는다()
            throws Exception {
        givenValidSession(MemberRole.USER);

        mockMvc.perform(
                        request(COMMENT_ID)
                                .with(user(principal(MemberRole.USER)))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verifyNoInteractions(commentReadService);
    }

    @Test
    void 현재_회원_정보와_불일치하는_인증은_401이다()
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
                        request(COMMENT_ID)
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(commentReadService);
    }

    @Test
    void 관리자_권한만_있고_MemberPrincipal이_없으면_401이다()
            throws Exception {
        mockMvc.perform(
                        request(COMMENT_ID)
                                .with(user("other-principal").roles("ADMIN"))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(commentReadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "1.5",
            "9223372036854775808"
    })
    void 댓글_ID_변환에_실패하면_400이고_서비스를_호출하지_않는다(
            String commentId
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        mockMvc.perform(
                        request(commentId)
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        verifyNoInteractions(commentReadService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void 숫자_범위가_유효하지_않은_ID는_서비스_검증을_거쳐_404다(
            long commentId
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(commentReadService.getComment(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                commentId
        )).thenThrow(new AdminContentReadException(
                AdminContentReadException.Code.COMMENT_NOT_FOUND
        ));

        mockMvc.perform(
                        request(commentId)
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));

        verify(commentReadService).getComment(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                commentId
        );
    }

    @ParameterizedTest
    @CsvSource({
            "AUTHENTICATION_REQUIRED, 401",
            "ADMIN_REQUIRED, 403",
            "COMMENT_NOT_FOUND, 404"
    })
    void 서비스_예외를_지정된_HTTP_응답으로_변환한다(
            AdminContentReadException.Code code,
            int expectedStatus
    ) throws Exception {
        givenValidSession(MemberRole.ADMIN);

        when(commentReadService.getComment(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                COMMENT_ID
        )).thenThrow(new AdminContentReadException(code));

        mockMvc.perform(
                        request(COMMENT_ID)
                                .with(user(principal(MemberRole.ADMIN)))
                )
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.message")
                        .value(new AdminContentReadException(code).getMessage()))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ));

        verify(commentReadService).getComment(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                COMMENT_ID
        );
    }

    @Test
    void 허용된_출처의_GET_사전_요청을_허용한다()
            throws Exception {
        mockMvc.perform(
                        options(PATH, COMMENT_ID)
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
    void 단건_조회_경로의_POST_사전_요청은_거부한다()
            throws Exception {
        mockMvc.perform(
                        options(PATH, COMMENT_ID)
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
        mockMvc.perform(
                        get(PATH, COMMENT_ID)
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

    private MockHttpServletRequestBuilder request(Object commentId) {
        return get(PATH, commentId)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ORIGIN, ORIGIN);
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

    private AdminCommentDetailResult detail(
            AdminPostStatus postStatus,
            AdminCommentStatus commentStatus,
            boolean edited,
            boolean withdrawn
    ) {
        return new AdminCommentDetailResult(
                COMMENT_ID,
                POST_ID,
                postStatus,
                FULL_CONTENT,
                new AdminCommentAuthorResult(
                        8L,
                        withdrawn ? "탈퇴한 회원" : "댓글작성회원",
                        withdrawn
                ),
                commentStatus,
                CREATED_AT,
                UPDATED_AT,
                edited ? CONTENT_UPDATED_AT : null,
                commentStatus == AdminCommentStatus.VISIBLE
                        ? null : HIDDEN_AT,
                commentStatus == AdminCommentStatus.DELETED
                        ? DELETED_AT : null
        );
    }
}