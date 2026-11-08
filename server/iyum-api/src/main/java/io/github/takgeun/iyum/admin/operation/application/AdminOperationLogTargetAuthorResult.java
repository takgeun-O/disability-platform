package io.github.takgeun.iyum.admin.operation.application;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;

/**
 * 운영 기록 대상 콘텐츠의 현재 작성자 정보
 * 운영 조치를 수행한 관리자(actorMemberId)와 구분
 */
public record AdminOperationLogTargetAuthorResult(
        Long memberId,
        String nickname,
        boolean withdrawn
) {

    public static AdminOperationLogTargetAuthorResult from(Member member) {
        boolean withdrawn = member.getStatus() == MemberStatus.DELETED;

        return new AdminOperationLogTargetAuthorResult(
                member.getId(),
                withdrawn ? "탈퇴한 회원" : member.getNickname(),
                withdrawn
        );
    }
}
