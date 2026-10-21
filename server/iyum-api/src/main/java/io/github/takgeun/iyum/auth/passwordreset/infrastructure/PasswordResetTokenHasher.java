package io.github.takgeun.iyum.auth.passwordreset.infrastructure;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// 토큰 해시 처리
@Component
public class PasswordResetTokenHasher {

    public String hash(String rawToken) {
        if(rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException(
                    "원본 토큰이 필요합니다."
            );
        }

        try {
            // MessageDigest는 호출마다 새로 만든다.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashedBytes = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashedBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 해시 알고리즘을 사용할 수 없습니다.",
                    exception
            );
        }
    }
}
