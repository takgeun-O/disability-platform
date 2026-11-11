# 관리자 회원 이용 정지·해제 API

현재 작업 트리 기준(2026-11-12). [회원 조회](admin-member-read.md) 및 [회원별 조치 이력](admin-member-moderation-logs.md)과 함께 사용한다. 게시글·댓글 숨김/복원 및 `/admin/operation-logs`와 별도 기능이다.

## 요청과 인증

| 메서드·경로 | 전환 | 이미 목표 상태인 경우 |
|---|---|---|
| POST `/api/v1/admin/members/{memberId}/suspend` | ACTIVE → SUSPENDED | SUSPENDED이면 changed=false |
| POST `/api/v1/admin/members/{memberId}/lift-suspension` | SUSPENDED → ACTIVE | ACTIVE이면 changed=false |

Content-Type: application/json. 현재 ACTIVE인 ADMIN의 유효한 로그인 세션 필요. 세션 쿠키를 포함하고 동일 세션에서 GET `/api/v1/auth/csrf`로 발급한 `X-CSRF-TOKEN`을 전달한다. 로그인 성공 시 세션/CSRF가 변경되므로 토큰을 다시 조회한다. 처리 관리자 ID와 credentialVersion은 로그인 principal에서 가져온다. 클라이언트가 body/query로 처리 관리자를 지정하는 계약은 없다.

```json
{
  "reason": "반복적인 이용 규칙 위반"
}
```

`reason`은 정지와 해제 모두 필수 자유 입력 문자열이다. 게시글·댓글의 reasonType/reasonDetail 계약을 사용하지 않는다.

- null 또는 Java `String.isBlank()`이면 INVALID_REASON.
- `String.strip()`으로 앞뒤 `Character.isWhitespace` 공백을 제거한 뒤 코드 포인트 수를 검사한다. NBSP(U+00A0)는 이 공백 정의에 포함되지 않는다. 내부 공백/줄바꿈은 합치지 않는다.
- 정규화 후 1~500 Unicode 코드 포인트. UTF-16 코드 단위나 grapheme cluster/화면 글자 수가 아니다. 보조 평면 이모지 하나는 코드 포인트 1개지만 복합 이모지는 여러 개일 수 있다.
- 빈 JSON 객체 또는 reason:null도 사유 검증 실패. 본문 누락·읽을 수 없는 JSON은 별도 INVALID_REQUEST_BODY.

## 대상·처리 정책

USER 역할이며 ACTIVE 또는 SUSPENDED만 허용한다. 자기 자신, ADMIN, PENDING/RESTRICTED/WITHDRAWN/DELETED는 거부한다. 프로필·이메일·닉네임·역할 변경, 정지 기간 지정, 자동 해제 기능은 없다. 게시글·댓글을 자동 숨김/삭제하지 않는다.

changed=false여도 관리자·대상·사유 검증을 생략하지 않는다. 실제 변경이 없으면 상태·credentialVersion·updatedAt·이력이 바뀌지 않는다(기존 서비스 통합 테스트의 반복 요청 검증). 실제 전환은 credentialVersion을 1 증가시키고 상태 변경과 이력 저장을 같은 트랜잭션으로 처리한다. 저장 실패 시 함께 롤백한다. 관리자와 대상 행을 ID 오름차순으로 비관적 잠금 조회하므로 같은 대상의 동시 요청은 직렬화되고 같은 상태로의 중복 기록을 방지한다. 별도 idempotency key API가 아니다.

서비스 검증 순서: 인증 입력 형식 → 관리자/대상 잠금 조회 → 현재 관리자 상태·인증 버전·역할 → 사유 → 대상 ID → 자기 자신 → 대상 존재 → 대상 역할 → 대상 상태 → 전환/기록. MVC 바인딩과 Security 검사는 서비스보다 먼저 수행된다. 복수 오류에서 첫 오류는 이 순서에 따라 달라진다.

## 응답

HTTP 200, `Cache-Control: no-store`. 모든 필드가 존재한다.

| 필드 | 타입 | 의미 |
|---|---|---|
| memberId | integer/int64 | 대상 회원 ID |
| status | string | 처리 후 ACTIVE 또는 SUSPENDED |
| changed | boolean | 이번 요청의 실제 상태 전환 여부 |

```json
{
  "memberId": 20,
  "status": "SUSPENDED",
  "changed": true
}
```
```json
{
  "memberId": 20,
  "status": "ACTIVE",
  "changed": true
}
```
```json
{
  "memberId": 20,
  "status": "SUSPENDED",
  "changed": false
}
```

해제 반복 요청은 ACTIVE/false다. 인증 버전 및 회원 개인정보는 응답하지 않는다.

## 로그인·기존 세션

정지된 회원의 기존 세션은 다음 보호 API 요청에서 회원 상태/인증 버전 검사를 통해 인증이 정리되어 `401 AUTHENTICATION_REQUIRED`로 차단된다. POST의 CSRF가 잘못되면 그보다 앞서 `403 CSRF_TOKEN_INVALID`가 나올 수 있다. 정지 회원이 올바른 비밀번호로 신규 로그인하면 `403 ACCOUNT_UNAVAILABLE`다. 해제 시에도 인증 버전이 증가하므로 정지 중 전혀 사용하지 않은 기존 세션까지 재사용할 수 없다. 새 로그인이 필요하다. 브라우저를 즉시 닫거나 푸시 로그아웃하는 기능은 아니다.

## 오류

일반 JSON 형식: 

```json
{
  "code": "INVALID_REASON",
  "message": "처리 사유는 필수이며, 앞뒤 공백을 제거한 뒤 500자 이하여야 합니다.",
  "fieldErrors": []
}
```

| HTTP | code | 조건 |
|---|---|---|
| 400 | INVALID_REQUEST_PARAMETER | memberId 숫자 변환 불가 또는 Long 범위 초과 |
| 400 | INVALID_REQUEST_BODY | 누락/읽을 수 없는 JSON 본문 |
| 400 | INVALID_REASON | 사유 null·공백·정규화 후 500 코드 포인트 초과 |
| 401 | AUTHENTICATION_REQUIRED | 인증 없음, 현재 상태/인증 버전 불일치 |
| 403 | ACCESS_DENIED | Security 관리자 권한 검사 실패 |
| 403 | ADMIN_REQUIRED | 서비스의 현재 관리자 역할 검사 실패 |
| 403 | CSRF_TOKEN_INVALID | CSRF 누락·불일치 |
| 403 | SELF_MODERATION_NOT_ALLOWED | 자기 자신 |
| 403 | ADMIN_TARGET_NOT_ALLOWED | USER가 아닌 대상 |
| 404 | MEMBER_NOT_FOUND | 없는 회원 또는 0·음수 ID, 앞선 검증 통과 시 |
| 409 | MEMBER_STATUS_NOT_ALLOWED | 허용하지 않는 대상 상태 |

서비스 예외는 GlobalExceptionHandler가 명시적으로 no-store를 설정한다. 성공도 명시적 no-store. Security 오류 및 MVC 바인딩 오류는 기본 Security 캐시 헤더(`no-cache, no-store, max-age=0, must-revalidate`)가 적용될 수 있으므로 전체 문자열 일치를 가정하지 않는다. 바인딩 오류 처리기 자체가 no-store를 명시하는 것은 아니다. CORS 거부는 JSON ApiErrorResponse를 보장하지 않는다. dev CORS는 설정된 Origin, 쿠키, POST/OPTIONS와 허용 헤더만 지원하며 비허용 Origin은 거부한다.

## 프론트 연동

fetch credentials:'include'와 현재 CSRF 토큰을 사용한다. reason 검증 후 서버 오류를 표시하되 통신 실패에 POST를 자동 재전송하지 않는다. changed=true 뒤 회원 상태와 이력을 재조회한다. changed=false도 서버 응답/재조회 최신 상태로 동기화한다. 인증 만료 시 이전 관리자 데이터를 제거한다. 회원 전환 시 이력 page=0, 이전 회원의 늦은 응답을 무효화한다. 입력 보존·중복 실행 차단은 UI에서 처리하며 회원 이력을 게시글·댓글 운영 기록과 섞지 않는다.
