package io.github.takgeun.iyum.community.comment.infrastructure;

public interface CommentReplyCount {

    Long getParentId();

    Long getReplyCount();
}
