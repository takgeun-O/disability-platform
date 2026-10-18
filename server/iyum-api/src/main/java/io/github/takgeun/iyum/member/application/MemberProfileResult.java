package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.member.domain.Member;

import java.time.Instant;

public record MemberProfileResult(
        Long memberId,
        String email,
        String nickname,
        Instant createdAt
) {

    public static MemberProfileResult from(Member member) {
        return new MemberProfileResult(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getCreatedAt()
        );
    }
}
