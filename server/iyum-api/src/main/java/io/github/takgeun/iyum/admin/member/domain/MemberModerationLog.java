package io.github.takgeun.iyum.admin.member.domain;

import io.github.takgeun.iyum.member.domain.MemberStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "admin_member_moderation_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberModerationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "actor_member_id",
            nullable = false,
            updatable = false
    )
    private Long actorMemberId;

    @Column(
            name = "target_member_id",
            nullable = false,
            updatable = false
    )
    private Long targetMemberId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "action",
            nullable = false,
            length = 30,
            updatable = false
    )
    private MemberModerationAction action;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "before_status",
            nullable = false,
            length = 20,
            updatable = false
    )
    private MemberStatus beforeStatus;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "after_status",
            nullable = false,
            length = 20,
            updatable = false
    )
    private MemberStatus afterStatus;

    @Column(
            name = "reason",
            nullable = false,
            length = MemberModerationInputPolicy.REASON_MAX_LENGTH,
            updatable = false
    )
    private String reason;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    private MemberModerationLog(
            Long actorMemberId,
            Long targetMemberId,
            MemberModerationAction action,
            String reason,
            Instant createdAt
    ) {
        validatePositiveId(actorMemberId, "처리 관리자 ID");
        validatePositiveId(targetMemberId, "대상 회원 ID");

        if(actorMemberId.equals(targetMemberId)) {
            throw new IllegalArgumentException(
                    "자기 자신에게 회원 운영 조치를 할 수 없습니다."
            );
        }

        if(action == null) {
            throw new IllegalArgumentException(
                    "처리 종류는 필수입니다."
            );
        }

        if(createdAt == null) {
            throw new IllegalArgumentException(
                    "처리 시각은 필수입니다."
            );
        }

        String normalizedReason =
                MemberModerationInputPolicy.normalizeReason(reason);

        this.actorMemberId = actorMemberId;
        this.targetMemberId = targetMemberId;
        this.action = action;
        this.beforeStatus = action.beforeStatus();
        this.afterStatus = action.afterStatus();
        this.reason = normalizedReason;
        this.createdAt = createdAt;
    }

    public static MemberModerationLog create(
            Long actorMemberId,
            Long targetMemberId,
            MemberModerationAction action,
            String reason,
            Instant createdAt
    ) {
        return new MemberModerationLog(
                actorMemberId,
                targetMemberId,
                action,
                reason,
                createdAt
        );
    }

    private static void validatePositiveId(Long id, String name) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException(
                    name + "는 양수여야 합니다."
            );
        }
    }
}
