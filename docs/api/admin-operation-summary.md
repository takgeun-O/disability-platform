# 관리자 운영 기록 요약 API

## 목적과 범위

`GET /api/v1/admin/operation-logs/summary`

운영 기록 목록과 같은 `ContentModerationLog`의 게시글·댓글 숨김(HIDE)·숨김 해제(RESTORE) 기록을 집계한다. 콘텐츠 수가 아닌 **저장된 기록 건수**다. 동일 게시글을 오늘 숨김 → 해제 → 숨김했다면 오늘 기록 3건, 숨김 2건, 해제 1건이다.

목록의 대상·조치·사유·날짜·정렬·페이지와 독립적이다. LEGACY 기록을 포함하며 현재 콘텐츠의 복원·삭제나 작성자 탈퇴로 과거 기록을 제외하거나 재분류하지 않는다. 대상·회원 조인 없이 로그 테이블을 직접 count한다.

회원 정지·해제(MemberModerationLog)와 신고 처리 완료·기각 자체는 제외한다. hide-and-resolve는 실제 콘텐츠 숨김으로 저장된 HIDE 기록만 포함한다. 이미 숨겨진 콘텐츠 유지, resolve/dismiss, 중복 조치, 실패·롤백은 기존 정책상 새 콘텐츠 기록이 없으면 증가하지 않는다. 이 API는 조치나 로그를 생성하지 않는다.

관련 API: [운영 기록 조회](../Admin-Operation-Log-API.md), [신고 처리](admin-report-handling.md).

## 인증과 요청

- 유효한 관리자 로그인 세션 쿠키가 필요하다. GET에 CSRF 토큰은 요구하지 않는다.
- 현재 회원의 ACTIVE 상태·credentialVersion·ADMIN 역할을 기존 관리자 조회와 동일하게 확인한다. 로그인 후 상태/역할/인증 버전 변경으로 기존 세션이 거부될 수 있다.
- 요청 쿼리 파라미터와 본문은 없다. 목록 조건을 전달하지 않는다.
- 성공: HTTP 200, `Content-Type: application/json`, `Cache-Control: no-store`.

## 응답

모든 필드는 필수이며 null이 아니다. 수치는 long, OpenAPI integer/int64, minimum 0이며 대상이 없으면 0이다.

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| queriedAt | string(date-time) | Clock에서 한 번 얻은 UTC ISO 8601 조회 기준 시각 |
| baseDate | string(date) | queriedAt의 Asia/Seoul 날짜, YYYY-MM-DD |
| counts | object | 아래 네 수치 |
| counts.totalOperationLogCount | integer(int64) | 전체 기간 콘텐츠 숨김·해제 기록 수 |
| counts.todayOperationLogCount | integer(int64) | 한국 날짜 기준 오늘 생성된 콘텐츠 운영 기록 수 |
| counts.todayHideCount | integer(int64) | 오늘 생성된 HIDE 기록 수 |
| counts.todayRestoreCount | integer(int64) | 오늘 생성된 RESTORE 기록 수 |

오늘은 ContentModerationLog.createdAt이 한국 날짜 시작 **이상**, 다음 날짜 시작 **미만**인 반개구간이다. 현재 콘텐츠 hiddenAt이나 신고 handledAt을 사용하지 않는다.

```json
{
  "queriedAt": "2026-11-18T03:00:00Z",
  "baseDate": "2026-11-18",
  "counts": {
    "totalOperationLogCount": 20,
    "todayOperationLogCount": 7,
    "todayHideCount": 3,
    "todayRestoreCount": 4
  }
}
```

기록이 없는 경우:

```json
{
  "queriedAt": "2026-11-18T03:00:00Z",
  "baseDate": "2026-11-18",
  "counts": {
    "totalOperationLogCount": 0,
    "todayOperationLogCount": 0,
    "todayHideCount": 0,
    "todayRestoreCount": 0
  }
}
```

기준 시각은 날짜 경계 계산의 기준이지 동일 DB 스냅샷 보장이 아니다. 독립된 count 조회 사이에 동시 조치가 발생할 수 있다. 프론트는 오늘 전체와 오늘 숨김+해제의 등식을 강제하거나, 등식 불일치만으로 응답을 거부하지 않는다. 목록 결과와 요약이 동일 스냅샷이라고 가정하지 않는다.

## 오류와 개발 CORS

| HTTP | code | 조건 |
| --- | --- | --- |
| 401 | AUTHENTICATION_REQUIRED | 비로그인, 없는 회원, 유효하지 않은 상태/인증 버전 등 |
| 403 | ACCESS_DENIED | Security의 관리자 권한 거부 |
| 403 | ADMIN_REQUIRED | 서비스에서 현재 관리자 역할 검증 실패 |

```json
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "유효한 로그인 정보가 필요합니다.",
  "fieldErrors": []
}
```

오류 message는 거부한 계층에 따라 다를 수 있다. 보안 필터와 MVC 오류 모두 no-store를 포함하되 전체 Cache-Control 문자열이 같다고 보장하지 않는다. 개발 프로필에서는 설정된 Origin에 세션 쿠키를 포함한 GET/OPTIONS를 허용한다. CORS 거부 응답이 ApiErrorResponse JSON이라고 보장하지 않는다. 새 Bearer 인증을 추가하지 않는다.

## 프론트 연결

- 전체 운영 기록 / 오늘 운영 기록 / 오늘 숨김 / 오늘 숨김 해제, 단위 건.
- 목록 필터와 독립된 집계이며 카드 클릭 동작은 없다.
- baseDate는 날짜 문자열 그대로, queriedAt은 한국 시간으로 표시한다.
- 화면 진입·요약 수동 새로고침·운영 패널 숨김/해제 성공 시 서버 값을 재조회한다. 클라이언트 임의 증감을 하지 않는다.
- 최초 로딩/실패와 실제 0을 구분한다. 재조회 실패 시 이전 값과 기준 시각 및 실패 안내를 유지한다.
- 요약 실패를 조치 실패로 표시하거나 성공한 POST를 재전송하지 않는다.

## 검증

AdminOperationSummaryReadRepositoryTest는 PostgreSQL에서 빈 집계·반복/LEGACY·한국 날짜 경계·대상 변경 독립성을 확인한다. 서비스 테스트는 Clock 1회 및 인증/권한, Controller 테스트는 매핑·CORS·캐시를 검증한다. 실제 로그인 세션+PostgreSQL 통합 테스트는 숨김/중복 숨김/해제/숨김과 실패 요청의 집계 반영을 확인한다. OpenAPI 테스트는 생성 /v3/api-docs를 JSON Schema 2020-12 validator로 검사하며 필수 필드 누락/null·음수·소수·문자열을 거부하고 정상·0·큰 수·오류 예시를 검증한다. 기존 로그 생성·신고 숨김/롤백·운영 기록 조회 회귀도 함께 실행한다.
