package io.github.takgeun.iyum.auth.passwordreset.api.dto;

import io.github.takgeun.iyum.member.domain.MemberInputPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "비밀번호 재설정 메일 발급 요청")
public record PasswordResetIssueRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식으로 입력해주세요.")
        @Size(
                max = 254,
                message = "이메일은 254자 이하여야 합니다."
        )
        @Schema(
                description = "앞뒤 공백 제거 및 소문자 변환 후 검증합니다.",
                example = "member@example.com",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        String email
) {

    public PasswordResetIssueRequest {
        // 생성자에서는 정규화만 수행
        // 필수값·형식·길이 검증은 Bean Validation이 담당
        // 여기서는 normalizeEmail()이 아닌 canonicalizeEmail() 사용
        // normalizeEmail()은 잘못된 값에 바로 예외를 던지지만
        // 이 DTO에서는 객체 생성 후 @Valid 검증을 거쳐
        // 기존 입력 오류 응답으로 처리하려는 목적임
        email = MemberInputPolicy.canonicalizeEmail(email);
    }

    @Override
    public String toString() {
        return "PasswordResetIssueRequest[REDACTED]";
    }
}
