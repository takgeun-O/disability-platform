package io.github.takgeun.iyum.admin.report.api;

import io.github.takgeun.iyum.admin.report.api.dto.AdminReportHandlingRequest;
import io.github.takgeun.iyum.admin.report.api.dto.AdminReportHandlingResponse;
import io.github.takgeun.iyum.admin.report.application.AdminReportHandlingException;
import io.github.takgeun.iyum.admin.report.application.AdminReportHandlingResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportHandlingService;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/v1/admin/reports",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Tag(
        name = "관리자 신고 처리",
        description = "접수된 신고의 처리 완료 및 기각"
)
public class AdminReportHandlingController {

    private final AdminReportHandlingService reportHandlingService;

    @Operation(
            summary = "추가 조치 없이 신고 처리 완료",
            description = """
                    관리자 로그인 세션과 CSRF 토큰이 필요합니다.
                    접수 상태의 신고만 처리할 수 있습니다.
                    이미 종결된 신고는 409 REPORT_ALREADY_HANDLED를 반환합니다.
                    해당 신고 한 건만 처리하며 같은 대상의 다른 신고는 변경하지 않습니다.
                    처리 관리자와 시각은 서버가 결정하고 저장된 처리 정보를 반환합니다.
                    콘텐츠 공개 상태나 회원 상태를 변경하지 않으며 handlingOutcome은 NO_CONTENT_ACTION입니다.
                    콘텐츠 숨김과 함께 처리하려면 POST /api/v1/admin/reports/{reportId}/hide-and-resolve를 사용합니다.
                    """
    )
    @Parameter(
            name = "X-CSRF-TOKEN",
            in = ParameterIn.HEADER,
            required = true,
            description = "로그인 후 같은 세션으로 발급받은 최신 CSRF 토큰",
            schema = @Schema(type = "string")
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(
                            implementation = AdminReportHandlingRequest.class
                    ),
                    examples = @ExampleObject(
                            name = "resolve",
                            value = """
                                    {
                                      "handlingReason": "검토를 완료했습니다."
                                    }
                                    """
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "해당 신고 한 건 처리 성공. 저장된 처리 정보",
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
                                    implementation = AdminReportHandlingResponse.class
                            ),
                            examples = @ExampleObject(
                                    name = "resolve",
                                    value = """
                                            {
                                              "reportId": 102,
                                              "status": "RESOLVED",
                                              "handledByMemberId": 40,
                                              "handledAt": "2026-11-13T01:00:00Z",
                                              "handlingReason": "검토를 완료했습니다.",
                                              "handlingOutcome": "NO_CONTENT_ACTION"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "처리 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                @ExampleObject(
                                        name = "INVALID_HANDLING_REASON",
                                        value = """
                                                {
                                                  "code": "INVALID_HANDLING_REASON",
                                                  "message": "처리 사유를 1자 이상 500자 이하로 입력해 주세요.",
                                                  "fieldErrors": []
                                                }
                                                """
                                ),
                                @ExampleObject(
                                        name = "INVALID_REQUEST_BODY",
                                        value = """
                                                {
                                                  "code": "INVALID_REQUEST_BODY",
                                                  "message": "요청 본문의 JSON 형식과 필드 값을 확인해 주세요.",
                                                  "fieldErrors": []
                                                }
                                                """
                                ),
                                @ExampleObject(
                                        name = "INVALID_REQUEST_PARAMETER",
                                        value = """
                                                {
                                                  "code": "INVALID_REQUEST_PARAMETER",
                                                  "message": "요청 파라미터의 형식과 값을 확인해 주세요.",
                                                  "fieldErrors": []
                                                }
                                                """
                                )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "처리 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                @ExampleObject(
                                        name = "AUTHENTICATION_REQUIRED",
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
                    description = "Security의 ACCESS_DENIED와 서비스의 ADMIN_REQUIRED를 구분",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                @ExampleObject(
                                        name = "ACCESS_DENIED",
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
                                        value = """
                                                {
                                                  "code": "ADMIN_REQUIRED",
                                                  "message": "관리자 권한이 필요합니다.",
                                                  "fieldErrors": []
                                                }
                                                """
                                ),
                                @ExampleObject(
                                        name = "CSRF_TOKEN_INVALID",
                                        value = """
                                                {
                                                  "code": "CSRF_TOKEN_INVALID",
                                                  "message": "CSRF 토큰을 확인해 주세요.",
                                                  "fieldErrors": []
                                                }
                                                """
                                )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "처리 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                @ExampleObject(
                                        name = "REPORT_NOT_FOUND",
                                        value = """
                                                {
                                                  "code": "REPORT_NOT_FOUND",
                                                  "message": "신고 내역을 찾을 수 없습니다.",
                                                  "fieldErrors": []
                                                }
                                                """
                                )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "처리 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                @ExampleObject(
                                        name = "REPORT_ALREADY_HANDLED",
                                        value = """
                                                {
                                                  "code": "REPORT_ALREADY_HANDLED",
                                                  "message": "이미 처리된 신고입니다.",
                                                  "fieldErrors": []
                                                }
                                                """
                                )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "415",
                    description = "처리 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                @ExampleObject(
                                        name = "HTTP_ERROR",
                                        value = """
                                                {
                                                  "code": "HTTP_ERROR",
                                                  "message": "요청을 처리할 수 없습니다.",
                                                  "fieldErrors": []
                                                }
                                                """
                                )
                            }
                    )
            )
    })
    @PostMapping(
            value = "/{reportId}/resolve",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AdminReportHandlingResponse> resolve(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(
                    description = "신고 ID. 0·음수·없는 ID는 404, 숫자 변환 실패는 400",
                    schema = @Schema(
                            type = "integer",
                            types = {"integer"},
                            format = "int64"
                    )
            )
            @PathVariable("reportId") Long reportId,

            @RequestBody AdminReportHandlingRequest request
    ) {
        requirePrincipal(principal);

        AdminReportHandlingResult result = reportHandlingService.resolve(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                reportId,
                request.handlingReason()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminReportHandlingResponse.from(result));
    }

    @Operation(
            summary = "신고 기각",
            description = """
                    관리자 로그인 세션과 CSRF 토큰이 필요합니다.
                    접수 상태의 신고만 기각할 수 있습니다.
                    이미 종결된 신고는 409 REPORT_ALREADY_HANDLED를 반환합니다.
                    해당 신고 한 건만 처리하며 같은 대상의 다른 신고는 변경하지 않습니다.
                    처리 관리자와 시각은 서버가 결정하고 저장된 처리 정보를 반환합니다.
                    신고 기각만으로 콘텐츠 공개 상태나 회원 상태를 변경하지 않습니다.
                    기존에 숨겨진 콘텐츠를 복원하지 않으며 handlingOutcome은 NO_CONTENT_ACTION입니다.
                    """
    )
    @Parameter(
            name = "X-CSRF-TOKEN",
            in = ParameterIn.HEADER,
            required = true,
            description = "로그인 후 같은 세션으로 발급받은 최신 CSRF 토큰",
            schema = @Schema(type = "string")
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(
                            implementation = AdminReportHandlingRequest.class
                    ),
                    examples = @ExampleObject(
                            name = "dismiss",
                            value = """
                                    {
                                      "handlingReason": "검토 결과 신고를 기각했습니다."
                                    }
                                    """
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "해당 신고 한 건 처리 성공. 저장된 처리 정보",
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
                                    implementation = AdminReportHandlingResponse.class
                            ),
                            examples = @ExampleObject(
                                    name = "dismiss",
                                    value = """
                                            {
                                              "reportId": 102,
                                              "status": "DISMISSED",
                                              "handledByMemberId": 40,
                                              "handledAt": "2026-11-13T01:00:00Z",
                                              "handlingReason": "검토 결과 신고를 기각했습니다.",
                                              "handlingOutcome": "NO_CONTENT_ACTION"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "처리 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                @ExampleObject(
                                        name = "INVALID_HANDLING_REASON",
                                        value = """
                                                {
                                                  "code": "INVALID_HANDLING_REASON",
                                                  "message": "처리 사유를 1자 이상 500자 이하로 입력해 주세요.",
                                                  "fieldErrors": []
                                                }
                                                """
                                ),
                                @ExampleObject(
                                        name = "INVALID_REQUEST_BODY",
                                        value = """
                                                {
                                                  "code": "INVALID_REQUEST_BODY",
                                                  "message": "요청 본문의 JSON 형식과 필드 값을 확인해 주세요.",
                                                  "fieldErrors": []
                                                }
                                                """
                                ),
                                @ExampleObject(
                                        name = "INVALID_REQUEST_PARAMETER",
                                        value = """
                                                {
                                                  "code": "INVALID_REQUEST_PARAMETER",
                                                  "message": "요청 파라미터의 형식과 값을 확인해 주세요.",
                                                  "fieldErrors": []
                                                }
                                                """
                                )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "처리 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                @ExampleObject(
                                        name = "AUTHENTICATION_REQUIRED",
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
                    description = "Security의 ACCESS_DENIED와 서비스의 ADMIN_REQUIRED를 구분",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                @ExampleObject(
                                        name = "ACCESS_DENIED",
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
                                        value = """
                                                {
                                                  "code": "ADMIN_REQUIRED",
                                                  "message": "관리자 권한이 필요합니다.",
                                                  "fieldErrors": []
                                                }
                                                """
                                ),
                                @ExampleObject(
                                        name = "CSRF_TOKEN_INVALID",
                                        value = """
                                                {
                                                  "code": "CSRF_TOKEN_INVALID",
                                                  "message": "CSRF 토큰을 확인해 주세요.",
                                                  "fieldErrors": []
                                                }
                                                """
                                )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "처리 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                @ExampleObject(
                                        name = "REPORT_NOT_FOUND",
                                        value = """
                                                {
                                                  "code": "REPORT_NOT_FOUND",
                                                  "message": "신고 내역을 찾을 수 없습니다.",
                                                  "fieldErrors": []
                                                }
                                                """
                                )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "처리 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                @ExampleObject(
                                        name = "REPORT_ALREADY_HANDLED",
                                        value = """
                                                {
                                                  "code": "REPORT_ALREADY_HANDLED",
                                                  "message": "이미 처리된 신고입니다.",
                                                  "fieldErrors": []
                                                }
                                                """
                                )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "415",
                    description = "처리 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                @ExampleObject(
                                        name = "HTTP_ERROR",
                                        value = """
                                                {
                                                  "code": "HTTP_ERROR",
                                                  "message": "요청을 처리할 수 없습니다.",
                                                  "fieldErrors": []
                                                }
                                                """
                                )
                            }
                    )
            )
    })
    @PostMapping(
            value = "/{reportId}/dismiss",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AdminReportHandlingResponse> dismiss(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(
                    description = "신고 ID. 0·음수·없는 ID는 404, 숫자 변환 실패는 400",
                    schema = @Schema(
                            type = "integer",
                            types = {"integer"},
                            format = "int64"
                    )
            )
            @PathVariable("reportId") Long reportId,

            @RequestBody AdminReportHandlingRequest request
    ) {
        requirePrincipal(principal);

        AdminReportHandlingResult result = reportHandlingService.dismiss(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                reportId,
                request.handlingReason()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminReportHandlingResponse.from(result));
    }

    private void requirePrincipal(MemberPrincipal principal) {
        if(principal == null) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED
            );
        }
    }
}
