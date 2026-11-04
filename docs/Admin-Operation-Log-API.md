# 관리자 운영 기록 조회 API

## 요청

GET /api/v1/admin/operation-logs

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

날짜 입력 범위는 0001-01-01부터 9999-12-31까지다.
시작일과 종료일은 각각 생략할 수 있다.
둘 다 지정하면 시작일은 종료일보다 늦을 수 없다.

시작일은 한국 시간 00:00 이상,
종료일은 다음 날 한국 시간 00:00 미만으로 조회한다.

정렬은 createdAt 내림차순, 같은 시각이면 logId 내림차순이다.
page × size는 Integer.MAX_VALUE 이하여야 한다.

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
| reason | 저장된 처리 사유 |
| createdAt | 처리 시각, UTC ISO 8601 |

createdAt은 프론트에서 Asia/Seoul 기준으로 표시한다.
관리자 닉네임과 콘텐츠 제목·본문은 이번 응답에 포함하지 않는다.
COMMENT의 targetId는 댓글 ID이며 부모 게시글 ID가 아니다.

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
- 필터 변경 후 조회 시 page를 0으로 초기화한다.
- 날짜 입력값을 UTC 날짜로 바꾸지 않고 YYYY-MM-DD로 전달한다.
- 새 검색 이후 도착한 이전 검색 응답이 화면을 덮어쓰지 않게 한다.
- 인증 상태가 변경되면 이전 관리자 조회 결과를 표시하지 않는다.
- 서버가 반환한 정렬 및 전체 개수를 사용한다.
- 운영 기록 수정·삭제 기능은 제공하지 않는다.