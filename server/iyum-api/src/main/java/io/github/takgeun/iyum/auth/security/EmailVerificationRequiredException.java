package io.github.takgeun.iyum.auth.security;

import org.springframework.security.authentication.DisabledException;

import java.io.Serial;

// DisabledException : Spring Security에서 사용이 허가되지 않은 계정의 인증을 거절할 때 사용하는 예외
// 이걸 상속한 예외를 만들어야 로그인 API에서 이메일 인증 대기 상태를 다른 로그인 실패와 구분 지을 수 있음.
public class EmailVerificationRequiredException extends DisabledException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EmailVerificationRequiredException() {
        super("이메일 인증을 완료해 주세요.");
    }
}
