package io.github.takgeun.iyum.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "이메일 인증 요청")
public record EmailVerificationRequest(

        /**
         * {
         *   "token": "메일에_포함된_이메일_인증_토큰"
         * }
         */
        @NotBlank(message = "이메일 인증 토큰이 필요합니다.")
        @Pattern(
                regexp = "[A-Za-z0-9_-]{43}",
                message = "이메일 인증 토큰 형식이 올바르지 않습니다."
        )
        @Schema(
                description = "인증 메일에 포함된 원본 토큰. "
                        + "공백 제거 또는 대소문자 변환 없이 전달합니다.",
                minLength = 43,
                maxLength = 43,
                pattern = "[A-Za-z0-9_-]{43}",
                // 클라이언트가 요청할 때 해당 필드를 반드시 포함해야 한다고 문서화
                requiredMode = Schema.RequiredMode.REQUIRED,
                // 요청으로만 받는 필드로 문서화 (즉, 클라이언트가 서버에 값을 전달하는 방향이며 응답에는 제공하지 않음을 표현)
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        String token
) {
        @Override
        public String toString() {
                return "EmailVerificationRequest[REDACTED]";
        }
}
