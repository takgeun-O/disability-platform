package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.member.domain.TermsCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static io.github.takgeun.iyum.auth.application.SignupTermsException.Code.*;

@Component
public class SignupTermsPolicy {

    /**
     * 개발용 약관 기준.
     * 실제 사용자 연결 전에 가입 화면의 본문 및 버전과 맞춰야 한다.
     */
    private static final Map<TermsCode, String> REQUIRED_VERSIONS =
            // Map.of()로 만든 Map은 수정할 수 없으므로, 호출한 서비스에서 반환값을 변경해 정책을 바꿀 수 없도록 함
            Map.of(
                    TermsCode.SERVICE_TERMS, "dev-v1",
                    TermsCode.PRIVACY_COLLECTION_USE, "dev-v1"
            );

    public Map<TermsCode, String> validateAndResolve(
            List<SignupAgreement> agreements
    ) {
        if (agreements == null || agreements.isEmpty()) {
            throw new SignupTermsException(
                    REQUIRED_AGREEMENT_MISSING,
                    null,
                    "필수 약관에 동의해 주세요."
            );
        }

        Map<TermsCode, SignupAgreement> submitted =
                new EnumMap<>(TermsCode.class);

        // 1. 입력값 형태와 중복 항목을 검사
        for (SignupAgreement agreement : agreements) {
            if (agreement == null || agreement.termsCode() == null) {
                throw new SignupTermsException(
                        INVALID_AGREEMENT,
                        null,
                        "약관 동의 정보가 올바르지 않습니다."
                );
            }

            TermsCode termsCode = agreement.termsCode();

            if (!REQUIRED_VERSIONS.containsKey(termsCode)) {
                throw new SignupTermsException(
                        INVALID_AGREEMENT,
                        termsCode,
                        "가입 시 허용되지 않는 약관 항목입니다."
                );
            }

            if (agreement.version() == null
                    || agreement.version().isBlank()) {
                throw new SignupTermsException(
                        INVALID_AGREEMENT,
                        termsCode,
                        "약관 버전이 필요합니다."
                );
            }

            // 중복 제출 감지
            // 단순히 put으로 덮어쓰면 같은 약관이 true와 false로 두 번 제출되었을 때 뒤쪽 값만 남을 수 있음.
            // 여기서는 동일한 약관을 두 번 제출하면 거절함.
            // 참고) putIfAbsent(key, value) : 기존 값이 없거나 null이었다면 null 반환
            SignupAgreement previous =
                    submitted.putIfAbsent(termsCode, agreement);

            if(previous != null) {
                throw new SignupTermsException(
                        DUPLICATE_AGREEMENT,
                        termsCode,
                        "동일한 약관을 중복 제출할 수 없습니다."
                );
            }
        }

        // 2. 필수 항목의 존재·동의 여부·버전을 검사한다.
        for (Map.Entry<TermsCode, String> required : REQUIRED_VERSIONS.entrySet()) {

            TermsCode termsCode = required.getKey();
            String requiredVersion = required.getValue();

            SignupAgreement agreement = submitted.get(termsCode);

            if(agreement == null) {
                throw new SignupTermsException(
                        REQUIRED_AGREEMENT_MISSING,
                        termsCode,
                        "필수 약관 동의 항목이 누락되었습니다."
                );
            }

            if(!Boolean.TRUE.equals(agreement.agreed())) {
                throw new SignupTermsException(
                        REQUIRED_AGREEMENT_NOT_ACCEPTED,
                        termsCode,
                        "필수 약관에 동의해 주세요."
                );
            }

            if(!requiredVersion.equals(agreement.version())) {
                throw new SignupTermsException(
                        TERMS_VERSION_MISMATCH,
                        termsCode,
                        "약관이 변경되었습니다. 내용을 다시 확인해 주세요."
                );
            }
        }

        // 검증에 성공하면 서버가 확인한 약관 코드·버전을 반환한다.
        return REQUIRED_VERSIONS;
    }
}
