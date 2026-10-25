package io.github.takgeun.iyum.member.api.dto;

import io.github.takgeun.iyum.auth.session.api.validation.Utf8ByteLength;
import io.github.takgeun.iyum.member.api.validation.ValidMemberPasswordChangeRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "로그인한 회원의 비밀번호 변경 요청")
@ValidMemberPasswordChangeRequest
public record MemberPasswordChangeRequest(

        // 여기서 memberId 필드는 만들지 않는다.
        // memberId는 컨트롤러에서 로그인한 회원 정보에서 추출할 것.

        // @Utf8ByteLength 재사용: 회원 탈퇴 DTO와 동일한 검증기 사용

        // @Schema(WRITE_ONLY): 여기서는 API 문서상의 표현임
        // 실제 로그 노출 방지는 toString()메서드 재정의와 요청 본문 로깅 관리가 별도 필요

        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        @Utf8ByteLength(
                max = 72,
                message = "현재 비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
        )
        @Schema(
                description = "현재 비밀번호. 앞뒤 공백을 포함한 원문을 유지합니다.",
                format = "password",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        String currentPassword,

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Size(
                min = 8,
                message = "비밀번호는 8자 이상이어야 합니다."
        )
        @Pattern(
                regexp = "(?s)(?=.*[A-Za-z])(?=.*[0-9]).*",
                message = "비밀번호는 영문과 숫자를 각각 1개 이상 포함해야 합니다."
        )
        @Utf8ByteLength(
                max = 72,
                message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
        )
        @Schema(
                description = "8자 이상, ASCII 영문·숫자 포함, UTF-8 최대 72바이트. "
                        + "앞뒤 공백을 포함한 원문을 유지합니다.",
                format = "password",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        String newPassword,

        @NotBlank(message = "새 비밀번호 확인을 입력해주세요.")
        @Schema(
                description = "새 비밀번호 원문과 정확히 일치해야 합니다.",
                format = "password",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        String newPasswordConfirm
) {

    // 입력값을 수정하는 생성자는 만들지 않는다.
    // 비밀번호는 사용자가 입력한 원문 그대로 검증하고 비교해야 하기 때문.
    // 생성자에서 공백을 제거하거나 길이를 잘라버리는 입력값을 수정하는 행위를 하면
    // 사용자가 입력한 비밀번호와 서버가 처리하는 비밀번호가 달라질 수 있다.

    @Override
    public String toString() {
        return "MemberPasswordChangeRequest[REDACTED]";
    }
}
