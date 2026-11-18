# 공개 게시글 목록 검색 API


비로그인으로 조회할 수 있으며 응답은 `Cache-Control: no-store`다. 다음은 URL 디코딩 후의 요청 예시다. 브라우저에서는 `URLSearchParams`에 원문을 전달하면 한글과 리터럴 %가 인코딩된다.

```http
GET /api/v1/community/posts
GET /api/v1/community/posts?q=보청기
GET /api/v1/community/posts?q=보청기&type=QUESTION&topic=HEARING_AID&page=0&size=10
```

선택적 `q`는 Java `String.strip()`으로 앞뒤 공백을 제거한다. 생략하거나 결과가 빈 문자열이면 기존 목록을 반환한다. 내부 공백은 보존한다. 정리한 문자열은 최대 100 유니코드 코드 포인트다. 예를 들어 😀 100개는 UTF-16 길이가 200이어도 허용된다. 101개이면 HTTP 400이다.

제목 또는 본문에 검색어가 포함된 공개 글을 찾는다. 영문 대소문자를 구분하지 않고 `%`와 `_`는 일반 문자로 찾는다. 숨김·삭제 글은 제외한다. type·topic은 검색어와 AND로 결합하며 totalElements에도 같은 조건이 적용된다. createdAt 내림차순, 동률이면 id 내림차순이다. page 기본값은 0, size 기본값은 20(1~50)이다. 비로그인 사용자의 liked는 false이고 로그인 사용자는 자신의 좋아요 상태를 받는다.

## 목록 항목의 본문 발췌

모든 항목에 `matchedContentExcerpt`가 포함된다. 정규화된 검색어가 본문에서 처음 일치한 구간과 그 앞뒤 **각각 최대 40 유니코드 코드 포인트**를 원문에서 가져온다. 잘라낸 쪽에만 `…`가 붙는다. 검색어가 반복돼도 첫 일치를 사용하며, 원문의 대소문자·줄바꿈·내부 공백을 보존한다. 영문 A–Z의 대소문자를 무시하고 `%`, `_`, 정규식 특수문자는 그대로 비교한다.

| 조건 | `matchedContentExcerpt` 값 |
|---|---|
| 본문 일치 (제목도 일치할 수 있음) | 발췌 문자열 |
| 제목만 일치 | 명시적 `null` |
| 검색어 생략 또는 앞뒤 공백 제거 후 빈 문자열 | 명시적 `null` |

목록 응답은 전체 본문 `content`와 내부 계산값 `bodyMatchPosition`을 노출하지 않는다. 짧은 본문은 발췌 문자열 안에 전부 들어갈 수 있다.

목록 조회에서 PostgreSQL이 계산한 첫 본문 일치 위치를 사용해 원문을 발췌한다. PostgreSQL 17.11의 libc 제공자, `en_US.utf8` 환경에서 제목이 ‘상담 후기’, 본문이 `CAFÉ`인 글을 `q=café`로 조회하면 `matchedContentExcerpt`는 `"CAFÉ"`다. 비ASCII 대소문자의 일치 여부는 DB 로케일과 제공자의 소문자 변환 규칙을 따른다.

예를 들어 `GET /api/v1/community/posts?q=보청기`에는 본문에서 찾은 글과 제목에서만 찾은 글이 함께 반환될 수 있다.

```json
{
  "items": [
    {
      "id": 42,
      "type": "REVIEW",
      "topic": "HEARING_AID",
      "title": "첫 착용 후기",
      "author": { "memberId": 8, "nickname": "예시회원" },
      "createdAt": "2026-11-18T01:00:00Z",
      "commentCount": 0,
      "likeCount": 0,
      "liked": false,
      "matchedContentExcerpt": "상담을 받았습니다.\n보청기를 처음 착용했어요."
    },
    {
      "id": 41,
      "type": "QUESTION",
      "topic": "HEARING_AID",
      "title": "보청기 상담 때 무엇을 물어보셨나요?",
      "author": { "memberId": 9, "nickname": "질문회원" },
      "createdAt": "2026-11-17T03:00:00Z",
      "commentCount": 0,
      "likeCount": 1,
      "liked": false,
      "matchedContentExcerpt": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1,
  "hasNext": false
}
```

## 빈 결과와 오류

검색 결과가 없으면 HTTP 200이다.

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false
}
```

검색어 길이 초과 시 HTTP 400 응답 예시다. 페이지 범위 오류는 INVALID_PAGE_REQUEST, 숫자·enum 변환 오류는 INVALID_REQUEST_PARAMETER다.

```json
{
  "code": "INVALID_SEARCH_KEYWORD",
  "message": "검색어는 100자 이하여야 합니다.",
  "fieldErrors": []
}
```
