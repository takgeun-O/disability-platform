package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.member.domain.TermsCode;
import lombok.Getter;

// 검증 실패를 표현할 예외
// 예외 처리기에서 입력 오류는 400, 버전 불일치는 409처럼 연결할 예정
@Getter
public class SignupTermsException extends RuntimeException {

    private final Code code;
    private final TermsCode termsCode;

    public SignupTermsException(
            Code code,
            TermsCode termsCode,
            String message
    ) {
        super(message);
        this.code = code;
        this.termsCode = termsCode;
    }

    public enum Code {

        INVALID_AGREEMENT,      // 약관 코드나 버전 등 입력값이 올바르지 않음
        DUPLICATE_AGREEMENT,    // 동일한 약관 코드가 여러 번 제출됨
        REQUIRED_AGREEMENT_MISSING,  // 필수 약관 항목이 누락됨
        REQUIRED_AGREEMENT_NOT_ACCEPTED, // 필수 약관에 동의하지 않음
        TERMS_VERSION_MISMATCH          // 서버가 요구하는 버전과 다름
    }
}
