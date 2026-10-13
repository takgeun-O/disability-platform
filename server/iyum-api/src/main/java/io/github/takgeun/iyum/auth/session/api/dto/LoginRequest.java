package io.github.takgeun.iyum.auth.session.api.dto;

import io.github.takgeun.iyum.auth.session.api.validation.Utf8ByteLength;
import io.github.takgeun.iyum.member.domain.MemberInputPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {
 *   "email": "member@example.com",
 *   "password": "Example123!"
 * }
 */
@Schema(description = "이메일과 비밀번호를 이용한 로그인 요청")
public record LoginRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식으로 입력해주세요.")
        @Size(
                max = 254,
                message = "이메일은 254자 이하여야 합니다."
        )
        @Schema(
                description = "앞뒤 공백을 제거하고 소문자로 변환합니다.",
                example = "member@example.com"
        )
        String email,

        @NotBlank
        @Utf8ByteLength(
                max = 72,
                message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
        )

        @Schema(
                description = "앞뒤 공백을 포함한 입력 원문을 유지합니다.",
                example = "Example123!",
                format = "password",
                // accessMode: API 문서에서 해당 필드를 요청으로 받는지, 응답으로 제공하는지 지정하는 속성
                // WRITE_ONLY: 클라이언트가 서버에 보내는 요청에 사용하는 필드라는 뜻
                // 비밀번호를 입력받되 응답으로 제공하지 않는다는 의도
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        String password
) {

    public LoginRequest {
        // 이메일 정규화
        email = MemberInputPolicy.canonicalizeEmail(email);
    }

    @Override
    public String toString() {
        return "LoginRequest[REDACTED]";
    }
}
