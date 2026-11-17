package io.github.takgeun.iyum.admin.dashboard.api;

import io.github.takgeun.iyum.admin.dashboard.api.dto.AdminDashboardResponse;
import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardReadException;
import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardReadService;
import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardResult;
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
        value = "/api/v1/admin/dashboard",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Tag(
        name = "관리자 대시보드",
        description = "운영 요약 수치와 처리 대기 신고 조회"
)
public class AdminDashboardReadController {

    private final AdminDashboardReadService dashboardReadService;

    @Operation(
            summary = "관리자 대시보드 조회",
            description = """
                    관리자 로그인 세션이 필요합니다.
                    GET 요청에는 CSRF 토큰이 필요하지 않습니다.

                    queriedAt은 서버에서 정한 조회 기준 시각이며,
                    baseDate는 해당 시각의 Asia/Seoul 기준 날짜입니다.
                    오늘 집계 범위는 baseDate의 00:00 이상,
                    다음 날 00:00 미만입니다.

                    오늘 접수 신고 수는 현재 신고 상태와 무관합니다.
                    오늘 작성 게시글 수는 현재 숨김·삭제 여부와 무관합니다.
                    오늘 생성 회원 계정 수는 현재 회원 상태와 무관합니다.

                    처리 대기 신고 수는 접수 날짜와 무관하게
                    현재 RECEIVED 상태인 신고 전체를 집계합니다.
                    대상 콘텐츠의 숨김·삭제 여부는 집계에 영향을 주지 않으며,
                    동일 대상의 여러 신고도 각각 한 건으로 집계합니다.

                    처리 대기 목록은 접수 시각 오름차순,
                    접수 시각이 같으면 신고 ID 오름차순으로 최대 5건을 반환합니다.
                    처리 대기 전체 건수와 반환된 목록 길이는 다를 수 있습니다.

                    집계 대상이 없으면 해당 수치는 0이며,
                    처리 대기 신고가 없으면 목록은 빈 배열입니다.

                    queriedAt은 집계 날짜 계산의 기준이며,
                    모든 조회가 동일한 DB 스냅샷임을 보장하지 않습니다.
                    응답에는 Cache-Control: no-store를 적용합니다.
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
                                    implementation = AdminDashboardResponse.class
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "mixed",
                                            summary = "게시글·댓글 처리 대기 신고가 있는 경우",
                                            value = """
                                                    {
                                                      "queriedAt": "2026-11-17T03:00:00Z",
                                                      "baseDate": "2026-11-17",
                                                      "counts": {
                                                        "pendingReportCount": 2,
                                                        "todayReportCount": 3,
                                                        "todayPostCount": 4,
                                                        "todayMemberCount": 2
                                                      },
                                                      "pendingReports": [
                                                        {
                                                          "reportId": 101,
                                                          "targetType": "POST",
                                                          "targetId": 89,
                                                          "reasonType": "SPAM_ADVERTISING",
                                                          "createdAt": "2026-11-16T01:00:00Z"
                                                        },
                                                        {
                                                          "reportId": 102,
                                                          "targetType": "COMMENT",
                                                          "targetId": 166,
                                                          "reasonType": "ABUSE_HATE",
                                                          "createdAt": "2026-11-17T01:00:00Z"
                                                        }
                                                      ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "empty",
                                            summary = "처리 대기 신고와 오늘 집계 대상이 없는 경우",
                                            value = """
                                                    {
                                                      "queriedAt": "2026-11-17T03:00:00Z",
                                                      "baseDate": "2026-11-17",
                                                      "counts": {
                                                        "pendingReportCount": 0,
                                                        "todayReportCount": 0,
                                                        "todayPostCount": 0,
                                                        "todayMemberCount": 0
                                                      },
                                                      "pendingReports": []
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
                            examples = @ExampleObject(
                                    name = "AUTHENTICATION_REQUIRED",
                                    value = """
                                            {
                                              "code": "AUTHENTICATION_REQUIRED",
                                              "message": "유효한 로그인 정보가 필요합니다.",
                                              "fieldErrors": []
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = """
                            관리자 권한 필요.
                            Security의 ACCESS_DENIED와
                            서비스의 ADMIN_REQUIRED를 구분합니다.
                            """,
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
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "ACCESS_DENIED",
                                            summary = "Security에서 관리자 권한이 없는 요청을 거부",
                                            value = """
                                                    {
                                                      "code": "ACCESS_DENIED",
                                                      "message": "접근 권한이 없습니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "ADMIN_REQUIRED",
                                            summary = "서비스에서 관리자 권한 검증 실패",
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
    public ResponseEntity<AdminDashboardResponse> getDashboard(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        if(principal == null) {
            throw new AdminDashboardReadException(
                    AdminDashboardReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        AdminDashboardResult result = dashboardReadService.getDashboard(
                principal.getMemberId(),
                principal.getCredentialVersion()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminDashboardResponse.from(result));
    }
}