package io.github.takgeun.iyum.admin.operation.api;

import io.github.takgeun.iyum.admin.operation.api.dto.AdminOperationSummaryResponse;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogReadException;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationSummaryReadService;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationSummaryResult;
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
        value = "/api/v1/admin/operation-logs/summary",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Tag(
        name = "관리자 운영 기록 요약",
        description = "운영 기록 화면의 전체 요약 수치 조회"
)
public class AdminOperationSummaryReadController {

    private final AdminOperationSummaryReadService summaryReadService;

    @Operation(
            summary = "관리자 운영 기록 요약 조회",
            description = """
                    관리자 로그인 세션이 필요합니다.
                    GET 요청에는 CSRF 토큰이 필요하지 않습니다.
                    현재 회원의 ACTIVE 상태, credentialVersion과 ADMIN 역할을 확인합니다.
                    요청 파라미터 없이 목록 검색·필터·페이지와 무관한 전체 요약을 반환합니다.
                    ContentModerationLog의 게시글·댓글 숨김·숨김 해제 기록 건수를 집계합니다.
                    동일 대상의 반복 기록과 LEGACY 사유도 각각 포함합니다.
                    오늘은 createdAt의 Asia/Seoul 오늘 시작 이상, 다음 날 시작 미만입니다.
                    현재 콘텐츠 상태·삭제·작성자 탈퇴와 무관하게 저장된 기록을 셉니다.
                    회원 제재와 신고 종결 자체는 제외하며 실제 숨김으로 생성된 기록만 포함합니다.
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
                                    implementation = AdminOperationSummaryResponse.class
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "summary",
                                            summary = "운영 기록 요약",
                                            value = """
                                                    {
                                                      "queriedAt": "2026-11-18T03:00:00Z",
                                                      "baseDate": "2026-11-18",
                                                      "counts": {
                                                        "totalOperationLogCount": 20,
                                                        "todayOperationLogCount": 7,
                                                        "todayHideCount": 3,
                                                        "todayRestoreCount": 5
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "empty",
                                            summary = "저장된 운영 기록이 없음",
                                            value = """
                                                    {
                                                      "queriedAt": "2026-11-18T03:00:00Z",
                                                      "baseDate": "2026-11-18",
                                                      "counts": {
                                                        "totalOperationLogCount": 0,
                                                        "todayOperationLogCount": 0,
                                                        "todayHideCount": 0,
                                                        "todayRestoreCount": 0
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
    public ResponseEntity<AdminOperationSummaryResponse> getSummary(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        /**
         * SecurityConfig: /api/v1/admin/**에 ADMIN 권한 요구 OK
         * DevCorsConfig: /api/v1/admin/operation-logs/summary에 GET, OPTIONS 허용
         * MemberSessionStatusFilter: 세션 회원의 현재 상태·인증 버전·역할 검증
         * GlobalExceptionHandler: AdminOperationLogReadException을 HTTP 오류로 변환하고 no-store 적용
         */
        if (principal == null) {
            throw new AdminOperationLogReadException(
                    AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        AdminOperationSummaryResult result =
                summaryReadService.getSummary(
                        principal.getMemberId(),
                        principal.getCredentialVersion()
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminOperationSummaryResponse.from(result));
    }
}
