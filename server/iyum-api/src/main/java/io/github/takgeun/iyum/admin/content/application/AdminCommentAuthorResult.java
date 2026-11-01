package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;

public record AdminCommentAuthorResult(
        Long memberId,
        String nickname,
        boolean withdrawn
) {

    public static AdminCommentAuthorResult from(Member member) {
        boolean withdrawn =
                member.getStatus() == MemberStatus.DELETED;

        return new AdminCommentAuthorResult(
                member.getId(),
                withdrawn ? "탈퇴한 회원" : member.getNickname(),
                withdrawn
        );
    }
}
