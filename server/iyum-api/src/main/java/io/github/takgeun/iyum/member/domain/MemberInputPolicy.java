package io.github.takgeun.iyum.member.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class MemberInputPolicy {

    private static final Pattern NICKNAME_PATTERN =
            Pattern.compile("[가-힣A-Za-z0-9_]{2,20}");

    private static final Set<String> RESERVED_NICKNAMES =
            Set.of("관리자", "운영자", "admin", "administrator", "moderator");

    private MemberInputPolicy() {}

    public static String canonicalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public static String normalizeEmail(String email) {
        if(email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }

        String normalized = canonicalizeEmail(email);

        if(normalized.length() > 254) {
            throw new IllegalArgumentException(
                    "이메일은 254자 이하여야 합니다."
            );
        }

        return normalized;
    }

    public static String normalizedNickname(String nickname) {
        if(nickname == null) {
            throw new IllegalArgumentException("닉네임은 필수입니다.");
        }

        String normalized = nickname.strip();

        if(!NICKNAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "닉네임은 한글, 영문, 숫자, 밑줄로 2~20자여야 합니다."
            );
        }

        if(RESERVED_NICKNAMES.contains(
                normalized.toLowerCase(Locale.ROOT)
        )) {
            throw new IllegalArgumentException(
                    "사용할 수 없는 닉네임입니다."
            );
        }

        return normalized;
    }
}
