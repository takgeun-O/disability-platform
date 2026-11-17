package io.github.takgeun.iyum.admin.report.api;

import io.github.takgeun.iyum.admin.report.api.dto.AdminReportSummaryResponse;
import io.github.takgeun.iyum.admin.report.application.AdminReportReadException;
import io.github.takgeun.iyum.admin.report.application.AdminReportSummaryReadService;
import io.github.takgeun.iyum.admin.report.application.AdminReportSummaryResult;
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
        value = "/api/v1/admin/reports/summary",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Tag(name = "관리자 신고 조회")
public class AdminReportSummaryReadController {

    private final AdminReportSummaryReadService summaryReadService;

    @Operation(
            summary = "관리자 신고 요약 조회",
            description = """
                    관리자 로그인 세션이 필요합니다.
                    GET 요청에는 CSRF 토큰이 필요하지 않습니다.
                    현재 회원의 ACTIVE 상태, credentialVersion과 ADMIN 역할을 확인합니다.
                    로그인 후 상태·권한·인증 버전 변경으로 기존 세션이 차단될 수 있습니다.
                    요청 파라미터 없이 목록 필터·페이지와 무관한 전체 신고 요약을 반환합니다.
                    오늘 집계는 Asia/Seoul 기준 오늘 시작 이상, 다음 날 시작 미만입니다.
                    Clock에서 한 번 읽은 queriedAt으로 baseDate와 기간을 계산합니다.
                    queriedAt은 날짜 계산 기준이며 세 집계의 동일 DB 스냅샷을 보장하지 않습니다.
                    처리 대기는 접수 날짜와 무관하게 집계합니다.
                    오늘 접수는 createdAt 기준입니다.
                    오늘 종결은 handledAt 기준으로 처리 완료와 기각을 포함하며 접수 날짜와 무관합니다.
                    게시글·댓글 신고를 모두 포함하고 현재 콘텐츠의 숨김·삭제로 제외하지 않습니다.
                    콘텐츠의 숨김·복원·삭제만으로 신고 상태가 변경되지는 않습니다.
                    동일 콘텐츠에 대한 여러 신고도 각각 집계합니다.
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
                                    implementation = AdminReportSummaryResponse.class
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "summary",
                                            summary = "신고 요약",
                                            value = """
                                                    {
                                                      "queriedAt": "2026-11-17T03:00:00Z",
                                                      "baseDate": "2026-11-17",
                                                      "counts": {
                                                        "pendingReportCount": 7,
                                                        "todayReportCount": 3,
                                                        "todayHandledReportCount": 2
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
                                                        "pendingReportCount": 0,
                                                        "todayReportCount": 0,
                                                        "todayHandledReportCount": 0
                                                      }
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인하지 않았거나 유효하지 않은 로그인 정보",
                    headers = @Header(
                            name = "Cache-Control",
                            description = "no-store를 포함합니다. MVC 오류와 보안 필터 오류의 전체 헤더 문자열은 다를 수 있습니다.",
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
                                            summary = "로그인 정보 필요",
                                            value = """
                                                    {
                                                      "code": "AUTHENTICATION_REQUIRED",
                                                      "message": "유효한 로그인 정보가 필요합니다.",
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
                            description = "no-store를 포함합니다. MVC 오류와 보안 필터 오류의 전체 헤더 문자열은 다를 수 있습니다.",
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
    public ResponseEntity<AdminReportSummaryResponse> getSummary(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        if (principal == null) {
            throw new AdminReportReadException(
                    AdminReportReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        AdminReportSummaryResult result =
                summaryReadService.getSummary(
                        principal.getMemberId(),
                        principal.getCredentialVersion()
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminReportSummaryResponse.from(result));
    }
}
