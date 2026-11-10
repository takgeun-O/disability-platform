package io.github.takgeun.iyum.admin.member.application;

import io.github.takgeun.iyum.member.application.MemberDisplayName;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;

// 목록 조회 결과 객체
public record AdminMemberItemResult(
        Long memberId,
        String nickname,
        // WITHDRAWN: 저장 이메일 유지, DELETED: null
        @Nullable String email,
        MemberStatus status,
        MemberRole role,
        Instant createdAt
) {

    public static AdminMemberItemResult from(Member member) {
        return new AdminMemberItemResult(
                member.getId(),
                // WITHDRAWN과 DELETED의 표시 이름은 "탈퇴한 회원"
                MemberDisplayName.resolve(member),
                member.getEmail(),
                member.getStatus(),
                member.getRole(),
                member.getCreatedAt()
        );
    }
}
