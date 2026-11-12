package io.github.takgeun.iyum.community.report.domain;

/**
 * 회원이 콘텐츠를 신고할 때 선택하는 사유.
 *
 * 관리자 숨김·복원 사유와 별도로 관리한다.
 * 복원 전용 사유나 LEGACY는 신고 사유에 포함하지 않는다.
 */
public enum ContentReportReasonType {

    SPAM_ADVERTISING("광고·홍보·스팸"),
    ABUSE_HATE("욕설·비방·혐오"),
    PERSONAL_INFORMATION("개인정보 노출"),
    DUPLICATE_FLOODING("도배·중복 게시"),
    OFF_TOPIC("게시판 주제와 무관"),
    OTHER("기타");

    private final String displayName;

    ContentReportReasonType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean requiresDetail() {
        return this == OTHER;
    }
}