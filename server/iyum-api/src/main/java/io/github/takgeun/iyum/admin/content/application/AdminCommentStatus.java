package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;

/**
 * 댓글 자체의 상태.
 * 부모 게시글 상태에 따른 공개 여부와 구분한다.
 */
public enum AdminCommentStatus {

    ALL,
    VISIBLE,
    HIDDEN,
    DELETED;

    public static AdminCommentStatus from(Comment comment) {
        // 숨긴 뒤 삭제했다면 삭제 상태를 우선한다.
        if (comment.getDeletedAt() != null) {
            return DELETED;
        }

        if (comment.getHiddenAt() != null) {
            return HIDDEN;
        }

        return VISIBLE;
    }
}