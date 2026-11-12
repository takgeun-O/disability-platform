package io.github.takgeun.iyum.admin.report.api;

import io.github.takgeun.iyum.admin.report.api.dto.AdminReportDetailResponse;
import io.github.takgeun.iyum.admin.report.api.dto.AdminReportListResponse;
import io.github.takgeun.iyum.admin.report.application.AdminReportDetailResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportItemResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportReadException;
import io.github.takgeun.iyum.admin.report.application.AdminReportReadQuery;
import io.github.takgeun.iyum.admin.report.application.AdminReportReadService;
import io.github.takgeun.iyum.admin.report.application.AdminReportSort;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetType;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/v1/admin/reports",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Tag(
        name = "관리자 신고 조회",
        description = "게시글·댓글 신고 목록 및 상세 조회"
)
public class AdminReportReadController {

    private final AdminReportReadService reportReadService;

    @Operation(
            summary = "관리자 신고 목록 조회",
            description = """
                    관리자 로그인 세션이 필요합니다.
                    GET 요청에는 CSRF 토큰이 필요하지 않습니다.
                    대상 유형·상태·사유·접수 날짜 조건은 AND로 결합합니다.
                    날짜는 한국 날짜 기준이며 종료일 전체를 포함합니다.
                    접수 시각과 신고 ID로 정렬한 후 페이지를 적용합니다.
                    제목과 본문 미리보기는 신고 당시 스냅샷이며,
                    currentAuthor는 조회 시점의 현재 작성자입니다.
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
                                    implementation = AdminReportListResponse.class
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "mixed",
                                            value = """
                                                    {
                                                      "items": [
                                                        {
                                                          "reportId": 102,
                                                          "targetType": "POST",
                                                          "targetId": 104,
                                                          "status": "RECEIVED",
                                                          "reasonType": "SPAM_ADVERTISING",
                                                          "titleSnapshot": "신고 당시 게시글 제목",
                                                          "contentSnapshotPreview": "신고 당시 본문입니다.",
                                                          "createdAt": "2026-11-12T01:00:00Z",
                                                          "currentAuthor": {
                                                            "memberId": 42,
                                                            "nickname": "소리나무",
                                                            "withdrawn": false
                                                          }
                                                        },
                                                        {
                                                          "reportId": 101,
                                                          "targetType": "COMMENT",
                                                          "targetId": 144,
                                                          "status": "RECEIVED",
                                                          "reasonType": "SPAM_ADVERTISING",
                                                          "titleSnapshot": null,
                                                          "contentSnapshotPreview": "신고 당시 본문입니다.",
                                                          "createdAt": "2026-11-12T01:00:00Z",
                                                          "currentAuthor": {
                                                            "memberId": 43,
                                                            "nickname": "탈퇴한 회원",
                                                            "withdrawn": true
                                                          }
                                                        }
                                                      ],
                                                      "page": 0,
                                                      "size": 20,
                                                      "totalElements": 2,
                                                      "totalPages": 1,
                                                      "hasNext": false,
                                                      "hasPrevious": false
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "missingCurrentAuthor",
                                            value = """
                                                    {
                                                      "items": [
                                                        {
                                                          "reportId": 102,
                                                          "targetType": "POST",
                                                          "targetId": 104,
                                                          "status": "RECEIVED",
                                                          "reasonType": "SPAM_ADVERTISING",
                                                          "titleSnapshot": "신고 당시 게시글 제목",
                                                          "contentSnapshotPreview": "신고 당시 본문입니다.",
                                                          "createdAt": "2026-11-12T01:00:00Z",
                                                          "currentAuthor": null
                                                        }
                                                      ],
                                                      "page": 0,
                                                      "size": 20,
                                                      "totalElements": 1,
                                                      "totalPages": 1,
                                                      "hasNext": false,
                                                      "hasPrevious": false
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "empty",
                                            value = """
                                                    {
                                                      "items": [],
                                                      "page": 0,
                                                      "size": 20,
                                                      "totalElements": 0,
                                                      "totalPages": 0,
                                                      "hasNext": false,
                                                      "hasPrevious": false
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "outOfRange",
                                            value = """
                                                    {
                                                      "items": [],
                                                      "page": 2,
                                                      "size": 20,
                                                      "totalElements": 21,
                                                      "totalPages": 2,
                                                      "hasNext": false,
                                                      "hasPrevious": true
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "조회 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "INVALID_REQUEST_PARAMETER",
                                            value = """
                                                    {
                                                      "code": "INVALID_REQUEST_PARAMETER",
                                                      "message": "요청 파라미터의 형식과 값을 확인해 주세요.",
                                                      "fieldErrors": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "INVALID_PAGE_REQUEST",
                                            value = """
                                                    {
                                                      "code": "INVALID_PAGE_REQUEST",
                                                      "message": "페이지 번호와 크기를 확인해 주세요.",
                                                      "fieldErrors": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "INVALID_DATE_RANGE",
                                            value = """
                                                    {
                                                      "code": "INVALID_DATE_RANGE",
                                                      "message": "조회 날짜 범위를 확인해 주세요.",
                                                      "fieldErrors": []
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "조회 오류",
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
                    description = "서비스 ADMIN_REQUIRED와 보안 ACCESS_DENIED 구분",
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
                                    )
                            }
                    )
            )
    })
    @GetMapping
    public ResponseEntity<AdminReportListResponse> getReports(
            @Parameter(hidden = true)
            @AuthenticationPrincipal
            MemberPrincipal principal,

            @Parameter(
                    description = "ALL, POST, COMMENT. 기본 ALL"
            )
            @RequestParam(
                    name = "targetType",
                    defaultValue = "ALL"
            )
            AdminReportTargetType targetType,

            @Parameter(
                    description = """
                            RECEIVED, RESOLVED, DISMISSED.
                            생략하거나 빈 문자열이면 전체.
                            ALL은 지원하지 않습니다.
                            """
            )
            @RequestParam(
                    name = "status",
                    required = false
            )
            ContentReportStatus status,

            @Parameter(
                    description = """
                            신고 사유 유형.
                            생략하거나 빈 문자열이면 전체.
                            ALL과 LEGACY는 지원하지 않습니다.
                            """
            )
            @RequestParam(
                    name = "reasonType",
                    required = false
            )
            ContentReportReasonType reasonType,

            @Parameter(
                    description = """
                            한국 날짜 기준 접수 시작일.
                            0001~9999년, 단독 지정 가능, 시작일 포함.
                            """,
                    example = "2026-11-01"
            )
            @RequestParam(
                    name = "startDate",
                    required = false
            )
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate startDate,

            @Parameter(
                    description = """
                            한국 날짜 기준 접수 종료일.
                            0001~9999년, 단독 지정 가능.
                            다음 날 00:00 미포함.
                            시작일 이상이어야 합니다.
                            """,
                    example = "2026-11-12"
            )
            @RequestParam(
                    name = "endDate",
                    required = false
            )
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate endDate,

            @Parameter(
                    description = "0부터 시작. page × size는 2147483647 이하",
                    schema = @Schema(
                            type = "integer",
                            types = {"integer"},
                            format = "int32",
                            defaultValue = "0",
                            minimum = "0"
                    )
            )
            @RequestParam(
                    name = "page",
                    defaultValue = "0"
            )
            int page,

            @Parameter(
                    description = "페이지 크기. 1~50, 기본 20",
                    schema = @Schema(
                            type = "integer",
                            types = {"integer"},
                            format = "int32",
                            defaultValue = "20",
                            minimum = "1",
                            maximum = "50"
                    )
            )
            @RequestParam(
                    name = "size",
                    defaultValue = "20"
            )
            int size,

            @Parameter(
                    description = """
                            LATEST: 접수 시각 DESC → 신고 ID DESC.
                            OLDEST: 접수 시각 ASC → 신고 ID ASC.
                            기본 LATEST.
                            """
            )
            @RequestParam(
                    name = "sort",
                    defaultValue = "LATEST"
            )
            AdminReportSort sort
    ) {
        requirePrincipal(principal);

        AdminReportReadQuery query = new AdminReportReadQuery(
                targetType,
                status,
                reasonType,
                startDate,
                endDate,
                page,
                size,
                sort
        );

        Page<AdminReportItemResult> result =
                reportReadService.getReports(
                        principal.getMemberId(),
                        principal.getCredentialVersion(),
                        query
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminReportListResponse.from(result));
    }

    @Operation(
            summary = "관리자 신고 상세 조회",
            description = """
                    관리자 로그인 세션이 필요합니다.
                    GET 요청에는 CSRF 토큰이 필요하지 않습니다.
                    신고 당시 전체 스냅샷과 현재 콘텐츠를 구분해서 반환합니다.
                    현재 대상이 없으면 currentTarget은 null입니다.
                    신고 자체가 없으면 REPORT_NOT_FOUND를 반환합니다.
                    처리 관리자·처리 시각·처리 사유는 저장된 값을 반환합니다.
                    접수 상태(RECEIVED)에서는 handledByMemberId, handledAt,
                    handlingReason을 생략하지 않고 명시적 null로 반환합니다.
                    처리 완료(RESOLVED) 또는 기각(DISMISSED) 상태에서는
                    해당 신고의 처리 정보를 반환합니다.
                    신고 처리는 게시글·댓글의 숨김 상태나 회원 상태를 변경하지 않습니다.
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
                                    implementation = AdminReportDetailResponse.class
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "post",
                                            summary = "접수 상태의 게시글 신고",
                                            value = """
                                                    {
                                                      "reportId": 102,
                                                      "targetType": "POST",
                                                      "targetId": 104,
                                                      "status": "RECEIVED",
                                                      "reasonType": "SPAM_ADVERTISING",
                                                      "titleSnapshot": "신고 당시 게시글 제목",
                                                      "createdAt": "2026-11-12T01:00:00Z",
                                                      "reporterMemberId": 51,
                                                      "reasonDetail": null,
                                                      "contentSnapshot": "신고 당시 본문입니다.",
                                                      "currentTarget": {
                                                        "targetId": 104,
                                                        "parentPostId": null,
                                                        "author": {
                                                          "memberId": 42,
                                                          "nickname": "소리나무",
                                                          "withdrawn": false
                                                        },
                                                        "title": "현재 게시글 제목",
                                                        "content": "조회 시점의 현재 본문입니다.",
                                                        "hiddenAt": null,
                                                        "deletedAt": null
                                                      },
                                                      "handledByMemberId": null,
                                                      "handledAt": null,
                                                      "handlingReason": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "comment",
                                            summary = "접수 상태의 댓글 신고",
                                            value = """
                                                    {
                                                      "reportId": 101,
                                                      "targetType": "COMMENT",
                                                      "targetId": 144,
                                                      "status": "RECEIVED",
                                                      "reasonType": "SPAM_ADVERTISING",
                                                      "titleSnapshot": null,
                                                      "createdAt": "2026-11-12T01:00:00Z",
                                                      "reporterMemberId": 51,
                                                      "reasonDetail": null,
                                                      "contentSnapshot": "신고 당시 본문입니다.",
                                                      "currentTarget": {
                                                        "targetId": 144,
                                                        "parentPostId": 104,
                                                        "author": {
                                                          "memberId": 43,
                                                          "nickname": "탈퇴한 회원",
                                                          "withdrawn": true
                                                        },
                                                        "title": null,
                                                        "content": "조회 시점의 현재 본문입니다.",
                                                        "hiddenAt": null,
                                                        "deletedAt": null
                                                      },
                                                      "handledByMemberId": null,
                                                      "handledAt": null,
                                                      "handlingReason": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "missingTarget",
                                            summary = "현재 대상을 찾을 수 없는 접수 상태의 신고",
                                            value = """
                                                    {
                                                      "reportId": 102,
                                                      "targetType": "POST",
                                                      "targetId": 104,
                                                      "status": "RECEIVED",
                                                      "reasonType": "SPAM_ADVERTISING",
                                                      "titleSnapshot": "신고 당시 게시글 제목",
                                                      "createdAt": "2026-11-12T01:00:00Z",
                                                      "reporterMemberId": 51,
                                                      "reasonDetail": null,
                                                      "contentSnapshot": "신고 당시 본문입니다.",
                                                      "currentTarget": null,
                                                      "handledByMemberId": null,
                                                      "handledAt": null,
                                                      "handlingReason": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "resolved",
                                            summary = "처리 완료된 게시글 신고",
                                            value = """
                                                    {
                                                      "reportId": 103,
                                                      "targetType": "POST",
                                                      "targetId": 104,
                                                      "status": "RESOLVED",
                                                      "reasonType": "SPAM_ADVERTISING",
                                                      "titleSnapshot": "신고 당시 게시글 제목",
                                                      "createdAt": "2026-11-12T01:00:00Z",
                                                      "reporterMemberId": 51,
                                                      "reasonDetail": null,
                                                      "contentSnapshot": "신고 당시 본문입니다.",
                                                      "currentTarget": {
                                                        "targetId": 104,
                                                        "parentPostId": null,
                                                        "author": {
                                                          "memberId": 42,
                                                          "nickname": "소리나무",
                                                          "withdrawn": false
                                                        },
                                                        "title": "현재 게시글 제목",
                                                        "content": "조회 시점의 현재 본문입니다.",
                                                        "hiddenAt": null,
                                                        "deletedAt": null
                                                      },
                                                      "handledByMemberId": 40,
                                                      "handledAt": "2026-11-13T01:00:00Z",
                                                      "handlingReason": "검토를 완료했습니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "dismissed",
                                            summary = "현재 대상을 찾을 수 없는 기각된 신고",
                                            value = """
                                                    {
                                                      "reportId": 104,
                                                      "targetType": "POST",
                                                      "targetId": 104,
                                                      "status": "DISMISSED",
                                                      "reasonType": "SPAM_ADVERTISING",
                                                      "titleSnapshot": "신고 당시 게시글 제목",
                                                      "createdAt": "2026-11-12T01:00:00Z",
                                                      "reporterMemberId": 51,
                                                      "reasonDetail": null,
                                                      "contentSnapshot": "신고 당시 본문입니다.",
                                                      "currentTarget": null,
                                                      "handledByMemberId": 40,
                                                      "handledAt": "2026-11-13T01:00:00Z",
                                                      "handlingReason": "검토 결과 신고를 기각했습니다."
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "조회 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            ),
                            examples = {
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
                    description = "조회 오류",
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
                    description = "서비스 ADMIN_REQUIRED와 보안 ACCESS_DENIED 구분",
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
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "조회 오류",
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
            )
    })
    @GetMapping("/{reportId}")
    public ResponseEntity<AdminReportDetailResponse> getReport(
            @Parameter(hidden = true)
            @AuthenticationPrincipal
            MemberPrincipal principal,

            @Parameter(
                    description = """
                            신고 ID.
                            0·음수·존재하지 않는 ID는 404 REPORT_NOT_FOUND.
                            숫자 변환 실패·Long 범위 초과는
                            400 INVALID_REQUEST_PARAMETER.
                            """,
                    schema = @Schema(
                            type = "integer",
                            types = {"integer"},
                            format = "int64"
                    )
            )
            @PathVariable("reportId")
            Long reportId
    ) {
        requirePrincipal(principal);

        AdminReportDetailResult result =
                reportReadService.getReport(
                        principal.getMemberId(),
                        principal.getCredentialVersion(),
                        reportId
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminReportDetailResponse.from(result));
    }

    private void requirePrincipal(MemberPrincipal principal) {
        if (principal == null) {
            throw new AdminReportReadException(
                    AdminReportReadException.Code.AUTHENTICATION_REQUIRED
            );
        }
    }
}