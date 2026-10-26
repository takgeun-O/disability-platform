package io.github.takgeun.iyum.community.comment.infrastructure;

public interface CommentLikeSummary {

    Long getCommentId();

    long getLikeCount();

    long getViewerLikeCount();  // 유니크 제약에 따라 0 또는 1
}
