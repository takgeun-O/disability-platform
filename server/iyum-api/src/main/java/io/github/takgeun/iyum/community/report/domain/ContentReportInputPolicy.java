package io.github.takgeun.iyum.community.report.domain;

/**
 * 신고 입력값의 공통 검증·정규화 규칙
 * <p>
 * HTTP 요청 검증과 별개로 도메인에서도 적용한다.
 */
public final class ContentReportInputPolicy {

    public static final int REASON_DETAIL_MAX_LENGTH = 500;

    private ContentReportInputPolicy() {
    }

    /**
     * 신고 상세 설명을 검증하고 정규화한다.
     * <p>
     * - 사유 유형은 필수
     * - 양끝 공백은 String.strip() 기준으로 제거
     * - 내부 공백과 줄바꿈은 유지
     * - 기타 사유는 상세 설명 필수
     * - 다른 사유의 빈 상세 설명은 null
     * - 최대 길이는 정규화 후 유니코드 코드 포인트 기준 500
     */
    public static String normalizeReasonDetail(
            ContentReportReasonType reasonType,
            String reasonDetail
    ) {
        if (reasonType == null) {
            throw new IllegalArgumentException(
                    "신고 사유 유형은 필수입니다."
            );
        }

        String normalized = reasonDetail == null
                ? null
                : reasonDetail.strip();

        if (normalized == null || normalized.isEmpty()) {
            if (reasonType.requiresDetail()) {
                throw new IllegalArgumentException(
                        "기타 사유는 상세 설명이 필요합니다."
                );
            }

            return null;
        }

        // 코드 포인트 기준이므로 일반적인 이모지 하나가 String.length()에서 2로 계산되더라도
        // 여기서는 1로 계산되게끔 한다.
        int codePointLength = normalized.codePointCount(
                0,
                normalized.length()
        );

        if(codePointLength > REASON_DETAIL_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "신고 상세 설명은 500자 이하여야 합니다."
            );
        }

        return normalized;
    }
}
