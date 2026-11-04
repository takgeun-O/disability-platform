package io.github.takgeun.iyum.admin.operation.api;

import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.operation.api.dto.AdminOperationLogListResponse;
import io.github.takgeun.iyum.admin.operation.application.*;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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
                    대상 유형, 조치 유형, 한국 날짜 기준 처리 기간으로 검색합니다.
                    시작일과 종료일은 각각 생략할 수 있으며 종료일 전체를 포함합니다.
                    처리 시각 내림차순, 같은 시각이면 기록 ID 내림차순입니다.
                    페이지 번호는 0부터 시작합니다.
                    """
    )
    @GetMapping
    public ResponseEntity<AdminOperationLogListResponse> getLogs(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "대상 유형, 생략하면 ALL")
            @RequestParam(name = "targetType", defaultValue = "ALL")
            AdminOperationLogTargetType targetType,

            @Parameter(description = "조치 유형, 생략하면 전체")
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
            int size
    ) {
        requirePrincipal(principal);

        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        targetType,
                        action,
                        startDate,
                        endDate,
                        page,
                        size
                );

        // 요청 파라미터로 받은 회원 ID가 아니라
        // 로그인 세션에 저장된 인증 정보를 사용한다.
        Page<AdminOperationLogItemResult> result =
                operationLogReadService.getLogs(
                        principal.getMemberId(),
                        principal.getCredentialVersion(),
                        query
                );

        /**
         * 응답 예시
         * {
         *   "items": [
         *     {
         *       "logId": 101,
         *       "actorMemberId": 7,
         *       "targetType": "COMMENT",
         *       "targetId": 456,
         *       "action": "HIDE",
         *       "reason": "운영 정책 위반",
         *       "createdAt": "2026-11-04T03:00:00Z"
         *     }
         *   ],
         *   "page": 0,
         *   "size": 20,
         *   "totalElements": 1,
         *   "totalPages": 1,
         *   "hasNext": false,
         *   "hasPrevious": false
         * }
         */
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminOperationLogListResponse.from(result));
    }

    private void requirePrincipal(MemberPrincipal principal) {
        if(principal == null) {
            throw new AdminOperationLogReadException(
                    AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
            );
        }
    }
}
