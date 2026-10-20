package io.github.takgeun.iyum.member.api.dto;

import io.github.takgeun.iyum.auth.session.api.validation.Utf8ByteLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "회원 탈퇴 요청")
public record MemberWithdrawalRequest(

        @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
        @Utf8ByteLength(
                max = 72,
                message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
        )
        @Schema(
                description = "현재 비밀번호. 앞뒤 공백을 포함한 원문을 유지합니다.",
                format = "password",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        String currentPassword,

        // @AssertTrue 사용 시 null은 유효한 값으로 취급하므로
        // @NotNull도 함꼐 사용
        @NotNull(message = "회원 탈퇴 안내에 동의해 주세요.")
        // 값이 반드시 true여야 검증을 통화하도록 하는 어노테이션 (컨트롤러에서 @Valid에 의해 요청 객체의 검증 실행)
        @AssertTrue(message = "회원 탈퇴 안내에 동의해 주세요.")
        @Schema(
                description = "탈퇴 안내 확인 및 동의 여부. true여야 합니다.",
                example = "true"
        )
        Boolean confirmed
) {

    @Override
    public String toString() {
        return "MemberWithdrawalRequest[REDACTED]";
    }
}
