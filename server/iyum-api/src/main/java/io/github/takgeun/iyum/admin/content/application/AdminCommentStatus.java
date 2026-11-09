package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;

import java.util.Collection;
import java.util.Set;

/**
 * 댓글 자체의 상태.
 * 부모 게시글 상태에 따른 공개 여부와 구분한다.
 * ALL은 기존 단일 상태 요청과의 호환을 위해 유지한다.
 */
public enum AdminCommentStatus {

    ALL,
    VISIBLE,
    HIDDEN,
    DELETED;

    private static final Set<AdminCommentStatus> ALL_STATUSES =
            Set.of(VISIBLE, HIDDEN, DELETED);

    /**
     * 기존 단일 상태 조건을 실제 상태 집합으로 변환한다.
     * null과 ALL은 전체 조회를 의미한다.
     */
    public static Set<AdminCommentStatus> fromSingleStatus(
            AdminCommentStatus status
    ) {
        if(status == null || status == ALL) {
            return ALL_STATUSES;
        }

        return Set.of(status);
    }

    /**
     * 명시적으로 전달된 다중 상태 조건을 검증하고
     * 중복을 제거한 불변 집합으로 복사한다.
     */
    public static Set<AdminCommentStatus> normalizeStatuses(
            Collection<AdminCommentStatus> statuses
    ) {
        if(statuses == null || statuses.isEmpty()) {
            throw invalidStatuses();
        }

        for (AdminCommentStatus status : statuses) {
            if(status == null || status == ALL) {
                throw invalidStatuses();
            }
        }

        return Set.copyOf(statuses);
    }

    public static AdminCommentStatus from(Comment comment) {
        // 부모 게시글의 상태를 참조하지 않는다.
        // 댓글 자체가 숨김과 삭제 상태라면 삭제를 우선한다.
        if (comment.getDeletedAt() != null) {
            return DELETED;
        }

        if (comment.getHiddenAt() != null) {
            return HIDDEN;
        }

        return VISIBLE;
    }

    private static AdminContentReadException invalidStatuses() {
        return new AdminContentReadException(
                AdminContentReadException.Code.INVALID_REQUEST_PARAMETER
        );
    }
}