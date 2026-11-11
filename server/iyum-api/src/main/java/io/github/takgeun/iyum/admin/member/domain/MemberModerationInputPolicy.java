package io.github.takgeun.iyum.admin.member.domain;

public final class MemberModerationInputPolicy {

    public static final int REASON_MAX_LENGTH = 500;

    private MemberModerationInputPolicy() {
    }

    public static String normalizeReason(String reason) {
        if(reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "처리 사유는 필수입니다."
            );
        }

        String normalized = reason.strip();

        int length = normalized.codePointCount(
                0,
                normalized.length()
        );

        if(length > REASON_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "처리 사유는 500자 이하여야 합니다."
            );
        }

        return normalized;
    }
}
