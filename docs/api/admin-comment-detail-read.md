# 관리자 댓글 단건 조회 API

## 목적

관리자가 댓글 ID로 댓글의 현재 본문, 작성자, 상태 및 부모 게시글 정보를 조회한다.

운영 기록의 대상이 댓글인 경우, 해당 댓글의 현재 내용을 확인하는 데 사용한다.

반환하는 본문은 현재 DB에 저장된 내용이다.
운영 조치 당시의 본문을 보관한 스냅샷이 아니다.

## 요청

GET /api/v1/admin/comments/{commentId}

### 인증

- 관리자 로그인 세션 쿠키가 필요하다.
- 현재 회원 상태가 ACTIVE이고 역할이 ADMIN이어야 한다.
- 세션의 인증 버전과 역할이 현재 DB 정보와 일치해야 한다.
- GET 요청이므로 CSRF 토큰은 필요하지 않다.
- 로그인 이후 역할·회원 상태·인증 버전이 변경되면 기존 세션은
  401 AUTHENTICATION_REQUIRED로 차단될 수 있다.

### 경로 변수

| 이름 | 타입 | 설명 |
|---|---|---|
| commentId | Long | 조회할 댓글 ID |

- 양수인 댓글 ID를 전달한다.
- 0 이하 또는 존재하지 않는 ID는 404 COMMENT_NOT_FOUND를 반환한다.
- 숫자로 변환할 수 없거나 Long 범위를 벗어난 값은
  400 INVALID_REQUEST_PARAMETER를 반환한다.
- 부모 게시글 ID는 요청에 필요하지 않다.
- 검색·필터·페이지·정렬 파라미터는 제공하지 않는다.

## 조회 정책

- 정상·숨김·삭제 댓글 모두 관리자 조회를 허용한다.
- 부모 게시글이 숨김 또는 삭제 상태여도 조회를 허용한다.
- 숨김·삭제 댓글도 현재 저장된 전체 본문을 반환한다.
- 일반 공개 조회의 노출 정책은 변경하지 않는다.
- 이 API는 조회만 수행하며 콘텐츠 상태나 운영 기록을 변경하지 않는다.

## 성공 응답

HTTP 200 OK
Cache-Control: no-store

```json
{
  "commentId": 456,
  "postId": 123,
  "postStatus": "HIDDEN",
  "content": "현재 저장된 댓글 본문입니다.",
  "author": {
    "memberId": 8,
    "nickname": "댓글작성회원",
    "withdrawn": false
  },
  "status": "VISIBLE",
  "createdAt": "2026-10-30T10:00:00Z",
  "updatedAt": "2026-10-30T10:15:00Z",
  "contentUpdatedAt": "2026-10-30T10:15:00Z",
  "hiddenAt": null,
  "deletedAt": null
}
```

### 응답 필드

| 필드 | 의미 |
|---|---|
| commentId | 댓글 ID |
| postId | 댓글이 속한 부모 게시글 ID |
| postStatus | 부모 게시글의 현재 상태 |
| content | 현재 저장된 댓글 전체 본문 |
| author.memberId | 작성자 회원 ID |
| author.nickname | 작성자 표시 닉네임 |
| author.withdrawn | 작성자 탈퇴 여부 |
| status | 댓글 자체의 현재 상태 |
| createdAt | 댓글 작성 시각 |
| updatedAt | 댓글 엔티티의 최종 변경 시각 |
| contentUpdatedAt | 마지막으로 본문이 실제 변경된 시각. 기록이 없으면 null |
| hiddenAt | 현재 개별 숨김 상태에 남아 있는 숨김 시각. 없으면 null |
| deletedAt | 댓글 삭제 시각. 삭제되지 않았으면 null |

### 상태 해석

postStatus와 status는 각각 VISIBLE, HIDDEN, DELETED 중 하나다.
ALL은 응답 상태로 반환하지 않는다.

| 상태 | 판정 기준 |
|---|---|
| VISIBLE | 삭제 시각과 숨김 시각이 모두 없음 |
| HIDDEN | 삭제 시각이 없고 숨김 시각이 있음 |
| DELETED | 삭제 시각이 있음. 숨김 시각 유무와 무관함 |

- 부모 게시글과 댓글의 상태는 독립적으로 판단한다.
- 부모 게시글이 HIDDEN 또는 DELETED여도 댓글 자체는 VISIBLE일 수 있다.
- 댓글의 VISIBLE 상태만으로 일반 사용자에게 공개된다고 판단하면 안 된다.
- 숨김 후 삭제된 댓글은 DELETED이며 hiddenAt이 함께 남아 있을 수 있다.
- 조회 가능 여부와 숨김·복원 조치 가능 여부는 별개다.
- 조치 가능 여부는 기존 관리자 숨김·복원 API 정책을 따른다.

### 작성자와 시각

- 탈퇴한 작성자는 nickname="탈퇴한 회원", withdrawn=true로 반환한다.
- 탈퇴한 작성자도 memberId는 유지한다.
- 작성자의 이메일과 비밀번호 해시는 반환하지 않는다.
- contentUpdatedAt, hiddenAt, deletedAt은 값이 없더라도
  필드를 생략하지 않고 명시적 null로 반환한다.
- contentUpdatedAt이 null이라는 이유로 과거에 본문 수정이 전혀 없었다고
  단정하지 않는다. 과거 수정 시각이 별도로 기록되지 않은 데이터도 포함한다.
- updatedAt은 본문 외 변경에도 갱신될 수 있으므로
  본문 수정 여부를 판단하는 기준으로 사용하지 않는다.
- 시각은 UTC ISO 8601 형식이며 프론트에서는 Asia/Seoul 기준으로 표시한다.

## 오류

| HTTP 상태 | code | 의미 |
|---|---|---|
| 400 | INVALID_REQUEST_PARAMETER | 댓글 ID 숫자 변환 실패 또는 Long 범위 초과 |
| 401 | AUTHENTICATION_REQUIRED | 비로그인 또는 유효하지 않은 인증 정보 |
| 403 | ACCESS_DENIED | Security 필터에서 관리자 접근 거부 |
| 403 | ADMIN_REQUIRED | 서비스에서 관리자 권한 부족 확인 |
| 404 | COMMENT_NOT_FOUND | 댓글이 존재하지 않거나 ID가 0 이하 |

프론트는 두 종류의 403 코드를 모두 처리해야 한다.

숨김·삭제 상태 자체는 이 관리자 조회 API의 404 사유가 아니다.
오류 응답의 Cache-Control에는 no-store 지시자가 포함된다.

## 운영 기록 연동

- 운영 기록의 targetType이 COMMENT이면 targetId를 commentId로 사용한다.
- COMMENT의 targetId를 부모 게시글 ID로 사용하면 안 된다.
- 부모 게시글 이동에는 단건 조회 응답의 postId를 사용한다.
- 운영 기록의 조치·처리 사유·처리 시각은 당시의 기록이다.
- 이 API의 본문·작성자·상태는 현재 조회한 정보다.
- 화면에서는 "현재 댓글 내용"처럼 표시하여 당시 기록과 구분한다.
- 운영 기록이 HIDE여도 댓글의 현재 상태는 VISIBLE 또는 DELETED일 수 있다.
- 댓글 조회가 실패해도 운영 기록 자체를 삭제하거나 숨기지 않는다.
  단, 인증·권한 오류에서는 관리자 화면의 기존 데이터도 정리한다.
- 이 API는 게시글 제목·본문이나 해당 댓글의 전체 운영 이력을 제공하지 않는다.

## 프론트 연결 시 유의 사항

- 세션 쿠키를 포함해 요청한다.
- 선택한 댓글에 대해 필요한 시점에 조회한다.
- 목록의 모든 댓글을 미리 단건 조회하지 않는다.
- 선택 항목이 바뀌면 이전 본문을 새 항목의 내용처럼 표시하지 않는다.
- 늦게 도착한 이전 요청의 성공·오류 응답이
  현재 선택 항목의 상세 화면을 덮어쓰지 않도록 한다.
- 401 또는 403 응답 시 기존 관리자 상세 데이터를 정리한다.
- 404와 네트워크 오류를 구분하여 안내한다.
- 본문은 일반 텍스트로 렌더링하고 줄바꿈을 보존한다.
- 관리자 조회 데이터를 일반 공개 화면의 데이터로 재사용하지 않는다.
- 공개 원문 이동은 일반 공개 조회의 접근 정책을 따른다.
  관리자 단건 조회 성공이 공개 원문 접근 가능을 의미하지 않는다.

## 관련 API

- [관리자 게시글별 댓글 목록 조회](admin-comment-read.md)
- [관리자 콘텐츠 다중 상태 조회](admin-content-status-filter.md)
- [댓글 본문 수정 시각](comment-content-updated-at.md)
- [관리자 운영 기록 조회](../Admin-Operation-Log-API.md)