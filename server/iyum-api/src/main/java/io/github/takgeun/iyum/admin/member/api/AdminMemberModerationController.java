package io.github.takgeun.iyum.admin.member.api;

import io.github.takgeun.iyum.admin.member.api.dto.AdminMemberModerationRequest;
import io.github.takgeun.iyum.admin.member.api.dto.AdminMemberModerationResponse;
import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationException;
import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationResult;
import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationService;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "관리자 회원 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/members")
public class AdminMemberModerationController {

    private final AdminMemberModerationService moderationService;

    @Operation(
            summary = "회원 이용 정지",
            description =
                    """
                    ACTIVE 관리자만 사용할 수 있습니다.
                    처리 관리자 ID와 인증 버전은 principal에서만 가져옵니다. USER 역할만 대상이며 PENDING/RESTRICTED/WITHDRAWN/DELETED는 거부합니다.
                    reason은 자유 입력 필수 값입니다(reasonType 사용 안 함). Java isBlank()로 빈 값을 거부하고 strip() 후 1~500 Unicode 코드 포인트를 허용합니다.
                    changed=false여도 관리자·대상·사유를 검증하며 상태·인증 버전·updatedAt·이력을 변경하지 않습니다.
                    실제 변경은 상태·인증 버전 증가·이력 저장을 같은 트랜잭션으로 처리합니다. 이력 저장 실패 시 함께 롤백합니다.
                    로그인 성공 후 CSRF 토큰을 다시 조회하고 동일 세션 쿠키와 X-CSRF-TOKEN을 전달하세요.
                    기존 세션은 다음 보호 요청에서 401 AUTHENTICATION_REQUIRED로 차단됩니다(잘못된 CSRF는 먼저 403일 수 있음).
                    정지 회원의 신규 로그인은 403 ACCOUNT_UNAVAILABLE입니다. 해제 후에도 이전 세션은 되살아나지 않습니다.
                    성공 응답은 Cache-Control: no-store입니다.

                    유효한 로그인 세션과 CSRF 토큰, 처리 사유가 필요합니다.

                    일반 회원의 ACTIVE 상태를 SUSPENDED로 변경합니다.
                    이미 SUSPENDED이면 changed=false를 반환합니다.
                    자기 자신과 관리자 계정은 조치할 수 없습니다.

                    실제 상태가 변경되면 인증 버전을 증가시키고
                    조치 이력을 저장합니다.
                    기존 게시글·댓글의 공개 상태는 변경하지 않습니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "처리 성공. Cache-Control: no-store",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        AdminMemberModerationResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "changed",
                                            value =
                                                    """
                                                    {
                                                      "memberId": 20,
                                                      "status": "SUSPENDED",
                                                      "changed": true
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "unchanged",
                                            value =
                                                    """
                                                    {
                                                      "memberId": 20,
                                                      "status": "SUSPENDED",
                                                      "changed": false
                                                    }
                                                    """)
                                })),
        @ApiResponse(
                responseCode = "400",
                description = "INVALID_REASON 또는 INVALID_REQUEST_BODY / INVALID_REQUEST_PARAMETER",
                content =
                        @Content(
                                schema = @Schema(implementation = ApiErrorResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "reason",
                                            value =
                                                    """
                                                    {
                                                      "code": "INVALID_REASON",
                                                      "message": "처리 사유는 필수이며, 앞뒤 공백을 제거한 뒤 500자 이하여야 합니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """)
                                })),
        @ApiResponse(
                responseCode = "401",
                description = "인증 없음·현재 상태/인증 버전 불일치: AUTHENTICATION_REQUIRED",
                content =
                        @Content(
                                schema = @Schema(implementation = ApiErrorResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "authentication",
                                            value =
                                                    """
                                                    {
                                                      "code": "AUTHENTICATION_REQUIRED",
                                                      "message": "로그인이 필요합니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """)
                                })),
        @ApiResponse(
                responseCode = "403",
                description =
                        "필터 ACCESS_DENIED/CSRF_TOKEN_INVALID, 서비스"
                            + " ADMIN_REQUIRED/SELF_MODERATION_NOT_ALLOWED/ADMIN_TARGET_NOT_ALLOWED",
                content =
                        @Content(
                                schema = @Schema(implementation = ApiErrorResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "csrf",
                                            value =
                                                    """
                                                    {
                                                      "code": "CSRF_TOKEN_INVALID",
                                                      "message": "CSRF 토큰을 확인해 주세요.",
                                                      "fieldErrors": []
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "self",
                                            value =
                                                    """
                                                    {
                                                      "code": "SELF_MODERATION_NOT_ALLOWED",
                                                      "message": "자기 자신에게 회원 운영 조치를 할 수 없습니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """)
                                })),
        @ApiResponse(
                responseCode = "404",
                description = "MEMBER_NOT_FOUND: 존재하지 않거나 0·음수 ID(다른 검증 통과 시)",
                content =
                        @Content(
                                schema = @Schema(implementation = ApiErrorResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "missing",
                                            value =
                                                    """
                                                    {
                                                      "code": "MEMBER_NOT_FOUND",
                                                      "message": "회원을 찾을 수 없습니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """)
                                })),
        @ApiResponse(
                responseCode = "409",
                description = "MEMBER_STATUS_NOT_ALLOWED",
                content =
                        @Content(
                                schema = @Schema(implementation = ApiErrorResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "state",
                                            value =
                                                    """
                                                    {
                                                      "code": "MEMBER_STATUS_NOT_ALLOWED",
                                                      "message": "현재 회원 상태에서는 이용 정지 또는 정지 해제할 수 없습니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """)
                                }))
    })
    @PostMapping(
            value = "/{memberId}/suspend",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminMemberModerationResponse> suspend(
            @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(
                            description =
                                    "대상 회원 ID. 0·음수는 MEMBER_NOT_FOUND, 숫자 변환 실패·Long 범위 초과는"
                                        + " INVALID_REQUEST_PARAMETER",
                            schema = @Schema(type = "integer", format = "int64"))
                    @PathVariable("memberId")
                    Long memberId,
            @RequestBody AdminMemberModerationRequest request) {
        requirePrincipal(principal);

        AdminMemberModerationResult result =
                moderationService.suspend(
                        principal.getMemberId(),
                        principal.getCredentialVersion(),
                        memberId,
                        request.reason());

        return response(result);
    }

    @Operation(
            summary = "회원 이용 정지 해제",
            description =
                    """
                    ACTIVE 관리자만 사용할 수 있습니다.
                    처리 관리자 ID와 인증 버전은 principal에서만 가져옵니다. USER 역할만 대상이며 PENDING/RESTRICTED/WITHDRAWN/DELETED는 거부합니다.
                    reason은 자유 입력 필수 값입니다(reasonType 사용 안 함). Java isBlank()로 빈 값을 거부하고 strip() 후 1~500 Unicode 코드 포인트를 허용합니다.
                    changed=false여도 관리자·대상·사유를 검증하며 상태·인증 버전·updatedAt·이력을 변경하지 않습니다.
                    실제 변경은 상태·인증 버전 증가·이력 저장을 같은 트랜잭션으로 처리합니다. 이력 저장 실패 시 함께 롤백합니다.
                    로그인 성공 후 CSRF 토큰을 다시 조회하고 동일 세션 쿠키와 X-CSRF-TOKEN을 전달하세요.
                    기존 세션은 다음 보호 요청에서 401 AUTHENTICATION_REQUIRED로 차단됩니다(잘못된 CSRF는 먼저 403일 수 있음).
                    정지 회원의 신규 로그인은 403 ACCOUNT_UNAVAILABLE입니다. 해제 후에도 이전 세션은 되살아나지 않습니다.
                    성공 응답은 Cache-Control: no-store입니다.

                    유효한 로그인 세션과 CSRF 토큰, 처리 사유가 필요합니다.

                    일반 회원의 SUSPENDED 상태를 ACTIVE로 변경합니다.
                    이미 ACTIVE이면 changed=false를 반환합니다.
                    자기 자신과 관리자 계정은 조치할 수 없습니다.

                    실제 상태가 변경되면 인증 버전을 증가시키고
                    조치 이력을 저장합니다.
                    정지 이전의 세션은 다시 유효해지지 않으며,
                    대상 회원은 다시 로그인해야 합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "처리 성공. Cache-Control: no-store",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        AdminMemberModerationResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "changed",
                                            value =
                                                    """
                                                    {
                                                      "memberId": 20,
                                                      "status": "ACTIVE",
                                                      "changed": true
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "unchanged",
                                            value =
                                                    """
                                                    {
                                                      "memberId": 20,
                                                      "status": "ACTIVE",
                                                      "changed": false
                                                    }
                                                    """)
                                })),
        @ApiResponse(
                responseCode = "400",
                description = "INVALID_REASON 또는 INVALID_REQUEST_BODY / INVALID_REQUEST_PARAMETER",
                content =
                        @Content(
                                schema = @Schema(implementation = ApiErrorResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "reason",
                                            value =
                                                    """
                                                    {
                                                      "code": "INVALID_REASON",
                                                      "message": "처리 사유는 필수이며, 앞뒤 공백을 제거한 뒤 500자 이하여야 합니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """)
                                })),
        @ApiResponse(
                responseCode = "401",
                description = "인증 없음·현재 상태/인증 버전 불일치: AUTHENTICATION_REQUIRED",
                content =
                        @Content(
                                schema = @Schema(implementation = ApiErrorResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "authentication",
                                            value =
                                                    """
                                                    {
                                                      "code": "AUTHENTICATION_REQUIRED",
                                                      "message": "로그인이 필요합니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """)
                                })),
        @ApiResponse(
                responseCode = "403",
                description =
                        "필터 ACCESS_DENIED/CSRF_TOKEN_INVALID, 서비스"
                            + " ADMIN_REQUIRED/SELF_MODERATION_NOT_ALLOWED/ADMIN_TARGET_NOT_ALLOWED",
                content =
                        @Content(
                                schema = @Schema(implementation = ApiErrorResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "csrf",
                                            value =
                                                    """
                                                    {
                                                      "code": "CSRF_TOKEN_INVALID",
                                                      "message": "CSRF 토큰을 확인해 주세요.",
                                                      "fieldErrors": []
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "self",
                                            value =
                                                    """
                                                    {
                                                      "code": "SELF_MODERATION_NOT_ALLOWED",
                                                      "message": "자기 자신에게 회원 운영 조치를 할 수 없습니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """)
                                })),
        @ApiResponse(
                responseCode = "404",
                description = "MEMBER_NOT_FOUND: 존재하지 않거나 0·음수 ID(다른 검증 통과 시)",
                content =
                        @Content(
                                schema = @Schema(implementation = ApiErrorResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "missing",
                                            value =
                                                    """
                                                    {
                                                      "code": "MEMBER_NOT_FOUND",
                                                      "message": "회원을 찾을 수 없습니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """)
                                })),
        @ApiResponse(
                responseCode = "409",
                description = "MEMBER_STATUS_NOT_ALLOWED",
                content =
                        @Content(
                                schema = @Schema(implementation = ApiErrorResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "state",
                                            value =
                                                    """
                                                    {
                                                      "code": "MEMBER_STATUS_NOT_ALLOWED",
                                                      "message": "현재 회원 상태에서는 이용 정지 또는 정지 해제할 수 없습니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """)
                                }))
    })
    @PostMapping(
            value = "/{memberId}/lift-suspension",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminMemberModerationResponse> liftSuspension(
            @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(
                            description =
                                    "대상 회원 ID. 0·음수는 MEMBER_NOT_FOUND, 숫자 변환 실패·Long 범위 초과는"
                                        + " INVALID_REQUEST_PARAMETER",
                            schema = @Schema(type = "integer", format = "int64"))
                    @PathVariable("memberId")
                    Long memberId,
            @RequestBody AdminMemberModerationRequest request) {
        requirePrincipal(principal);

        AdminMemberModerationResult result =
                moderationService.liftSuspension(
                        principal.getMemberId(),
                        principal.getCredentialVersion(),
                        memberId,
                        request.reason());

        return response(result);
    }

    private void requirePrincipal(MemberPrincipal principal) {
        if (principal == null) {
            throw new AdminMemberModerationException(
                    AdminMemberModerationException.Code.AUTHENTICATION_REQUIRED);
        }
    }

    private ResponseEntity<AdminMemberModerationResponse> response(
            AdminMemberModerationResult result) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminMemberModerationResponse.from(result));
    }
}
