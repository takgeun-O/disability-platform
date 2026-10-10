package io.github.takgeun.iyum.auth.api;

import io.github.takgeun.iyum.auth.api.dto.EmailVerificationResendRequest;
import io.github.takgeun.iyum.auth.api.dto.EmailVerificationResendResponse;
import io.github.takgeun.iyum.auth.application.EmailVerificationResendService;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "인증",
        description = "회원가입 및 인증 요청"
)
public class EmailVerificationResendController {

    private final EmailVerificationResendService resendService;

    @Operation(
            summary = "이메일 인증 메일 재전송 접수",
            description = "인증 대기 회원에게 조건 충족 시 새 인증 메일을 요청합니다. "
                    + "없는 이메일, PENDING 이외 회원, 간격·횟수 제한도 같은 202/ACCEPTED 응답입니다. "
                    + "최초 가입을 포함하여 기본 60초 간격, 최근 1시간 최대 5회입니다. "
                    + "202는 실제 발송·도착을 보장하지 않습니다. DB 장애는 공통 서버 오류로 반환합니다. "
                    + "먼저 GET /api/v1/auth/csrf를 호출하고 같은 세션 쿠키와 CSRF 헤더를 유지하세요.",
            parameters = @Parameter(
                    name = "X-CSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "동일 세션의 CSRF 조회 결과 token",
                    schema = @Schema(type = "string")
            )
    )
    @ApiResponse(
            responseCode = "202",
            description = "요청 접수 (발송 보장 없음)",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(
                            implementation = EmailVerificationResendResponse.class
                    )
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_FAILED 또는 INVALID_REQUEST_BODY",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(
                            implementation = ApiErrorResponse.class
                    )
            )
    )
    @ApiResponse(
            responseCode = "403",
            description = "CSRF_TOKEN_INVALID: CSRF 누락 또는 불일치",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(
                            implementation = ApiErrorResponse.class
                    )
            )
    )
    @ApiResponse(
            responseCode = "500",
            description = "INTERNAL_SERVER_ERROR: 서버 처리 실패",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(
                            implementation = ApiErrorResponse.class
                    )
            )
    )
    @PostMapping(
            value = "/api/v1/auth/email/resend",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<EmailVerificationResendResponse> resend(
            @Valid @RequestBody EmailVerificationResendRequest request
    ) {
        resendService.resend(request.email());

        return ResponseEntity
                .accepted()
                .cacheControl(CacheControl.noStore())
                .body(EmailVerificationResendResponse.accepted());
    }
}