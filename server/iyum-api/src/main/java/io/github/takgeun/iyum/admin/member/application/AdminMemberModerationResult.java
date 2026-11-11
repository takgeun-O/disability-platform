package io.github.takgeun.iyum.admin.member.application;

import io.github.takgeun.iyum.member.domain.MemberStatus;

public record AdminMemberModerationResult(
        // 인증 버전은 응답에 포함하지 않는다.
        Long memberId,
        MemberStatus status,    // 요청 처리 후 회원 상태
        boolean changed         // 이번 요청에서 실제 상태가 변경되었는지 여부
) {
}
