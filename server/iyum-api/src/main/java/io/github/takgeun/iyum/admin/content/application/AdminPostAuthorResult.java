package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;

// 작성자 결과 객체
public record AdminPostAuthorResult(
        Long memberId,
        String nickname,
        boolean withdrawn
) {

    public static AdminPostAuthorResult from(Member member) {
        boolean withdrawn =
                member.getStatus() == MemberStatus.DELETED;

        return new AdminPostAuthorResult(
                member.getId(),
                withdrawn ? "탈퇴한 회원" : member.getNickname(),
                withdrawn
        );
    }
}
