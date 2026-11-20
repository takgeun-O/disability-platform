# 주간 베스트 게시글 API

전체 토픽 조회

```http
GET /api/v1/community/posts/weekly-best?size=4
```

보청기 토픽 조회

```http
GET /api/v1/community/posts/weekly-best?size=4&topic=HEARING_AID
```

선택 파라미터 `topic`은 게시글 토픽 enum 값이다. 생략하면 전체 토픽에서, 지정하면 해당 토픽의 공개 글에서 순위를 선정한다. 글 유형은 순위 조건에 포함되지 않는다.

공개 조회이며 성공 응답은 `Cache-Control: no-store`다. 선택적 정수 `size`는 반환할 최대 건수다. 기본값은 20이고 1부터 50까지 허용한다.

요청마다 기준 시각 `asOf`를 한 번 정한다. 게시글의 `createdAt`이 `[asOf - 7일, asOf]`에 속하고 현재 좋아요가 1개 이상인 공개 글을 선정한다. 양 끝 시각은 포함하며 숨김·삭제 글은 제외한다. 기간은 게시글 작성 시각에 적용하고 좋아요 수는 전체 기간의 현재 좋아요를 집계한다.

한 응답의 순위와 좋아요 수는 같은 조회 시점의 상태를 사용한다. 조회 도중 취소한 좋아요는 다음 요청에 반영된다.

## 성공 응답

`items`는 현재 좋아요 수 내림차순, 동률이면 작성 시각 내림차순, 두 값이 같으면 게시글 ID 내림차순이다. 각 항목은 공개 피드와 동일한 `PostFeedItemResponse`다. `contentPreview`는 공백을 정리한 최대 200 유니코드 코드포인트의 본문 미리보기다. `likeCount`는 전체 좋아요 수이고, `liked`는 로그인 회원 본인의 좋아요 여부다. 비회원이면 항상 `false`다.

다음은 비회원이 전체 토픽에서 4건을 요청했을 때 대상이 2건인 경우의 HTTP 200 응답이다.

```json
{
  "items": [
    {
      "id": 41,
      "type": "REVIEW",
      "topic": "HEARING_AID",
      "title": "보청기 첫 착용 후기",
      "contentPreview": "첫 일주일 동안 유용했던 설정을 공유합니다.",
      "author": { "memberId": 8, "nickname": "예시회원" },
      "createdAt": "2026-11-17T01:00:00Z",
      "commentCount": 2,
      "likeCount": 6,
      "liked": false
    },
    {
      "id": 52,
      "type": "QUESTION",
      "topic": "DAILY",
      "title": "회의 자막은 어떻게 사용하시나요?",
      "contentPreview": "처음 사용하는 동료와 함께 설정하려고 합니다.",
      "author": { "memberId": 12, "nickname": "예시독자" },
      "createdAt": "2026-11-18T02:00:00Z",
      "commentCount": 0,
      "likeCount": 1,
      "liked": false
    }
  ]
}
```

대상이 없을 때도 HTTP 200이다.

```json
{"items":[]}
```

## 잘못된 요청

| 쿼리 예시 | HTTP 상태 | 오류 코드 |
|---|---|---|
| `size=0`, `size=51` | 400 | `INVALID_PAGE_REQUEST` |
| `size=abc` | 400 | `INVALID_REQUEST_PARAMETER` |
| `topic=UNKNOWN_TOPIC` | 400 | `INVALID_REQUEST_PARAMETER` |
