package io.github.takgeun.iyum.community.report.domain;

/**
 * 신고 종결 당시 실제 콘텐츠 조치 결과.
 *
 * 이후 콘텐츠가 복구되거나 삭제되어도
 * 이 기록은 변경하지 않는다.
 */
public enum ContentReportHandlingOutcome {

    // 이번 신고 처리에서 콘텐츠를 새로 숨겼다.
    CONTENT_HIDDEN,

    // 대상 콘텐츠가 이미 숨김 상태였다.
    CONTENT_ALREADY_HIDDEN,

    // 콘텐츠 변경 없이 처리 완료하거나 기각했다.
    NO_CONTENT_ACTION,

    // 조치 결과 필드 도입 이전의 종결 기록.
    // 신규 처리에서는 사용할 수 없다.
    LEGACY_UNKNOWN
}