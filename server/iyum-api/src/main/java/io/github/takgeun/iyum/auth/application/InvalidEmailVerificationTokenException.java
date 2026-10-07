package io.github.takgeun.iyum.auth.application;

/**
 * 잘못된 토큰, 만료된 토큰, 이미 사용한 토큰 등을 이 예외로 표현할 것
 * RuntimeException을 상속했으므로, 이 예외가 @Transactional 메서드 밖으로 전달되면 기본 설정에서 해당 트랜잭션이 롤백된다.
 */
public class InvalidEmailVerificationTokenException extends RuntimeException {

    public InvalidEmailVerificationTokenException() {
        super("유효하지 않거나 사용할 수 없는 이메일 인증 링크입니다.");
    }
}
