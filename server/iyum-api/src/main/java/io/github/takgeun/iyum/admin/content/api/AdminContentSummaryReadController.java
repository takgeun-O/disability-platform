package io.github.takgeun.iyum.admin.content.api;

import io.github.takgeun.iyum.admin.content.api.dto.AdminContentSummaryResponse;
import io.github.takgeun.iyum.admin.content.application.AdminContentReadException;
import io.github.takgeun.iyum.admin.content.application.AdminContentSummaryReadService;
import io.github.takgeun.iyum.admin.content.application.AdminContentSummaryResult;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/v1/admin/content/summary",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Tag(
        name = "관리자 콘텐츠 요약",
        description = "게시글·댓글 관리 화면의 전체 요약 수치 조회"
)
public class AdminContentSummaryReadController {

    private final AdminContentSummaryReadService summaryReadService;

    @Operation(
            summary = "관리자 콘텐츠 요약 조회",
            description = """
                    관리자 로그인 세션이 필요합니다.
                    GET 요청에는 CSRF 토큰이 필요하지 않습니다.
                    현재 회원의 ACTIVE 상태, credentialVersion과 ADMIN 역할을 확인합니다.
                    요청 파라미터 없이 목록 필터·페이지와 무관한 전체 요약을 반환합니다.
                    오늘 작성 수는 Asia/Seoul 기준 오늘 시작 이상, 다음 날 시작 미만으로 집계합니다.
                    오늘 작성 수는 현재 숨김·삭제 여부와 무관합니다.
                    현재 숨김 수는 전체 기간 기준이며 삭제된 콘텐츠는 제외합니다.
                    댓글 집계에는 부모 게시글의 숨김·삭제 상태를 반영하지 않습니다.
                    queriedAt은 집계 날짜 계산의 기준이며,
                    모든 집계가 동일한 DB 스냅샷임을 보장하지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    headers = @Header(
                            name = "Cache-Control",
                            schema = @Schema(
                                    type = "string",
                                    example = "no-store"
                            )
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = AdminContentSummaryResponse.class
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "summary",
                                            summary = "게시글·댓글 요약",
                                            value = """
                                                    {
                                                      "queriedAt": "2026-11-17T03:00:00Z",
                                                      "baseDate": "2026-11-17",
                                                      "counts": {
                                                        "todayPostCount": 7,
                                                        "todayCommentCount": 11,
                                                        "hiddenPostCount": 3,
                                                        "hiddenCommentCount": 5
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "empty",
                                            summary = "집계 대상 없음",
                                            value = """
                                                    {
                                                      "queriedAt": "2026-11-17T03:00:00Z",
                                                      "baseDate": "2026-11-17",
                                                      "counts": {
                                                        "todayPostCount": 0,
                                                        "todayCommentCount": 0,
                                                        "hiddenPostCount": 0,
                                                        "hiddenCommentCount": 0
                                                      }
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = """
                            로그인하지 않았거나 유효하지 않은 로그인 정보.
                            거부한 처리 계층에 따라 message 문구는 다를 수 있습니다.
                            """,
                    headers = @Header(
                            name = "Cache-Control",
                            description = """
                                    no-store를 포함합니다.
                                    MVC 오류와 보안 필터 오류의 전체 헤더 문자열은 다를 수 있습니다.
                                    """,
                            schema = @Schema(
                                    type = "string",
                                    example = "no-store"
                            )
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "authenticationRequired",
                                            summary = "유효한 로그인 정보 필요",
                                            value = """
                                                    {
                                                      "code": "AUTHENTICATION_REQUIRED",
                                                      "message": "유효한 로그인 정보가 필요합니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "loginRequired",
                                            summary = "Security에서 비로그인 요청 거부",
                                            value = """
                                                    {
                                                      "code": "AUTHENTICATION_REQUIRED",
                                                      "message": "로그인이 필요합니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = """
                            관리자 권한 필요.
                            Security에서 거부하면 ACCESS_DENIED,
                            서비스에서 관리자 권한 검증에 실패하면 ADMIN_REQUIRED.
                            """,
                    headers = @Header(
                            name = "Cache-Control",
                            description = """
                                    no-store를 포함합니다.
                                    MVC 오류와 보안 필터 오류의 전체 헤더 문자열은 다를 수 있습니다.
                                    """,
                            schema = @Schema(
                                    type = "string",
                                    example = "no-store"
                            )
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "accessDenied",
                                            summary = "Security 권한 거부",
                                            value = """
                                                    {
                                                      "code": "ACCESS_DENIED",
                                                      "message": "접근 권한이 없습니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "adminRequired",
                                            summary = "서비스 관리자 권한 검증 실패",
                                            value = """
                                                    {
                                                      "code": "ADMIN_REQUIRED",
                                                      "message": "관리자 권한이 필요합니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    @GetMapping
    public ResponseEntity<AdminContentSummaryResponse> getSummary(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        if (principal == null) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        AdminContentSummaryResult result =
                summaryReadService.getSummary(
                        principal.getMemberId(),
                        principal.getCredentialVersion()
                );

        /**
         * 해당 API 정상 응답 형태
         * {
         *   "queriedAt": "2026-11-17T03:00:00Z",
         *   "baseDate": "2026-11-17",
         *   "counts": {
         *     "todayPostCount": 7,
         *     "todayCommentCount": 11,
         *     "hiddenPostCount": 3,
         *     "hiddenCommentCount": 5
         *   }
         * }
         */
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminContentSummaryResponse.from(result));
    }
}
