package io.github.takgeun.iyum.auth.api.dto;

import io.github.takgeun.iyum.auth.api.validation.ValidSignupRequest;
import io.github.takgeun.iyum.auth.application.SignupAgreement;
import io.github.takgeun.iyum.auth.application.SignupCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import io.github.takgeun.iyum.member.domain.MemberInputPolicy;

@Schema(description = "회원가입 요청. 약관 두 항목 모두 현재 개발용 버전 dev-v1에 동의해야 합니다.")
@ValidSignupRequest // SignupRequestValidator 를 실행하게 한다.
public record SignupRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식으로 입력해주세요.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
        @Schema(description = "앞뒤 공백을 strip한 뒤 Locale.ROOT로 소문자화하여 검증·저장하고, "
                + "그 값으로 중복을 판단합니다. 정규화 후 최대 254자입니다.", example = "member@example.com")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        @Pattern(
                regexp = "(?s)(?=.*[A-Za-z])(?=.*[0-9]).*",
                message = "비밀번호는 영문과 숫자를 각각 1개 이상 포함해야 합니다."
        )
        @Schema(description = "8자 이상이며 영문(A-Z/a-z)과 숫자(0-9)를 각각 1개 이상 포함해야 합니다. "
                + "특수문자는 필수가 아닙니다. UTF-8 최대 72바이트이며 72자 제한과 다릅니다. "
                + "앞뒤 공백을 포함한 원문을 유지합니다.", example = "Example123!",
                format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password,

        @NotBlank(message = "비밀번호 확인을 입력해주세요.")
        @Schema(description = "비밀번호 원문과 정확히 일치해야 하는 확인값. 서비스나 저장 계층으로 전달하지 않습니다.",
                example = "Example123!", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String passwordConfirm,

        @NotBlank(message = "닉네임을 입력해주세요.")
        @Schema(description = "앞뒤 공백을 strip하며 대소문자는 저장 시 유지하고 중복 판정에서는 구분하지 않습니다. "
                + "한글 완성형(가-힣), 영문, 숫자, 밑줄로 2~20자입니다. "
                + "관리자, 운영자, admin, administrator, moderator는 대소문자와 무관하게 사용할 수 없습니다.",
                example = "Iyum_user", minLength = 2, maxLength = 20, pattern = "[가-힣A-Za-z0-9_]{2,20}")
        String nickname,

        @Schema(description = "SERVICE_TERMS와 PRIVACY_COLLECTION_USE를 각각 한 번 제출합니다. "
                + "각 항목은 termsCode, version, agreed를 포함하며 개발용 버전 dev-v1과 agreed=true가 필요합니다.",
                example = """
                        [{"termsCode":"SERVICE_TERMS","version":"dev-v1","agreed":true},
                         {"termsCode":"PRIVACY_COLLECTION_USE","version":"dev-v1","agreed":true}]
                        """)
        @NotEmpty(message = "약관 동의 정보를 입력해주세요.")
        @Valid  // 내부 객체까지 검증을 이어가게 하는 역할만 할 뿐
        List<
                @NotNull(
                        message = "약관 항목은 null일 수 없습니다.")
                        AgreementRequest
                >
        agreements
) {

    public SignupRequest {
        // 입력값 정리만 수행
        // 잘못된 입력에 대한 판단은 검증기가 담당

        if (email != null) {
            email = MemberInputPolicy.canonicalizeEmail(email);
        }

        if(nickname != null) {
            nickname = nickname.strip();
        }

        // password와 passwordConfirm은 원본 그대로 유지
    }

    // toCommand 는 데이터 변환만 수행한다. 실제 요청 검증은 컨트롤러의 @Valid에서 실행
    // passwordConfirm은 제외된다. 두 비밀번호가 같은지는 이미 확인되었음. (SignupRequest의 @ValidSignupRequest에 의해)
    public SignupCommand toCommand() {
        return new SignupCommand(
                email,
                password,
                nickname,
                toSignupAgreements()
        );
    }

    // 입력값 검증이 끝난 뒤 호출
    public List<SignupAgreement> toSignupAgreements() {
        return agreements.stream()
                .map(AgreementRequest::toSignupAgreement)
                .toList();
    }



    // record의 기본 toString()에 비밀번호가 포함되지 않도록 한다.
    @Override
    public String toString() {
        return "SignupRequest[REDACTED]";
    }
}
