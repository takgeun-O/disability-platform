package io.github.takgeun.iyum.admin.operation.application;

/** 목록 조건과 무관한 콘텐츠 숨김·해제 기록 건수. */
public record AdminOperationSummaryCountsResult(
        long totalOperationLogCount,
        long todayOperationLogCount,
        long todayHideCount,
        long todayRestoreCount
) {
}
