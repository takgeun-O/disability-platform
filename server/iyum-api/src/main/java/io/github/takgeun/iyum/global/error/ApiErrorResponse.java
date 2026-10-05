package io.github.takgeun.iyum.global.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "공통 오류 응답. 입력 원문이나 내부 예외 정보는 포함하지 않습니다.")
public record ApiErrorResponse(
        @Schema(description = "클라이언트가 구분할 오류 코드", example = "VALIDATION_FAILED",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "사용자에게 전달할 메시지", example = "입력값을 확인해 주세요.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(description = "필드 검증 오류. 해당 오류가 없으면 빈 배열입니다.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<FieldError> fieldErrors
) {
    public ApiErrorResponse {
        fieldErrors = List.copyOf(fieldErrors);
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, List.of());
    }

    @Schema(name = "ApiFieldError", description = "필드명과 검증 메시지만 제공하며 거절된 값은 제공하지 않습니다.")
    public record FieldError(
            @Schema(example = "passwordConfirm", requiredMode = Schema.RequiredMode.REQUIRED)
            String field,
            @Schema(example = "비밀번호가 일치하지 않습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
            String message
    ) {
    }
}
