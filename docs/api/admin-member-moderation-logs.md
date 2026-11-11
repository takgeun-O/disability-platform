# 관리자 회원별 이용 정지·해제 이력 조회

GET `/api/v1/admin/members/{memberId}/moderation-logs`

현재 ACTIVE ADMIN의 유효한 세션 쿠키가 필요하며 GET에는 CSRF 토큰이 불필요하다. [정지·해제 API](admin-member-moderation.md)가 저장한 특정 회원의 이력만 반환한다. 게시글·댓글 운영 기록과 다른 테이블·API다. 이력 수정·삭제 API는 없다.

## 조건

| 파라미터 | 타입 | 기본값/범위 |
|---|---|---|
| memberId | path integer/int64 | 존재하는 회원 ID |
| page | query integer/int32 | 0, 0 이상 |
| size | query integer/int32 | 20, 1~50 |

page/size는 생략 또는 빈 문자열이면 기본값을 적용한다. page × size ≤ Integer.MAX_VALUE. sort는 지원하지 않으며 전체 대상 결과를 createdAt DESC → id DESC로 정렬한 다음 페이지를 적용한다.

회원 행이 있으면 현재 상태·역할 제한 없이 조회한다. 탈퇴 완료 회원 및 관리자 자신도 가능하다. 정지 실행의 대상 제한을 적용하지 않는다. 회원 존재·이력 없음은 200/빈 items, 회원 없음은 404다. 범위 밖 페이지도 200이며 totalElements/totalPages를 유지한다. hasPrevious는 page > 0이면 true(이전 페이지에 데이터가 있음을 보장하는 값은 아님), hasNext는 다음 페이지 존재 여부다.

## 응답

200, Cache-Control: no-store. 필드는 모두 존재한다.

| 필드 | 타입/의미 |
|---|---|
| items | 아래 항목 배열 |
| page, size, totalPages | integer/int32 |
| totalElements | integer/int64, 해당 회원 전체 이력 개수 |
| hasNext, hasPrevious | boolean |

항목: logId, actorMemberId, targetMemberId는 integer/int64. action은 SUSPEND 또는 LIFT_SUSPENSION. beforeStatus/afterStatus는 저장된 조치 당시 상태로 SUSPEND이면 ACTIVE→SUSPENDED, LIFT_SUSPENSION이면 SUSPENDED→ACTIVE다. 현재 회원 상태가 아니다. reason은 정규화하여 저장했던 값 그대로 반환한다. createdAt은 UTC ISO 8601이며 화면에서 Asia/Seoul로 변환한다. 이메일·비밀번호 해시·credentialVersion·관리자 닉네임은 포함하지 않는다.

```json
{
  "items": [
    {
      "logId": 102,
      "actorMemberId": 40,
      "targetMemberId": 20,
      "action": "LIFT_SUSPENSION",
      "beforeStatus": "SUSPENDED",
      "afterStatus": "ACTIVE",
      "reason": "재검토 후 이용 정지 해제",
      "createdAt": "2026-11-12T01:00:00Z"
    },
    {
      "logId": 101,
      "actorMemberId": 40,
      "targetMemberId": 20,
      "action": "SUSPEND",
      "beforeStatus": "ACTIVE",
      "afterStatus": "SUSPENDED",
      "reason": "반복적인 이용 규칙 위반",
      "createdAt": "2026-11-12T00:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1,
  "hasNext": false,
  "hasPrevious": false
}
```
```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false,
  "hasPrevious": false
}
```

## 오류·순서

| HTTP | code | 조건 |
|---|---|---|
| 400 | INVALID_REQUEST_PARAMETER | memberId의 비숫자/Long 초과, page/size의 비숫자/int 초과 |
| 400 | INVALID_PAGE_REQUEST | page<0, size 범위 또는 offset 초과 |
| 401 | AUTHENTICATION_REQUIRED | 인증 없음·현재 관리자 상태/인증 버전 무효 |
| 403 | ACCESS_DENIED | Security 권한 검사 |
| 403 | ADMIN_REQUIRED | 서비스 현재 역할 검사 |
| 404 | MEMBER_NOT_FOUND | 회원 없음, 0·음수 memberId |

Security/MVC 바인딩 이후 서비스는 요청 관리자 → 페이지 범위 → 대상 존재 → 조회 순서다. 따라서 없는 대상과 잘못된 페이지가 함께 있으면 페이지 오류가 먼저 나올 수 있다. 서비스 오류/성공의 명시적 no-store, Security/MVC 오류의 기본 캐시 헤더, CORS의 비JSON 가능성은 [공통 설명](admin-member-moderation.md#오류)을 따른다. dev CORS는 허용 Origin의 GET/OPTIONS만 허용하며 이 경로의 변경 메서드 preflight는 허용하지 않는다.

## 프론트

쿠키를 포함한다. 회원 전환 시 page=0, 이전 회원 요청 소유권을 무효화해 늦은 응답을 버린다. 정지·해제 후 최신 회원 상태와 이력을 재조회한다. 인증 만료 시 기존 관리자 데이터를 제거한다. 저장된 beforeStatus/afterStatus와 사유를 현재 상태로 다시 계산하지 않는다.
