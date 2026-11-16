package io.github.takgeun.iyum.community.report.domain;

/**
 * 이미 처리된 신고를 구분할 예외 추가
 */
public class ContentReportAlreadyHandledException
        extends IllegalStateException {

    public ContentReportAlreadyHandledException() {
        super("이미 처리된 신고입니다.");
    }
}