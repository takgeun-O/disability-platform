package io.github.takgeun.iyum.admin.report.application;

import jakarta.annotation.Nullable;

/**
 * 조회 시점의 신고 대상 콘텐츠 상태.
 *
 * 신고 처리 상태 및 처리 당시 저장된 결과와 구분한다.
 * 댓글은 부모 게시글이 아닌 댓글 자체의 상태를 나타낸다.
 */
public enum AdminReportCurrentTargetStatus {

    // 대상 존재, 숨김·삭제 시각 없음 -> 화면 표시: 정상
    NORMAL,

    // 숨김 시각 있음, 삭제 시각 없음 -> 화면 표시: 숨김
    HIDDEN,

    // 삭제 시각 있음 -> 화면 표시: 삭제
    DELETED;

    /**
     * 현재 대상을 조회할 수 없으면 null을 반환한다. -> 화면 표시: 확인 불가
     * 삭제와 숨김 시각이 모두 존재하면 삭제를 우선한다.
     */
    @Nullable
    public static AdminReportCurrentTargetStatus from(
            @Nullable AdminReportCurrentTargetResult currentTarget
    ) {
        if (currentTarget == null) {
            return null;
        }

        if (currentTarget.deletedAt() != null) {
            return DELETED;
        }

        if (currentTarget.hiddenAt() != null) {
            return HIDDEN;
        }

        return NORMAL;
    }
}