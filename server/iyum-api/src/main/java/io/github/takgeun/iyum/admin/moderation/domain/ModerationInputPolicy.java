package io.github.takgeun.iyum.admin.moderation.domain;

/**
 * 처리 사유 검증을 공통 정책으로 분리한다.
 *
 * 중복 요청에도 사유 검증은 필요.
 * 이미 숨겨진 게시글이라고 해서 빈 사유를 허용하면 요청마다 검증 규칙이 달라지기 때문
 */
public final class ModerationInputPolicy {

    public static final int REASON_MAX_LENGTH = 500;

    private ModerationInputPolicy() {
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
