# 관리자 회원 조회 API

현재 로컬 구현 기준: `AdminMemberReadController`, `AdminMemberReadQuery`, `AdminMemberReadService`,
`AdminMemberReadRepository`, 회원 응답 DTO, 기존 Controller·세션·Query·Repository·Service 테스트.

이 API는 **읽기 전용**이다. 이용 정지·해제는 별도 [회원 운영 API](admin-member-moderation.md), 조치 이력은 [회원별 이력 조회 API](admin-member-moderation-logs.md)를 사용한다. 이 조회 API는 상태·역할 변경이나 강제 탈퇴를 수행하지 않는다.

## 공통 정책

- 관리자 로그인 세션 쿠키가 필요하다. GET 조회에 CSRF 토큰 발급·전달은 필요하지 않다.
- 요청자의 회원 ID와 credentialVersion은 인증된 `MemberPrincipal`에서 확인한다. URL에 전달하는 관리자 ID나 인증 버전으로 요청자를 지정하거나 변경할 수 없다.
- 성공 응답은 `Content-Type: application/json`, `Cache-Control: no-store`다.
- 관리자에게 회원 이메일을 포함한 조회 정보를 제공한다. 공개 회원 API의 노출 정책을 바꾸지 않는다.
- `PENDING`(이메일 인증 대기), `ACTIVE`(활성), `RESTRICTED`(일부 활동 제한), `SUSPENDED`(이용 정지), `WITHDRAWN`(탈퇴·정리 대기), `DELETED`(탈퇴 완료) 모두 조회할 수 있다.
- `WITHDRAWN`, `DELETED`의 nickname은 **탈퇴한 회원**이다. DB의 원래 닉네임을 검색하는 조건과 응답 표시 이름은 별개다. “탈퇴한 회원”을 검색해 탈퇴 회원을 전부 찾는 정책이 아니다. 탈퇴 완료 회원 조회는 검색어를 비우고 `status=DELETED`로 요청한다. 검색어·상태·권한은 AND 조건이며 상태 변경으로 검색어를 자동 삭제하거나 표시용 이름을 상태 조건으로 변환하지 않는다.
- `DELETED` 이메일은 명시적 null이다. 현재 탈퇴 처리와 DB 제약이 저장 이메일을 null로 만들고, 조회 결과/DTO는 그 저장값을 전달한다.
- `WITHDRAWN`은 현재 저장된 이메일을 반환한다. 두 상태의 이메일을 일괄 null로 바꾸는 정책이 아니다. MVP의 일반 탈퇴는 개인정보 정리 후 즉시 DELETED로 전환한다. WITHDRAWN은 이용 종료 후 개인정보 정리가 완료되지 않은 상태를 표현하는 예약 상태이며, 현재 일반 탈퇴 경로에서 별도로 저장하지 않는다.
- `passwordHash`, `password`, `credentialVersion`, 인증 토큰은 반환하지 않는다.
- 시각은 UTC ISO 8601 문자열(`2026-11-10T01:00:00Z`)이다. 소수 초가 포함될 수 있다. 화면에서는 `Asia/Seoul` 기준으로 표시한다. `updatedAt`은 회원 정보 최종 변경 시각이며 최근 로그인 시각이 아니다.

### 탈퇴 상태의 의미

- `DELETED`는 회원 행의 물리 삭제나 관리자 강제 삭제가 아니다. 일반 탈퇴 시 이메일·비밀번호 해시·닉네임을 제거하고 탈퇴 시각을 저장하되, 회원 행과 회원 ID는 유지한다.
- 프론트 상태 표시는 `WITHDRAWN`: **탈퇴·정리 대기**, `DELETED`: **탈퇴 완료**다. 표시용 nickname **탈퇴한 회원**과 상태 표시명은 별개다.
- WITHDRAWN 상세 안내: “서비스 이용이 종료되었으며 계정 정보 정리가 완료되지 않은 상태입니다.”
- DELETED 상세 안내: “탈퇴 처리가 완료되어 이메일 등 계정 정보가 제거되었습니다.”
- 탈퇴 유예·복구·정리 배치·물리 삭제 기능은 현재 MVP 일반 탈퇴 흐름에 추가하지 않는다. API enum·응답 예시의 `DELETED` 값, 인증·세션 종료와 게시글·댓글 작성자 표시 정책은 유지한다.

## 1. 목록 — GET /api/v1/admin/members

### 요청 파라미터

| 이름 | 타입 / 허용 값 | 생략 또는 빈 문자열 | 계약 |
|---|---|---|---|
| searchType | MEMBER_ID / NICKNAME / EMAIL | 검색 유형 없음 | 정리한 keyword가 있으면 필수. keyword가 없으면 유효한 검색 유형도 무시 |
| keyword | string | 검색 조건 없음 | Java `String.strip()` 후 빈 값이면 검색하지 않음 |
| status | PENDING / ACTIVE / RESTRICTED / SUSPENDED / WITHDRAWN / DELETED | 전체 상태 | 단일 상태 필터. ALL 없음 |
| role | USER / ADMIN | 전체 역할 | 단일 역할 필터. ALL 없음 |
| sort | LATEST / OLDEST | LATEST | 정렬 후 페이징 |
| page | int32 | 0 | 0 이상 |
| size | int32 | 20 | 1~50 |

- enum은 대문자 값만 허용한다. `ALL`, 소문자, 알 수 없는 값은 `400 INVALID_REQUEST_PARAMETER`다. 검색어가 없더라도 잘못된 searchType 문자열의 바인딩 오류는 무시하지 않는다.
- Spring enum 변환은 값 양끝의 공백을 제거한다. 정확한 빈 문자열은 선택 enum에서 null로 변환된다. 공백만 있는 enum 문자열은 빈 문자열 생략과 같지 않으며 변환 오류다. `sort=`는 Controller 기본값 LATEST를 사용한다.
- page/size는 생략·`page=`·`size=`일 때 기본값을 사용한다. 숫자로 변환할 수 없거나 int32 범위를 넘으면 `INVALID_REQUEST_PARAMETER`, 숫자 변환 후 범위 검증 실패는 `INVALID_PAGE_REQUEST`다.
- `(long) page × size ≤ 2147483647`이어야 한다. 기본 size=20이면 page=107374182까지 허용된다. DB 결과 페이지 수를 넘는 것과 이 offset 허용 범위를 넘는 것은 다르다.
- 검색·status·role 조건은 AND다. 상태·역할 전체 조회는 해당 파라미터를 **생략**한다.
- `LATEST`: createdAt DESC → memberId DESC. `OLDEST`: createdAt ASC → memberId ASC. 가입 시각이 같아도 ID로 순서를 확정하며 전체 검색 결과를 정렬한 뒤 페이지를 자른다.

### 검색 규칙

| 유형 | 정규화 / 제한 | 검색 방식 |
|---|---|---|
| MEMBER_ID | strip 후 ASCII 숫자 `[0-9]+`, 최대 19자리, 1~9223372036854775807. 선행 0 제거 (`00104` → `104`) | ID 정확 일치 |
| NICKNAME | strip 후 최대 100 **유니코드 코드 포인트**. 가입용 2~20자 규칙을 적용하지 않음 | 영문 대소문자를 구분하지 않는 부분 검색 |
| EMAIL | strip 후 Locale.ROOT 소문자 변환, 정규화 결과 최대 254 **Java String.length() / UTF-16 코드 단위** | 저장된 전체 이메일과 정확 일치 |

- ID의 0·음수·`+104`·소수·비숫자·20자리 이상·Long 초과는 `400 INVALID_SEARCH_KEYWORD`. 선행 0을 제거하기 **전에** 19자리 제한을 검사한다.
- 닉네임의 `%`, `_`, `!`는 LIKE 와일드카드가 아닌 일반 문자로 검색한다. Repository에서 `!`를 escape 문자로 사용해 처리한다.
- 이메일 검색에 이메일 형식 Validation은 적용하지 않는다. `member` 같은 일부 문자열도 400으로 거부하지 않지만, 부분 검색이 아니므로 저장된 주소와 정확히 같지 않으면 결과가 없다.
- `strip()`은 Java의 whitespace 기준이다. 모든 유니코드 공백을 제거한다는 뜻이 아니며 NBSP(`U+00A0`)는 일반 공백처럼 제거되지 않는다.
- 이모지 같은 보조 평면 문자는 닉네임에서는 코드 포인트 1개, 이메일 길이에서는 UTF-16 코드 단위 2개다. grapheme cluster 개수 제한이 아니다.

### 정상 요청 예시 (URL 인코딩 적용)

```http
GET /api/v1/admin/members
GET /api/v1/admin/members?searchType=MEMBER_ID&keyword=00104
GET /api/v1/admin/members?searchType=NICKNAME&keyword=%EC%9D%B4%EC%9B%80
GET /api/v1/admin/members?searchType=NICKNAME&keyword=%25_%21
GET /api/v1/admin/members?searchType=EMAIL&keyword=%20Member%40Example.com%20
GET /api/v1/admin/members?searchType=NICKNAME&keyword=%EC%9D%B4%EC%9B%80&status=ACTIVE&role=USER
GET /api/v1/admin/members?status=SUSPENDED&role=USER&sort=OLDEST&page=1&size=10
```

### 응답 필드

모든 아래 필드는 응답에 존재한다. **필수 존재와 null 허용은 별개**다.

| 페이지 필드 | 타입 | null | 의미 |
|---|---|---|---|
| items | AdminMemberItemResponse[] | 불가 | 현재 페이지 항목, 없으면 [] |
| page | int32 | 불가 | 요청 페이지(0부터 시작) |
| size | int32 | 불가 | 요청 크기 |
| totalElements | int64 | 불가 | 조건에 맞는 전체 회원 수 |
| totalPages | int32 | 불가 | ceil(totalElements / size); 0건이면 0 |
| hasNext | boolean | 불가 | 다음 페이지 존재 여부 |
| hasPrevious | boolean | 불가 | page > 0 여부. 범위 밖 페이지여도 true일 수 있음 |

| 회원 항목 필드 | 타입 | null | 의미 |
|---|---|---|---|
| memberId | int64 | 불가 | 대상 회원 ID |
| nickname | string | 불가 | 표시 이름; WITHDRAWN/DELETED는 탈퇴한 회원 |
| email | string | 허용 | 현재 저장 이메일; DELETED는 명시적 null |
| status | MemberStatus enum | 불가 | 현재 회원 상태 |
| role | USER / ADMIN | 불가 | 현재 역할 |
| createdAt | string(date-time) | 불가 | 가입 시각 |

```json
{
  "items": [{
    "memberId": 42,
    "nickname": "이움회원",
    "email": "member@example.com",
    "status": "ACTIVE",
    "role": "USER",
    "createdAt": "2026-11-10T01:00:00Z"
  }],
  "page": 0, "size": 20, "totalElements": 1,
  "totalPages": 1, "hasNext": false, "hasPrevious": false
}
```

결과 없음은 200이다. 오류나 404로 처리하지 않는다.

```json
{"items":[],"page":0,"size":20,"totalElements":0,"totalPages":0,"hasNext":false,"hasPrevious":false}
```

범위 밖 페이지도 200이며 전체 개수를 유지한다. 예: 15건에서 page=2, size=10.

```json
{"items":[],"page":2,"size":10,"totalElements":15,"totalPages":2,"hasNext":false,"hasPrevious":true}
```

## 2. 상세 — GET /api/v1/admin/members/{memberId}

`memberId`는 대상 회원의 Long ID다. 요청 관리자의 ID가 아니다.
인증·관리자 자격을 먼저 검증하고 대상 회원을 조회한다.

```http
GET /api/v1/admin/members/42
```

| 상세 필드 | 타입 | null | 의미 |
|---|---|---|---|
| memberId | int64 | 불가 | 대상 회원 ID |
| nickname | string | 불가 | 표시 이름; WITHDRAWN/DELETED는 탈퇴한 회원 |
| email | string | 허용 | 저장된 이메일; DELETED는 명시적 null |
| status | MemberStatus enum | 불가 | 현재 회원 상태 |
| role | USER / ADMIN | 불가 | 현재 역할 |
| createdAt | string(date-time) | 불가 | 가입 시각 |
| updatedAt | string(date-time) | 불가 | 회원 정보 최종 변경 시각 |
| withdrawnAt | string(date-time) | 허용 | 탈퇴 시각; 없으면 **필드를 생략하지 않고 null** |

정상 회원:

```json
{"memberId":42,"nickname":"이움회원","email":"member@example.com","status":"ACTIVE","role":"USER","createdAt":"2026-11-10T01:00:00Z","updatedAt":"2026-11-10T01:00:00Z","withdrawnAt":null}
```

개인정보 정리가 완료된 회원:

```json
{"memberId":43,"nickname":"탈퇴한 회원","email":null,"status":"DELETED","role":"USER","createdAt":"2026-11-01T01:00:00Z","updatedAt":"2026-11-10T02:00:00Z","withdrawnAt":"2026-11-10T02:00:00Z"}
```

- 회원 없음, Long으로 변환 가능한 0·음수 ID: `404 MEMBER_NOT_FOUND`.
- 숫자로 변환 불가 또는 Long 범위 초과: `400 INVALID_REQUEST_PARAMETER`.
- 목록의 MEMBER_ID keyword=0은 `400 INVALID_SEARCH_KEYWORD`로, 상세 ID=0과 구분한다.

## 3. 인증·오류

| HTTP | code | 발생 조건 / 실제 메시지 |
|---|---|---|
| 400 | INVALID_REQUEST_PARAMETER | enum/숫자 변환 실패: `요청 파라미터의 형식과 값을 확인해 주세요.` |
| 400 | INVALID_REQUEST_PARAMETER | keyword가 있으나 searchType 없음: `회원 조회 조건을 올바르게 입력해 주세요.` |
| 400 | INVALID_PAGE_REQUEST | page/size/offset 범위 위반: `페이지는 0 이상이고 조회 개수는 1 이상 50 이하여야 하며, 조회 시작 위치는 지원 범위 이내여야 합니다.` |
| 400 | INVALID_SEARCH_KEYWORD | 검색 유형별 숫자/길이 검증 실패: `검색 유형에 맞는 유효한 검색어를 입력해 주세요.` |
| 401 | AUTHENTICATION_REQUIRED | 비로그인/무효 세션 필터: `로그인이 필요합니다.`; 서비스 자격 검증: `유효한 로그인 정보가 필요합니다.` |
| 403 | ACCESS_DENIED | 유효한 일반 회원의 Security 접근 거부: `접근 권한이 없습니다.` |
| 403 | ADMIN_REQUIRED | 서비스의 현재 관리자 자격 검증 실패: `관리자 권한이 필요합니다.` |
| 404 | MEMBER_NOT_FOUND | 상세 대상 없음/유효하지 않은 ID(0·음수): `회원을 찾을 수 없습니다.` |

현재 세션 필터는 DB의 ACTIVE 상태·credentialVersion·role을 로그인 Principal과 비교한다.
로그인 이후 역할·상태·인증 버전이 달라진 세션은 403 대신 **401로 차단될 수 있다**.
프론트는 ACCESS_DENIED와 ADMIN_REQUIRED를 모두 관리자 접근 거부로 처리한다.
401과 일반 회원의 403을 동일한 로그아웃 사유로 취급하지 않는다.

오류는 `ApiErrorResponse`의 code/message/fieldErrors 구조다. 위 오류의 fieldErrors는 빈 배열이다.
클라이언트 분기는 메시지 대신 HTTP 상태와 code를 기준으로 한다.

```json
{"code":"INVALID_REQUEST_PARAMETER","message":"회원 조회 조건을 올바르게 입력해 주세요.","fieldErrors":[]}
```
```json
{"code":"AUTHENTICATION_REQUIRED","message":"로그인이 필요합니다.","fieldErrors":[]}
```
```json
{"code":"ACCESS_DENIED","message":"접근 권한이 없습니다.","fieldErrors":[]}
```
```json
{"code":"MEMBER_NOT_FOUND","message":"회원을 찾을 수 없습니다.","fieldErrors":[]}
```

### 개발 CORS

`DevCorsConfig`는 dev 프로필 전용이다. `iyum.cors.allowed-origin`의 단일 Origin을 허용하며,
개발 기본값은 `IYUM_WEB_ORIGIN` 미설정 시 `http://127.0.0.1:8443`이다. localhost와 127.0.0.1 또는 다른 포트는 다른 Origin이다.

- 경로: `/api/v1/admin/members`, `/api/v1/admin/members/*`
- credentials: true, 메서드: GET / OPTIONS
- 허용 요청 헤더: Accept / Content-Type / X-CSRF-TOKEN (헤더 허용이 GET의 CSRF 요구를 의미하지 않음)
- preflight maxAge: 600초. 회원 응답 캐시 허용이 아니다.
- 허용 Origin의 GET preflight는 비로그인으로 가능하나 실제 GET에는 관리자 세션이 필요하다.
- 비허용 Origin/메서드는 CORS에서 거부될 수 있다. 이 거부는 위 ApiErrorResponse 형태를 보장하지 않는다.
- 개발 CORS 허용을 운영 환경의 CORS 보장 사항으로 해석하지 않는다.

## 4. 프론트 연동

- `fetch(url, {credentials: "include"})`로 세션 쿠키를 포함한다. GET 전 CSRF 발급 요청을 추가하지 않는다.
- 검색 유형·검색어·상태·역할·정렬 변경을 적용할 때 page=0으로 초기화한다. 상태·역할 전체는 파라미터 생략, ALL 전송 금지.
- URLSearchParams 등으로 검색어를 인코딩한다. `%`, `!`, 한글을 원문 그대로 URL에 이어 붙이지 않는다.
- 서버 목록 순서와 totalElements/totalPages/hasNext/hasPrevious를 사용한다. 현재 페이지를 별도로 정렬하거나 다른 조건의 결과를 합치지 않는다.
- 이전 조건/이전 계정의 늦은 응답이 최신 목록을 덮어쓰지 않도록 요청 소유권을 확인한다.
- 인증 만료·권한 변경 시 기존 회원 조회 데이터를 지운다. 페이지 복귀로 이전 이메일 등이 노출되지 않도록 한다.
- email=null과 필드 누락을 구분한다. 탈퇴 상태의 표시 이름 정책을 유지하며 null을 빈 이메일/가상의 이메일로 만들지 않는다.
- 조회 화면에 이 API로 실행할 수 없는 회원 상태 변경 기능을 연결하지 않는다.

## 5. Swagger와 검증 범위

라이브러리 의존성은 **springdoc 3.1.1**, 생성된 문서의 `openapi` 값은 **OpenAPI 3.1.0**이다. 기존 세션 인증 설명 방식을 사용한다. HTTP Bearer 인증은 추가하지 않는다.
기본 설정은 Swagger/API docs 비활성, dev에서는 `/v3/api-docs`와 Swagger UI를 제공한다.
응답 DTO의 필수 존재 필드와 nullable 필드를 구분하며 OpenAPI 3.1에서는 `type: ["string", "null"]`로 표현한다.
Validation, JSON 이름, DTO 타입, 직렬화 정책, 조회/권한 로직은 문서화 작업으로 변경하지 않는다.

`AdminMemberOpenApiDocumentationTest`는 실제 springdoc 출력을 확인하는 MVC 문서 테스트다.
DB 조회·실제 로그인 통합 검증을 대체하지 않는다. 생성 문서 확인 파일은 테스트 실행 후
`server/iyum-api/build/reports/admin-member-openapi.json`에 남는다(생성 산출물이며 소스에 포함하지 않음).

관련 문서: [관리자 게시글 조회](admin-post-read.md), [관리자 댓글 조회](admin-comment-read.md),
[운영 기록 조회](../Admin-Operation-Log-API.md), [API 문서 목록](README.md).

### 페이지 파라미터의 생성 스키마

page·size는 선택 query 파라미터이며, Java int에 대응하는 `integer` / `int32`로 생성한다.
어노테이션의 `defaultValue`는 Java 문자열이지만, 생성 JSON의 default는 **정수 노드**다.

```json
{"page":{"type":"integer","format":"int32","default":0,"minimum":0},"size":{"type":"integer","format":"int32","default":20,"minimum":1,"maximum":50}}
```

page에는 고정 maximum을 추가하지 않는다. `page × size ≤ 2147483647` 검증은 기존대로 유지한다.
문서 테스트는 default의 존재·정수 타입, 범위의 존재·숫자 타입도 확인하므로 문자열 숫자나 누락을 숫자로 변환해 통과시키지 않는다.
