package io.github.takgeun.iyum.auth.api;

import io.github.takgeun.iyum.auth.api.dto.EmailVerificationRequest;
import io.github.takgeun.iyum.auth.api.dto.EmailVerificationResponse;
import io.github.takgeun.iyum.auth.application.EmailVerificationResult;
import io.github.takgeun.iyum.auth.application.EmailVerificationService;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

@RestController // 메서드의 반환값을 HTTP 응답 본문에 담아 클라이언트에게 전달하기 위함
@RequiredArgsConstructor
@Tag(
        name = "인증",
        description = "회원가입 및 인증 요청"
)
public class EmailVerificationController {

    private final EmailVerificationService verificationService;

    @Operation(
            summary = "이메일 인증 완료",
            description = "유효한 이메일 인증 토큰으로 PENDING 회원을 "
                    + "ACTIVE로 변경하고 토큰을 사용 처리합니다. "
                    + "인증 성공 후 사용자가 직접 로그인해야 합니다. "
                    + "먼저 GET /api/v1/auth/csrf를 호출하고, "
                    + "같은 세션에서 CSRF 토큰을 X-CSRF-TOKEN 헤더에 전달하세요. "
                    + "이메일 인증 토큰은 JSON 본문의 token 필드에 전달합니다.",
            parameters = @Parameter(
                    name = "X-CSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "GET /api/v1/auth/csrf의 token. "
                            + "해당 조회와 동일한 세션 쿠키를 유지해야 합니다.",
                    schema = @Schema(type = "string")
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = "이메일 인증 완료",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(
                            implementation = EmailVerificationResponse.class
                    ),
                    examples = @ExampleObject(
                            value = """
                                    {"status":"ACTIVE"}
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "입력 오류 또는 사용할 수 없는 인증 토큰",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(
                            implementation = ApiErrorResponse.class
                    ),
                    examples = {
                            @ExampleObject(
                                    name = "validation",
                                    value = """
                                            {
                                              "code":"VALIDATION_FAILED",
                                              "message":"입력값을 확인해 주세요.",
                                              "fieldErrors":[
                                                {
                                                  "field":"token",
                                                  "message":"이메일 인증 토큰 형식이 올바르지 않습니다."
                                                }
                                              ]
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "invalidToken",
                                    value = """
                                            {
                                              "code":"EMAIL_VERIFICATION_INVALID",
                                              "message":"유효하지 않거나 사용할 수 없는 이메일 인증 링크입니다.",
                                              "fieldErrors":[]
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "invalidBody",
                                    value = """
                                            {
                                              "code":"INVALID_REQUEST_BODY",
                                              "message":"요청 본문의 JSON 형식과 필드 값을 확인해 주세요.",
                                              "fieldErrors":[]
                                            }
                                            """
                            )
                    }
            )
    )
    @ApiResponse(
            responseCode = "403",
            description = "CSRF 토큰 누락 또는 불일치",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(
                            implementation = ApiErrorResponse.class
                    ),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code":"CSRF_TOKEN_INVALID",
                                      "message":"CSRF 토큰을 확인해 주세요.",
                                      "fieldErrors":[]
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "500",
            description = "처리하지 못한 서버 오류",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(
                            implementation = ApiErrorResponse.class
                    )
            )
    )
    @PostMapping(
            value = "/api/v1/auth/email/verify",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<EmailVerificationResponse> verify(
            @Valid @RequestBody EmailVerificationRequest request
    ) {

        /**
         * 1. JSON을 EmailVerificationRequest로 변환 -> 실패 시 기본적으로 MethodArgumentNotValidException 발생 -> 기존 예외 처리기가 400 응답으로 바꿔줌
         * 2. @Valid로 입력을 검사 -> 실패 시 기본적으로 MethodArgumentNotValidException 발생 -> 기존 예외 처리기가 400 응답으로 바꿔줌
         * 3. 서비스에서 토큰 검증과 회원 활성화를 처리
         * 4. 결과를 응답 DTO로 변환
         * 5. 200 / ACTIVE를 반환한다.
         */
        EmailVerificationResult result = verificationService.verify(request.token());

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(EmailVerificationResponse.from(result));
    }
}
