package io.github.takgeun.iyum.auth.api;

import io.github.takgeun.iyum.auth.api.dto.SignupRequest;
import io.github.takgeun.iyum.auth.api.dto.SignupResponse;
import io.github.takgeun.iyum.auth.application.SignupResult;
import io.github.takgeun.iyum.auth.application.SignupService;
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
import org.springframework.http.HttpStatus;
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
public class SignupController {

    private final SignupService signupService;

    @Operation(
            summary = "회원가입",
            description = "비로그인 사용자를 PENDING(이메일 인증 대기) 상태로 가입시킵니다. "
                    + "이메일 인증 후 ACTIVE가 되면 사용자가 직접 로그인해야 하며, 가입 성공은 로그인이 아닙니다. "
                    + "회원가입 시 이메일 인증 토큰을 발급합니다. "
                    + "메일 발송이 활성화된 환경에서는 커밋 후 인증 메일 발송을 시도합니다. "
                    + "메일 발송 실패 시에도 가입 데이터는 유지됩니다. "
                    + "먼저 GET /api/v1/auth/csrf를 호출하고, 같은 세션에서 token을 X-CSRF-TOKEN 헤더에 전달하세요.",
            parameters = @Parameter(
                    name = "X-CSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "GET /api/v1/auth/csrf의 token. 동일한 세션 쿠키를 유지해야 합니다.",
                    schema = @Schema(type = "string")
            )
    )
    @ApiResponse(
            responseCode = "201",
            description = "가입 완료, 이메일 인증 대기",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SignupResponse.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                        "status": "PENDING"
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "입력 검증 실패, JSON 파싱 실패 또는 약관 정책 위반",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = {
                            @ExampleObject(
                                    name = "validation",
                                    value = """
                                            {
                                                "code": "VALIDATION_FAILED",
                                                "message": "입력값을 확인해 주세요.",
                                                "fieldErrors": [
                                                    {
                                                        "field": "passwordConfirm",
                                                        "message": "비밀번호가 일치하지 않습니다."
                                                    }
                                                ]
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "invalidBody",
                                    value = """
                                            {
                                                "code": "INVALID_REQUEST_BODY",
                                                "message": "요청 본문의 JSON 형식과 필드 값을 확인해 주세요.",
                                                "fieldErrors": []
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "missingTerms",
                                    value = """
                                            {
                                                "code": "REQUIRED_AGREEMENT_MISSING",
                                                "message": "필수 약관 동의 항목이 누락되었습니다.",
                                                "fieldErrors": []
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "notAccepted",
                                    value = """
                                            {
                                                "code": "REQUIRED_AGREEMENT_NOT_ACCEPTED",
                                                "message": "필수 약관에 동의해 주세요.",
                                                "fieldErrors": []
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "termsVersion",
                                    value = """
                                            {
                                                "code": "TERMS_VERSION_MISMATCH",
                                                "message": "약관이 변경되었습니다. 내용을 다시 확인해 주세요.",
                                                "fieldErrors": []
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "duplicateTerms",
                                    value = """
                                            {
                                                "code": "DUPLICATE_AGREEMENT",
                                                "message": "동일한 약관을 중복 제출할 수 없습니다.",
                                                "fieldErrors": []
                                            }
                                            """
                            )
                    }
            )
    )
    @ApiResponse(
            responseCode = "409",
            description = "정규화된 이메일 또는 대소문자를 구분하지 않는 닉네임 중복",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = {
                            @ExampleObject(
                                    name = "email",
                                    value = """
                                            {
                                                "code": "EMAIL_ALREADY_EXISTS",
                                                "message": "이미 가입된 이메일입니다.",
                                                "fieldErrors": []
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "nickname",
                                    value = """
                                            {
                                                "code": "NICKNAME_ALREADY_EXISTS",
                                                "message": "이미 사용 중인 닉네임입니다.",
                                                "fieldErrors": []
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
                    schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                        "code": "CSRF_TOKEN_INVALID",
                                        "message": "CSRF 토큰을 확인해 주세요.",
                                        "fieldErrors": []
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
                    schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                        "code": "INTERNAL_SERVER_ERROR",
                                        "message": "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
                                        "fieldErrors": []
                                    }
                                    """
                    )
            )
    )
    @PostMapping(
            value = "/api/v1/auth/signup",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        SignupResult result = signupService.signup(request.toCommand());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SignupResponse.from(result));
    }
}