package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.member.domain.MemberStatus;
import jakarta.annotation.Nullable;

/**
 * 신고 대상 콘텐츠의 현재 작성자 표시 정보
 * 신고자나 처리 관리자 정보와 구분한다.
 */
public record AdminReportTargetAuthorResult(
        Long memberId,
        String nickname,
        boolean withdrawn
) {

    public static AdminReportTargetAuthorResult from(
            Long memberId,
            @Nullable String nickname,
            MemberStatus status
    ) {
        boolean withdrawn = status == MemberStatus.DELETED;

        return new AdminReportTargetAuthorResult(
                memberId,
                withdrawn ? "탈퇴한 회원" : nickname,
                withdrawn
        );
    }
}
