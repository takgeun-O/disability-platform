package io.github.takgeun.iyum.auth.api.dto;

import io.github.takgeun.iyum.auth.application.SignupResult;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/*
가입 직후 응답
{
  "status": "PENDING"
}
 */
@Schema(description = "가입 결과. PENDING은 이메일 인증 대기이며 로그인된 상태가 아닙니다.")
public record SignupResponse(
        @Schema(description = "가입 직후 PENDING. 이메일 인증으로 ACTIVE가 된 뒤 사용자가 직접 로그인합니다.",
                example = "PENDING", allowableValues = {"PENDING"}, requiredMode = Schema.RequiredMode.REQUIRED)
        MemberStatus status
) {
    public static SignupResponse from(SignupResult result) {
        return new SignupResponse(result.status());
    }
}
