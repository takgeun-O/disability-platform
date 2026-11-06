# 관리자 운영 기록 조회 API

## 요청

```
GET /api/v1/admin/operation-logs
GET /api/v1/admin/operation-logs?sort=OLDEST&page=0&size=20
GET /api/v1/admin/operation-logs?targetType=COMMENT&action=HIDE&sort=LATEST
GET /api/v1/admin/operation-logs?startDate=2026-11-01&endDate=2026-11-06&sort=OLDEST
GET /api/v1/admin/operation-logs?reasonType=SPAM_ADVERTISING
GET /api/v1/admin/operation-logs?reasonType=LEGACY&sort=OLDEST
```

관리자 로그인 세션이 필요하다.
GET 조회에는 CSRF 토큰을 요구하지 않는다.

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| targetType | ALL | ALL, POST, COMMENT |
| action | 생략 | 생략하면 전체, HIDE 또는 RESTORE |
| startDate | 생략 | 한국 날짜 기준 시작일 |
| endDate | 생략 | 한국 날짜 기준 종료일, 해당 날짜 전체 포함 |
| page | 0 | 0 이상 |
| size | 20 | 1 이상 50 이하 |
| sort | LATEST | LATEST: 최신순, OLDEST: 오래된순. 생략하거나 빈 문자열이면 LATEST |
| reasonType | 생략 | 처리 사유 유형. 생략하거나 빈 문자열이면 전체 조회. LEGACY 포함 |

날짜 입력 범위는 0001-01-01부터 9999-12-31까지다.
시작일과 종료일은 각각 생략할 수 있다.
둘 다 지정하면 시작일은 종료일보다 늦을 수 없다.

시작일은 한국 시간 00:00 이상,
종료일은 다음 날 한국 시간 00:00 미만으로 조회한다.

정렬 기준은 운영 기록의 처리 시각(createdAt)이다.

- LATEST: createdAt 내림차순 → logId 내림차순
- OLDEST: createdAt 오름차순 → logId 오름차순
- sort를 생략하거나 빈 문자열로 전달하면 LATEST를 적용한다.
- 값은 대문자 LATEST, OLDEST만 허용한다.
- 잘못된 값은 400 INVALID_REQUEST_PARAMETER를 반환한다.
- 서버에서 검색 조건에 맞는 전체 결과를 정렬한 뒤 페이지를 적용한다.
- 정렬은 게시글·댓글의 작성 시각이나 수정 시각을 기준으로 하지 않는다.

page × size는 Integer.MAX_VALUE 이하여야 한다.

### 처리 사유 유형 필터

reasonType은 저장된 사유 유형 코드와 정확히 일치하는 기록을 조회한다.
reason 또는 reasonDetail의 문구를 검색하유는 기능은 아니다.

허용 값:

- SPAM_ADVERTISING: 광고·홍보·스팸
- ABUSE_HATE: 욕설·비방·혐오
- PERSONAL_INFORMATION: 개인정보 노출
- DUPLICATE_FLOODING: 도배·중복 게시
- OFF_TOPIC: 게시판 주제와 무관
- MODERATION_ERROR: 운영 조치 오류
- NO_VIOLATION: 재검토 결과 위반 없음
- OTHER: 기타
- LEGACY: 기존 자유입력 사유

reasonType을 생략하거나 빈 문자열로 전달하면 모든 사유를 조회한다.
전체 조회를 의미하는 ALL 값은 지원하지 않는다.
소문자 값이나 정의되지 않은 값은 400 INVALID_REQUEST_PARAMETER를 반환한다.

targetType, action, reasonType, 날짜 조건은 AND로 결합한다.
신규 조치에서 허용하지 않는 action과 reasonType 조합도 조회 조건으로는 허용한다.
예를 들어 action=RESTORE&reasonType=SPAM_ADVERTISING으로 조회하여
일치하는 기록이 없으면 200과 빈 items를 반환한다.

LEGACY는 조회 필터로 사용할 수 있지만 신규 숨김·복원 요청에는 사용할 수 없다.

전체 개수와 페이지 수에도 동일한 검색 조건을 적용한다.
검색 조건에 맞는 전체 결과를 정렬한 뒤 페이지를 적용한다.

## 응답

- items: 운영 기록 목록
- page, size: 페이지 번호와 크기
- totalElements, totalPages: 검색 조건에 맞는 전체 개수와 페이지 수
- hasNext, hasPrevious: 다음·이전 페이지 여부

각 항목의 필드:

| 필드 | 의미 |
|---|---|
| logId | 운영 기록 ID |
| actorMemberId | 해당 조치를 실행한 관리자 회원 ID |
| targetType | POST 또는 COMMENT |
| targetId | 게시글 ID 또는 댓글 ID |
| action | HIDE 또는 RESTORE |
| reasonType | 저장된 처리 사유 유형. 기존 자유 입력 기록은 LEGACY |
| reasonDetail | 정규화된 상세 사유. 상세가 없거나 기존 자유 입력 기록이면 null |
| reason | 저장된 처리 사유 표시 문구. 기존 필드 유지 |
| createdAt | 처리 시각, UTC ISO 8601 |

createdAt은 프론트에서 Asia/Seoul 기준으로 표시한다.
관리자 닉네임과 콘텐츠 제목·본문은 이번 응답에 포함하지 않는다.
COMMENT의 targetId는 댓글 ID이며 부모 게시글 ID가 아니다.

reasonDetail은 값이 없어도 필드를 생략하지 않고 명시적 null로 반환한다.
기존 자유 입력 기록은 reasonType=LEGACY, reasonDetail=null이며,
reason은 기존 저장 문구를 그대로 유지한다. OTHER로 재분류하지 않는다.
LEGACY는 기존 기록의 조회용 유형이며 새로운 숨김·복원 요청에는 사용할 수 없다.

새 기록의 상세 사유는 저장 시 정규화된다. 상세가 없는 새 기록의 reason에는
생성 당시 유형 표시명이 저장된다. 조회에서는 reason을 현재 유형 표시명으로
다시 계산하지 않고 저장된 값을 그대로 반환한다.
reasonType은 각 기록의 응답 필드이자 선택적 검색 조건이다.
조회 요청에 지정하면 해당 사유 유형의 기록만 반환한다.

조회 결과가 없으면 200과 빈 items를 반환한다.
범위 밖 페이지도 200이며 전체 검색 결과 개수는 유지한다.
성공 응답의 Cache-Control은 no-store다.

## 오류

| HTTP 상태 | 코드 | 의미 |
|---|---|---|
| 400 | INVALID_REQUEST_PARAMETER | 숫자·날짜·enum 변환 실패 |
| 400 | INVALID_PAGE_REQUEST | 페이지 또는 offset 범위 오류 |
| 400 | INVALID_DATE_RANGE | 날짜 범위 또는 날짜 순서 오류 |
| 401 | AUTHENTICATION_REQUIRED | 인증 정보가 없거나 유효하지 않음 |
| 403 | ACCESS_DENIED | Security 필터에서 관리자 접근 거부 |
| 403 | ADMIN_REQUIRED | 서비스에서 관리자 권한 부족 확인 |

프론트는 관리자 접근 거부 시 두 종류의 403 코드를 처리해야 한다.
로그인 후 역할·회원 상태·인증 버전이 변경되면 기존 세션이
401로 차단될 수 있다.

## 프론트 연결 시 유의 사항

- 세션 쿠키를 포함해 요청한다.
- 조치 전체 조회 시 action 파라미터를 생략한다.
- 필터 또는 정렬 변경 시 다른 검색 조건은 유지하고 page를 0으로 초기화한다.
- 현재 페이지의 배열을 뒤집지 않고, 선택한 sort로 서버에 다시 요청한다.
- 필터·정렬·페이지 변경 후 늦게 도착한 이전 요청의 응답이 최신 화면을 덮어쓰지 않도록 처리한다.
- 날짜 입력값을 UTC 날짜로 바꾸지 않고 YYYY-MM-DD로 전달한다.
- 인증 상태가 변경되면 이전 관리자 조회 결과를 표시하지 않는다.
- 목록은 서버가 반환한 순서대로 표시하고, 페이지 계산에는 응답의 전체 개수를 사용한다.
- 운영 기록 수정·삭제 기능은 제공하지 않는다.
- 사유 전체 조회 시 reasonType 파라미터를 생략한다.
- 사유 유형 변경 시 나머지 검색 조건과 정렬을 유지하고 page를 0으로 초기화한다.
- LEGACY는 "기존 자유입력 사유"로 표시하며 OTHER와 구분한다.