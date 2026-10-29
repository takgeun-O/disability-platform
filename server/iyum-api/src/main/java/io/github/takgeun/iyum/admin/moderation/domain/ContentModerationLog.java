package io.github.takgeun.iyum.admin.moderation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "admin_content_moderation_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentModerationLog {

    public static final int REASON_MAX_LENGTH =
            ModerationInputPolicy.REASON_MAX_LENGTH;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_member_id", nullable = false, updatable = false)
    private Long actorMemberId;

    @Column(name = "post_id", updatable = false)
    private Long postId;

    @Column(name = "comment_id", updatable = false)
    private Long commentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20, updatable = false)
    private ModerationAction action;

    @Column(
            name = "reason",
            nullable = false,
            length = REASON_MAX_LENGTH,
            updatable = false
    )
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 참고로 처리자가 ACTIVE ADMIN인지 확인하는 책임은 서비스에 있다.
    // 이력 엔티티는 형식과 값의 유효성을 검증함.
    public ContentModerationLog(
            Long actorMemberId,
            Long postId,
            Long commentId,
            ModerationAction action,
            String reason,
            Instant createdAt
    ) {
        validatePositiveId(actorMemberId, "처리 관리자 ID");

        // 둘 다 없거나 둘 다 있으면 올바른 처리 대상이 아니다.
        if ((postId == null) == (commentId == null)) {
            throw new IllegalArgumentException(
                    "처리 대상은 게시글 또는 댓글 중 하나여야 합니다."
            );
        }

        if(postId != null) {
            validatePositiveId(postId, "게시글 ID");
        }

        if(commentId != null) {
            validatePositiveId(commentId, "댓글 ID");
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
                ModerationInputPolicy.normalizeReason(reason);

        this.actorMemberId = actorMemberId;
        this.postId = postId;
        this.commentId = commentId;
        this.action = action;
        this.reason = normalizedReason;
        this.createdAt = createdAt;
    }

    public static ContentModerationLog forPost(
            Long actorMemberId,
            Long postId,
            ModerationAction action,
            String reason,
            Instant createdAt
    ) {
        return new ContentModerationLog(
                actorMemberId,
                postId,
                null,
                action,
                reason,
                createdAt
        );
    }

    public static ContentModerationLog forComment(
            Long actorMemberId,
            Long commentId,
            ModerationAction action,
            String reason,
            Instant createdAt
    ) {
        return new ContentModerationLog(
                actorMemberId,
                null,
                commentId,
                action,
                reason,
                createdAt
        );
    }

    private static void validatePositiveId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    name + "는 양수여야 합니다."
            );
        }
    }
}
