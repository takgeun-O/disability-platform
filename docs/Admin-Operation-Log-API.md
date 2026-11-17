# 관리자 운영 기록 조회 API

## API 목적

관리자가 게시글·댓글의 과거 숨김·복원 이력과 조회 시점의 현재 대상 요약을 함께 확인하는 읽기 전용 API다. 기록 수정·삭제 기능은 제공하지 않는다.

## 요청·인증·파라미터

```
GET /api/v1/admin/operation-logs
GET /api/v1/admin/operation-logs?sort=OLDEST&page=0&size=20
GET /api/v1/admin/operation-logs?targetType=COMMENT&action=HIDE&sort=LATEST
GET /api/v1/admin/operation-logs?startDate=2026-11-01&endDate=2026-11-06&sort=OLDEST
GET /api/v1/admin/operation-logs?reasonType=SPAM_ADVERTISING
GET /api/v1/admin/operation-logs?reasonType=LEGACY&sort=OLDEST
```

관리자 로그인 세션이 필요하다.
GET 조회에는 CSRF 토큰을 요구하지 않는다.

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| targetType | ALL | ALL, POST, COMMENT. 빈 문자열도 ALL |
| action | 생략 | 생략·빈 문자열은 전체, HIDE 또는 RESTORE |
| startDate | 생략 | 한국 날짜 기준 시작일 |
| endDate | 생략 | 한국 날짜 기준 종료일, 해당 날짜 전체 포함 |
| page | 0 | 0 이상. 빈 문자열은 기본값 |
| size | 20 | 1 이상 50 이하. 빈 문자열은 기본값 |
| sort | LATEST | LATEST: 최신순, OLDEST: 오래된순. 생략하거나 빈 문자열이면 LATEST |
| reasonType | 생략 | 처리 사유 유형. 생략하거나 빈 문자열이면 전체 조회. LEGACY 포함 |

빈 날짜 문자열은 생략과 같이 처리한다. enum은 지정된 대문자 코드를 사용하며, 공백만인 값은 빈 문자열과 같다고 보장하지 않는다.

## 날짜 범위·정렬·페이지·사유 필터

날짜 입력 범위는 0001-01-01부터 9999-12-31까지다.
시작일과 종료일은 각각 생략할 수 있다.
둘 다 지정하면 시작일은 종료일보다 늦을 수 없다.

시작일은 한국 시간 00:00 이상,
종료일은 다음 날 한국 시간 00:00 미만으로 조회한다.

정렬 기준은 운영 기록의 처리 시각(createdAt)이다.

- LATEST: createdAt 내림차순 → logId 내림차순
- OLDEST: createdAt 오름차순 → logId 오름차순
- sort를 생략하거나 빈 문자열로 전달하면 LATEST를 적용한다.
- 값은 대문자 LATEST, OLDEST만 허용한다.
- 잘못된 값은 400 INVALID_REQUEST_PARAMETER를 반환한다.
- 서버에서 검색 조건에 맞는 전체 결과를 정렬한 뒤 페이지를 적용한다.
- 정렬은 게시글·댓글의 작성 시각이나 수정 시각을 기준으로 하지 않는다.

page × size는 Integer.MAX_VALUE 이하여야 한다.

### 처리 사유 유형 필터

reasonType은 저장된 사유 유형 코드와 정확히 일치하는 기록을 조회한다.
reason 또는 reasonDetail의 문구를 검색하는 기능은 아니다.

허용 값:

- SPAM_ADVERTISING: 광고·홍보·스팸
- ABUSE_HATE: 욕설·비방·혐오
- PERSONAL_INFORMATION: 개인정보 노출
- DUPLICATE_FLOODING: 도배·중복 게시
- OFF_TOPIC: 게시판 주제와 무관
- MODERATION_ERROR: 운영 조치 오류
- NO_VIOLATION: 재검토 결과 위반 없음
- OTHER: 기타
- LEGACY: 기존 자유 입력 사유

reasonType을 생략하거나 빈 문자열로 전달하면 모든 사유를 조회한다.
전체 조회를 의미하는 ALL 값은 지원하지 않는다.
소문자 값이나 정의되지 않은 값은 400 INVALID_REQUEST_PARAMETER를 반환한다.

targetType, action, reasonType, 날짜 조건은 AND로 결합한다.
신규 조치에서 허용하지 않는 action과 reasonType 조합도 조회 조건으로는 허용한다.
예를 들어 action=RESTORE&reasonType=SPAM_ADVERTISING으로 조회하여
일치하는 기록이 없으면 200과 빈 items를 반환한다.

LEGACY는 조회 필터로 사용할 수 있지만 신규 숨김·복원 요청에는 사용할 수 없다.

전체 개수와 페이지 수에도 동일한 검색 조건을 적용한다.
검색 조건에 맞는 전체 결과를 정렬한 뒤 페이지를 적용한다.

## 응답 구조

- items: 운영 기록 목록
- page, size: 페이지 번호와 크기
- totalElements, totalPages: 검색 조건에 맞는 전체 개수와 페이지 수
- hasNext, hasPrevious: 다음·이전 페이지 여부

각 항목의 필드:

| 필드 | 의미 |
|---|---|
| logId | 운영 기록 ID |
| actorMemberId | 해당 조치를 실행한 관리자 회원 ID |
| targetType | POST 또는 COMMENT |
| targetId | 게시글 ID 또는 댓글 ID |
| action | HIDE 또는 RESTORE |
| reasonType | 저장된 처리 사유 유형. 기존 자유 입력 기록은 LEGACY |
| reasonDetail | 정규화된 상세 사유. 상세가 없거나 기존 자유 입력 기록이면 null |
| reason | 저장된 처리 사유 표시 문구. 기존 필드 유지 |
| createdAt | 처리 시각, UTC ISO 8601 |
| currentTarget | 조회 시점의 대상 게시글·댓글 요약. 대상을 찾을 수 없으면 명시적 null |

createdAt은 프론트에서 Asia/Seoul 기준으로 표시한다.
actorMemberId는 해당 조치를 실행한 관리자 회원 ID다.
currentTarget.author는 대상 게시글·댓글의 현재 작성자 표시 정보이며,
처리 관리자 정보와 구분한다.

처리 관리자의 닉네임은 응답에 포함하지 않는다.
대상의 현재 작성자 정보, 게시글 제목, 본문 미리보기는 currentTarget에 포함한다.
COMMENT의 targetId는 댓글 ID이며 부모 게시글 ID가 아니다.

reasonDetail은 값이 없어도 필드를 생략하지 않고 명시적 null로 반환한다.
기존 자유 입력 기록은 reasonType=LEGACY, reasonDetail=null이며,
reason은 기존 저장 문구를 그대로 유지한다. OTHER로 재분류하지 않는다.
LEGACY는 기존 기록의 조회용 유형이며 새로운 숨김·복원 요청에는 사용할 수 없다.

새 기록의 상세 사유는 저장 시 정규화된다. 상세가 없는 새 기록의 reason에는
생성 당시 유형 표시명이 저장된다. 조회에서는 reason을 현재 유형 표시명으로
다시 계산하지 않고 저장된 값을 그대로 반환한다.
reasonType은 각 기록의 응답 필드이자 선택적 검색 조건이다.
조회 요청에 지정하면 해당 사유 유형의 기록만 반환한다.

조회 결과가 없으면 200과 빈 items를 반환한다.
범위 밖 페이지도 200이며 전체 검색 결과 개수는 유지한다.
성공 응답의 Cache-Control은 no-store다.

## 현재 대상 요약(currentTarget)

currentTarget은 조회 시점의 콘텐츠 요약이며, 운영 조치 당시의
본문이나 작성자 표시 정보를 보존한 스냅샷이 아니다.

| 필드 | 의미 |
|---|---|
| author.memberId | 대상 콘텐츠 작성자의 회원 ID |
| author.nickname | 현재 작성자 표시 이름. 탈퇴 회원은 "탈퇴한 회원" |
| author.withdrawn | 작성자의 탈퇴 여부 |
| title | 게시글의 현재 제목. 댓글은 명시적 null |
| contentPreview | 현재 본문의 미리보기 |

- 작성자의 이메일과 비밀번호 해시 등은 포함하지 않는다.
- 숨김·소프트 삭제된 대상도 존재하면 현재 요약을 반환한다.
- 작성자가 탈퇴해도 memberId는 유지하며 nickname과 withdrawn으로 표시 상태를 구분한다.
- 대상을 찾을 수 없으면 currentTarget 필드를 생략하지 않고 null로 반환한다.
- currentTarget이 null이어도 운영 기록 자체를 제외하지 않으며,
  전체 개수와 페이지 계산은 운영 기록을 기준으로 유지한다.
- currentTarget이 존재하면 author, author.memberId, author.nickname, author.withdrawn, contentPreview는 null이 아니다. title만 댓글에서 null이다.
- 위 하위 필드와 currentTarget, reasonDetail은 응답에 항상 존재한다.
- 현재 공개 상태, 전체 본문, 댓글의 부모 게시글 ID는 currentTarget에 포함하지 않는다.
- 댓글의 title은 필드를 생략하지 않고 null로 반환한다.

### 본문 미리보기 규칙

- 유니코드 공백 문자의 연속을 일반 공백 하나로 바꾸고 양끝 공백을 제거한다.
- 정규화한 본문이 120 유니코드 코드 포인트 이하면 그대로 반환한다.
- 120 코드 포인트를 초과하면 앞의 120 코드 포인트 뒤에 말줄임표 "…"를 붙인다.
- 말줄임표는 120 코드 포인트 제한에 포함하지 않는다.
- 코드 포인트를 기준으로 자르며 UTF-16 서로게이트 쌍을 분리하지 않는다.
- 여러 코드 포인트로 구성된 이모지 전체를 하나의 글자로 계산하는 규칙은 아니다.

현재 콘텐츠가 변경되면 currentTarget도 달라질 수 있다.
기존 운영 기록의 조치, 처리 관리자, 처리 사유, 처리 시각은 변경하지 않는다.

목록의 contentPreview는 미리보기 용도다.
상세 확인과 숨김·숨김 해제 판단에는 기존 관리자 상세 조회 API의 본문을 사용한다.

## 성공 응답 예시

아래 네 예시는 각각 완전한 목록 응답이다. 모두 기본 LATEST 순서이며 예시 ID와 작성자는 가상 데이터다. 대상 요약 없음은 응답 계약 예시로, 물리 삭제 기능을 제공한다는 뜻이 아니다. 실제 FK는 대상 삭제를 제한한다.

### 게시글·댓글 정상 목록

```json
{
  "items": [
    {
      "logId": 102,
      "actorMemberId": 7,
      "targetType": "POST",
      "targetId": 104,
      "action": "HIDE",
      "reasonType": "SPAM_ADVERTISING",
      "reasonDetail": null,
      "reason": "광고·홍보·스팸",
      "createdAt": "2026-11-04T03:01:00Z",
      "currentTarget": {
        "author": {
          "memberId": 42,
          "nickname": "이음회원",
          "withdrawn": false
        },
        "title": "보청기 적응 경험",
        "contentPreview": "새 보청기에 적응한 경험을 공유합니다."
      }
    },
    {
      "logId": 101,
      "actorMemberId": 7,
      "targetType": "COMMENT",
      "targetId": 144,
      "action": "RESTORE",
      "reasonType": "NO_VIOLATION",
      "reasonDetail": "재검토 결과 복원",
      "reason": "재검토 결과 복원",
      "createdAt": "2026-11-04T03:00:00Z",
      "currentTarget": {
        "author": {
          "memberId": 43,
          "nickname": "햇살",
          "withdrawn": false
        },
        "title": null,
        "contentPreview": "경험을 나눠 주셔서 감사합니다."
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1,
  "hasNext": false,
  "hasPrevious": false
}
```

### 탈퇴 작성자

```json
{
  "items": [
    {
      "logId": 101,
      "actorMemberId": 7,
      "targetType": "COMMENT",
      "targetId": 144,
      "action": "RESTORE",
      "reasonType": "NO_VIOLATION",
      "reasonDetail": "재검토 결과 복원",
      "reason": "재검토 결과 복원",
      "createdAt": "2026-11-04T03:00:00Z",
      "currentTarget": {
        "author": {
          "memberId": 43,
          "nickname": "탈퇴한 회원",
          "withdrawn": true
        },
        "title": null,
        "contentPreview": "경험을 나눠 주셔서 감사합니다."
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false,
  "hasPrevious": false
}
```

### 대상 요약 없음 — 응답 계약 예시

```json
{
  "items": [
    {
      "logId": 102,
      "actorMemberId": 7,
      "targetType": "POST",
      "targetId": 104,
      "action": "HIDE",
      "reasonType": "SPAM_ADVERTISING",
      "reasonDetail": null,
      "reason": "광고·홍보·스팸",
      "createdAt": "2026-11-04T03:01:00Z",
      "currentTarget": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false,
  "hasPrevious": false
}
```

### 검색 결과 없음

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false,
  "hasPrevious": false
}
```

## 오류

| HTTP 상태 | 코드 | 의미 |
|---|---|---|
| 400 | INVALID_REQUEST_PARAMETER | 숫자·날짜·enum 변환 실패 |
| 400 | INVALID_PAGE_REQUEST | 페이지 또는 offset 범위 오류 |
| 400 | INVALID_DATE_RANGE | 날짜 범위 또는 날짜 순서 오류 |
| 401 | AUTHENTICATION_REQUIRED | 인증 정보가 없거나 유효하지 않음 |
| 403 | ACCESS_DENIED | Security 필터에서 관리자 접근 거부 |
| 403 | ADMIN_REQUIRED | 서비스에서 관리자 권한 부족 확인 |

프론트는 관리자 접근 거부 시 두 종류의 403 코드를 처리해야 한다.
로그인 후 역할·회원 상태·인증 버전이 변경되면 기존 세션이
401로 차단될 수 있다.

## 프론트 연결 시 유의 사항

- 세션 쿠키를 포함해 요청한다.
- 조치 전체 조회 시 action 파라미터를 생략한다.
- 필터 또는 정렬 변경 시 다른 검색 조건은 유지하고 page를 0으로 초기화한다.
- 현재 페이지의 배열을 뒤집지 않고, 선택한 sort로 서버에 다시 요청한다.
- 필터·정렬·페이지 변경 후 늦게 도착한 이전 요청의 응답이 최신 화면을 덮어쓰지 않도록 처리한다.
- 날짜 입력값을 UTC 날짜로 바꾸지 않고 YYYY-MM-DD로 전달한다.
- 인증 상태가 변경되면 이전 관리자 조회 결과를 표시하지 않는다.
- 목록은 서버가 반환한 순서대로 표시하고, 페이지 계산에는 응답의 전체 개수를 사용한다.
- 운영 기록 수정·삭제 기능은 제공하지 않는다.
- 사유 전체 조회 시 reasonType 파라미터를 생략한다.
- 사유 유형 변경 시 나머지 검색 조건과 정렬을 유지하고 page를 0으로 초기화한다.
- LEGACY는 "기존 자유 입력 사유"로 표시하며 OTHER와 구분한다.

- 목록의 작성자는 currentTarget.author로 표시하고 actorMemberId와 혼동하지 않는다.
- 화면 컬럼이 바뀌어도 상세 조회용 targetType·targetId를 유지한다. 댓글 targetId를 부모 게시글 ID로 사용하지 않는다.
- currentTarget이 null이어도 기록 행을 유지한다. 댓글 title: null은 정상 응답이다.
- 과거 action으로 현재 공개 상태를 추정하지 않는다. 현재 상태와 전체 본문은 기존 관리자 상세 API로 확인한다.
- 상태 변경 후 관련 상세와 목록을 재조회한다. 이전 요청이 최신 목록·상세를 덮어쓰지 않게 한다.

## 전체 운영 기록 요약

목록 필터와 독립적인 전체·오늘·오늘 숨김·오늘 숨김 해제 기록 건수는 [운영 기록 요약 API](api/admin-operation-summary.md)를 사용한다. 대상 콘텐츠 수가 아닌 저장된 ContentModerationLog 건수이며 회원 제재와 신고 종결 자체는 제외한다.
