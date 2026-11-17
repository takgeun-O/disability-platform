# 관리자 대시보드 조회 API

현재 작업 트리의 `AdminDashboardReadController`, `AdminDashboardReadService`, `AdminDashboardReadRepository` 및 응답 DTO 기준입니다.

운영 요약 수치 4개와 오래된 처리 대기 신고 최대 5건을 읽기 전용으로 제공합니다. 이번 MVP에는 통계 필터·차트·캐시·스케줄러가 없습니다. 신고 확인과 처리는 [신고 목록·상세 조회](admin-report-read.md), [신고 숨김·처리 완료·기각](admin-report-handling.md) API를 사용합니다.

## 요청 및 인증

```http
GET /api/v1/admin/dashboard
Accept: application/json
```

- 요청 본문이나 조회 파라미터가 없습니다. 날짜·페이지·정렬을 요청으로 지정하지 않습니다.
- 관리자 로그인 세션 쿠키가 필요합니다. 교차 출처 요청은 세션 쿠키를 포함해야 합니다. Bearer 인증을 사용하지 않습니다.
- GET 요청에는 CSRF 토큰이 필요하지 않습니다.
- 기존 보안 필터가 현재 회원의 ACTIVE 상태, 세션의 권한과 credentialVersion을 검증합니다. 서비스도 현재 ACTIVE·ADMIN·인증 버전을 확인합니다. 회원 ID와 인증 버전은 요청 파라미터가 아닌 로그인 principal에서 받습니다.
- 로그인 후 상태·권한·인증 버전이 바뀌면 기존 세션이 무효화되어 401이 반환될 수 있습니다.
- 성공 응답은 HTTP 200, `Content-Type: application/json`, `Cache-Control: no-store`입니다.
- MVC 오류 응답에도 `no-store`를 적용합니다. 보안 필터 응답은 다른 캐시 방지 지시어를 함께 포함할 수 있으므로 모든 오류의 헤더 전체 문자열이 동일하다고 보장하지 않습니다.
- 개발 CORS는 설정된 Origin과 쿠키 포함 요청에만 허용되며 이 경로의 허용 메서드는 GET·OPTIONS입니다. CORS 거부는 `ApiErrorResponse` JSON으로 보장하지 않습니다.

## 시간 기준과 집계

관리자 검증 후 `Clock.instant()`에서 조회 기준 시각을 한 번 얻습니다. `queriedAt`은 그 시각이며 `baseDate`는 이를 Asia/Seoul로 변환한 날짜입니다.

오늘의 범위는 **baseDate의 한국 시간 00:00 이상, 다음 날 00:00 미만**입니다. 예를 들어 `baseDate = 2026-11-17`이면 UTC 구간은 `[2026-11-16T15:00:00Z, 2026-11-17T15:00:00Z)`입니다. 조회 순간까지의 누계 구간이 아닌 날짜 전체 구간입니다.

| 집계 필드 | 포함 기준 |
| --- | --- |
| pendingReportCount | 접수 날짜와 무관하게 현재 RECEIVED인 모든 신고. 대상 숨김·삭제와 무관하며 동일 대상의 여러 신고도 각각 한 건 |
| todayReportCount | 오늘 접수된 신고. 현재 RECEIVED·RESOLVED·DISMISSED 상태와 무관 |
| todayPostCount | 오늘 생성된 게시글. 현재 숨김·삭제 여부와 무관 |
| todayMemberCount | 오늘 생성된 회원 계정. 현재 인증 대기·정지·탈퇴 등 회원 상태와 무관 |

집계 대상이 없으면 0을 반환합니다. 신고 상태, 처리 당시 `handlingOutcome`, 현재 콘텐츠 상태는 별개입니다. 콘텐츠 숨김·복원만으로 신고 상태나 대기 건수를 변경하지 않습니다. 신고 종결 시 대기 건수에서는 제외되지만 오늘 접수된 신고라면 오늘 신고 건수에는 계속 포함됩니다. 다른 신고나 회원 상태를 자동 변경하지 않습니다.

`queriedAt`은 응답 시각·날짜 범위의 공통 기준이지 여러 SQL 결과의 동일한 DB 스냅샷을 보장하는 값이 아닙니다. 저장소는 집계 4회와 대기 목록 1회를 조회하며 항목별 콘텐츠 조회는 하지 않습니다. 조회 중 동시 변경으로 집계와 목록이 일시적으로 다를 수 있습니다. 별도 스냅샷이나 격리 수준 변경은 없습니다.

## 응답 필드

아래 필드는 모두 필수이며 **null을 허용하지 않습니다**. 0과 빈 배열도 생략하지 않습니다. 정수는 JSON 숫자로 반환합니다. 이메일·비밀번호 해시·인증 버전·신고자 정보는 포함하지 않습니다.

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| queriedAt | string(date-time) | 조회 기준 시각, UTC ISO 8601 |
| baseDate | string(date) | queriedAt의 한국 날짜, yyyy-MM-dd |
| counts | object | 요약 수치 객체 |
| counts.pendingReportCount | integer(int64), minimum 0 | 전체 처리 대기 신고 수 |
| counts.todayReportCount | integer(int64), minimum 0 | 오늘 접수 신고 수 |
| counts.todayPostCount | integer(int64), minimum 0 | 오늘 생성 게시글 수 |
| counts.todayMemberCount | integer(int64), minimum 0 | 오늘 생성 회원 계정 수 |
| pendingReports | array | 처리 대기 신고 최대 5건. 없으면 [] |
| pendingReports[].reportId | integer(int64) | 신고 ID |
| pendingReports[].targetType | string enum | POST 또는 COMMENT. 목록 필터의 ALL은 응답에 없음 |
| pendingReports[].targetId | integer(int64) | POST는 게시글 ID, COMMENT는 댓글 ID |
| pendingReports[].reasonType | string enum | 신고 사유 유형 |
| pendingReports[].createdAt | string(date-time) | 신고 접수 시각, UTC ISO 8601 |

사유 유형은 `SPAM_ADVERTISING`, `ABUSE_HATE`, `PERSONAL_INFORMATION`, `DUPLICATE_FLOODING`, `OFF_TOPIC`, `OTHER`입니다.

처리 대기 목록은 RECEIVED만 `createdAt ASC, id ASC` 순으로 반환합니다. 최대 5건이며 숨김·삭제된 대상의 신고도 포함합니다. 동일 대상 신고를 합치지 않습니다. `pendingReportCount`는 전체 건수이므로 목록 길이로 대신 계산하면 안 됩니다. 예를 들어 대기 신고가 7건이면 전체 수는 7, 목록은 오래된 5건입니다. 이 응답에는 처리 정보나 현재 콘텐츠 상태를 추가하지 않습니다.

## 일반 성공 예시

아래 예시는 Swagger의 `mixed` 예시와 같습니다. 전날 접수된 신고도 대기에 포함되므로 오늘 신고 수와 대기 수는 서로 다를 수 있습니다.

```json
{
  "queriedAt": "2026-11-17T03:00:00Z",
  "baseDate": "2026-11-17",
  "counts": {
    "pendingReportCount": 2,
    "todayReportCount": 3,
    "todayPostCount": 4,
    "todayMemberCount": 2
  },
  "pendingReports": [
    {
      "reportId": 101,
      "targetType": "POST",
      "targetId": 89,
      "reasonType": "SPAM_ADVERTISING",
      "createdAt": "2026-11-16T01:00:00Z"
    },
    {
      "reportId": 102,
      "targetType": "COMMENT",
      "targetId": 166,
      "reasonType": "ABUSE_HATE",
      "createdAt": "2026-11-17T01:00:00Z"
    }
  ]
}
```

## 빈 결과 예시

```json
{
  "queriedAt": "2026-11-17T03:00:00Z",
  "baseDate": "2026-11-17",
  "counts": {
    "pendingReportCount": 0,
    "todayReportCount": 0,
    "todayPostCount": 0,
    "todayMemberCount": 0
  },
  "pendingReports": []
}
```

## 인증·권한 오류

| HTTP | code | 발생 조건 |
| --- | --- | --- |
| 401 | AUTHENTICATION_REQUIRED | 비로그인, 유효한 principal 없음, 존재하지 않거나 비활성인 회원, 인증 버전 불일치 등 유효하지 않은 로그인 정보. 로그인 후 DB 권한 변경으로 세션 검증이 실패한 경우도 포함 |
| 403 | ACCESS_DENIED | Security에서 유효한 일반 회원의 관리자 경로 접근을 거부 |
| 403 | ADMIN_REQUIRED | 서비스의 현재 관리자 권한 검증 실패. 필터의 ACCESS_DENIED와 구분 |

회원 권한 변경의 응답이 항상 ADMIN_REQUIRED라고 가정하지 않습니다. 앞선 세션 검증에서 먼저 거부될 수 있습니다.

Security에서 비로그인 또는 무효 세션을 거부하는 예시:

```json
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "로그인이 필요합니다.",
  "fieldErrors": []
}
```

Controller·서비스에서 인증 정보를 거부하는 예시(Swagger의 401 예시):

```json
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "유효한 로그인 정보가 필요합니다.",
  "fieldErrors": []
}
```

```json
{
  "code": "ACCESS_DENIED",
  "message": "접근 권한이 없습니다.",
  "fieldErrors": []
}
```

```json
{
  "code": "ADMIN_REQUIRED",
  "message": "관리자 권한이 필요합니다.",
  "fieldErrors": []
}
```

## 검증 범위

- 서비스 테스트: 관리자 검증 선행, Clock 1회 호출, 한국 자정·월말·연말·윤년의 날짜 경계.
- PostgreSQL 저장소 테스트: 시작 포함·끝 제외, 상태와 무관한 오늘 집계, 중복 대상의 개별 집계, 정렬·최대 5건, 전체 건수와 목록 길이 구분.
- Controller 테스트: principal 전달, 성공·오류 JSON 및 캐시, GET의 CSRF 불필요, 허용·거부 CORS.
- 세션 통합 테스트: 실제 로그인·보안 필터·서비스·저장소를 사용하는 MockMvc와 전용 Testcontainers DB. 변경 커밋 후 재조회하며 운영 DB를 사용하지 않음.
- OpenAPI 테스트: 실제 생성 `/v3/api-docs`의 로컬 참조를 해석하는 JSON Schema 검증, 필수 필드 누락·null·음수·잘못된 타입·ALL 거부, 성공·오류 예시 검증. `int64` 형식과 숫자 minimum도 별도 확인.

스키마의 필수·타입 검증이 날짜의 상호 관계나 정렬·집계 불변식을 모두 강제한다는 의미는 아닙니다. 해당 정책은 서비스·저장소·통합 테스트에서 검증합니다.
