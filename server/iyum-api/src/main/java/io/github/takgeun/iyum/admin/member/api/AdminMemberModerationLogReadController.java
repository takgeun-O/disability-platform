package io.github.takgeun.iyum.admin.member.api;

import io.github.takgeun.iyum.admin.member.api.dto.AdminMemberModerationLogListResponse;
import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationLogReadService;
import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationLogResult;
import io.github.takgeun.iyum.admin.member.application.AdminMemberReadException;
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
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/v1/admin/members/{memberId}/moderation-logs",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "관리자 회원 운영 이력", description = "회원별 이용 정지·해제 이력 조회")
public class AdminMemberModerationLogReadController {

    private final AdminMemberModerationLogReadService readService;

    @Operation(
            summary = "회원 이용 정지·해제 이력 조회",
            description =
                    """
                    현재 ACTIVE 상태의 관리자 로그인 세션이 필요합니다.
                    관리자 자신의 이력도 허용하며 조치 실행의 대상 상태·역할 제한은 적용하지 않습니다.
                    별도 sort 파라미터는 지원하지 않습니다. page > 0이면 빈 페이지에도 hasPrevious=true입니다.
                    게시글·댓글 운영 기록과 별개이며 회원 이메일·비밀번호 해시·인증 버전·관리자 닉네임은 반환하지 않습니다.
                    GET 조회에는 CSRF 토큰을 요구하지 않습니다.

                    처리 시각 내림차순, 동일 시각이면 이력 ID 내림차순으로 조회합니다.
                    서버에서 전체 이력을 정렬한 뒤 페이지를 적용합니다.

                    page는 0 이상, size는 1 이상 50 이하입니다.
                    page × size는 Integer.MAX_VALUE 이하여야 합니다.
                    page와 size를 생략하거나 빈 문자열로 전달하면 각각 0과 20을 적용합니다.

                    회원이 존재하지만 이력이 없으면 200과 빈 items를 반환합니다.
                    범위 밖 페이지도 200이며 전체 이력 개수와 페이지 수를 유지합니다.
                    대상 회원이 존재하지 않으면 404를 반환합니다.

                    탈퇴 완료 회원도 회원 행이 존재하면 조회할 수 있습니다.
                    beforeStatus와 afterStatus는 조치 당시의 저장된 상태입니다.
                    현재 회원 상태와 다를 수 있습니다.
                    이력 수정·삭제 기능은 제공하지 않습니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "조회 성공. Cache-Control: no-store",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        AdminMemberModerationLogListResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "history",
                                            value =
                                                    """
                                                    {
                                                      "items": [
                                                        {
                                                          "logId": 102,
                                                          "actorMemberId": 40,
                                                          "targetMemberId": 20,
                                                          "action": "LIFT_SUSPENSION",
                                                          "beforeStatus": "SUSPENDED",
                                                          "afterStatus": "ACTIVE",
                                                          "reason": "재검토 후 이용 정지 해제",
                                                          "createdAt": "2026-11-12T01:00:00Z"
                                                        },
                                                        {
                                                          "logId": 101,
                                                          "actorMemberId": 40,
                                                          "targetMemberId": 20,
                                                          "action": "SUSPEND",
                                                          "beforeStatus": "ACTIVE",
                                                          "afterStatus": "SUSPENDED",
                                                          "reason": "반복적인 이용 규칙 위반",
                                                          "createdAt": "2026-11-12T00:00:00Z"
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
                                    @ExampleObject(
                                            name = "empty",
                                            value =
                                                    """
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
                                })),
        @ApiResponse(
                responseCode = "400",
                description =
                        """
                        숫자 변환 실패: INVALID_REQUEST_PARAMETER.
                        페이지·크기·offset 범위 오류: INVALID_PAGE_REQUEST.
                        """,
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 정보 없음 또는 유효하지 않음: AUTHENTICATION_REQUIRED",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "관리자 접근 거부: ACCESS_DENIED 또는 ADMIN_REQUIRED",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "대상 회원 없음 또는 0·음수 회원 ID: MEMBER_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<AdminMemberModerationLogListResponse> getLogs(
            @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "조회 대상 회원 ID") @PathVariable("memberId") Long memberId,
            @Parameter(
                            description = "0부터 시작. page × size ≤ Integer.MAX_VALUE",
                            schema =
                                    @Schema(
                                            type = "integer",
                                            format = "int32",
                                            defaultValue = "0",
                                            minimum = "0"))
                    @RequestParam(name = "page", defaultValue = "0")
                    int page,
            @Parameter(
                            description = "페이지 크기. 1~50",
                            schema =
                                    @Schema(
                                            type = "integer",
                                            format = "int32",
                                            defaultValue = "20",
                                            minimum = "1",
                                            maximum = "50"))
                    @RequestParam(name = "size", defaultValue = "20")
                    int size) {
        if (principal == null) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.AUTHENTICATION_REQUIRED);
        }

        Page<AdminMemberModerationLogResult> result =
                readService.getLogs(
                        principal.getMemberId(),
                        principal.getCredentialVersion(),
                        memberId,
                        page,
                        size);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminMemberModerationLogListResponse.from(result));
    }
}
