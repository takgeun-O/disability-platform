package io.github.takgeun.iyum.auth.passwordreset.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 발급 가능한 활성 회원
 * 가입하지 않은 이메일
 * 재설정 대상이 아닌 회원 상태
 * 회원별 재발급 간격·횟수 제한에 걸린 경우
 */
@Schema(description = "비밀번호 재설정 메일 요청 안내")
public record PasswordResetIssueResponse(

        @Schema(
                description = "가입 여부와 회원 상태를 노출하지 않는 공통 안내",
                example = "입력하신 이메일이 재설정 가능한 계정이면 "
                        + "비밀번호 재설정 안내 메일을 보내드립니다."
        )
        String message
) {

    public static PasswordResetIssueResponse accepted() {
        return new PasswordResetIssueResponse(
                "입력하신 이메일이 재설정 가능한 계정이면 "
                        + "비밀번호 재설정 안내 메일을 보내드립니다."
        );
    }
}
