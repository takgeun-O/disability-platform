package io.github.takgeun.iyum.auth.passwordreset.application;

import java.util.regex.Pattern;

// 조회와 실제 변경에서 같은 원본 토큰 형식만 허용한다. 값을 정규화하거나 오류에 포함하지 않는다.
final class PasswordResetTokenInput {

    private static final Pattern RAW_TOKEN
            = Pattern.compile("[A-Za-z0-9_-]{43}");

    private PasswordResetTokenInput() {

    }

    static void validate(String rawToken) {
        if (rawToken == null
                || !RAW_TOKEN.matcher(rawToken).matches()
        ) {
            throw PasswordResetException.invalidToken();
        }
    }
}
