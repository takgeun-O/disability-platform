package io.github.takgeun.iyum.auth.application;

import java.util.Objects;

/**
 * 메일 발송을 요청하는 이벤트 작성
 * 서비스에서 곧바로 메일을 보내는 대신, 아래 내용을 담은 객체를 Spring에 전달
 * "이 회원의 인증 메일을 보내야 한다. 현재 트랜잭션이 커멧되면 처리해 달라"
 *
 * 이 객체는 서버 내부 이벤트로, 프론트엔드에 반환하는 응답 DTO가 아님을 주의
 */
public record EmailVerificationMailRequested(
        String recipientEmail,
        IssuedEmailVerificationToken issuedToken
) {

    public EmailVerificationMailRequested {
        if(recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "수신 이메일 주소가 필요합니다."
            );
        }

        Objects.requireNonNull(
                issuedToken,
                "발급된 이메일 인증 토큰이 필요합니다."
        );
    }

    // toString()에는 회원 ID만 포함.
    // 이벤트를 실수로 출력하더라도 이메일 주소나 원본 토큰이 드러나지 않도록 한 것.
    @Override
    public String toString() {
        return "EmailVerificationMailRequested["
                + "memberId=" + issuedToken.memberId()
                + "]";
    }
}
