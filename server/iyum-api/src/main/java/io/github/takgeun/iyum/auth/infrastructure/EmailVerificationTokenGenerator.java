package io.github.takgeun.iyum.auth.infrastructure;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class EmailVerificationTokenGenerator {

    private static final int TOKEN_BYTE_LENGTH = 32;

    // SecureRandom : 보안 용도로 사용할 난수 생성
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        // 32바이트의 난수를 담을 공간 마련
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];

        // nextBytes : 배열을 난수로 채움
        secureRandom.nextBytes(randomBytes);

        // getUrlEncoder() : URL에 넣기 편한 문자로 변환
        // withoutPadding() : 끝의 = 패딩을 생략
//        패딩 포함:
//        https://example.com/verify-email?token=AQ==
//        패딩 생략:
//        https://example.com/verify-email?token=AQ
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }
}
