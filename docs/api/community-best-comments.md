# 게시글별 베스트 댓글 조회

`GET /api/v1/community/posts/{postId}/comments/best`

경로에 조회할 게시글 ID만 전달한다. 요청 본문과 쿼리 파라미터는 없다.
비로그인으로 조회할 수 있으며, 로그인 세션이 있으면 해당 회원을 기준으로 `liked`를 반환한다.
성공 응답의 헤더는 `Cache-Control: no-store`다.

## 선정과 순서

해당 게시글의 공개 댓글 중 작성 시각에 관계없이 **현재 좋아요가 10개 이상인 댓글**을 최대 3개 반환한다.
좋아요 수가 많은 순서로 정렬하고, 동률이면 먼저 작성된 댓글을, 작성 시각도 같으면 ID가 작은 댓글을 우선한다.
댓글이 숨김·삭제되거나 좋아요가 9개로 줄면 다음 조회에서 제외되며, 남은 후보가 있으면 그 순서대로 채운다.

## 성공 응답

예를 들어 `GET /api/v1/community/posts/42/comments/best`에 대한 비로그인 응답은 다음과 같다.

```json
{
  "items": [
    {
      "commentId": 17,
      "content": "첫 상담에서 궁금했던 내용을 미리 적어 갔어요.",
      "author": { "memberId": 8, "nickname": "이움회원" },
      "createdAt": "2026-09-01T01:00:00Z",
      "updatedAt": "2026-09-01T01:00:00Z",
      "contentUpdatedAt": null,
      "likeCount": 10,
      "liked": false
    }
  ]
}
```

`items` 배열 순서가 순위다. `likeCount`는 전체 회원의 현재 좋아요 수이며, `liked`는 조회한 회원의 좋아요 여부다. 비로그인이면 `liked`는 항상 `false`다.
`createdAt`은 작성 시각, `updatedAt`은 댓글 엔티티의 마지막 변경 시각이다.
`contentUpdatedAt`은 마지막 본문 변경 시각이며 별도 기록이 없으면 `null`이다.

## 후보 없음과 게시글 오류

| 상황 | HTTP | 응답 |
|---|---|---|
| 조회 가능한 게시글에 선정 기준을 충족하는 댓글이 없음 | 200 | `{"items":[]}` |
| 게시글이 존재하지 않거나 숨김·삭제되어 공개 조회할 수 없음 | 404 | 공통 오류 응답의 `code`가 `POST_NOT_FOUND` |
