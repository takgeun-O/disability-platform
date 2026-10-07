package io.github.takgeun.iyum.auth.infrastructure;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class EmailVerificationTokenHasher {

    public String hash(String rawToken) {
        if(rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException(
                    "원본 토큰이 필요합니다."
            );
        }

        /**
         * 1. 문자열을 UTF-8 바이트로 변환한다.
         * 2. SHA-256으로 32바이트의 해시를 계산한다.
         * 3. 바이트당 두 자리의 16진수로 표현한다.
         * 4. 기존 엔티티가 요구하는 소문자 16진수 64자리가 완성된다.
         */
        try {
            // MessageDigest는 해시 계산 중 상태가 바뀌는 객체이므로 호출마다 새로 만들어서 사용한다.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashedBytes = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            // 토큰에는 trim()이나 소문자 변환을 적용하지 않고 원본 문자 그대로 해시해야 함.

            return HexFormat.of().formatHex(hashedBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 해시 알고리즘을 사용할 수 없습니다.",
                    exception
            );
        }
    }
}
