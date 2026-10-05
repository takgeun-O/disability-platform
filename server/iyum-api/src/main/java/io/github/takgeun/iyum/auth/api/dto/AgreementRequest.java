package io.github.takgeun.iyum.auth.api.dto;

import io.github.takgeun.iyum.auth.application.SignupAgreement;
import io.github.takgeun.iyum.member.domain.TermsCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/*
{
  "termsCode": "SERVICE_TERMS",
  "version": "dev-v1",
  "agreed": true
}
 */
@Schema(description = "약관별 동의 정보. 필수 약관 두 항목 모두 명시적인 true가 필요합니다.")
public record AgreementRequest(

        @NotNull(message = "약관 코드를 입력해주세요.")
        @Schema(description = "필수 약관 코드. 두 코드 모두 필요하며 중복 제출할 수 없습니다.", example = "SERVICE_TERMS")
        TermsCode termsCode,

        @NotBlank(message = "약관 버전을 입력해주세요.")
        @Size(max = 50, message = "약관 버전은 50자 이하여야 합니다.")
        @Schema(description = "현재 개발용 약관 버전은 dev-v1입니다. 공백 제거 없이 정확히 비교합니다. "
                + "실제 사용자 연결 전 약관 본문과 버전을 확정해야 합니다.", example = "dev-v1")
        String version,

        @NotNull(message = "약관 동의 여부를 입력해주세요.")
        @Schema(description = "필수 약관의 명시적 동의. false는 정책 검증에서 거절됩니다.", example = "true")
        Boolean agreed  // 입력 누락과 명시적인 미동의를 구분하기 위해 Boolean
) {

    public SignupAgreement toSignupAgreement() {
        return new SignupAgreement(
                termsCode,
                version,
                agreed
        );
    }
}
