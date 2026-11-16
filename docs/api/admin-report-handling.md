# 관리자 신고 숨김·처리 완료·기각 API

[신고 접수](content-report-create.md) · [관리자 목록·상세 조회](admin-report-read.md)

## 목적과 정책

- `POST /api/v1/admin/reports/{reportId}/hide-and-resolve`: 콘텐츠 숨김과 함께 RECEIVED → RESOLVED.
- `POST /api/v1/admin/reports/{reportId}/resolve`: 추가 콘텐츠 조치 없이 RECEIVED → RESOLVED, `NO_CONTENT_ACTION`.
- `POST /api/v1/admin/reports/{reportId}/dismiss`: RECEIVED → DISMISSED(기각), `NO_CONTENT_ACTION`. 이미 숨겨진 콘텐츠를 복원하지 않는다.
- 해당 신고 **한 건**을 종결한다. 같은 대상의 다른 신고를 일괄 처리하지 않는다.
- 신고 **접수**는 콘텐츠를 자동 숨김·삭제하지 않는다. resolve·dismiss도 공개 상태를 바꾸지 않는다. hide-and-resolve만 대상 콘텐츠 숨김을 수행한다. 어느 처리도 회원 상태를 바꾸지 않는다.
- hide-and-resolve에서 정상 대상을 새로 숨기면 `CONTENT_HIDDEN`과 숨김 운영 기록 한 건을 저장한다. 이미 개별 숨김 상태이면 `CONTENT_ALREADY_HIDDEN`으로 종결하며 기존 숨김 시각을 유지하고 운영 기록을 추가하지 않는다. 새 운영 기록의 사유 유형은 `OTHER`, 상세는 정규화된 처리 사유다.
- 콘텐츠 숨김·운영 기록 저장·신고 종결은 하나의 트랜잭션이며 실패하면 일부 변경만 남지 않는다. 댓글 신고는 해당 댓글만 숨긴다. 부모 게시글 숨김은 허용하지만 대상 게시글·댓글 또는 댓글 부모가 삭제된 경우 거부한다.
- RECEIVED만 처리 가능하다. 이미 RESOLVED 또는 DISMISSED라면 동일 처리 재시도·다른 처리 모두 `409 REPORT_ALREADY_HANDLED`이며 기존 처리 정보를 덮어쓰지 않는다.
- 기존 중복 신고 정책은 유지한다. 종결된 신고가 있어도 같은 신고자·같은 대상의 재신고를 허용하지 않는다.

## 인증과 요청

현재 ACTIVE·ADMIN인 회원의 유효한 로그인 세션 및 같은 세션의 최신 `X-CSRF-TOKEN`이 필요하다. 로그인 후 `GET /api/v1/auth/csrf`로 발급받은 토큰과 세션 쿠키를 함께 보낸다. Bearer 인증이 아니다. 로그인 이후 회원 상태·권한·credentialVersion 변경 시 기존 세션이 차단될 수 있다. 서비스도 현재 관리자 자격을 확인한다.

`Content-Type: application/json`. reportId는 int64 신고 ID이며 게시글·댓글 ID가 아니다. 0·음수·없는 신고 ID는 404, 숫자 변환 실패·Long 범위 초과는 400이다.

```json
{
  "handlingReason": "검토를 완료했습니다."
}
```

| 필드 | 타입 | 정책 |
| --- | --- | --- |
| handlingReason | string | 필수. 정규화 이후 1~500 Unicode 코드 포인트 |

`ContentReportHandlingPolicy`는 `UNICODE_CHARACTER_CLASS`의 `\s`를 사용해 **앞뒤** 공백을 제거한다. NBSP·전각 공백도 포함한다. 내부 공백과 줄바꿈은 그대로 보존한다. UTF-16 길이나 사용자에게 보이는 복합 이모지 개수 기준이 아니다. 원문에 대한 maxLength 제한이 아니며, 신고 접수 상세 사유의 String.strip()과 구분한다.

필드 생략·null·공백뿐·500 코드 포인트 초과는 서비스에서 INVALID_HANDLING_REASON이다. 읽을 수 없는 JSON/본문은 INVALID_REQUEST_BODY다. 추가 필드는 현재 JSON 설정상 무시되며, 요청에 관리자·처리 시각·상태·handlingOutcome을 넣어 서버 값을 지정할 수 없다. 처리 관리자 ID·인증 버전은 세션에서, 처리 시각은 서버 시계에서 결정한다.

검사 순서는 관리자 자격 → 신고 조회 → RECEIVED 확인 → 사유 검증이다. 종결 신고에 잘못된 사유까지 보내면 사유 오류보다 409가 먼저다. 보안 필터·JSON 바인딩 오류는 서비스보다 먼저 발생할 수 있다.

## 성공 응답

세 API 모두 **200 OK**, `Cache-Control: no-store`. 저장된 값을 반환한다.

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| reportId | int64 | 처리한 신고 ID |
| status | RESOLVED 또는 DISMISSED | 처리 후 신고 상태 |
| handledByMemberId | int64 | 처리 관리자 회원 ID |
| handledAt | string(date-time) | 서버가 결정해 저장한 처리 시각, UTC ISO 8601 |
| handlingReason | string | 정규화하여 저장한 처리 사유 |
| handlingOutcome | string enum | 필수·non-null. CONTENT_HIDDEN, CONTENT_ALREADY_HIDDEN, NO_CONTENT_ACTION |

총 6개 필드를 반환한다. 신규 처리 응답은 LEGACY_UNKNOWN을 반환하지 않는다. 처리 결과는 저장된 이력이며 현재 hiddenAt으로 계산하지 않는다. 이후 복원·삭제되어도 유지된다.

### 처리 완료

```json
{
  "reportId": 102,
  "status": "RESOLVED",
  "handledByMemberId": 40,
  "handledAt": "2026-11-13T01:00:00Z",
  "handlingReason": "검토를 완료했습니다.",
  "handlingOutcome": "NO_CONTENT_ACTION"
}
```

### 기각

```json
{
  "reportId": 102,
  "status": "DISMISSED",
  "handledByMemberId": 40,
  "handledAt": "2026-11-13T01:00:00Z",
  "handlingReason": "검토 결과 신고를 기각했습니다.",
  "handlingOutcome": "NO_CONTENT_ACTION"
}
```

두 예시는 서로 다른 실행 상황이며 동일 신고에 순서대로 적용하는 예시가 아니다.

### 숨김·처리 완료

아래 두 예시는 독립된 요청의 결과다.

```json
{
  "reportId": 105,
  "status": "RESOLVED",
  "handledByMemberId": 40,
  "handledAt": "2026-11-13T01:00:00Z",
  "handlingReason": "운영 정책 위반을 확인하여 숨김을 적용합니다.",
  "handlingOutcome": "CONTENT_HIDDEN"
}
```

```json
{
  "reportId": 105,
  "status": "RESOLVED",
  "handledByMemberId": 40,
  "handledAt": "2026-11-13T01:00:00Z",
  "handlingReason": "운영 정책 위반을 확인하여 숨김을 적용합니다.",
  "handlingOutcome": "CONTENT_ALREADY_HIDDEN"
}
```

## 오류

| HTTP | code | 발생 조건 |
| --- | --- | --- |
| 400 | INVALID_HANDLING_REASON | 필수 사유 누락·null·정규화 후 공란·500 코드 포인트 초과 |
| 400 | INVALID_REQUEST_BODY | 본문 없음 또는 JSON 파싱/바인딩 오류 |
| 400 | INVALID_REQUEST_PARAMETER | reportId 숫자 변환 실패 또는 Long 범위 초과 |
| 401 | AUTHENTICATION_REQUIRED | 유효한 관리자 로그인 정보 없음·현재 회원 상태/인증 버전 불일치 |
| 403 | ACCESS_DENIED | Security의 권한 거부 |
| 403 | ADMIN_REQUIRED | 서비스의 현재 관리자 권한 검증 실패 |
| 403 | CSRF_TOKEN_INVALID | CSRF 토큰 누락·불일치 |
| 404 | REPORT_NOT_FOUND | 0·음수·존재하지 않는 신고 ID |
| 409 | REPORT_ALREADY_HANDLED | 이미 처리 완료·기각된 신고 |
| 404 | POST_NOT_FOUND | hide-and-resolve 대상 게시글 또는 댓글 부모 게시글 없음 |
| 404 | COMMENT_NOT_FOUND | hide-and-resolve 대상 댓글 없음 |
| 409 | POST_DELETED | hide-and-resolve 대상 게시글 또는 댓글 부모 게시글 삭제 |
| 409 | COMMENT_DELETED | hide-and-resolve 대상 댓글 삭제 |
| 415 | HTTP_ERROR | 지원하지 않는 Content-Type |

```json
{
  "code": "REPORT_ALREADY_HANDLED",
  "message": "이미 처리된 신고입니다.",
  "fieldErrors": []
}
```

비로그인·오래된 세션에 CSRF 오류도 있으면 필터 순서상 403이 먼저일 수 있다. 서비스 오류는 no-store를 적용하며 모든 보안/MVC 오류가 정확히 같은 Cache-Control 문자열을 반환한다고 보장하지 않는다. 개발 CORS는 허용된 Origin에 POST·OPTIONS와 자격 증명 전달을 허용한다. CORS 거부 응답을 ApiErrorResponse JSON으로 보장하지 않는다.

## 상세 조회와 프론트 연결

상세 조회는 저장된 처리 정보 4개(handledByMemberId, handledAt, handlingReason, handlingOutcome)를 항상 포함한다. RECEIVED는 모두 null, RESOLVED/DISMISSED는 저장된 값이다. 신고자(reporterMemberId), 처리 관리자(handledByMemberId), 현재 작성자(currentTarget.author)는 별개다. createdAt은 접수 시각, handledAt은 처리 시각이다.

현재 대상이 없더라도 신고 스냅샷과 저장된 처리 정보는 유지되며 currentTarget만 null이다. 목록 응답에는 이 처리 정보 4개가 없다. 과거 처리 기록의 handlingOutcome=LEGACY_UNKNOWN은 당시 콘텐츠 조치 여부를 확정할 수 없다는 뜻이며 NO_CONTENT_ACTION과 다르다. 상태별 처리 정보 조합은 도메인·DB 제약 및 통합 테스트의 계약이며 OpenAPI의 required/nullable만으로 상태별 조합까지 강제하는 것은 아니다.

제출 중 중복 클릭을 차단하고, 응답 유실 시 POST를 자동 반복하지 않는다. 상세 재조회로 현재 저장 상태를 확인한다. 409를 새 처리 성공으로 오인하지 않는다. resolve·dismiss 성공을 숨김 성공으로 해석하지 않는다. hide-and-resolve 결과도 최신 대상 상태와 저장된 처리 이력을 구분하여 표시한다. 어떤 응답도 회원 제재를 뜻하지 않는다.
