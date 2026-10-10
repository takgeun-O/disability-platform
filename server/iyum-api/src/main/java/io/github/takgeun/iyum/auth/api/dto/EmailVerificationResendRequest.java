package io.github.takgeun.iyum.auth.api.dto;

import io.github.takgeun.iyum.member.domain.MemberInputPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerificationResendRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식으로 입력해주세요.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
        @Schema(description = "가입 시와 동일하게 앞뒤 공백 제거 및 Locale.ROOT 소문자화 후 검증합니다.",
                example = "member@example.com", accessMode = Schema.AccessMode.WRITE_ONLY)
        String email
) {
    public EmailVerificationResendRequest {
        email = MemberInputPolicy.canonicalizeEmail(email);
    }

    @Override
    public String toString() {
        return "EmailVerificationResendRequest[REDACTED]";
    }
}
