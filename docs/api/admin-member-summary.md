# 관리자 회원 요약 API

`AdminMemberSummaryReadController`, `AdminMemberSummaryReadService`, `AdminMemberSummaryReadRepository`와 응답 DTO 기준입니다.

[회원 목록·상세](admin-member-read.md) 상단에서 사용할 전체 회원 요약을 읽기 전용으로 제공합니다. 목록 검색·상태·역할·페이지·정렬 조건과 독립된 집계입니다. 프론트 연동은 이 문서 작업 범위에 포함하지 않습니다.

## 요청 및 인증

```http
GET /api/v1/admin/members/summary
Accept: application/json
```

- 요청 본문과 정의된 조회 파라미터가 없습니다. 추가 쿼리 파라미터로 집계 범위나 인증된 회원 정보를 바꾸지 않습니다.
- 관리자 로그인 세션 쿠키가 필요합니다. 기존 세션 인증 방식을 사용하며 Bearer 인증은 사용하지 않습니다.
- GET 조회에는 CSRF 토큰이 필요하지 않습니다.
- 회원 ID·credentialVersion은 로그인 `MemberPrincipal`에서 가져옵니다.
- 기존 Security의 `/api/v1/admin/**` 규칙이 ADMIN 권한을 요구합니다. 세션 필터는 DB의 현재 ACTIVE 상태·인증 버전·로그인 당시 역할을 확인합니다. 서비스도 현재 회원의 ACTIVE·credentialVersion·ADMIN 역할을 검사한 뒤 집계합니다.
- 로그인 이후 상태·역할·인증 버전이 변경되면 기존 세션이 무효화되어 401로 거부될 수 있습니다. 유효한 일반 회원은 403입니다.
- 성공은 HTTP 200, `Content-Type: application/json`, `Cache-Control: no-store`입니다. 401·403에도 `no-store`가 포함됩니다. MVC 오류와 보안 필터 오류의 전체 캐시 헤더 문자열이 같다고 보장하지 않습니다.
- 개발 CORS는 기존 `/api/v1/admin/members/*`의 GET·OPTIONS 설정으로 이 경로를 지원합니다. 허용 Origin과 쿠키 정책을 따르며 CORS 거부 응답을 `ApiErrorResponse` JSON으로 보장하지 않습니다.
- 정적 `/summary` 경로는 기존 `/{memberId}` 상세 조회와 구분됩니다.

## 응답 필드 및 집계

모든 필드는 필수이고 null을 허용하지 않습니다. 집계값은 문자열이 아닌 0 이상의 Java `long`이며 OpenAPI에서 `integer`, `int64`, `minimum: 0`입니다. 해당 집계 대상이 없으면 0을 반환합니다.

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| queriedAt | string(date-time) | 날짜 계산에 사용한 조회 기준 시각, UTC ISO 8601 |
| baseDate | string(date) | queriedAt의 Asia/Seoul 날짜, yyyy-MM-dd |
| counts | object | 아래 네 요약 수치 |
| counts.currentMemberCount | integer(int64), minimum 0 | 전체 기간에서 WITHDRAWN·DELETED를 제외한 현재 회원 수 |
| counts.todayMemberCount | integer(int64), minimum 0 | 한국 날짜 기준 오늘 생성된 계정 수, createdAt 기준 |
| counts.pendingMemberCount | integer(int64), minimum 0 | 전체 기간에서 현재 PENDING인 회원 수 |
| counts.suspendedMemberCount | integer(int64), minimum 0 | 전체 기간에서 현재 SUSPENDED인 회원 수 |

모든 집계에 USER와 ADMIN을 포함합니다. 로그인한 관리자도 집계 대상입니다.

- 현재 회원에는 PENDING·ACTIVE·RESTRICTED·SUSPENDED가 포함됩니다. 정지·정지 해제만으로 현재 회원 수는 바뀌지 않습니다.
- 오늘 가입은 현재 인증·정지·탈퇴 상태와 역할에 관계없이 포함합니다. 오늘 가입한 후 탈퇴해도 오늘 가입 수에 남습니다.
- 인증 대기·이용 정지는 가입 날짜와 무관한 현재 상태의 수치입니다. 과거 조치 이력이나 정지 만료 시각으로 추정하지 않습니다. 정지 만료 기능을 제공하는 API가 아닙니다.
- 개인정보나 회원별 데이터는 응답에 포함하지 않습니다.

## 시간 기준

관리자 검증 후 `Clock.instant()`를 한 번 읽습니다. `queriedAt`을 Asia/Seoul로 변환하여 `baseDate`와 오늘의 시간 범위를 계산합니다.

오늘 가입 조건은 **한국 날짜 오늘 시작 이상, 다음 날 시작 미만**인 반개구간입니다. 예를 들어 baseDate가 `2026-11-18`이면 UTC 기준 `[2026-11-17T15:00:00Z, 2026-11-18T15:00:00Z)`입니다. 조회 시각까지가 아니라 날짜 전체 구간입니다. 다른 세 집계에는 날짜 조건이 없습니다.

저장소는 Member에 대한 집계 쿼리 4개를 실행하며 불필요한 조인이나 전체 회원 로딩을 하지 않습니다. `queriedAt`은 날짜 계산 기준이지 여러 집계와 별도 목록 조회의 **동일 DB 스냅샷을 보장하는 값이 아닙니다**. 동시 상태 변경이 있으면 각 수치가 서로 다른 시점의 상태를 반영할 수 있습니다.

## 성공 예시

Swagger `summary` — 일반적인 회원 요약:

```json
{
  "queriedAt": "2026-11-18T03:00:00Z",
  "baseDate": "2026-11-18",
  "counts": {
    "currentMemberCount": 20,
    "todayMemberCount": 7,
    "pendingMemberCount": 3,
    "suspendedMemberCount": 5
  }
}
```

Swagger `adminOnly` — 오늘 이전에 가입한 ACTIVE 관리자만 존재:

```json
{
  "queriedAt": "2026-11-18T03:00:00Z",
  "baseDate": "2026-11-18",
  "counts": {
    "currentMemberCount": 1,
    "todayMemberCount": 0,
    "pendingMemberCount": 0,
    "suspendedMemberCount": 0
  }
}
```

로그인한 관리자도 포함하므로 안정된 상태에서 관리자만 있는 경우를 네 집계가 모두 0인 빈 응답으로 설명하지 않습니다. 다만 각 필드의 스키마 최솟값은 0입니다. 스키마가 집계 간 관계나 최소 관리자 수까지 강제하는 것은 아닙니다.

## 상태 변경과 수치 변화

한국 날짜가 바뀌지 않고 다른 동시 변경이 없는 상황에서, 어제 가입한 관리자 1명과 오늘 가입한 대상 회원 1명만 있다고 가정합니다.

| 대상 회원 상태 | 현재 회원 | 오늘 가입 | 인증 대기 | 이용 정지 |
| --- | ---: | ---: | ---: | ---: |
| 가입 직후 PENDING | 2 | 1 | 1 | 0 |
| 인증 후 ACTIVE | 2 | 1 | 0 | 0 |
| 이용 정지 SUSPENDED | 2 | 1 | 0 | 1 |
| 정지 해제 ACTIVE | 2 | 1 | 0 | 0 |
| 탈퇴 완료 DELETED | 1 | 1 | 0 | 0 |

WITHDRAWN 역시 현재 회원에서 제외됩니다. 일반 탈퇴 경로와 개인정보 정리 정책은 [회원 조회 문서](admin-member-read.md)를 따릅니다. 이 요약 API는 상태를 변경하지 않습니다.

## 오류

| HTTP | code | 조건·처리 계층 |
| --- | --- | --- |
| 401 | AUTHENTICATION_REQUIRED | 비로그인·무효 세션·principal 부재 또는 서비스의 현재 회원/ACTIVE/인증 버전 검증 실패 |
| 403 | ACCESS_DENIED | Security의 관리자 권한 거부 |
| 403 | ADMIN_REQUIRED | 서비스의 현재 ADMIN 역할 검증 실패 |

message는 거부한 계층에 따라 다를 수 있습니다. Swagger의 서비스 인증 실패 예시(`authenticationRequired`):

```json
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "유효한 로그인 정보가 필요합니다.",
  "fieldErrors": []
}
```

Security 비로그인 예시(`loginRequired`):

```json
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "로그인이 필요합니다.",
  "fieldErrors": []
}
```

Security 권한 거부 예시(`accessDenied`):

```json
{
  "code": "ACCESS_DENIED",
  "message": "접근 권한이 없습니다.",
  "fieldErrors": []
}
```

서비스 권한 거부 예시(`adminRequired`):

```json
{
  "code": "ADMIN_REQUIRED",
  "message": "관리자 권한이 필요합니다.",
  "fieldErrors": []
}
```

잘못된 요청 메서드 등 다른 공통 HTTP 오류는 기존 공통 정책을 따릅니다. 입력 파라미터가 없으므로 회원 목록의 검색·페이지 검증 오류나 회원 상세 404를 이 API의 오류로 추가하지 않습니다.

## 검증 범위와 프론트 연동 유의 사항

- 저장소 테스트: 전용 PostgreSQL Testcontainers DB에서 상태별 포함/제외, 관리자 포함, 마이크로초 정밀도의 날짜 경계, 상태 변화 및 0건을 검증합니다.
- 서비스 테스트: 관리자 검증 순서, Clock 1회 조회, 자정·월말·연말·윤년 범위, 인증/권한 실패 시 집계 미호출을 검증합니다.
- 컨트롤러 테스트: 정적 경로 선택, principal 전달, int 범위를 넘는 long 값, GET CSRF 불필요, 성공/오류 캐시 및 개발 CORS를 검증합니다.
- 세션 통합 테스트: 실제 로그인 API·보안 필터·서비스·저장소와 전용 PostgreSQL을 사용합니다. MockMvc와 고정 Clock/Auditing 시각을 사용하며, 엔티티 상태 변경을 TransactionTemplate으로 커밋한 후 다음 GET에서 확인합니다. 이 테스트 자체를 실제 브라우저나 정지·탈퇴 명령 API 호출 검증으로 해석하지 않습니다. 클래스 전체 트랜잭션은 사용하지 않고 데이터 정리는 전용 컨테이너 DB 안에서만 수행합니다.
- OpenAPI 테스트: 생성된 `/v3/api-docs`의 로컬 참조를 해석하여 성공/오류 예시·필수·null·숫자 타입·최솟값·날짜 형식을 JSON Schema로 검증합니다. `build/reports/admin-member-summary-openapi.json`은 MVC 테스트에 등록된 컨트롤러 기준의 실제 생성 문서이며 전체 실행 서버의 OpenAPI는 아닙니다.
- 프론트는 목록의 items/totalElements로 요약을 추정하지 않고 이 API를 호출합니다. 정상 0과 로딩/실패를 구분하고 baseDate를 브라우저 시간대 변환으로 바꾸지 않습니다. queriedAt은 필요 시 한국 시간으로 표시합니다.
