package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.community.post.domain.Post;

import java.util.Collection;
import java.util.Set;

/**
 * 관리자 게시글 목록의 상태 필터.
 *
 * 삭제된 게시글은 hiddenAt이 남아 있어도 DELETED로 분류한다.
 * ALL은 기존 단일 상태 요청과의 호환을 위해 유지한다.
 * 다중 상태 집합에는 실제 상태인 VISIBLE, HIDDEN, DELETED 만 저장한다.
 */
public enum AdminPostStatus {

    // 상태 제한 없음
    ALL,

    // deletedAt is NULL AND hiddenAt IS NULL
    VISIBLE,

    // deletedAt is NULL AND hiddenAt IS NOT NULL
    HIDDEN,

    // deletedAt is NOT NULL
    DELETED;

    private static final Set<AdminPostStatus> ALL_STATUSES =
            Set.of(VISIBLE, HIDDEN, DELETED);

    /**
     * 기존 단일 상태 조건을 실제 상태 집합으로 변환
     *
     * 기존 동작대로 null과 ALL은 전체 조회를 의미한다.
     */
    public static Set<AdminPostStatus> fromSingleStatus(
            AdminPostStatus status
    ) {
        if(status == null || status == ALL) {
            return ALL_STATUSES;
        }

        return Set.of(status);
    }

    /**
     * 명시적으로 전달된 다중 상태 조건을 검증한다.
     *
     * null·빈 목록·null 원소·ALL은 허용하지 않는다.
     * 중복은 제거하고, 원본 컬렉션과 분리된 불변 집합을 반환한다.
     *
     * 요청에서 상태 조건을 생략한 경우의 기본값 처리는
     * 호출자가 fromSingleStatus(null)을 사용해 명시적으로 수행한다.
     */
    public static Set<AdminPostStatus> normalizeStatuses(
            Collection<AdminPostStatus> statuses
    ) {
        if(statuses == null || statuses.isEmpty()) {
            throw invalidStatuses();
        }

        for (AdminPostStatus status : statuses) {
            if(status == null || status == ALL) {
                throw invalidStatuses();
            }
        }

        return Set.copyOf(statuses);
    }

    public static AdminPostStatus from(Post post) {
        // 숨김과 삭제가 모두 존재하면 삭제 상태를 우선한다.
        if(post.getDeletedAt() != null) {
            return DELETED;
        }

        if(post.getHiddenAt() != null) {
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