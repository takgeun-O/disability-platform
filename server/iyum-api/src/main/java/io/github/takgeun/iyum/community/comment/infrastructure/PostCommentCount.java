package io.github.takgeun.iyum.community.comment.infrastructure;

/**
 * 게시글별 댓글 개수 조회 결과
 * 댓글 엔티티 전체를 읽지 않고 게시글 ID와 개수만 받는다.
 */
public interface PostCommentCount {

    Long getPostId();

    Long getCommentCount();
}
