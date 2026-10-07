package io.github.takgeun.iyum.auth.application;

public interface EmailVerificationMailSender {

    // 이 이메일 주소로 발급된 토큰을 포함한 인증 메일을 보내라.
    void send(
            String recipientEmail,      // 해당 회원의 DB에 저장된 이메일 전달
            IssuedEmailVerificationToken issuedToken
    );
}
