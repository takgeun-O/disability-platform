package io.github.takgeun.iyum.admin.report.api;

import io.github.takgeun.iyum.admin.report.api.dto.AdminReportHandlingRequest;
import io.github.takgeun.iyum.admin.report.api.dto.AdminReportHandlingResponse;
import io.github.takgeun.iyum.admin.report.application.AdminReportHandlingException;
import io.github.takgeun.iyum.admin.report.application.AdminReportHandlingResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportHideAndResolveService;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/v1/admin/reports",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Tag(name = "관리자 신고 처리")
public class AdminReportHideAndResolveController {

    private final AdminReportHideAndResolveService hideAndResolveService;

    @Operation(
            summary = "콘텐츠 숨김 후 신고 처리 완료",
            description = """
                    관리자 로그인 세션과 CSRF 토큰이 필요합니다.

                    접수 상태의 신고 한 건을 대상으로 콘텐츠를 숨기고
                    신고를 RESOLVED 상태로 처리합니다.

                    정상 콘텐츠를 새로 숨긴 경우:
                    - handlingOutcome은 CONTENT_HIDDEN입니다.
                    - 콘텐츠 숨김 운영 기록을 한 건 저장합니다.

                    이미 개별 숨김 상태인 경우:
                    - handlingOutcome은 CONTENT_ALREADY_HIDDEN입니다.
                    - 기존 숨김 시각을 유지합니다.
                    - 숨김 운영 기록을 추가하지 않습니다.

                    댓글 신고는 해당 댓글만 숨깁니다.
                    부모 게시글이 숨김 상태여도 댓글 개별 숨김은 가능합니다.
                    댓글 또는 부모 게시글이 삭제된 경우에는 거부합니다.

                    이미 처리 완료되거나 기각된 신고는
                    409 REPORT_ALREADY_HANDLED를 반환합니다.

                    같은 콘텐츠에 대한 다른 신고와 회원 상태는 변경하지 않습니다.
                    콘텐츠 숨김, 운영 기록 저장, 신고 처리는 하나의 트랜잭션으로 수행합니다.
                    처리 결과는 저장된 값이며 이후 콘텐츠 상태가 바뀌어도 유지됩니다.
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
                            name = "hideAndResolve",
                            value = """
                                    {
                                      "handlingReason": "운영 정책 위반을 확인하여 숨김 처리합니다."
                                    }
                                    """
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "대상 콘텐츠 숨김 또는 기존 숨김 유지 후 신고 처리 완료",
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
                            examples = {
                                    @ExampleObject(
                                            name = "CONTENT_HIDDEN",
                                            summary = "이번 요청에서 콘텐츠를 숨김",
                                            value = """
                                                    {
                                                      "reportId": 100,
                                                      "status": "RESOLVED",
                                                      "handledByMemberId": 40,
                                                      "handledAt": "2026-11-13T07:00:00Z",
                                                      "handlingReason": "운영 정책 위반을 확인하여 숨김 처리합니다.",
                                                      "handlingOutcome": "CONTENT_HIDDEN"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "CONTENT_ALREADY_HIDDEN",
                                            summary = "기존 개별 숨김 상태를 유지",
                                            value = """
                                                    {
                                                      "reportId": 101,
                                                      "status": "RESOLVED",
                                                      "handledByMemberId": 40,
                                                      "handledAt": "2026-11-13T07:00:00Z",
                                                      "handlingReason": "이미 숨김 처리된 콘텐츠임을 확인했습니다.",
                                                      "handlingOutcome": "CONTENT_ALREADY_HIDDEN"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                            INVALID_HANDLING_REASON: 처리 사유가 유효하지 않음.
                            INVALID_REQUEST_BODY: 요청 본문을 읽을 수 없음.
                            INVALID_REQUEST_PARAMETER: 신고 ID 등의 형식이 유효하지 않음.
                            """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "AUTHENTICATION_REQUIRED: 유효한 로그인 정보가 필요함",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = """
                            ACCESS_DENIED: Security의 관리자 접근 권한 검사 실패.
                            ADMIN_REQUIRED: 서비스의 관리자 권한 검사 실패.
                            CSRF_TOKEN_INVALID: CSRF 토큰 누락 또는 불일치.
                            """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            REPORT_NOT_FOUND: 신고 ID가 0·음수이거나 신고가 없음.
                            POST_NOT_FOUND: 대상 게시글 또는 댓글의 부모 게시글이 없음.
                            COMMENT_NOT_FOUND: 대상 댓글이 없음.
                            """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = """
                            REPORT_ALREADY_HANDLED: 이미 처리 완료되거나 기각된 신고.
                            POST_DELETED: 대상 게시글 또는 댓글의 부모 게시글이 삭제됨.
                            COMMENT_DELETED: 대상 댓글이 삭제됨.
                            """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "415",
                    description = "HTTP_ERROR: 지원하지 않는 요청 Content-Type",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    @PostMapping(
            value = "/{reportId}/hide-and-resolve",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AdminReportHandlingResponse> hideAndResolve(
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

        AdminReportHandlingResult result = hideAndResolveService.hideAndResolve(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                reportId,
                request.handlingReason()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminReportHandlingResponse.from(result));
    }

    private void requirePrincipal(
            MemberPrincipal principal
    ) {
       if(principal == null) {
           throw new AdminReportHandlingException(
                   AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED
           );
       }
    }
}
