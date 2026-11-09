package io.github.takgeun.iyum.admin.moderation.domain;

/**
 * 운영 조치 사유의 분류.
 * 표시 문구와 별개로 안정적인 코드값을 사용한다.
 */
public enum ModerationReasonType {

    // 광고·홍보·스팸
    SPAM_ADVERTISING,

    // 욕설·비방''혐오
    ABUSE_HATE,

    // 개인정보 노출
    PERSONAL_INFORMATION,

    // 도배·중복 게시
    DUPLICATE_FLOODING,

    // 게시판 주제와 무관
    OFF_TOPIC,

    // (복원) 운영 조치 오류
    MODERATION_ERROR,

    // (복원) 재검토 결과 위반 없음
    NO_VIOLATION,

    // (숨김·복원) 기타
    OTHER,

    // 기존 자유 입력 기록 전용. 신규 조치에서는 선택할 수 없다.
    LEGACY;

    /**
     * 해당 사유를 지정한 조치에 사용할 수 있는지 확인한다.
     */
    public boolean supports(ModerationAction action) {
        if (action == null) {
            return false;
        }

        return switch (this) {
            case SPAM_ADVERTISING,
                 ABUSE_HATE,
                 PERSONAL_INFORMATION,
                 DUPLICATE_FLOODING,
                 OFF_TOPIC -> action == ModerationAction.HIDE;

            case MODERATION_ERROR,
                 NO_VIOLATION -> action == ModerationAction.RESTORE;

            case OTHER -> action == ModerationAction.HIDE
                    || action == ModerationAction.RESTORE;

            // LEGACY는 단순히 기존 기록을 구분하는 용도이며,
            // 새로 추가한 신규 조치에는 사용 불가
            case LEGACY -> false;
        };
    }

    /**
     * 상세 사유가 없는 경우 기존 reason 필드에 저장할 "표시 문구".
     * 상세 사유가 있으면 reason에 그대로 나올 것.
     * 분류와 검색에는 이 문구 대신 enum 코드값을 사용한다.
     */
    public String displayName() {
        return switch (this) {
            case SPAM_ADVERTISING -> "광고·홍보·스팸";
            case ABUSE_HATE -> "욕설·비방·혐오";
            case PERSONAL_INFORMATION -> "개인정보 노출";
            case DUPLICATE_FLOODING -> "도배·중복 게시";
            case OFF_TOPIC -> "게시판 주제와 무관";
            case MODERATION_ERROR -> "운영 조치 오류";
            case NO_VIOLATION -> "재검토 결과 위반 없음";
            case OTHER -> "기타";
            case LEGACY -> "기존 자유 입력 사유";
        };
    }
}