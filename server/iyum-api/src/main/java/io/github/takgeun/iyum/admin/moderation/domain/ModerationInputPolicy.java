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

    /**
     * 신규 운영 조치의 사유 유형과 상세 사유를 검증한다.
     *
     * 반환값:
     * - 상세 사유가 있으면 앞뒤 공백을 제거한 문자열
     * - 선택 입력인 상세 사유가 없으면 null
     *
     * 기존 자유 입력 기록은 이 메서드로 다시 검증하지 않는다.
     */
    public static String normalizeReasonDetail(
        ModerationAction action,
        ModerationReasonType reasonType,
        String reasonDetail
    ) {
        if(action == null) {
            throw new IllegalArgumentException(
                    "처리 종류는 필수입니다."
            );
        }

        if(reasonType == null) {
            throw new IllegalArgumentException(
                    "처리 사유 유형은 필수입니다."
            );
        }

        if(!reasonType.supports(action)) {
            throw new IllegalArgumentException(
                    "해당 조치에 사용할 수 없는 처리 사유 유형입니다."
            );
        }

        if(reasonDetail == null || reasonDetail.isBlank()) {
            if(reasonType == ModerationReasonType.OTHER) {
                throw new IllegalArgumentException(
                        "기타 사유는 상세 내용을 입력해야 합니다."
                );
            }

            return null;
        }

        // 기존의 앞뒤 공백 제거 및 코드 포인트 기준 500자 검증을 재사용한다.
        return normalizeReason(reasonDetail);
    }
}
