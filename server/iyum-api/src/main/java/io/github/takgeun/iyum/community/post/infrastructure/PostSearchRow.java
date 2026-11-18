package io.github.takgeun.iyum.community.post.infrastructure;

import io.github.takgeun.iyum.community.post.domain.Post;

/**
 * 공개 목록에서 게시글과 DB가 계산한 본문 첫 일치 위치를 함께 읽는다.
 * 검색 조건과 같은 대소문자 비교 결과를 사용해 원문을 발췌한다.
 *
 * PostRepository의 공개 목록 쿼리에서
 * post라는 이름으로 조회한 값은 getPost()로,
 * bodyMatchPosition 이라는 이름으로 조회한 값은 getBodyMatchPosition()으로 읽는다.
 */
public interface PostSearchRow {

    Post getPost();

    // 0이면 본문 일치 없음
    // 1이면 첫 번째 문자에서 일치
    // DB가 소문자로 변환한 본문에서 센 위치
    // 이후 Java 원문을 자를 때 이 숫자를 subString()에 바로 넣으면 안된다.
    int getBodyMatchPosition();
}
