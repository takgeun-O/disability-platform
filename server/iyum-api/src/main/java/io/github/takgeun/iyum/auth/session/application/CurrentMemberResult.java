package io.github.takgeun.iyum.auth.session.application;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;

// 마이페이지의 MemberProfileResult와 달리 기존 인증 응답에 필요한 role이 있음.
public record CurrentMemberResult(
        Long memberId,
        String email,
        String nickname,
        MemberRole role
) {

    public static CurrentMemberResult from(Member member) {
        return new CurrentMemberResult(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole()
        );
    }
}
