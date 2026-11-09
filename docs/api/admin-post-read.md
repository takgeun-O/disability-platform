# 관리자 게시글 조회 API

## 공통 사항

관리자 로그인 세션이 필요하다.
요청에는 로그인 세션 쿠키를 포함한다.

GET 조회에는 CSRF 토큰을 요구하지 않는다.
목록·상세 조회 성공 응답의 Cache-Control은 no-store다.

관리자는 정상·숨김·삭제 게시글을 조회할 수 있다.
관리자 조회가 가능하더라도 일반 공개 조회의 노출 정책은 변경되지 않는다.

---

## 1. 게시글 목록 조회

### 요청

```http
GET /api/v1/admin/posts
GET /api/v1/admin/posts?sort=OLDEST&page=0&size=20
GET /api/v1/admin/posts?status=HIDDEN&keyword=보청기
GET /api/v1/admin/posts?statuses=VISIBLE&statuses=HIDDEN
GET /api/v1/admin/posts?startDate=2026-11-01&endDate=2026-11-07&sort=LATEST
GET /api/v1/admin/posts?statuses=VISIBLE&statuses=HIDDEN&keyword=보청기&startDate=2026-11-01&sort=OLDEST
```

위 예시의 한글 검색어는 실제 요청 시 URL 인코딩하여 전달한다.

### 요청 파라미터

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| status | 생략 시 전체 | 기존 단일 상태 필터. ALL, VISIBLE, HIDDEN, DELETED |
| statuses | 생략 | 다중 상태 필터. VISIBLE, HIDDEN, DELETED를 반복 파라미터로 전달 |
| keyword | 생략 | 게시글 제목 부분 검색 |
| startDate | 생략 | 한국 날짜 기준 작성 시작일. YYYY-MM-DD |
| endDate | 생략 | 한국 날짜 기준 작성 종료일. 해당 날짜 전체 포함 |
| sort | LATEST | LATEST: 최신순, OLDEST: 오래된순 |
| page | 0 | 0 이상인 페이지 번호 |
| size | 20 | 페이지 크기. 1 이상 50 이하 |

### 상태 필터

#### 상태의 의미

| 상태 | 의미 |
|---|---|
| VISIBLE | 삭제되지 않았고 숨김 상태도 아닌 게시글 |
| HIDDEN | 삭제되지 않았으며 숨김 상태인 게시글 |
| DELETED | 삭제된 게시글. 숨김 시각이 남아 있어도 삭제 상태로 분류 |

삭제 상태가 숨김 상태보다 우선한다.

#### 상태 파라미터 생략

status와 statuses를 모두 생략하면 정상·숨김·삭제 게시글을 모두 조회한다.

#### 기존 단일 상태: status

```http
GET /api/v1/admin/posts?status=HIDDEN
```

- ALL, VISIBLE, HIDDEN, DELETED를 사용할 수 있다.
- 생략하거나 빈 문자열로 전달하면 전체 조회다.
- 앞뒤 공백은 제거한 뒤 해석한다.
- 공백 제거 후 빈 값도 전체 조회로 처리한다.
- 소문자 또는 정의되지 않은 값은 허용하지 않는다.
- status를 여러 번 전달하는 요청은 허용하지 않는다.

#### 다중 상태: statuses

```http
GET /api/v1/admin/posts?statuses=VISIBLE&statuses=HIDDEN
```

- VISIBLE, HIDDEN, DELETED만 허용한다.
- 선택한 상태끼리는 OR로 결합한다.
- 같은 상태를 반복해 전달하면 중복을 제거한다.
- 상태를 전달한 순서는 결과 정렬에 영향을 주지 않는다.
- 세 상태를 모두 선택하면 전체 상태 조회와 같다.
- 빈 값, 공백, ALL, 소문자, 정의되지 않은 값은 허용하지 않는다.
- 다중 상태 값의 앞뒤 공백을 자동으로 제거하지 않는다.
- 쉼표로 연결한 문자열은 허용하지 않는다.

허용되는 예:

```http
GET /api/v1/admin/posts?statuses=VISIBLE&statuses=HIDDEN
GET /api/v1/admin/posts?statuses=HIDDEN&statuses=HIDDEN
GET /api/v1/admin/posts?statuses=VISIBLE&statuses=HIDDEN&statuses=DELETED
```

허용되지 않는 예:

```http
GET /api/v1/admin/posts?statuses=
GET /api/v1/admin/posts?statuses=ALL
GET /api/v1/admin/posts?statuses=hidden
GET /api/v1/admin/posts?statuses=VISIBLE,HIDDEN
```

#### 단일·다중 상태 동시 전달 금지

status와 statuses는 동시에 전달할 수 없다.

status 값이 빈 문자열 또는 ALL이어도 두 파라미터가 함께 있으면
400 INVALID_REQUEST_PARAMETER를 반환한다.

```http
GET /api/v1/admin/posts?status=ALL&statuses=HIDDEN
GET /api/v1/admin/posts?status=&statuses=HIDDEN
```

### 제목 검색

keyword는 게시글 제목에 대한 부분 검색이다.
본문이나 작성자 닉네임 검색은 지원하지 않는다.

- 앞뒤 공백은 Java String.strip() 기준으로 제거한다.
- 생략하거나 공백 제거 후 빈 값이면 검색어 조건을 적용하지 않는다.
- 공백 제거 후 최대 100 Unicode 코드 포인트까지 허용한다.
- 검색은 대소문자를 구분하지 않는다.
- %, _, !는 검색어에 포함된 일반 문자로 취급한다.
- 검색어를 LIKE 와일드카드 표현식으로 해석하지 않는다.

최대 길이를 초과하면 400 INVALID_SEARCH_KEYWORD를 반환한다.

### 작성일 범위

작성일 필터는 게시글의 createdAt을 기준으로 한다.
수정 시각, 숨김 시각, 삭제 시각은 기준으로 사용하지 않는다.

- 시작일과 종료일은 각각 생략할 수 있다.
- 둘 다 생략하면 작성일을 제한하지 않는다.
- 날짜 입력 범위는 0001-01-01부터 9999-12-31까지다.
- 시작일과 종료일을 모두 지정하면 시작일은 종료일보다 늦을 수 없다.
- 같은 날짜를 지정하면 해당 날짜 전체를 조회한다.
- 날짜는 Asia/Seoul 기준으로 해석한다.
- 시작일의 한국 시간 00:00 이상을 포함한다.
- 종료일 다음 날의 한국 시간 00:00 미만을 포함한다.

예를 들어 다음 요청은 한국 날짜 2026-11-07 전체를 조회한다.

```http
GET /api/v1/admin/posts?startDate=2026-11-07&endDate=2026-11-07
```

실제 조회에 사용하는 UTC 범위는 다음과 같다.

```text
createdAt >= 2026-11-06T15:00:00Z
createdAt <  2026-11-07T15:00:00Z
```

종료일 마지막 순간을 23:59:59 등으로 제한하지 않고,
다음 날 시작 시각 미만으로 처리한다.

### 작성 시각 정렬

| sort | 정렬 기준 |
|---|---|
| LATEST | createdAt 내림차순 → 게시글 ID 내림차순 |
| OLDEST | createdAt 오름차순 → 게시글 ID 오름차순 |

- sort를 생략하거나 빈 문자열로 전달하면 LATEST를 적용한다.
- 정렬 코드는 대문자 LATEST, OLDEST를 사용한다.
- 소문자 또는 정의되지 않은 값은 400 INVALID_REQUEST_PARAMETER를 반환한다.
- 작성 시각이 같으면 게시글 ID로 순서를 결정한다.
- 서버에서 검색 조건에 맞는 전체 결과를 정렬한 뒤 페이지를 적용한다.
- 현재 페이지의 결과만 정렬하는 방식이 아니다.

### 검색 조건 결합

선택한 상태끼리는 OR로 결합한다.
상태, 제목 검색어, 작성일 범위는 AND로 결합한다.

예를 들어 다음 요청은 작성일 범위 안에서 제목에 "보청기"가 포함된
정상 또는 숨김 게시글을 조회한다.

```http
GET /api/v1/admin/posts?statuses=VISIBLE&statuses=HIDDEN&keyword=보청기&startDate=2026-11-01&endDate=2026-11-07
```

기존 단일 상태 파라미터 status에도 작성일과 정렬을 함께 사용할 수 있다.

### 페이지 정책

- page는 0부터 시작한다.
- size는 1 이상 50 이하이며 기본값은 20이다.
- page × size는 Integer.MAX_VALUE 이하여야 한다.
- 목록과 전체 개수에 동일한 검색 조건을 적용한다.
- 검색 결과가 없으면 200과 빈 items를 반환한다.
- 범위 밖 페이지도 200과 빈 items를 반환한다.
- 범위 밖 페이지에서도 검색 조건에 맞는 totalElements와 totalPages는 유지한다.
- hasPrevious는 페이지 번호를 기준으로 하므로 범위 밖 페이지에서도 true일 수 있다.

### 목록 응답

| 필드 | 의미 |
|---|---|
| items | 게시글 목록 |
| page | 요청한 페이지 번호 |
| size | 요청한 페이지 크기 |
| totalElements | 검색 조건에 맞는 전체 게시글 수 |
| totalPages | 전체 페이지 수 |
| hasNext | 다음 페이지 여부 |
| hasPrevious | 이전 페이지 여부 |

items의 각 항목은 다음 필드를 포함한다.

| 필드 | 의미 |
|---|---|
| postId | 게시글 ID |
| type | 게시글 유형 코드 |
| topic | 게시글 주제 코드 |
| title | 게시글 제목 |
| contentPreview | 목록용 본문 미리보기 |
| author | 작성자 정보 |
| status | VISIBLE, HIDDEN 또는 DELETED |
| createdAt | 작성 시각 |
| updatedAt | 최종 변경 시각 |
| hiddenAt | 숨김 시각. 숨김 시각이 없으면 null |
| deletedAt | 삭제 시각. 삭제되지 않았으면 null |

목록에는 원문 content 대신 contentPreview를 제공한다.
sort와 검색 조건을 별도의 응답 필드로 반환하지 않는다.

updatedAt은 본문 수정 시각만을 의미하지 않는다.
숨김·복원·삭제 등 상태 변경으로도 갱신될 수 있다.

#### 목록 응답 예시

```json
{
  "items": [
    {
      "postId": 104,
      "type": "GENERAL",
      "topic": "DAILY",
      "title": "보청기 적응 경험",
      "contentPreview": "보청기를 사용하면서 느낀 경험을 공유합니다.",
      "author": {
        "memberId": 8,
        "nickname": "이움회원",
        "withdrawn": false
      },
      "status": "HIDDEN",
      "createdAt": "2026-11-07T01:00:00Z",
      "updatedAt": "2026-11-07T02:00:00Z",
      "hiddenAt": "2026-11-07T02:00:00Z",
      "deletedAt": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false,
  "hasPrevious": false
}
```

---

## 2. 게시글 상세 조회

### 요청

```http
GET /api/v1/admin/posts/{postId}
```

예:

```http
GET /api/v1/admin/posts/104
```

| 경로 파라미터 | 설명 |
|---|---|
| postId | 조회할 게시글 ID |

정상·숨김·삭제 상태와 관계없이 관리자 상세 조회를 허용한다.
목록의 상태·검색어·작성일·정렬 조건은 상세 조회에 적용하지 않는다.

게시글이 존재하지 않으면 404 POST_NOT_FOUND를 반환한다.
0 이하의 게시글 ID도 404 POST_NOT_FOUND로 처리한다.
숫자로 변환할 수 없는 ID는 400 INVALID_REQUEST_PARAMETER를 반환한다.

### 상세 응답

| 필드 | 의미 |
|---|---|
| postId | 게시글 ID |
| type | 게시글 유형 코드 |
| topic | 게시글 주제 코드 |
| title | 게시글 제목 |
| content | 게시글 본문 원문 |
| author | 작성자 정보 |
| status | VISIBLE, HIDDEN 또는 DELETED |
| createdAt | 작성 시각 |
| updatedAt | 최종 변경 시각 |
| hiddenAt | 숨김 시각. 숨김 시각이 없으면 null |
| deletedAt | 삭제 시각. 삭제되지 않았으면 null |

상세에는 contentPreview 대신 content를 제공한다.
본문 원문의 줄바꿈을 유지한다.

#### 상세 응답 예시

```json
{
  "postId": 104,
  "type": "GENERAL",
  "topic": "DAILY",
  "title": "보청기 적응 경험",
  "content": "보청기를 사용하면서 느낀 경험을 공유합니다.\n\n처음에는 적응 시간이 필요했습니다.",
  "author": {
    "memberId": 8,
    "nickname": "이움회원",
    "withdrawn": false
  },
  "status": "HIDDEN",
  "createdAt": "2026-11-07T01:00:00Z",
  "updatedAt": "2026-11-07T02:00:00Z",
  "hiddenAt": "2026-11-07T02:00:00Z",
  "deletedAt": null
}
```

관리자가 상세를 조회할 수 있다는 사실이 숨김·복원 가능 여부를 의미하지 않는다.
삭제된 게시글의 숨김·복원 제한은 별도 운영 조치 API 정책을 따른다.

게시글의 댓글은 별도 관리자 댓글 조회 API로 조회한다.
상세 응답에 댓글 목록이나 운영 기록은 포함하지 않는다.

---

## 3. 작성자 및 시각 표시

### 작성자 정보

| 필드 | 의미 |
|---|---|
| author.memberId | 작성자 회원 ID |
| author.nickname | 표시용 닉네임 |
| author.withdrawn | 탈퇴 여부 |

탈퇴 작성자의 게시글도 관리자 조회가 가능하다.
탈퇴 작성자의 nickname은 "탈퇴한 회원"으로 반환한다.

이메일, 비밀번호 해시 등은 응답에 포함하지 않는다.
프론트는 작성자 표시 시 서버가 반환한 nickname과 withdrawn을 사용한다.

### 시각 정보

createdAt, updatedAt, hiddenAt, deletedAt은
UTC ISO 8601 형식으로 반환한다.

프론트에서는 Asia/Seoul 기준으로 표시한다.
날짜 필터에 전달하는 YYYY-MM-DD와 응답의 UTC 시각을 구분한다.

게시글 상태는 status 필드를 기준으로 표시한다.
hiddenAt이 남아 있어도 deletedAt이 있으면 DELETED 상태다.

---

## 4. 오류

| HTTP 상태 | 코드 | 의미 |
|---|---|---|
| 400 | INVALID_REQUEST_PARAMETER | 숫자·날짜·enum 변환 실패 또는 잘못된 상태 파라미터 |
| 400 | INVALID_PAGE_REQUEST | 페이지·크기 또는 offset 범위 오류 |
| 400 | INVALID_SEARCH_KEYWORD | 정규화한 제목 검색어가 최대 길이를 초과 |
| 400 | INVALID_DATE_RANGE | 허용 날짜 범위 또는 시작일·종료일 순서 오류 |
| 401 | AUTHENTICATION_REQUIRED | 인증 정보가 없거나 유효하지 않음 |
| 403 | ACCESS_DENIED | Security 필터에서 관리자 접근 거부 |
| 403 | ADMIN_REQUIRED | 서비스에서 관리자 권한 부족 확인 |
| 404 | POST_NOT_FOUND | 상세 조회 대상 게시글이 없거나 ID가 0 이하 |

### 날짜·정렬 오류 예시

| 요청 조건 | 오류 코드 |
|---|---|
| startDate=2026-02-30 | INVALID_REQUEST_PARAMETER |
| endDate=not-a-date | INVALID_REQUEST_PARAMETER |
| sort=oldest | INVALID_REQUEST_PARAMETER |
| sort=UNKNOWN | INVALID_REQUEST_PARAMETER |
| startDate=2026-11-08&endDate=2026-11-07 | INVALID_DATE_RANGE |
| startDate=0000-01-01 | INVALID_DATE_RANGE |

날짜 문자열을 LocalDate로 변환하지 못하면
INVALID_REQUEST_PARAMETER를 반환한다.

LocalDate로 변환된 값이 허용 범위를 벗어나거나 날짜 순서가 잘못되면
INVALID_DATE_RANGE를 반환한다.

### 인증·인가 처리

프론트는 관리자 접근 거부 시 ACCESS_DENIED와 ADMIN_REQUIRED를 모두 처리한다.

로그인 후 역할·회원 상태·인증 버전이 변경되면
기존 세션이 401 AUTHENTICATION_REQUIRED로 차단될 수 있다.

요청자의 회원 ID와 인증 버전은 서버의 로그인 인증 정보에서 가져온다.
프론트가 관리자 식별용 회원 ID를 요청 파라미터로 전달하지 않는다.

오류 처리 분기는 message 문자열이 아닌 code를 기준으로 한다.
오류 응답의 Cache-Control에는 no-store 외의 지시자가 함께 포함될 수 있다.

---

## 5. 프론트 연결 시 유의 사항

### 요청 구성

- 세션 쿠키를 포함해 요청한다.
- GET 조회에는 CSRF 토큰을 요구하지 않는다.
- 새 다중 선택 UI는 statuses 반복 파라미터를 사용한다.
- status와 statuses를 동시에 전송하지 않는다.
- statuses를 쉼표 문자열로 합치지 않는다.
- 전체 상태 조회는 두 상태 파라미터를 모두 생략하거나 실제 상태 세 개를 전달한다.
- 상태를 하나도 선택하지 않은 경우 빈 statuses를 서버에 보내지 않는다.
- 미선택 상태를 파라미터 생략으로 바꾸면 전체 조회가 되므로 구분한다.
- 지정하지 않은 날짜 파라미터는 생략한다.
- 날짜는 달력에서 선택한 YYYY-MM-DD 문자열로 전달한다.
- 날짜를 UTC로 바꾸거나 toISOString()으로 가공해 전달하지 않는다.

### 조건 변경과 페이지

- 적용되는 상태·검색어·작성일·정렬이 변경되면 page를 0으로 초기화한다.
- 특정 조건을 변경할 때 다른 적용된 검색 조건은 유지한다.
- 정렬 변경 시 선택한 sort로 서버에 다시 요청한다.
- 현재 페이지의 배열을 뒤집는 방식으로 정렬하지 않는다.
- 목록은 서버가 반환한 순서대로 표시한다.
- 페이지 계산에는 응답의 totalElements와 totalPages를 사용한다.
- sort 응답 필드는 없으므로 요청에 사용한 정렬 조건을 화면 상태로 관리한다.

### 재조회와 인증

- 이전 요청의 늦은 응답이 최신 검색 결과를 덮어쓰지 않도록 처리한다.
- 결과를 유지한 채 재조회한다면 조회 중임을 구분해 표시한다.
- 조회 실패를 검색 결과 0건으로 표시하지 않는다.
- 인증 상태가 변경되거나 관리자 접근이 거부되면 이전 관리자 조회 결과를 표시하지 않는다.

### 표시와 이동

- 모든 시각은 Asia/Seoul 기준으로 표시한다.
- updatedAt을 본문 수정 전용 시각으로 표시하지 않는다.
- 작성자 표시는 서버의 nickname과 withdrawn을 사용한다.
- 목록 항목의 postId로 관리자 상세 조회를 요청한다.
- 관리자 상세 조회와 일반 공개 게시글 조회의 접근 정책을 구분한다.
- 제목·본문·미리보기를 렌더링할 때 사용자 입력을 실행 가능한 HTML로 직접 삽입하지 않는다.