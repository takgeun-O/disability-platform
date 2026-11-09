package io.github.takgeun.iyum.admin.content.application;

/**
 * 관리자 게시글 목록의 작성 시각 정렬 방식.
 *
 * 같은 작성 시각을 가진 게시글도 일정한 순서로 조회되도록
 * 게시글 ID를 보조 정렬 기준으로 사용한다.
 *
 * 실제 QueryDSL 정렬 표현식은 조회 저장소에서 생성한다.
 */
public enum AdminPostSort {

    // 작성 시각 내림차순 -> 게시글 ID 내림차순
    LATEST,

    // 작성 시각 오름차순 -> 게시글 ID 오름차순
    OLDEST
}
