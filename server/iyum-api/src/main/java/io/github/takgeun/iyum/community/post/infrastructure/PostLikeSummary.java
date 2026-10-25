package io.github.takgeun.iyum.community.post.infrastructure;

/**
 * 이런 인터페이스를 조회 결과 프로젝션이라고 한다.
 * 엔티티 전체 대신 필요한 조회 값만 받을 때 사용하는 방식
 *
 * 응답 만들 때 아래처럼 변환할 것
 * boolean liked = summary.getViewerLikeCount() > 0;
 */
public interface PostLikeSummary {

    Long getPostId();

    long getLikeCount();

    // 조회 회원이 해당 게시글에 등록한 좋아요 개수.
    // 유니크 제약에 의해 0 또는 1이다.
    // 비로그인 조회에서는 0이다.
    long getViewerLikeCount();
}
