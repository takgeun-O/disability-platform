package io.github.takgeun.iyum.auth.passwordreset.api.dto;

import io.github.takgeun.iyum.auth.passwordreset.api.validation.ValidPasswordResetCompleteRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 사용자가 메일 링크를 열고 새 비밀번호를 제출하면 다음과 같은 데이터를 받는다.
 * {
 *   "token": "메일 링크에 포함된 원본 토큰",
 *   "newPassword": "NewPassword123!",
 *   "newPasswordConfirm": "NewPassword123!"
 * }
 */
@Schema(description = "비밀번호 재설정 완료 요청")
@ValidPasswordResetCompleteRequest
public record PasswordResetCompleteRequest(

        @NotBlank(message = "재설정 토큰이 필요합니다.")
        @Pattern(
                regexp = "[A-Za-z0-9_-]{43}",
                message = "재설정 토큰 형식이 올바르지 않습니다."
        )
        @Schema(
                description = "메일 링크에 포함된 43자 원본 토큰",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        String token,

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Size(
                min = 8,
                message = "비밀번호는 8자 이상이어야 합니다."
        )
        @Pattern(
                regexp = "(?s)(?=.*[A-Za-z])(?=.*[0-9]).*",
                message = "비밀번호는 영문과 숫자를 각각 1개 이상 포함해야 합니다."
        )
        @Schema(
                description = "8자 이상, 영문·숫자 포함, UTF-8 최대 72바이트. "
                        + "앞뒤 공백을 포함한 원문을 유지합니다.",
                format = "password",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        String newPassword,

        @NotBlank(message = "새 비밀번호 확인을 입력해주세요.")
        @Schema(
                description = "새 비밀번호와 정확히 일치해야 합니다.",
                format = "password",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        String newPasswordConfirm
) {

    @Override
    public String toString() {
        return "PasswordResetCompleteRequest[REDACTED]";
    }
}
