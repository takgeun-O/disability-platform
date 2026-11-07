# 관리자 게시글·댓글 다중 상태 조회

## 적용 API

- GET /api/v1/admin/posts
- GET /api/v1/admin/posts/{postId}/comments

관리자 인증과 기존 조회 정책은 유지한다.
GET 조회에는 CSRF 토큰을 요구하지 않는다.

이번 변경은 상태 검색 조건 확장이며 응답 구조는 변경하지 않는다.

## 요청 파라미터

| 파라미터 | 설명 |
|---|---|
| status | 기존 단일 상태 조건. ALL, VISIBLE, HIDDEN, DELETED |
| statuses | 다중 상태 조건. VISIBLE, HIDDEN, DELETED를 반복 파라미터로 전달 |

status와 statuses는 함께 전달할 수 없다.

### 전체 조회

두 파라미터를 모두 생략하면 전체 상태를 조회한다.

기존 요청과의 호환을 위해 다음 요청도 전체 조회로 처리한다.

- status=ALL
- status=

statuses로 실제 상태 세 개를 모두 전달해도 전체 조회가 된다.

### 단일 상태 조회

기존 방식:

GET /api/v1/admin/posts?status=HIDDEN

새 방식:

GET /api/v1/admin/posts?statuses=HIDDEN

### 다중 상태 조회

GET /api/v1/admin/posts?statuses=VISIBLE&statuses=HIDDEN

GET /api/v1/admin/posts/123/comments?statuses=VISIBLE&statuses=HIDDEN

선택한 상태끼리는 OR로 결합한다.
상태 조건과 검색어 조건은 AND로 결합한다.
댓글 조회에는 해당 부모 게시글 ID 조건도 함께 적용한다.

동일한 상태를 여러 번 전달하면 중복을 제거한다.

쉼표로 연결한 단일 값은 지원하지 않는다.
예: statuses=VISIBLE,HIDDEN은 잘못된 요청이다.

## 잘못된 상태 요청

다음 요청은 400 INVALID_REQUEST_PARAMETER로 거부한다.

- status와 statuses를 함께 전달
- status를 여러 번 전달
- statuses에 빈 값 포함
- statuses에 ALL 포함
- statuses에 소문자, 정의되지 않은 값 또는 앞뒤 공백이 있는 값 포함
- statuses에 쉼표로 연결한 값 전달

status가 빈 문자열이거나 ALL이어도 statuses와 함께 전달할 수 없다.

기존 단일 status는 앞뒤 공백을 제거한 뒤 해석한다.
새 statuses의 각 값은 대문자 상태 코드와 정확히 일치해야 한다.

## 상태 판정

| 상태 | 조건 |
|---|---|
| VISIBLE | 삭제되지 않았고 숨겨지지 않음 |
| HIDDEN | 삭제되지 않았고 숨겨짐 |
| DELETED | 삭제됨. 숨김 여부와 무관 |

숨김 후 삭제된 콘텐츠는 DELETED에만 포함한다.

댓글의 상태 필터는 댓글 자체의 상태를 기준으로 한다.
부모 게시글의 숨김·삭제 상태가 댓글 자체의 상태를 바꾸지는 않는다.

부모 게시글이 숨겨지거나 삭제되었을 때 댓글의 상태가 VISIBLE이어도,
일반 사용자에게 공개된다는 의미는 아니다.

## 정렬과 페이지

서버에서 전체 검색 결과에 상태와 검색어 조건을 적용한 후,
정렬하고 페이지를 나눈다.

- 게시글: createdAt 내림차순 → id 내림차순
- 댓글: createdAt 오름차순 → id 오름차순

전체 개수와 페이지 수에도 동일한 검색 조건을 적용한다.

조회 결과가 없으면 200과 빈 items를 반환한다.
범위 밖 페이지도 200이며 전체 검색 결과 개수는 유지한다.

기존 keyword, page, size 검증 정책은 변경하지 않는다.

## 프론트 연동

- 정상·숨김·삭제 상태를 복수 선택할 수 있도록 한다.
- 초기 선택은 세 상태 모두 선택이다.
- 체크박스와 검색어 변경 후 검색 버튼으로 조건을 적용한다.
- 검색 조건 적용 시 page를 0으로 초기화한다.
- 아무 상태도 선택하지 않았으면 안내하고 요청하지 않는다.
- 빈 선택을 파라미터 생략으로 변환하지 않는다.
  파라미터 생략은 전체 조회를 의미하기 때문이다.
- 다중 상태 요청에는 statuses만 전달하고 status는 전달하지 않는다.
- 현재 페이지의 데이터를 프론트에서 필터링하거나,
  상태별 조회 결과를 합쳐 페이지를 구성하지 않는다.
- 서버가 반환한 목록 순서와 전체 개수를 사용한다.
- 늦게 도착한 이전 요청이 최신 검색 결과를 덮어쓰지 않도록 한다.
- 조치 후 재조회에도 현재 적용된 검색 조건을 유지한다.
- 인증 상태가 변경되면 이전 관리자 조회 결과를 표시하지 않는다.