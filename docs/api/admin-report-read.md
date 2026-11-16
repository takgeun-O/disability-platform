# 관리자 신고 목록·상세 조회 API

현재 작업 트리의 `AdminReportReadController`, 조회 조건·서비스·저장소 및 응답 DTO 기준입니다. [신고 숨김·처리 완료·기각 API](admin-report-handling.md)를 별도로 제공합니다. [사용자 신고 접수](content-report-create.md)와 구분합니다.

## 인증 및 공통 정책

- 관리자 로그인 세션 쿠키가 필요하며 GET에는 CSRF 토큰이 필요하지 않습니다. Bearer 인증을 사용하지 않습니다.
- 현재 회원의 ACTIVE 상태, ADMIN 권한과 세션 credentialVersion을 검증합니다. 로그인 후 상태·권한·인증 버전 변경으로 기존 세션이 차단될 수 있습니다.
- 성공 응답은 `Cache-Control: no-store`입니다. MVC 오류와 보안 필터 오류도 캐시 방지 정책을 적용하지만 헤더 전체 문자열은 같다고 보장하지 않습니다. CORS 거부는 공통 오류 JSON으로 보장하지 않습니다.
- 날짜·시간 응답은 UTC ISO 8601입니다. 검색 날짜는 한국 시간(Asia/Seoul) 기준입니다.
- 이 API는 관리자 전용입니다. 신고자 ID와 스냅샷을 일반 공개 응답으로 재노출하지 마세요. 이메일·비밀번호 해시·인증 버전은 응답에 없습니다.

## 목록 `GET /api/v1/admin/reports`

모든 파라미터는 선택 사항입니다. 생략 또는 빈 문자열은 아래 기본값/전체 조건으로 처리합니다. enum은 대소문자를 구분합니다. 잘못된 숫자·날짜·enum은 400입니다.

| 파라미터 | 값 및 기본 동작 |
| --- | --- |
| targetType | ALL(기본), POST, COMMENT |
| status | RECEIVED, RESOLVED, DISMISSED. 생략 시 전체. ALL 불가 |
| reasonType | SPAM_ADVERTISING, ABUSE_HATE, PERSONAL_INFORMATION, DUPLICATE_FLOODING, OFF_TOPIC, OTHER. 생략 시 전체. ALL·LEGACY 불가 |
| startDate | yyyy-MM-dd, 0001-01-01~9999-12-31. 한국 날짜 시작 00:00 포함 |
| endDate | 같은 날짜 형식·연도 범위. 다음 날 한국 시간 00:00 미포함 |
| sort | LATEST(기본): createdAt DESC, 신고 ID DESC. OLDEST: 두 값 모두 ASC |
| page | 정수, 기본 0, 0 이상 |
| size | 정수, 기본 20, 1~50 |

`page × size <= 2147483647`이어야 합니다. page에는 size와 무관한 고정 최대값을 두지 않습니다. 날짜는 각각 단독 지정 가능하며 둘 다 있으면 startDate <= endDate여야 합니다. 모든 검색 조건을 AND로 결합하고 정렬·페이지를 적용합니다. 전체 개수에도 같은 조건을 적용합니다. API 상태 기본값은 전체이며 프론트 최초 RECEIVED 선택 정책과 별개입니다.

```http
GET /api/v1/admin/reports?targetType=COMMENT&status=RECEIVED&reasonType=OTHER&startDate=2026-11-01&endDate=2026-11-12&sort=OLDEST&page=0&size=20
```

### 목록 응답 필드

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| items | 배열 | 대상별 집계가 아닌 신고 한 건당 한 항목 |
| page / size | 정수 | 요청 페이지 / 페이지 크기 |
| totalElements | 정수(int64) | 검색 조건에 맞는 전체 신고 수 |
| totalPages | 정수 | 전체 페이지 수 |
| hasNext / hasPrevious | boolean | 다음 / 이전 페이지 여부 |
| items[].reportId | 정수(int64) | 신고 ID |
| items[].targetType | POST 또는 COMMENT | 응답에는 ALL 없음 |
| items[].targetId | 정수(int64) | 게시글 ID 또는 댓글 ID |
| items[].status | 신고 상태 enum | RECEIVED / RESOLVED / DISMISSED |
| items[].reasonType | 신고 사유 enum | 위 6개 사유 |
| items[].titleSnapshot | string 또는 null | 접수 당시 게시글 제목. 댓글은 null |
| items[].contentSnapshotPreview | string | 접수 당시 본문의 목록용 미리보기 |
| items[].createdAt | string(date-time) | 접수 시각 |
| items[].currentAuthor | 작성자 객체 또는 null | 조회 시점의 대상 작성자. 대상 조회 불가 시 null |

결과 없음 및 범위 밖 페이지도 200입니다. 범위 밖에서는 items만 비고 검색 결과 전체 개수와 요청 page·size를 유지합니다.

미리보기는 저장된 스냅샷을 변경하지 않습니다. 유니코드 연속 공백(줄바꿈·탭·NBSP·전각 공백 포함)을 공백 하나로 바꾸고 앞뒤 공백을 제거한 후 최대 120 Unicode 코드 포인트를 표시합니다. 초과할 때만 `…`를 추가하며 이 기호는 120 제한에 포함하지 않습니다. 보조 평면 문자를 중간에서 자르지 않지만 복합 이모지의 grapheme cluster 개수나 화면상 글자 수 기준은 아닙니다. 공백뿐이면 빈 문자열입니다.

## 상세 `GET /api/v1/admin/reports/{reportId}`

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| reportId | int64 | 신고 ID |
| reporterMemberId | int64 | 신고자 ID. 콘텐츠 작성자 ID가 아님 |
| targetType / targetId | enum / int64 | POST·COMMENT 및 해당 대상 ID |
| status / reasonType | enum | 신고 상태 및 접수 사유 |
| reasonDetail | string 또는 null | 접수 시 정규화된 상세 사유 |
| titleSnapshot | string 또는 null | 신고 당시 제목. 댓글은 null |
| contentSnapshot | string | 신고 당시 전체 본문 |
| createdAt | date-time | 접수 시각 |
| currentTarget | 객체 또는 null | 조회 시점의 현재 콘텐츠 |
| handledByMemberId | int64 또는 null | 처리 관리자. 신고자·현재 작성자와 구분 |
| handledAt | date-time 또는 null | 처리 시각. 접수 시각 createdAt과 구분 |
| handlingReason | string 또는 null | 저장된 처리 사유 |
| handlingOutcome | string enum 또는 null | 필수 존재. CONTENT_HIDDEN, CONTENT_ALREADY_HIDDEN, NO_CONTENT_ACTION, LEGACY_UNKNOWN 또는 실제 JSON null |
| currentTarget.targetId | int64 | 현재 대상 ID |
| currentTarget.parentPostId | int64 또는 null | 현재 댓글 부모 게시글 ID. 게시글은 null |
| currentTarget.author | 작성자 객체 | 현재 대상 작성자 |
| currentTarget.title | string 또는 null | 현재 게시글 제목. 댓글은 null |
| currentTarget.content | string | 현재 전체 본문 |
| currentTarget.hiddenAt / deletedAt | date-time 또는 null | 대상 자체의 숨김 / 소프트 삭제 시각. 없으면 null |

목록 currentAuthor와 상세 author는 `memberId`(int64), `nickname`(string), `withdrawn`(boolean)으로 구성됩니다. 현재 구현은 작성자 상태가 DELETED일 때 `탈퇴한 회원`, true로 표시하며 그 외에는 저장 닉네임과 false를 사용합니다. WITHDRAWN까지 동일 처리한다고 확대 해석하지 않습니다.

응답 DTO의 nullable 필드는 생략하지 않고 명시적 null로 반환합니다. 숨김·소프트 삭제 대상도 현재 조회에 포함되며 댓글 부모의 숨김·삭제로 제외하지 않습니다. 댓글 hiddenAt·deletedAt은 부모의 상태가 아닙니다. 현재 대상이 없으면 신고와 스냅샷은 유지되고 목록 currentAuthor / 상세 currentTarget만 null입니다. 신고 자체가 없으면 404 REPORT_NOT_FOUND입니다. 상세에는 handledByMemberId(처리 관리자 ID), handledAt(저장된 처리 시각), handlingReason(저장된 처리 사유), handlingOutcome(저장된 처리 결과)이 항상 존재합니다. 최상위 필드는 총 15개입니다. RECEIVED는 네 값 모두 null, RESOLVED/DISMISSED는 저장된 값입니다. 목록에는 이 네 필드가 없습니다. 현재 대상 유무와 처리 상태는 독립적이며 대상이 없어도 스냅샷과 처리 정보를 반환합니다.

handlingOutcome은 현재 hiddenAt으로 다시 계산하지 않는 처리 이력입니다. 이후 콘텐츠가 복원·삭제되어도 유지합니다. `LEGACY_UNKNOWN`은 필드 도입 전 과거 기록의 콘텐츠 조치 여부를 확정할 수 없다는 뜻이며 `NO_CONTENT_ACTION`과 다릅니다.

## 오류

| HTTP | code | 조건 |
| --- | --- | --- |
| 400 | INVALID_REQUEST_PARAMETER | 숫자·날짜·enum 변환 실패, int/Long 범위 초과 |
| 400 | INVALID_PAGE_REQUEST | 음수 page, size 범위 위반, page × size 범위 위반 |
| 400 | INVALID_DATE_RANGE | 날짜의 허용 연도 위반 또는 시작일 > 종료일 |
| 401 | AUTHENTICATION_REQUIRED | 비로그인, 유효하지 않은 현재 회원/세션 |
| 403 | ACCESS_DENIED | Security 권한 검사에서 거부 |
| 403 | ADMIN_REQUIRED | 서비스의 현재 관리자 권한 검사에서 거부 |
| 404 | REPORT_NOT_FOUND | 신고 상세의 0·음수·존재하지 않는 ID |

숫자가 아닌 상세 ID와 Long 범위 초과는 404가 아닌 400 INVALID_REQUEST_PARAMETER입니다. 인증·권한 검사가 먼저 거부할 수 있으므로 비로그인 요청에 특정 입력 오류 응답을 보장하지 않습니다. 기타 HTTP 오류는 기존 공통 예외 처리 정책을 따릅니다.

```json
{
  "code": "REPORT_NOT_FOUND",
  "message": "신고 내역을 찾을 수 없습니다.",
  "fieldErrors": []
}
```

## 응답 예시

아래는 가상 데이터입니다. 댓글 작성자 탈퇴 예시는 신고자와 별개의 현재 작성자 상태입니다.

### 게시글·댓글 목록 (탈퇴 작성자 포함)

```json
{
  "items": [
    {
      "reportId": 102,
      "targetType": "POST",
      "targetId": 104,
      "status": "RECEIVED",
      "reasonType": "SPAM_ADVERTISING",
      "titleSnapshot": "신고 당시 게시글 제목",
      "contentSnapshotPreview": "신고 당시 본문입니다.",
      "createdAt": "2026-11-12T01:00:00Z",
      "currentAuthor": {
        "memberId": 42,
        "nickname": "소리나무",
        "withdrawn": false
      }
    },
    {
      "reportId": 101,
      "targetType": "COMMENT",
      "targetId": 144,
      "status": "RECEIVED",
      "reasonType": "SPAM_ADVERTISING",
      "titleSnapshot": null,
      "contentSnapshotPreview": "신고 당시 본문입니다.",
      "createdAt": "2026-11-12T01:00:00Z",
      "currentAuthor": {
        "memberId": 43,
        "nickname": "탈퇴한 회원",
        "withdrawn": true
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

### 빈 목록

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

### 범위 밖 페이지 (전체 21건 유지)

```json
{
  "items": [],
  "page": 2,
  "size": 20,
  "totalElements": 21,
  "totalPages": 2,
  "hasNext": false,
  "hasPrevious": true
}
```

### 게시글 신고 상세

```json
{
  "reportId": 102,
  "targetType": "POST",
  "targetId": 104,
  "status": "RECEIVED",
  "reasonType": "SPAM_ADVERTISING",
  "titleSnapshot": "신고 당시 게시글 제목",
  "createdAt": "2026-11-12T01:00:00Z",
  "reporterMemberId": 51,
  "reasonDetail": null,
  "contentSnapshot": "신고 당시 본문입니다.",
  "currentTarget": {
    "targetId": 104,
    "parentPostId": null,
    "author": {
      "memberId": 42,
      "nickname": "소리나무",
      "withdrawn": false
    },
    "title": "현재 게시글 제목",
    "content": "조회 시점의 현재 본문입니다.",
    "hiddenAt": null,
    "deletedAt": null
  },
  "handledByMemberId": null,
  "handledAt": null,
  "handlingReason": null,
  "handlingOutcome": null
}
```

### 댓글 신고 상세 (탈퇴 작성자)

```json
{
  "reportId": 101,
  "targetType": "COMMENT",
  "targetId": 144,
  "status": "RECEIVED",
  "reasonType": "SPAM_ADVERTISING",
  "titleSnapshot": null,
  "createdAt": "2026-11-12T01:00:00Z",
  "reporterMemberId": 51,
  "reasonDetail": null,
  "contentSnapshot": "신고 당시 본문입니다.",
  "currentTarget": {
    "targetId": 144,
    "parentPostId": 104,
    "author": {
      "memberId": 43,
      "nickname": "탈퇴한 회원",
      "withdrawn": true
    },
    "title": null,
    "content": "조회 시점의 현재 본문입니다.",
    "hiddenAt": null,
    "deletedAt": null
  },
  "handledByMemberId": null,
  "handledAt": null,
  "handlingReason": null,
  "handlingOutcome": null
}
```

### 현재 대상이 없는 상세

```json
{
  "reportId": 102,
  "targetType": "POST",
  "targetId": 104,
  "status": "RECEIVED",
  "reasonType": "SPAM_ADVERTISING",
  "titleSnapshot": "신고 당시 게시글 제목",
  "createdAt": "2026-11-12T01:00:00Z",
  "reporterMemberId": 51,
  "reasonDetail": null,
  "contentSnapshot": "신고 당시 본문입니다.",
  "currentTarget": null,
  "handledByMemberId": null,
  "handledAt": null,
  "handlingReason": null,
  "handlingOutcome": null
}
```

### 현재 대상 작성자를 조회할 수 없는 목록

현재 대상 조회가 불가능해도 신고 기록과 접수 당시 스냅샷은 유지합니다. 전체 1건은 그대로이며 `currentAuthor`는 생략하지 않고 null로 반환합니다.

```json
{
  "items": [
    {
      "reportId": 102,
      "targetType": "POST",
      "targetId": 104,
      "status": "RECEIVED",
      "reasonType": "SPAM_ADVERTISING",
      "titleSnapshot": "신고 당시 게시글 제목",
      "contentSnapshotPreview": "신고 당시 본문입니다.",
      "createdAt": "2026-11-12T01:00:00Z",
      "currentAuthor": null
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

### 처리 완료 상세

```json
{
  "reportId": 103,
  "targetType": "POST",
  "targetId": 104,
  "status": "RESOLVED",
  "reasonType": "SPAM_ADVERTISING",
  "titleSnapshot": "신고 당시 게시글 제목",
  "createdAt": "2026-11-12T01:00:00Z",
  "reporterMemberId": 51,
  "reasonDetail": null,
  "contentSnapshot": "신고 당시 본문입니다.",
  "currentTarget": {
    "targetId": 104,
    "parentPostId": null,
    "author": {
      "memberId": 42,
      "nickname": "소리나무",
      "withdrawn": false
    },
    "title": "현재 게시글 제목",
    "content": "조회 시점의 현재 본문입니다.",
    "hiddenAt": null,
    "deletedAt": null
  },
  "handledByMemberId": 40,
  "handledAt": "2026-11-13T01:00:00Z",
  "handlingReason": "검토를 완료했습니다.",
  "handlingOutcome": "NO_CONTENT_ACTION"
}
```

### 기각 상세 — 현재 대상 조회 불가

```json
{
  "reportId": 104,
  "targetType": "POST",
  "targetId": 104,
  "status": "DISMISSED",
  "reasonType": "SPAM_ADVERTISING",
  "titleSnapshot": "신고 당시 게시글 제목",
  "createdAt": "2026-11-12T01:00:00Z",
  "reporterMemberId": 51,
  "reasonDetail": null,
  "contentSnapshot": "신고 당시 본문입니다.",
  "currentTarget": null,
  "handledByMemberId": 40,
  "handledAt": "2026-11-13T01:00:00Z",
  "handlingReason": "검토 결과 신고를 기각했습니다.",
  "handlingOutcome": "NO_CONTENT_ACTION"
}
```

OpenAPI는 처리 정보의 필수 존재와 nullable 타입을 표현합니다. 상태별 값 조합의 불변식은 도메인·DB·통합 테스트로 보장하며 스키마가 이를 조건부로 강제한다고 해석하지 않습니다.

### 이번 처리에서 새로 숨긴 게시글 신고 (`contentHidden`)

```json
{
  "reportId": 105,
  "targetType": "POST",
  "targetId": 104,
  "status": "RESOLVED",
  "reasonType": "SPAM_ADVERTISING",
  "titleSnapshot": "신고 당시 게시글 제목",
  "createdAt": "2026-11-12T01:00:00Z",
  "reporterMemberId": 51,
  "reasonDetail": null,
  "contentSnapshot": "신고 당시 본문입니다.",
  "currentTarget": {
    "targetId": 104,
    "parentPostId": null,
    "author": {
      "memberId": 42,
      "nickname": "소리나무",
      "withdrawn": false
    },
    "title": "현재 게시글 제목",
    "content": "조회 시점의 현재 본문입니다.",
    "hiddenAt": "2026-11-13T01:00:00Z",
    "deletedAt": null
  },
  "handledByMemberId": 40,
  "handledAt": "2026-11-13T01:00:00Z",
  "handlingReason": "운영 정책 위반을 확인하여 숨겼습니다.",
  "handlingOutcome": "CONTENT_HIDDEN"
}
```

### 기존 개별 숨김을 유지하며 처리 완료한 신고 (`contentAlreadyHidden`)

```json
{
  "reportId": 106,
  "targetType": "POST",
  "targetId": 104,
  "status": "RESOLVED",
  "reasonType": "SPAM_ADVERTISING",
  "titleSnapshot": "신고 당시 게시글 제목",
  "createdAt": "2026-11-12T01:00:00Z",
  "reporterMemberId": 51,
  "reasonDetail": null,
  "contentSnapshot": "신고 당시 본문입니다.",
  "currentTarget": {
    "targetId": 104,
    "parentPostId": null,
    "author": {
      "memberId": 42,
      "nickname": "소리나무",
      "withdrawn": false
    },
    "title": "현재 게시글 제목",
    "content": "조회 시점의 현재 본문입니다.",
    "hiddenAt": "2026-11-12T02:00:00Z",
    "deletedAt": null
  },
  "handledByMemberId": 40,
  "handledAt": "2026-11-13T01:00:00Z",
  "handlingReason": "기존 숨김을 유지하고 신고 검토를 완료했습니다.",
  "handlingOutcome": "CONTENT_ALREADY_HIDDEN"
}
```

### 당시 콘텐츠 조치 여부를 확정할 수 없는 과거 신고 (`legacyUnknown`)

```json
{
  "reportId": 107,
  "targetType": "POST",
  "targetId": 104,
  "status": "RESOLVED",
  "reasonType": "SPAM_ADVERTISING",
  "titleSnapshot": "신고 당시 게시글 제목",
  "createdAt": "2026-11-01T01:00:00Z",
  "reporterMemberId": 51,
  "reasonDetail": null,
  "contentSnapshot": "신고 당시 본문입니다.",
  "currentTarget": {
    "targetId": 104,
    "parentPostId": null,
    "author": {
      "memberId": 42,
      "nickname": "소리나무",
      "withdrawn": false
    },
    "title": "현재 게시글 제목",
    "content": "조회 시점의 현재 본문입니다.",
    "hiddenAt": null,
    "deletedAt": null
  },
  "handledByMemberId": 40,
  "handledAt": "2026-11-02T01:00:00Z",
  "handlingReason": "신고 검토를 완료했습니다.",
  "handlingOutcome": "LEGACY_UNKNOWN"
}
```

위 상세 예시는 각각 독립적인 상황입니다. 새 숨김 예시의 hiddenAt은 처리 시각과 같고 기존 숨김 예시는 더 이릅니다. 과거 기록 예시의 현재 공개 상태로 당시 조치 여부를 추정하지 않습니다.
