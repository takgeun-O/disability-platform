package io.github.takgeun.iyum.admin.operation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * 운영 기록 한 건의 서비스 반환 객체.
 * 컨트롤러에 JPA 엔티티를 직접 전달하지 않는다.
 */
public record AdminOperationLogItemResult(
        Long logId,
        Long actorMemberId,
        AdminOperationLogTargetType targetType,
        Long targetId,
        ModerationAction action,
        ModerationReasonType reasonType,
        String reasonDetail,
        String reason,
        Instant createdAt,  // 운영 조치 시각임. 대상 콘텐츠의 작성 시각이 아님을 주의
        @Nullable AdminOperationLogCurrentTargetResult currentTarget
) {

    public static AdminOperationLogItemResult from(
            ContentModerationLog log,
            @Nullable AdminOperationLogCurrentTargetResult currentTarget
    ) {
        // 엔티티와 DB 제약조건에 의해
        // postId와 commentId 중 정확히 하나만 존재한다.
        boolean postTarget = log.getPostId() != null;

        return new AdminOperationLogItemResult(
                log.getId(),
                log.getActorMemberId(),
                // ALL은 검색 조건에서만 사용하고,
                // 실제 결과에는 POST 또는 COMMENT만 들어간다.
                postTarget
                        ? AdminOperationLogTargetType.POST
                        : AdminOperationLogTargetType.COMMENT,
                postTarget
                        ? log.getPostId()
                        : log.getCommentId(),
                log.getAction(),
                log.getReasonType(),
                log.getReasonDetail(),
                // 과거 기록의 표시 문구를 그대로 반환한다.
                // 현재 enum 표시명으로 다시 계산하지 않는다.
                log.getReason(),
                log.getCreatedAt(),
                currentTarget
        );
    }
}
