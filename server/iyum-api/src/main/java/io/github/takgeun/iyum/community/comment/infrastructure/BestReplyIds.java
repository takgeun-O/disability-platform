package io.github.takgeun.iyum.community.comment.infrastructure;

/**
 * 조회 결과를 받을 인터페이스 추가
 * 두 ID를 함께 받으면 서비스에서 어느 원댓글에 대댓글을 붙일지 바로 알 수 있다.
 */
public interface BestReplyIds {

    Long getParentId();

    Long getReplyId();
}
