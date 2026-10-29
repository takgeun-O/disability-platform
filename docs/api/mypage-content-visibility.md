# MyPage 콘텐츠 공개 상태 응답

## 목적

관리자가 숨긴 게시글과 댓글을 작성자가 마이페이지에서
제한된 정보로 확인하고 직접 삭제할 수 있도록 한다.

공개 커뮤니티 조회의 숨김 정책은 유지한다.

## 적용 API

- GET /api/v1/members/me/posts
- GET /api/v1/members/me/comments
- GET /api/v1/members/me/activity-summary

모든 요청은 로그인한 회원을 기준으로 처리한다.
요청자가 전달한 memberId로 조회 대상을 변경할 수 없다.

개인화 응답에는 Cache-Control: no-store를 적용한다.

## visibility

| 값 | 의미 | 적용 대상 |
|---|---|---|
| VISIBLE | 공개 상태 | 게시글, 댓글 |
| HIDDEN | 해당 콘텐츠가 숨김 상태 | 게시글, 댓글 |
| PARENT_HIDDEN | 원본 게시글이 숨김 상태 | 댓글 |

댓글과 원본 게시글이 모두 숨김 상태이면 PARENT_HIDDEN을 반환한다.

## 내 게시글

작성자의 삭제되지 않은 게시글을 반환한다.
숨김 게시글도 목록에 포함한다.

VISIBLE은 기존 정보를 유지한다.

HIDDEN은 다음 필드만 값을 유지한다.

- id
- createdAt
- visibility

다음 필드는 명시적인 null로 반환한다.

- type
- topic
- title
- contentPreview
- author
- commentCount
- likeCount
- liked

예시:

```json
{
  "id": 31,
  "type": null,
  "topic": null,
  "title": null,
  "contentPreview": null,
  "author": null,
  "createdAt": "2026-10-19T10:00:00.123456Z",
  "commentCount": null,
  "likeCount": null,
  "liked": null,
  "visibility": "HIDDEN"
}
```

## 내 댓글

작성자의 삭제되지 않은 댓글을 반환한다.
원본 게시글이 삭제된 댓글은 제외한다.

댓글이나 원본 게시글이 숨김 상태여도 목록에 포함한다.

VISIBLE은 기존 정보를 유지한다.

HIDDEN과 PARENT_HIDDEN은 다음 필드만 값을 유지한다.

- commentId
- createdAt
- post.postId
- visibility

다음 필드는 명시적인 null로 반환한다.

- content
- updatedAt
- likeCount
- liked
- post.title
- post.type
- post.topic

post 객체 자체는 null이 아니다.

예시:

```json
{
  "commentId": 103,
  "content": null,
  "createdAt": "2026-10-19T10:00:00.123456Z",
  "updatedAt": null,
  "post": {
    "postId": 31,
    "title": null,
    "type": null,
    "topic": null
  },
  "likeCount": null,
  "liked": null,
  "visibility": "PARENT_HIDDEN"
}
```

## 활동 개수

postCount:
- 작성자의 삭제되지 않은 게시글 수
- 숨김 게시글 포함

commentCount:
- 작성자의 삭제되지 않은 댓글 수
- 숨김 댓글과 숨김 게시글의 댓글 포함
- 삭제된 게시글의 댓글 제외

개수와 내 활동 목록의 포함 기준을 일치시킨다.

## 정렬과 커서

작성 시각 내림차순, ID 내림차순을 유지한다.

숨김 항목도 페이지 크기에 포함한다.
다음 커서는 실제 반환한 마지막 항목을 기준으로 만든다.
마지막 항목이 숨김 상태여도 동일하게 처리한다.

다음 페이지 존재 여부 확인용으로 추가 조회한 항목은
응답 항목과 좋아요 집계 대상에 포함하지 않는다.

## 숨김 항목 삭제

기존 삭제 API를 사용한다.

- DELETE /api/v1/community/posts/{postId}
- DELETE /api/v1/community/posts/{postId}/comments/{commentId}

ACTIVE 상태의 작성자 본인만 삭제할 수 있다.
로그인 세션과 해당 세션의 유효한 CSRF 토큰이 필요하다.

- 숨김 게시글: 작성자 삭제 가능
- 숨김 댓글: 댓글 작성자 삭제 가능
- 숨김 게시글의 댓글: 댓글 작성자 삭제 가능
- 게시글 작성자가 다른 회원의 댓글 삭제: 불가
- 삭제된 게시글의 댓글 삭제: 불가

성공 응답은 204이며, 삭제 후 내 활동 목록에서 제외한다.
소프트 삭제를 적용하며 원본과 기존 숨김 상태를 보존한다.

## 프론트엔드 연동 유의사항

- visibility에 따라 공개 항목과 숨김 안내 항목을 구분한다.
- 숨김 항목의 null 값을 좋아요 0개 또는 liked=false로 해석하지 않는다.
- 숨김 항목에서는 제목·본문·작성자·집계 정보를 표시하지 않는다.
- 숨김 항목의 상세 이동·수정·좋아요 조작은 제공하지 않는다.
- 작성자가 삭제할 수 있는 동작을 제공한다.
- 삭제 성공 후 목록과 활동 개수를 갱신한다.
- 관리자 처리 사유는 이 응답에 포함하지 않는다.

이번 변경은 nullable 필드를 추가하므로
프론트엔드의 응답 타입과 런타임 검증도 함께 수정해야 한다.