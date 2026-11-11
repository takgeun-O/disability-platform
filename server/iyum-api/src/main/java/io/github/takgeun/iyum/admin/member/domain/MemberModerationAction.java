package io.github.takgeun.iyum.admin.member.domain;

import io.github.takgeun.iyum.member.domain.MemberStatus;

public enum MemberModerationAction {

    SUSPEND(
            MemberStatus.ACTIVE,
            MemberStatus.SUSPENDED
    ),

    LIFT_SUSPENSION(
            MemberStatus.SUSPENDED,
            MemberStatus.ACTIVE
    );

    private final MemberStatus beforeStatus;
    private final MemberStatus afterStatus;

    MemberModerationAction(
            MemberStatus beforeStatus,
            MemberStatus afterStatus
    ) {
        this.beforeStatus = beforeStatus;
        this.afterStatus = afterStatus;
    }

    public MemberStatus beforeStatus() {
        return beforeStatus;
    }

    public MemberStatus afterStatus() {
        return afterStatus;
    }
}
