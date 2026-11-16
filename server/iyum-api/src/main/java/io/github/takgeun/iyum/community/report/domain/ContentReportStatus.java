package io.github.takgeun.iyum.community.report.domain;

/**
 * 신고 업무의 처리 상태.
 *
 * 게시글·댓글의 숨김 상태 및 회원의 이용 정지 상태와 독립적이다.
 */
public enum ContentReportStatus {

    // 신고가 접수되어 처리 대기 중
    RECEIVED("접수"),

    // 관리자가 검토하고 필요한 대응을 마친 신고 (RESOLVED가 곧 콘텐츠 숨김을 의미하지 않음)
    RESOLVED("처리 완료"),

    // 검토 결과 신고를 받아들이지 않고 종결한 신고
    DISMISSED("기각");

    private final String displayName;

    ContentReportStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == RESOLVED || this == DISMISSED;
    }
}