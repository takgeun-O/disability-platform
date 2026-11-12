# 관리자 신고 목록·상세 조회 API

현재 작업 트리의 `AdminReportReadController`, 조회 조건·서비스·저장소 및 응답 DTO 기준입니다. 신고 처리 완료·기각 API는 제공하지 않습니다. [사용자 신고 접수](content-report-create.md)와 구분합니다.

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
| currentTarget.targetId | int64 | 현재 대상 ID |
| currentTarget.parentPostId | int64 또는 null | 현재 댓글 부모 게시글 ID. 게시글은 null |
| currentTarget.author | 작성자 객체 | 현재 대상 작성자 |
| currentTarget.title | string 또는 null | 현재 게시글 제목. 댓글은 null |
| currentTarget.content | string | 현재 전체 본문 |
| currentTarget.hiddenAt / deletedAt | date-time 또는 null | 대상 자체의 숨김 / 소프트 삭제 시각. 없으면 null |

목록 currentAuthor와 상세 author는 `memberId`(int64), `nickname`(string), `withdrawn`(boolean)으로 구성됩니다. 현재 구현은 작성자 상태가 DELETED일 때 `탈퇴한 회원`, true로 표시하며 그 외에는 저장 닉네임과 false를 사용합니다. WITHDRAWN까지 동일 처리한다고 확대 해석하지 않습니다.

응답 DTO의 nullable 필드는 생략하지 않고 명시적 null로 반환합니다. 숨김·소프트 삭제 대상도 현재 조회에 포함되며 댓글 부모의 숨김·삭제로 제외하지 않습니다. 댓글 hiddenAt·deletedAt은 부모의 상태가 아닙니다. 현재 대상이 없으면 신고와 스냅샷은 유지되고 목록 currentAuthor / 상세 currentTarget만 null입니다. 신고 자체가 없으면 404 REPORT_NOT_FOUND입니다. 처리 관리자·처리 시각·처리 사유는 현재 응답 필드가 아닙니다.

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
  }
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
  }
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
  "currentTarget": null
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
