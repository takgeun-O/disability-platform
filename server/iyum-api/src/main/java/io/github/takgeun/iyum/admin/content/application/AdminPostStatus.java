package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.community.post.domain.Post;

/**
 * 관리자 게시글 목록의 상태 필터.
 *
 * 삭제된 게시글은 hiddenAt이 남아 있어도 DELETED로 분류한다.
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
}