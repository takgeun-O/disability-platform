package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.member.domain.TermsCode;

// 검증할 동의 입력값
// 가입서비스가 정책 검증에 사용하는 객체
public record SignupAgreement(
        TermsCode termsCode,
        String version,
        Boolean agreed  // 정책에서 명시적으로 true인 경우만 동의한 것으로 인정
) {
}
