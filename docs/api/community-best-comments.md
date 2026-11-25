# 게시글별 베스트 댓글 조회

`GET /api/v1/community/posts/{postId}/comments/best`

경로에 조회할 게시글 ID만 전달한다. 요청 본문과 쿼리 파라미터는 없다.
비로그인으로 조회할 수 있으며, 로그인 세션이 있으면 해당 회원을 기준으로 `liked`를 반환한다.
성공 응답의 헤더는 `Cache-Control: no-store`다.

## 선정과 순서

작성 기간 제한 없이 현재 좋아요가 10개 이상인 공개 원댓글과 공개 답글을 후보로 삼는다. 같은 원댓글에 속한 후보 중 좋아요가 가장 많은 댓글이 그 묶음의 순위를 결정한다. 동률이면 작성 시각이 빠른 댓글, 시각까지 같으면 ID가 작은 댓글을 우선한다. 이 기준으로 최대 3개의 원댓글 묶음을 반환한다.

각 카드의 최상위 필드는 원댓글이다. 원댓글이 10개 미만이어도 선정된 답글이 있으면 카드에 포함된다. 같은 원댓글 아래의 공개 답글 중 기준을 충족한 최상위 한 건을 같은 정렬 기준으로 골라 `bestReply`에 담는다. 삭제된 원댓글은 삭제 안내와 함께 공개 답글을 표시할 수 있다. 숨긴 원댓글 아래의 답글은 후보에서 제외한다. 숨김·삭제 또는 좋아요 취소로 후보가 달라지면 다음 조회에 반영된다.

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
      "replyCount": 1,
      "deleted": false,
      "likeCount": 0,
      "liked": false,
      "bestReply": {
        "commentId": 24,
        "content": "저도 미리 질문을 적어 가니 도움이 됐어요.",
        "author": { "memberId": 12, "nickname": "다온" },
        "createdAt": "2026-09-02T02:00:00Z",
        "updatedAt": "2026-09-02T02:00:00Z",
        "contentUpdatedAt": null,
        "replyCount": 0,
        "deleted": false,
        "likeCount": 10,
        "liked": false
      }
    }
  ]
}
```

`items` 배열 순서가 카드 순위다. 선정된 답글이 없으면 `bestReply`는 null이다. 기존 응답에서 필드가 생략된 경우에도 원댓글만 표시한다. 예시에서 원댓글의 좋아요는 0개이고 답글의 좋아요는 10개다. `likeCount`는 전체 회원의 현재 좋아요 수이며, `liked`는 조회한 회원의 좋아요 여부다. 비로그인이면 `liked`는 항상 `false`다.
`createdAt`은 작성 시각, `updatedAt`은 댓글 엔티티의 마지막 변경 시각이다.
`contentUpdatedAt`은 마지막 본문 변경 시각이며 별도 기록이 없으면 `null`이다.

## 후보 없음과 게시글 오류

| 상황 | HTTP | 응답 |
|---|---|---|
| 조회 가능한 게시글에 선정 기준을 충족하는 댓글이 없음 | 200 | `{"items":[]}` |
| 게시글이 존재하지 않거나 숨김·삭제되어 공개 조회할 수 없음 | 404 | 공통 오류 응답의 `code`가 `POST_NOT_FOUND` |
