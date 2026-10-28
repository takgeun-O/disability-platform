# 게시글별 댓글 정렬 API

## 조회 주소

GET /api/v1/community/posts/{postId}/comments

비로그인 조회 가능.
로그인 시 세션의 회원을 기준으로 liked를 반환한다.
성공 응답은 Cache-Control: no-store를 유지한다.

## 정렬

- OLDEST: 작성 시각 ASC → 댓글 ID ASC. 기본값.
- LATEST: 작성 시각 DESC → 댓글 ID DESC.
- POPULAR: 좋아요 수 DESC → 작성 시각 ASC → 댓글 ID ASC.

size는 기본값 20, 허용 범위 1~50이다.

## 첫 조회

sort와 size를 전달한다.
afterCreatedAt, afterId, afterSort, afterLikeCount는 모두 생략한다.

## 후속 조회

이전 응답의 nextCursor를 다음과 같이 전달한다.

- createdAt → afterCreatedAt
- commentId → afterId
- sort → afterSort
- likeCount → afterLikeCount: POPULAR일 때만 전달

createdAt은 서버가 반환한 문자열을 그대로 사용한다.
Date 변환을 거쳐 마이크로초 정밀도를 잃지 않도록 한다.

기존 OLDEST 요청은 afterSort 생략을 허용한다.
LATEST·POPULAR의 후속 조회는 afterSort가 필요하다.
POPULAR의 afterLikeCount는 0도 유효하며, 생략하거나 음수를 보낼 수 없다.

정렬 변경 시 기존 목록·커서를 초기화하고 첫 페이지부터 조회한다.

## 응답

items와 hasNext의 기존 구조는 유지한다.

nextCursor:
- createdAt
- commentId
- sort
- likeCount: POPULAR은 집계값, OLDEST·LATEST는 null

마지막 페이지는 hasNext=false, nextCursor=null이다.

마이페이지 '내 댓글' API의 커서 응답은 변경하지 않는다.

## 오류

- INVALID_REQUEST_PARAMETER: 정렬 값·숫자·날짜 형식 오류, HTTP 400
- INVALID_CURSOR: 커서 누락·조합 오류·정렬 불일치, HTTP 400
- INVALID_PAGE_REQUEST: 조회 개수 범위 오류, HTTP 400
- POST_NOT_FOUND: 없거나 삭제된 게시글, HTTP 404

## 인기순의 일관성 범위

한 서비스 조회 트랜잭션 안에서는 REPEATABLE_READ로
순위·본문·좋아요 상태를 같은 스냅샷에서 조회한다.

서로 다른 페이지 요청 사이에는 좋아요 변경으로 순위가 바뀔 수 있다.
전체 페이지에 걸친 고정 순위나 중복·누락 방지를 보장하지 않는다.

프론트는 댓글 ID로 중복을 제거하고,
새로고침 시 첫 페이지부터 다시 조회한다.
좋아요 조작만으로 현재 화면의 댓글을 즉시 재배치하지 않는다.