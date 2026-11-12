# 사용자 게시글·댓글 신고 접수 API

현재 작업 트리 기준(2026-11-12). `ContentReportCreateService`, `ContentReportInputPolicy`, 보안 필터 및 신고 테스트를 기준으로 한다. 관리자 숨김·복원, 회원 정지·해제와 별도 기능이다.

## 요청과 인증

| 메서드 | 경로 | 대상 |
|---|---|---|
| POST | `/api/v1/community/posts/{postId}/reports` | 게시글 |
| POST | `/api/v1/community/posts/{postId}/comments/{commentId}/reports` | 해당 게시글의 댓글 |

- `Content-Type: application/json`. 경로 ID는 Java `Long`(integer/int64)이다.
- 현재 **ACTIVE** 회원의 유효한 로그인 세션과 CSRF 토큰이 필요하다. 관리자도 같은 신고 정책을 적용한다. 신고를 위한 관리자 전용 권한은 요구하지 않는다.
- 로그인 요청 자체의 CSRF 흐름은 기존 인증 API를 따른다. **로그인 성공 후** `GET /api/v1/auth/csrf`로 최신 토큰을 다시 발급받고, 같은 로그인 세션 쿠키와 `X-CSRF-TOKEN` 헤더를 함께 전송한다. 다른 세션의 토큰은 사용할 수 없다.
- 신고자 회원 ID와 credentialVersion은 인증된 `MemberPrincipal`에서 가져온다. 클라이언트가 신고자나 인증 버전을 지정하는 계약은 없다.
- 제목·본문 스냅샷은 서버가 잠금 조회한 현재 원문으로 저장한다. 게시글은 제목·본문, 댓글은 본문만 저장한다. 프론트가 스냅샷을 생성하거나 전송하지 않는다.
- 현재 Jackson/MVC 설정에서 알 수 없는 추가 JSON 필드는 **무시**된다. `reporterMemberId`, `credentialVersion`, `titleSnapshot`, `contentSnapshot` 등을 추가해도 서비스 인자로 사용하지 않는다. 이는 추가 필드 지원 계약이 아니므로 아래 두 필드만 전송한다. 이 동작은 실제 MVC 바인딩 테스트로 확인했다.

### 요청 본문

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| reasonType | string enum | 예 | 아래 6개 값 중 하나, 대문자 정확히 일치 |
| reasonDetail | string 또는 null | OTHER에서 필수 | 아래 정규화·길이 규칙 적용 |

일반 사유는 상세를 생략할 수 있다.

```json
{
  "reasonType": "SPAM_ADVERTISING"
}
```

일반 사유에도 선택적으로 상세를 보낼 수 있다.

```json
{
  "reasonType": "PERSONAL_INFORMATION",
  "reasonDetail": "  타인의 연락처가 본문에 포함되어 있습니다.  "
}
```

기타 사유는 상세가 필요하다.

```json
{
  "reasonType": "OTHER",
  "reasonDetail": "게시판 이용 목적과 맞지 않는 내용을 반복해서 게시합니다."
}
```

## 사유 정책

| reasonType | 표시명 |
|---|---|
| SPAM_ADVERTISING | 광고·홍보·스팸 |
| ABUSE_HATE | 욕설·비방·혐오 |
| PERSONAL_INFORMATION | 개인정보 노출 |
| DUPLICATE_FLOODING | 도배·중복 게시 |
| OFF_TOPIC | 게시판 주제와 무관 |
| OTHER | 기타 |

- reasonType 누락 또는 null은 서비스의 `INVALID_REASON`이다. 알 수 없는 값이나 소문자 문자열은 JSON enum 파싱의 `INVALID_REQUEST_BODY`이다. 관리자 조치용 `LEGACY`, `NO_VIOLATION` 등은 허용하지 않는다.
- 상세는 Java `String.strip()`으로 양끝의 `Character.isWhitespace` 공백만 제거한다. 모든 유니코드 공백을 제거하는 규칙이 아니다. **NBSP(U+00A0)는 제거하지 않는다.** 내부 공백·탭·줄바꿈은 합치거나 제거하지 않는다.
- OTHER에서 상세 생략·null·정규화 후 빈 문자열은 `INVALID_REASON`이다.
- OTHER 외 유형에서 상세 생략·null·정규화 후 빈 문자열은 null로 저장한다.
- 정규화 후 최대 **500 Unicode 코드 포인트**다. UTF-16 코드 단위 길이(`String.length`, JavaScript 문자열 length)나 화면에 보이는 글자/grapheme cluster 개수가 아니다. `😀`는 코드 포인트 1개지만 복합 이모지는 여러 코드 포인트일 수 있다.
- 원문 JSON 문자열에 바로 maxLength=500을 적용하면 양끝 공백 제거 후 허용되는 입력까지 거부할 수 있어, OpenAPI에서는 정규화 후 제한을 설명으로 명시한다. 접수 서비스가 최종 검증한다.

## 대상·중복·처리 정책

- 자신의 게시글·댓글은 신고할 수 없다. 자기 게시글에 달린 **다른 회원의 댓글**은 신고할 수 있다.
- 게시글은 존재하고 숨김·소프트 삭제 상태가 아니어야 한다.
- 댓글은 부모 게시글과 댓글이 모두 공개 상태여야 하고 요청 경로의 부모 게시글에 속해야 한다.
- 숨김과 삭제는 이 API의 not-found 응답으로 구분해서 알리지 않는다.
- 동일 신고자 + 동일 대상은 한 번만 신고할 수 있다. 기존 신고가 `RECEIVED`, `RESOLVED`, `DISMISSED` 중 무엇이든 재신고는 409다. 상태를 조건으로 제외하지 않는 조회와 대상별 DB 유일 인덱스가 이를 보장한다.
- 게시글과 댓글은 서로 다른 대상 종류다. 숫자 ID가 같아도 동일 대상으로 합치지 않는다.
- 신고 접수만으로 콘텐츠 숨김·삭제 또는 회원 제재가 발생하지 않는다. 신고 상태와 콘텐츠 공개 상태·회원 상태는 독립적이다.
- 신고 취소·사유 수정 및 사용자 신고 단건 조회 API는 현재 제공하지 않는다. 이 문서는 관리자 신고 조회·처리 API를 정의하지 않는다.
- 신고자 정보와 스냅샷을 접수 응답이나 대상 작성자·일반 공개 응답에 추가하지 않는다.

서비스 검증 순서: 신고자 잠금/현재 ACTIVE·credentialVersion 확인 → 사유 검증 → 공개 부모 게시글 → 댓글 신고라면 해당 부모의 공개 댓글 → 자기 콘텐츠 여부 → 기존 신고 → 스냅샷 및 신고 저장. 보안 필터와 MVC 바인딩 검사는 그보다 먼저 실행된다. 둘 이상의 문제가 있으면 먼저 확인한 문제가 반환된다.

## 성공 응답

HTTP **201 Created**, `Cache-Control: no-store`. 별도 `Location` 헤더를 제공하지 않는다.

| 필드 | 타입 | 의미 |
|---|---|---|
| reportId | integer/int64 | 저장된 신고 ID |
| status | string | 신규 접수 결과는 RECEIVED |
| createdAt | string/date-time | 접수 시각, UTC ISO 8601 |

```json
{
  "reportId": 100,
  "status": "RECEIVED",
  "createdAt": "2026-11-12T10:00:00Z"
}
```

세 필드는 모두 존재한다. 신고자 ID·인증 버전·사유·제목/본문 스냅샷은 이 응답에 포함하지 않는다. 기존 도메인에는 종결 상태도 있지만 신규 접수 응답은 RECEIVED다.

## 오류

보안 필터 또는 MVC/서비스가 아래 오류를 작성하면 `ApiErrorResponse`를 사용한다. 신고 서비스 오류에는 `Cache-Control: no-store`가 설정되며 기존 Security 캐시 방지 정책도 유지된다.

| HTTP | code | 의미 |
|---|---|---|
| 400 | INVALID_REASON | 유형 누락/null, OTHER 상세 없음, 정규화 후 500 코드 포인트 초과 |
| 400 | INVALID_REQUEST_BODY | 본문 없음, JSON null, 잘못된 JSON, 알 수 없거나 소문자인 enum 문자열 등 읽을 수 없는 본문 |
| 400 | INVALID_REQUEST_PARAMETER | 경로 ID를 Long으로 변환할 수 없음(문자열, 범위 초과 등) |
| 401 | AUTHENTICATION_REQUIRED | 비로그인, 현재 ACTIVE가 아님, 인증 버전/권한과 저장된 세션이 불일치하는 등 유효한 인증 없음 |
| 403 | SELF_REPORT_NOT_ALLOWED | 자신이 작성한 대상 신고 |
| 403 | CSRF_TOKEN_INVALID | 토큰 누락·불일치·다른 세션의 토큰 |
| 403 | ACCESS_DENIED | 공통 보안 접근 거부. 신고에 관리자 권한을 요구한다는 뜻은 아님 |
| 404 | POST_NOT_FOUND | 공개 부모 게시글 없음, 숨김/삭제, 0 이하 게시글 ID |
| 404 | COMMENT_NOT_FOUND | 공개 댓글 없음, 숨김/삭제, 부모 불일치, 0 이하 댓글 ID |
| 409 | REPORT_ALREADY_EXISTS | 동일 신고자의 같은 대상 신고가 이미 존재 |
| 415 | HTTP_ERROR | 지원하지 않는 Content-Type 등 MVC 미디어 타입 오류 |

- **파싱 오류와 사유 오류는 다르다.** `{}`는 파싱되지만 reasonType이 null이라 서비스에서 INVALID_REASON, 잘못된 enum 문자열은 서비스 호출 전에 INVALID_REQUEST_BODY다.
- 댓글 신고에서 부모 게시글을 먼저 검증하므로 부모가 거부되면 COMMENT_NOT_FOUND 대신 POST_NOT_FOUND일 수 있다. 숫자 0/음수 ID는 바인딩 실패가 아니라 서비스 not-found 경로다.
- CSRF 검사가 회원 세션 상태 및 인가 검사보다 먼저 실패할 수 있다. 비로그인·오래된 세션에 CSRF 문제도 있으면 401 대신 403이 먼저 반환될 수 있다. 403만으로 로그인 만료라고 단정하지 않는다.
- 405/415 등 `GlobalExceptionHandler.handleExceptionInternal`을 거치는 MVC 공통 오류는 상태 코드를 유지하면서 `HTTP_ERROR`와 고정 메시지를 반환한다. 모든 5xx 또는 CORS/필터 외 오류까지 이 형식이라고 보장하지 않는다.
- dev CORS는 설정된 허용 Origin, credentials, POST/OPTIONS, Accept/Content-Type/X-CSRF-TOKEN을 사용한다. **CORS 거부의 본문이 ApiErrorResponse JSON이라는 보장은 없다.** 다른 프로필의 배포 CORS 정책을 dev 설정으로 추정하지 않는다.

서비스 사유 검증 실패 예시:

```json
{
  "code": "INVALID_REASON",
  "message": "신고 사유를 확인해 주세요. 상세 설명은 500자 이하여야 하며, 기타 사유는 상세 설명이 필요합니다.",
  "fieldErrors": []
}
```

JSON 파싱 실패 예시:

```json
{
  "code": "INVALID_REQUEST_BODY",
  "message": "요청 본문의 JSON 형식과 필드 값을 확인해 주세요.",
  "fieldErrors": []
}
```

중복 신고 예시:

```json
{
  "code": "REPORT_ALREADY_EXISTS",
  "message": "이미 신고한 콘텐츠입니다.",
  "fieldErrors": []
}
```

오류 메시지는 처리 지점에 따라 다를 수 있다(인증 필터와 서비스 등). 프론트 분기는 HTTP 상태와 code를 기준으로 한다. 입력 원문·내부 예외·스택 트레이스를 응답 예시나 UI에 노출하지 않는다.

## 프론트 연결

1. 세션 쿠키를 포함한다(교차 출처 fetch는 `credentials: "include"`). 로그인 후 최신 CSRF 토큰을 같은 세션에서 받는다.
2. 요청은 reasonType/reasonDetail만 생성한다. 신고자·인증 버전·콘텐츠 스냅샷은 생성하지 않는다.
3. 제출 중 중복 클릭을 막는다. 네트워크 오류만으로 미접수라고 확정하거나 자동 재전송하지 않는다.
4. 201은 “신고가 접수되었습니다.”로 안내한다. 접수만으로 대상 공개 상태를 숨김으로 바꾸거나 제재 완료로 표현하지 않는다.
5. 409는 이번 요청 성공과 구분해 “이미 신고한 콘텐츠입니다.”로 안내한다.
6. 401은 기존 인증 상태·화면 정책에 따라 처리한다. 403은 CSRF/자기 신고/권한 거부를 code로 구분한다.
7. 현재 취소·수정 기능이 없음을 고려해 실제 정책과 맞는 확인 문구를 제공한다.

## 검증과 문서 생성

```sh
cd server/iyum-api
./gradlew compileJava
./gradlew test --tests 'io.github.takgeun.iyum.community.report.*'
./gradlew test --tests '*ContentReportOpenApiDocumentationTest' \
  --tests '*OpenApiDocumentationTest' --tests '*OpenApiDisabledTest'
```

`ContentReportOpenApiDocumentationTest`는 기존 springdoc MVC 테스트 구성을 사용해 실제 생성 `/v3/api-docs`의 두 경로, CSRF 헤더, principal 비노출, DTO 타입·필수/nullable 구분, 201·오류 예시를 확인한다. JSON은 `server/iyum-api/build/reports/content-report-openapi.json`에 생성된다. springdoc 라이브러리는 3.1.1, 생성 규격은 OpenAPI 3.1.0이며 서로 다른 버전이다.
