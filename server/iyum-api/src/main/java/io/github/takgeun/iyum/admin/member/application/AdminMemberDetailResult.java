package io.github.takgeun.iyum.admin.member.application;

import io.github.takgeun.iyum.member.application.MemberDisplayName;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;

// 상세 조회 결과 객체
public record AdminMemberDetailResult(
        Long memberId,
        String nickname,
        @Nullable String email,
        MemberStatus status,
        MemberRole role,
        Instant createdAt,
        Instant updatedAt,
        // 탈퇴 시각. 탈퇴하지 않았으면 null
        @Nullable Instant withdrawnAt
) {

    public static AdminMemberDetailResult from(Member member) {
        return new AdminMemberDetailResult(
                member.getId(),
                MemberDisplayName.resolve(member),
                member.getEmail(),
                member.getStatus(),
                member.getRole(),
                member.getCreatedAt(),
                member.getUpdatedAt(),
                member.getWithdrawnAt()
        );
    }
}
