package io.github.takgeun.iyum.admin.operation.api;

import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.admin.operation.api.dto.AdminOperationLogListResponse;
import io.github.takgeun.iyum.admin.operation.application.*;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 요청 형태
 * GET /api/v1/admin/operation-logs
 * → 기본 최신순
 * <p>
 * GET /api/v1/admin/operation-logs?sort=OLDEST
 * → 오래된순
 * <p>
 * GET /api/v1/admin/operation-logs?targetType=COMMENT&action=HIDE&sort=OLDEST
 * → 댓글 숨김 기록을 오래된순으로 조회
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/v1/admin/operation-logs",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Tag(name = "관리자 운영 기록 조회")
@ApiResponses({
        @ApiResponse(
                responseCode = "400",
                description = "조회 조건 또는 요청 파라미터가 올바르지 않음",
                content = @Content(
                        schema = @Schema(
                                implementation = ApiErrorResponse.class
                        )
                )
        ),
        @ApiResponse(
                responseCode = "401",
                description = "유효한 로그인이 필요함",
                content = @Content(
                        schema = @Schema(
                                implementation = ApiErrorResponse.class
                        )
                )
        ),
        @ApiResponse(
                responseCode = "403",
                description = "관리자 권한이 없음",
                content = @Content(
                        schema = @Schema(
                                implementation = ApiErrorResponse.class
                        )
                )
        )
})
public class AdminOperationLogReadController {

    private final AdminOperationLogReadService operationLogReadService;

    @Operation(
            summary = "관리자 운영 기록 목록 조회",
            description = """
                    게시글·댓글의 숨김 및 복원 기록을 조회합니다.
                    대상 유형, 조치 유형, 사유 유형, 한국 날짜 기준 처리 기간으로 검색합니다.
                    사유 유형을 생략하거나 빈 문자열로 전달하면 모든 사유를 조회합니다.
                    LEGACY는 기존 자유입력 기록을 조회하는 조건입니다.
                    지정한 검색 조건은 모두 AND로 결합합니다.
                    시작일과 종료일은 각각 생략할 수 있으며 종료일 전체를 포함합니다.
                    정렬을 생략하면 LATEST를 적용합니다.
                    LATEST는 처리 시각 내림차순, 같은 시각이면 기록 ID 내림차순입니다.
                    OLDEST는 처리 시각 오름차순, 같은 시각이면 기록 ID 오름차순입니다.
                    처리 시각은 운영 기록의 createdAt을 기준으로 합니다.
                    페이지 번호는 0부터 시작합니다.
                    각 기록의 currentTarget에는 조회 시점의 대상 작성자 정보와
                    게시글 제목, 본문 미리보기를 포함합니다.
                    이는 조치 당시의 콘텐츠 스냅샷이 아닙니다.
                    actorMemberId는 과거 처리 관리자이며 currentTarget.author는 콘텐츠 작성자입니다.
                    관리자 로그인 세션이 필요하며 GET에는 CSRF 토큰이 필요하지 않습니다.
                    댓글의 title은 null이며, 대상을 찾을 수 없으면 currentTarget은 null입니다.
                    현재 요약의 변경은 기존 운영 기록의 조치·사유·처리 시각에 영향을 주지 않습니다.
                    """
    )
    @ApiResponse(responseCode = "200", description = "운영 기록 목록. Cache-Control: no-store",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AdminOperationLogListResponse.class),
                    examples = {
                        @ExampleObject(name = "mixed", summary = "게시글·댓글 정상 목록", value = """
                                {
                                  "items": [
                                    {
                                      "logId": 102,
                                      "actorMemberId": 7,
                                      "targetType": "POST",
                                      "targetId": 104,
                                      "action": "HIDE",
                                      "reasonType": "SPAM_ADVERTISING",
                                      "reasonDetail": null,
                                      "reason": "광고·홍보·스팸",
                                      "createdAt": "2026-11-04T03:01:00Z",
                                      "currentTarget": {
                                        "author": {
                                          "memberId": 42,
                                          "nickname": "이음회원",
                                          "withdrawn": false
                                        },
                                        "title": "보청기 적응 경험",
                                        "contentPreview": "새 보청기에 적응한 경험을 공유합니다."
                                      }
                                    },
                                    {
                                      "logId": 101,
                                      "actorMemberId": 7,
                                      "targetType": "COMMENT",
                                      "targetId": 144,
                                      "action": "RESTORE",
                                      "reasonType": "NO_VIOLATION",
                                      "reasonDetail": "재검토 결과 복원",
                                      "reason": "재검토 결과 복원",
                                      "createdAt": "2026-11-04T03:00:00Z",
                                      "currentTarget": {
                                        "author": {
                                          "memberId": 43,
                                          "nickname": "햇살",
                                          "withdrawn": false
                                        },
                                        "title": null,
                                        "contentPreview": "경험을 나눠 주셔서 감사합니다."
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
                                """),
                        @ExampleObject(name = "withdrawn", summary = "탈퇴 작성자", value = """
                                {
                                  "items": [
                                    {
                                      "logId": 101,
                                      "actorMemberId": 7,
                                      "targetType": "COMMENT",
                                      "targetId": 144,
                                      "action": "RESTORE",
                                      "reasonType": "NO_VIOLATION",
                                      "reasonDetail": "재검토 결과 복원",
                                      "reason": "재검토 결과 복원",
                                      "createdAt": "2026-11-04T03:00:00Z",
                                      "currentTarget": {
                                        "author": {
                                          "memberId": 43,
                                          "nickname": "탈퇴한 회원",
                                          "withdrawn": true
                                        },
                                        "title": null,
                                        "contentPreview": "경험을 나눠 주셔서 감사합니다."
                                      }
                                    }
                                  ],
                                  "page": 0,
                                  "size": 20,
                                  "totalElements": 1,
                                  "totalPages": 1,
                                  "hasNext": false,
                                  "hasPrevious": false
                                }
                                """),
                        @ExampleObject(name = "unavailable", summary = "대상 요약 없음 — 응답 계약 예시", value = """
                                {
                                  "items": [
                                    {
                                      "logId": 102,
                                      "actorMemberId": 7,
                                      "targetType": "POST",
                                      "targetId": 104,
                                      "action": "HIDE",
                                      "reasonType": "SPAM_ADVERTISING",
                                      "reasonDetail": null,
                                      "reason": "광고·홍보·스팸",
                                      "createdAt": "2026-11-04T03:01:00Z",
                                      "currentTarget": null
                                    }
                                  ],
                                  "page": 0,
                                  "size": 20,
                                  "totalElements": 1,
                                  "totalPages": 1,
                                  "hasNext": false,
                                  "hasPrevious": false
                                }
                                """),
                        @ExampleObject(name = "empty", summary = "검색 결과 없음", value = """
                                {
                                  "items": [],
                                  "page": 0,
                                  "size": 20,
                                  "totalElements": 0,
                                  "totalPages": 0,
                                  "hasNext": false,
                                  "hasPrevious": false
                                }
                                """)
                    }))
    @GetMapping
    public ResponseEntity<AdminOperationLogListResponse> getLogs(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "대상 유형, 생략하거나 빈 문자열이면 ALL")
            @RequestParam(name = "targetType", defaultValue = "ALL")
            AdminOperationLogTargetType targetType,

            @Parameter(description = "조치 유형, 생략하거나 빈 문자열이면 전체")
            @RequestParam(name = "action", required = false)
            ModerationAction action,

            @Parameter(
                    description = "시작일, 한국 날짜 기준",
                    example = "2026-11-01"
            )
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @Parameter(
                    description = "종료일, 한국 날짜 기준 해당 날짜 전체 포함",
                    example = "2026-11-04"
            )
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @Parameter(description = "페이지 번호, 0부터 시작")
            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @Parameter(description = "페이지 크기, 기본 20, 최소 1, 최대 50")
            @RequestParam(name = "size", defaultValue = "20")
            int size,

            @Parameter(
                    description = "처리 시각 정렬, 생략하거나 빈 값이면 LATEST",
                    schema = @Schema(
                            allowableValues = {"LATEST", "OLDEST"},
                            defaultValue = "LATEST"
                    )
            )
            @RequestParam(name = "sort", defaultValue = "LATEST")
            AdminOperationLogSort sort,

            @Parameter(
                    description = """
                            처리 사유 유형. 생략하거나 빈 문자열이면 전체 조회.
                            LEGACY는 기존 자유입력 기록 조회에 사용합니다.
                            """,
                    example = "SPAM_ADVERTISING"
            )
            @RequestParam(name = "reasonType", required = false)
            ModerationReasonType reasonType
    ) {
        requirePrincipal(principal);

        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        targetType,
                        action,
                        startDate,
                        endDate,
                        page,
                        size,
                        sort,
                        reasonType
                );

        // 요청 파라미터로 받은 회원 ID가 아니라
        // 로그인 세션에 저장된 인증 정보를 사용한다.
        Page<AdminOperationLogItemResult> result =
                operationLogReadService.getLogs(
                        principal.getMemberId(),
                        principal.getCredentialVersion(),
                        query
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminOperationLogListResponse.from(result));
    }

    private void requirePrincipal(MemberPrincipal principal) {
        if (principal == null) {
            throw new AdminOperationLogReadException(
                    AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
            );
        }
    }
}
