package io.github.takgeun.iyum.auth.passwordreset.infrastructure;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordResetTokenGenerator {

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);

        // 32바이트 난수를 URL에 넣기 편한 Base64 URL 형식으로 변환
        // -> 결과는 43글자이며, 영문·숫자·-·_ 사용
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }
}
