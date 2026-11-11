package io.github.takgeun.iyum.admin.member.application;

import io.github.takgeun.iyum.admin.member.domain.MemberModerationAction;
import io.github.takgeun.iyum.admin.member.domain.MemberModerationLog;
import io.github.takgeun.iyum.member.domain.MemberStatus;

import java.time.Instant;

// 조회 결과 객체
public record AdminMemberModerationLogResult(
        Long logId,
        Long actorMemberId,
        Long targetMemberId,
        MemberModerationAction action,
        MemberStatus beforeStatus,
        MemberStatus afterStatus,
        String reason,
        Instant createdAt
) {

    public static AdminMemberModerationLogResult from(
            MemberModerationLog log
    ) {
        return new AdminMemberModerationLogResult(
                log.getId(),
                log.getActorMemberId(),
                log.getTargetMemberId(),
                log.getAction(),
                log.getBeforeStatus(),
                log.getAfterStatus(),
                log.getReason(),
                log.getCreatedAt()
        );
    }
}
