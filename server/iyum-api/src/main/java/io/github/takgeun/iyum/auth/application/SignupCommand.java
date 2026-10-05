package io.github.takgeun.iyum.auth.application;

import java.util.List;

// DTO 검증을 통과한 값을 전달받는 용도
// 생성자 호출만으로 이메일 형식이나 비밀번호 복잡도 검증이 실행되지 않음.
public record SignupCommand(
        String email,
        String rawPassword,
        String nickname,
        List<SignupAgreement> agreements
) {

    // record가 자동 생성하는 toString()에는 필드값이 포함되므로
    // 비밀번호가 로그에 노출되지 않도록 재정의한다.
    @Override
    public String toString() {
        return "SignupCommand[REDACTED]";
    }
}
