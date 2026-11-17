# 관리자 콘텐츠 요약 조회 API

현재 작업 트리의 `AdminContentSummaryReadController`, `AdminContentSummaryReadService`, `AdminContentSummaryReadRepository` 및 응답 DTO 기준입니다.

[게시글 목록·상세](admin-post-read.md)와 [댓글 목록](admin-comment-read.md)에서 사용할 전체 요약 수치 4개를 읽기 전용으로 제공합니다. 목록 검색어·필터·페이지와 독립된 집계이며, 이번 작업에는 프론트 연동을 포함하지 않습니다.

## 요청 및 인증

```http
GET /api/v1/admin/content/summary
Accept: application/json
```

- 요청 본문이나 조회 파라미터가 없습니다. 날짜·검색어·필터·페이지를 지정하지 않습니다. 추가 쿼리 파라미터로 집계 범위나 로그인 정보를 바꿀 수 없습니다.
- 관리자 로그인 세션 쿠키가 필요합니다. 교차 출처 호출에도 기존 세션 쿠키를 포함하며 Bearer 인증은 사용하지 않습니다.
- GET 요청에는 CSRF 토큰이 필요하지 않습니다.
- 기존 `/api/v1/admin/**` 보안 규칙과 세션 필터가 현재 회원의 ACTIVE 상태·세션 권한·credentialVersion을 확인합니다. 서비스도 현재 ACTIVE·ADMIN·인증 버전을 검사한 뒤 집계를 시작합니다.
- 회원 ID와 credentialVersion은 `MemberPrincipal`에서 전달합니다. 로그인 후 상태·권한·인증 버전이 변경되면 기존 세션이 무효화되어 401로 거부될 수 있습니다. 인증 실패 시 집계를 실행하지 않습니다.
- 성공은 HTTP 200, `Content-Type: application/json`, `Cache-Control: no-store`입니다.
- MVC 오류에도 `no-store`를 적용합니다. 보안 필터 오류에는 다른 캐시 방지 지시어가 함께 붙을 수 있어 전체 헤더 문자열이 같다고 보장하지 않습니다.
- 개발 프로필의 `DevCorsConfig`는 정확한 이 경로에 GET·OPTIONS를 등록합니다. 기존 설정된 Origin·쿠키·허용 헤더 정책을 따릅니다. CORS 거부 응답은 `ApiErrorResponse` JSON이라고 보장하지 않습니다.

## 시간 기준과 집계

관리자 검증 후 `Clock.instant()`를 한 번 읽습니다. `queriedAt`은 이 기준 시각이고, `baseDate`는 이를 Asia/Seoul로 변환한 날짜입니다.

오늘은 **baseDate의 한국 시간 00:00 이상, 다음 날 00:00 미만**입니다. 예를 들어 2026-11-17의 UTC 구간은 `[2026-11-16T15:00:00Z, 2026-11-17T15:00:00Z)`입니다. 조회 시각까지가 아닌 날짜 전체 구간이며 `createdAt`을 사용합니다. 현재 숨김 수에는 날짜 조건이 없습니다.

| 집계 필드 | 포함 기준 |
| --- | --- |
| todayPostCount | 오늘 생성된 게시글. 현재 정상·숨김·소프트 삭제 여부와 무관 |
| todayCommentCount | 오늘 생성된 댓글. 댓글과 부모 게시글의 현재 숨김·소프트 삭제 여부와 무관 |
| hiddenPostCount | 전체 기간의 게시글 중 `hiddenAt != null AND deletedAt == null` |
| hiddenCommentCount | 전체 기간의 댓글 자체가 `hiddenAt != null AND deletedAt == null`. 부모 게시글 상태는 반영하지 않음 |

삭제된 대상은 오늘 작성 수에 남지만 현재 숨김 수에서는 제외됩니다. 부모 게시글만 숨기거나 삭제했다고 댓글의 숨김 수가 늘거나 줄지는 않습니다. 작성자 상태로도 집계를 제한하지 않습니다.

저장소는 집계 쿼리 4개를 실행하며 부모 게시글·작성자와 조인하지 않습니다. `queriedAt`은 날짜 계산 기준이지 네 집계와 별도 목록 조회의 동일한 DB 스냅샷을 보장하는 값이 아닙니다. 조회 중 콘텐츠 변경으로 수치가 서로 다른 시점의 DB 상태를 반영할 수 있습니다.

## 응답 필드

아래 필드는 모두 필수이며 **명시적 null도 허용하지 않습니다**. 집계 대상이 없으면 숫자 0을 반환합니다. 수치는 Java `long`, OpenAPI `integer / int64 / minimum: 0`이며 문자열이 아닙니다. 회원 정보나 콘텐츠 본문은 포함하지 않습니다.

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| queriedAt | string(date-time) | 조회 기준 시각, UTC ISO 8601 |
| baseDate | string(date) | queriedAt의 한국 날짜, yyyy-MM-dd |
| counts | object | 네 가지 요약 수치 |
| counts.todayPostCount | integer(int64), minimum 0 | 오늘 생성 게시글 수 |
| counts.todayCommentCount | integer(int64), minimum 0 | 오늘 생성 댓글 수 |
| counts.hiddenPostCount | integer(int64), minimum 0 | 전체 기간의 현재 숨김 게시글 수, 삭제 제외 |
| counts.hiddenCommentCount | integer(int64), minimum 0 | 전체 기간의 현재 숨김 댓글 수, 댓글 자체의 삭제 제외 |

## 정상 및 0건 예시

Swagger `summary` 예시입니다. 오늘 작성 수와 현재 숨김 수는 서로 다른 조건이므로 부분집합 관계로 해석하면 안 됩니다.

```json
{
  "queriedAt": "2026-11-17T03:00:00Z",
  "baseDate": "2026-11-17",
  "counts": {
    "todayPostCount": 7,
    "todayCommentCount": 11,
    "hiddenPostCount": 3,
    "hiddenCommentCount": 5
  }
}
```

집계 대상이 없을 때도 200입니다. Swagger `empty` 예시:

```json
{
  "queriedAt": "2026-11-17T03:00:00Z",
  "baseDate": "2026-11-17",
  "counts": {
    "todayPostCount": 0,
    "todayCommentCount": 0,
    "hiddenPostCount": 0,
    "hiddenCommentCount": 0
  }
}
```

## 상태 변경과 수치 예

같은 한국 날짜 안에서 다른 동시 변경이 없다고 가정합니다. 게시글 1건과 그 댓글 1건을 오늘 생성한 예입니다.

| 상황 | 오늘 게시글 | 오늘 댓글 | 숨김 게시글 | 숨김 댓글 |
| --- | ---: | ---: | ---: | ---: |
| 둘 다 정상 | 1 | 1 | 0 | 0 |
| 게시글만 숨김 | 1 | 1 | 1 | 0 |
| 댓글도 개별 숨김 | 1 | 1 | 1 | 1 |
| 둘 다 복원 | 1 | 1 | 0 | 0 |
| 둘 다 다시 숨긴 뒤 소프트 삭제 | 1 | 1 | 0 | 0 |

어제 작성·숨김 처리한 콘텐츠가 아직 숨겨져 있다면 오늘 작성 수에는 0, 현재 숨김 수에는 1로 반영됩니다. 이 API는 조회만 수행합니다. 신고 상태·처리 당시 `handlingOutcome`·현재 콘텐츠 상태는 별개이며 요약 조회가 이들을 변경하지 않습니다.

## 오류

| HTTP | code | 발생 계층·조건 |
| --- | --- | --- |
| 401 | AUTHENTICATION_REQUIRED | 비로그인, 무효화된 세션, principal 부재 또는 서비스의 회원·상태·인증 버전 검증 실패 |
| 403 | ACCESS_DENIED | Security의 관리자 권한 거부 |
| 403 | ADMIN_REQUIRED | 서비스의 현재 ADMIN 역할 검증 실패 |

거부한 계층에 따라 message가 다릅니다. 서비스의 인증 오류 예시(`authenticationRequired`):

```json
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "유효한 로그인 정보가 필요합니다.",
  "fieldErrors": []
}
```

Security 비로그인 오류 예시(`loginRequired`):

```json
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "로그인이 필요합니다.",
  "fieldErrors": []
}
```

Security 권한 오류 예시(`accessDenied`):

```json
{
  "code": "ACCESS_DENIED",
  "message": "접근 권한이 없습니다.",
  "fieldErrors": []
}
```

서비스 권한 오류 예시(`adminRequired`):

```json
{
  "code": "ADMIN_REQUIRED",
  "message": "관리자 권한이 필요합니다.",
  "fieldErrors": []
}
```

다른 공통 HTTP 오류는 기존 공통 처리 정책을 따릅니다. 이 API에는 페이지·날짜 입력 검증이나 콘텐츠 단건 404 계약을 추가하지 않습니다.

## 검증 범위와 프론트 연동 유의 사항

- 저장소 테스트는 전용 PostgreSQL Testcontainers DB에서 경계 시각, 상태별 포함 여부, 숨김·복원·삭제와 부모 상태 독립성을 검증합니다.
- 서비스 테스트는 인증 실패 시 집계 미호출, Clock 1회 조회, 한국 자정·월말·연말·윤년 범위를 검증합니다.
- 컨트롤러 테스트는 인증 전달, 0·int 범위 초과 수치, 캐시·오류·CORS를 검증합니다.
- 실제 로그인 세션 통합 테스트는 실제 서비스·저장소와 PostgreSQL을 사용합니다. Clock·Auditing 시각을 고정하고 TransactionTemplate 커밋 후 MockMvc GET으로 확인합니다. 엔티티 메서드로 상태를 바꾸므로 이 테스트 자체가 숨김·복원 명령 API 호출 검증은 아닙니다.
- OpenAPI 테스트는 생성된 `/v3/api-docs`의 로컬 참조를 해석하여 성공·오류 예시, 필수·null 불허, 집계 타입·범위, 날짜 형식을 JSON Schema로 검증합니다. 날짜 검증은 `formatAssertionsEnabled(true)`를 사용합니다.
- `build/reports/admin-content-summary-openapi.json`은 MVC 테스트에 등록된 컨트롤러 기준 문서이며 전체 애플리케이션 OpenAPI가 아닙니다.
- 프론트는 목록의 길이나 전체 개수로 집계를 추정하지 않고 이 API를 사용해야 합니다. 정상 0과 로딩·조회 실패를 구분하고, `baseDate`를 브라우저 시간대 변환으로 바꾸지 않습니다. `queriedAt` 표시에는 한국 시간 기준을 적용합니다.
