# 커뮤니티 API 연결을 실제 React 코드로 읽기

> 후속 변경: [커뮤니티 리뷰 반영 기록](Community-Review-Fixes-2026-10-14.md)에서 SSR 최초 조회·스크롤/초점·범위 밖 페이지·모바일 베스트 숨김을 적용했습니다. 아래 본문은 최초 구현 당시 기록입니다.

기준: `feat/community-posts-ui`, 시작 HEAD `88650cf1443f635bc0d36e9bd20c83c607576c6f`, 2026-10-14 로컬 구현. [구현·검증 기록](Community-API-Integration-2026-10-14.md)과 함께 읽습니다.

## 먼저 읽을 순서

아래 경로는 `web/` 기준입니다.

1. `src/lib/community-posts.ts`: 글 유형·주제, 입력 검증, URL 규칙. 일반 JavaScript 함수와 가장 가깝습니다.
2. `src/features/PostCreate.tsx`: `values` → `edit` → `handleSubmit` → 성공/실패 화면. 입력에서 저장까지 한 흐름을 먼저 봅니다.
3. `src/lib/community-api.ts`: `createPost`, `request`, `getPosts`, `getPost`. 네트워크 요청과 응답 검증을 확인합니다.
4. `src/features/PostList.tsx`: `FilterForm`, `navigate`, 조회 `useEffect`, `snapshot`, JSX의 `map` 순서로 읽습니다.
5. `src/features/PostDetail.tsx`: 주소의 게시글 ID로 조회한 결과를 보여주는 비교적 짧은 화면입니다.
6. `src/components/AuthProvider.tsx`, `src/lib/auth-session.ts`, `src/lib/signup-api.ts`: 기존 로그인 상태와 CSRF 함수가 연결되는 지점입니다. 이번에 인증 체계를 다시 구현하지 않았습니다.
7. `src/components/NavigationGuard.tsx`, `src/components/AppLink.tsx`, `src/components/community/CommunityDialog.tsx`: 작성 중 이탈과 키보드 초점 처리입니다.

## React에서 값을 보관한다는 의미

일반 JavaScript에서는 `let title = ''`처럼 변수를 만들 수 있습니다. React 화면에서는 값이 바뀌면 화면도 다시 계산해야 하므로 `useState`를 씁니다.

```tsx
const [values, setValues] = useState<PostForm>(...)
```

`values`는 **이번 화면을 그릴 때 사용하는 값**, `setValues`는 **새 값으로 다음 화면 계산을 예약하는 함수**입니다. `setValues`를 호출한 직후 같은 함수 안의 `values`가 바로 다른 객체로 바뀌는 것은 아닙니다. 그래서 이전 값으로부터 새 값을 만들 때는 `setValues(previous => ...)` 형태를 씁니다.

컴포넌트는 `PostCreate()`처럼 UI를 반환하는 함수입니다. JSX의 `<input>`은 DOM을 직접 만드는 명령이라기보다 “이 상태라면 이런 화면을 보여 달라”는 표현입니다. TypeScript의 `PostForm`은 개발 중 실수를 잡는 타입입니다. 서버에서 온 JSON까지 실행 중 자동 검증해 주지는 않으므로 API 모듈에 별도 검사 함수가 있습니다.

`useRef`는 화면 표시와 직접 관계없는 값을 보관합니다. 예를 들어 `inFlight.current`는 클릭이 연속해서 들어와도 첫 번째 제출만 통과시키는 즉시 확인용 잠금입니다. 이 값 자체를 바꾸는 것으로 화면을 다시 그리지는 않습니다.

## 작성: 입력에서 API까지

### 1. 주소에 있는 초기 분류를 읽습니다

`PostCreate`의 `useSearchParams()`는 현재 URL 쿼리를 읽습니다. `parsePostQuery()`는 `QUESTION`과 기존 한글 링크 `질문·답변`을 모두 받아 내부에서는 `QUESTION`으로 통일합니다. 주소에 없는 분류는 빈 선택값입니다. 로그인 복귀 URL에도 분류와 목록 복귀 주소를 유지합니다.

`values`에는 네 필드만 보관합니다.

```ts
{ type, topic, title: '', content: '' }
```

### 2. 입력 이벤트가 상태를 바꿉니다

제목 입력창의 연결은 다음과 같습니다.

```tsx
<input value={values.title}
  onChange={event => edit('title', event.target.value)} />
```

`value`는 React 상태를 입력창에 보여주고, `onChange`는 사용자가 입력한 새 문자열을 읽습니다. `edit`은 `setValues`로 해당 필드만 바꾸고 `setDirty(true)`와 `blocker.markDirty()`를 호출합니다. 여기서 `dirty`는 저장하지 않은 편집이 있다는 뜻입니다.

두 dirty 호출에는 이유가 있습니다. 로그아웃 직전에 이탈에 동의하면 기존 NavigationGuard가 dirty를 초기화합니다. 같은 작성 화면에서 응답을 기다리며 다시 입력할 때 `dirty`가 이미 `true`라면 React 효과가 재실행되지 않을 수 있습니다. 편집 이벤트에서도 `markDirty()`를 호출해야 **그 이후 입력도 다시 보호**됩니다.

### 3. 등록 버튼은 form의 `handleSubmit`을 실행합니다

버튼 클릭과 Enter 제출 모두 `<form onSubmit={handleSubmit}>`로 모입니다. `event.preventDefault()`는 브라우저의 기본 전체 페이지 전송을 막습니다.

`handleSubmit`의 순서는 다음과 같습니다.

1. 전송 중이거나 로그인 확인이 끝나지 않았으면 보내지 않습니다.
2. `validatePostForm(values)`로 필수값과 글자 수를 검사합니다.
3. 오류가 있으면 `errors`를 갱신하고 첫 오류 필드로 초점을 옮깁니다. `aria-invalid`, `aria-describedby`가 필드와 설명을 연결합니다.
4. `inFlight.current = true`로 즉시 중복 제출을 막고, `submitting` 상태로 “등록 중…”과 비활성 입력을 표시합니다.
5. `await createPost(values)`로 저장 요청 결과를 기다립니다.

제목은 Java `String.strip()`에 맞는 앞뒤 공백만 제거하고 최대 120 코드 포인트를 허용합니다. 본문은 최대 10,000 코드 포인트이며 공백·줄바꿈을 그대로 보냅니다. `string.length`는 이모지를 두 글자로 셀 수 있어 `[...value].length`를 사용합니다. JS `trim()`과 Java의 공백 범위가 같지 않은 점도 `normalizeTitle()`에 반영했습니다.

### 4. 요청 객체와 fetch는 API 모듈에 있습니다

`community-api.ts`의 `createPost()`는 기존 `signup-api.ts`의 **`getCsrfContext()`를 매번 새로 호출**합니다. 보안 확인 정보를 얻는 데 실패하면 게시글 POST 자체를 보내지 않습니다.

그 후 다음 네 필드로 요청을 만듭니다.

```ts
JSON.stringify({
  type: values.type,
  topic: values.topic,
  title: normalizeTitle(values.title),
  content: values.content,
})
```

작성자 ID는 보내지 않습니다. 서버가 현재 로그인 세션에서 결정합니다. 실제 `fetch()`는 같은 파일의 `request()` 안에 있습니다.

- URL: `NEXT_PUBLIC_API_BASE_URL` + `/api/v1/community/posts`
- 메서드: `POST`
- `credentials: 'include'`: 브라우저가 해당 서버의 세션 쿠키를 함께 보냅니다.
- `Content-Type: application/json`: 전송 본문의 형식을 알립니다.
- `X-CSRF-TOKEN`: 방금 받은 토큰입니다. 토큰을 문서·로그·브라우저 저장소에 보관하지 않습니다.

로그인 상태 표시용 `useAuth()`와 실제 서버 권한 검사는 역할이 다릅니다. 화면에서 로그인 상태로 보여도 서버 세션이 만료됐을 수 있으며, 서버가 최종적으로 ACTIVE 여부와 권한을 검사합니다.

### 5. 성공과 실패가 화면을 바꿉니다

`fetch`는 HTTP 400·500에서도 일반적으로 Promise를 성공적으로 반환합니다. 따라서 `response.status`를 직접 확인해야 합니다. 또 `response.json()`은 JSON을 객체로 바꿀 뿐, 올바른 응답인지 보장하지 않습니다.

`createPost`는 **201, JSON 콘텐츠 타입, 객체 형태, 양의 안전한 정수 ID**를 모두 확인해야 성공합니다. HTML, 깨진 JSON, 누락 ID는 성공으로 넘기지 않습니다.

성공하면 `PostCreate.handleSubmit`이 `flushSync`로 dirty 해제를 화면에 반영한 뒤, 그 ID의 상세 경로로 `router.replace()` 합니다. 실제 데이터를 다시 GET하므로 새로고침해도 글이 남습니다. 사용자가 요청 중 이탈에 동의해 이미 다른 화면으로 갔다면 `mounted` 확인으로 지연된 응답의 강제 이동을 막습니다.

실패하면 `values`와 dirty를 그대로 두고 `feedback`, `errors`만 갱신합니다. 401이면 기존 `auth.refresh()`로 현재 회원 상태도 다시 확인합니다. 로그아웃·세션 만료 후에도 dirty 폼은 화면에 남으며 등록만 제한됩니다.

네트워크 오류나 손상된 성공 응답은 **서버에서는 이미 저장됐을 가능성**이 있습니다. “저장되지 않았다”고 단정하지 않고 새 탭에서 목록을 확인하도록 안내합니다. 어떤 경우에도 게시글 POST를 자동 반복하지 않습니다. CSRF 오류 뒤 사용자가 직접 다시 등록하면 새 CSRF를 받습니다.

폼 내용은 컴포넌트 메모리에 있습니다. 로그인 화면으로 이동하거나 새로고침하면 기존 이탈 확인을 거치지만, 이동에 동의한 뒤까지 초안을 영구 보관하지는 않습니다. 비밀번호·초안을 localStorage에 추가 저장하지 않았습니다.

## 목록: 선택값과 적용값을 분리합니다

`/community`와 `/community/posts`는 같은 `PostList` 화면을 사용합니다. `CommunityHome`은 이 화면을 다시 내보내므로 예전 모의 진입 화면으로 돌아가지 않습니다.

### 1. FilterForm은 선택 중인 값만 보관합니다

`FilterForm` 안의 `type`, `topic`은 `useState`에 있는 임시 선택값입니다. 선택 상자의 `onChange`는 이 두 값만 바꿉니다. API 호출이나 URL 변경은 하지 않습니다.

모바일에서는 `filterOpen`이 참일 때 `CommunityDialog` 안에 폼을 만듭니다. 취소/Escape로 닫으면 그 폼의 상태도 사라집니다. 다시 열 때 URL에 적용된 조건으로 시작하므로 취소한 선택은 남지 않습니다. dialog는 배경 조작을 막고 Tab 순환, 닫은 뒤 필터 버튼으로의 초점 복귀를 처리합니다.

### 2. 적용 버튼이 URL을 바꿉니다

`FilterForm.submit()`이 `onApply({ type, topic, page: 0 })`를 부릅니다. 부모가 전달한 `onApply`의 실제 함수는 `PostList.navigate()`입니다. 함수도 값처럼 다른 컴포넌트에 전달할 수 있다는 예입니다.

`navigate()`는 `listHref()`로 안전한 목록 주소를 만들고 `router.push(..., { scroll: false })`합니다. 새로운 조건은 API 기준 0페이지, 사용자 화면 기준 1페이지부터 조회합니다. 같은 조건을 다시 적용하면 URL 대신 `retry` 번호를 바꿔 재조회합니다.

예를 들면 화면의 “질문·답변 / 보청기”는 다음 주소가 됩니다.

```text
/community/posts?type=QUESTION&topic=HEARING_AID
```

페이지 2의 URL은 `page=1`입니다. URL에는 **적용을 누른 조건만** 들어갑니다. 페이지 이동도 `navigate()`를 재사용하므로 새로고침·공유·브라우저 뒤로 가기가 같은 조건을 읽습니다.

### 3. useEffect가 바뀐 URL의 데이터를 조회합니다

`useEffect`는 렌더링 이후 외부 시스템과 동기화할 때 사용하는 React 기능입니다. 목록은 URL 조건이 바뀌면 API와 동기화해야 합니다. 렌더 함수 몸체에서 곧바로 `fetch`하면 화면을 다시 그릴 때마다 요청할 수 있으므로 효과 안에서 시작합니다.

조회 효과는 `getPosts(requested, controller.signal)`를 부릅니다. `getPosts`는 `type`, `topic`, `page`, `size=20` 쿼리를 만들고 `request()`에서 GET을 실행합니다. 읽기 GET에는 CSRF 선행 요청이 없습니다.

응답에는 목록뿐 아니라 페이지 정보가 필요합니다. `isPage()`가 필수 필드·enum·작성자·날짜·요청 페이지와 응답 페이지 일치·총 페이지와 항목 수 관계를 검사합니다. 정상적인 `items: []`는 빈 결과 화면이고, 잘못된 응답은 오류입니다.

### 4. snapshot이 목록과 조건을 함께 보관합니다

`result.snapshot`에는 마지막으로 성공한 **목록과 그 목록의 조건**이 같이 들어 있습니다. 새 요청을 시작해도 snapshot을 비우지 않습니다.

- 로딩: 이전 목록/조건 + “이전 결과를 보여드리고 있어요.”
- 성공: 새 목록과 조건을 한 번에 교체
- 실패: 이전 목록/조건 + 실패한 요청 조건/페이지 + 재시도 버튼

URL은 방금 적용한 요청 조건을 나타냅니다. 실패했을 때 표시 중인 이전 목록을 새 조건의 결과인 것처럼 표시하지 않습니다. `displayedQuery`와 `query`를 구분한 이유입니다. 선택 상자는 실패한 요청의 입력을 유지합니다.

JSX는 `snapshot.page.items.map(post => ...)`로 각 게시글을 태그·제목 링크·작성자·날짜로 바꿉니다. `key={post.id}`는 React가 어떤 행이 같은 게시글인지 구분하는 식별자입니다.

### 5. 오래된 응답은 버립니다

첫 요청 A가 느린 동안 B를 적용하면 B가 먼저 도착할 수 있습니다. `generation` 번호를 새 요청마다 올리고 응답의 번호가 최신일 때만 `setResult`를 허용합니다. 적용 버튼을 누르는 순간에도 번호를 올려 URL 전환이 완료되기 전의 틈을 막습니다.

효과가 교체되거나 화면을 떠나면 cleanup에서 `AbortController.abort()`도 호출합니다. 취소 신호와 번호 검사를 함께 사용하므로 이전 성공뿐 아니라 **늦은 실패도** 새 화면을 덮지 않습니다. 상세 화면도 ID별 효과와 취소 신호를 사용합니다.

## 상세·뒤로 가기·스크롤 복원

목록의 제목 링크에는 실제 ID와 검증 가능한 `from` 목록 주소가 붙습니다. 클릭 때 `rememberListScroll()`이 현재 목록 URL의 스크롤 숫자만 `sessionStorage`에 기록합니다. 게시글 본문이나 인증 정보는 저장하지 않습니다.

`PostDetail`은 `useParams()`로 ID, `useSearchParams()`로 `from`을 읽고 `getPost()`를 호출합니다. 결과의 `content`를 JSX 텍스트로 그대로 넣습니다. `dangerouslySetInnerHTML`은 사용하지 않습니다. CSS의 `white-space: pre-wrap`, `overflow-wrap: anywhere`가 줄바꿈과 긴 문자열을 처리합니다.

`safeListReturn()`은 `/community`, `/community/posts`와 정상 필터만 복귀 주소로 허용합니다. 외부 주소나 다른 경로가 들어오면 기본 `/community/posts`로 돌아갑니다. 상세 URL 직접 진입도 이 기본 경로를 사용합니다.

목록으로 돌아와 성공한 데이터를 그린 뒤 `restoreListScroll()`이 `requestAnimationFrame`에서 저장 위치로 복귀합니다. Next가 관리하는 history 객체를 새 형식으로 덮어쓰지 않습니다. 기존 `AppLink`, NavigationGuard, 브라우저 Back/Forward를 유지합니다. 저장소를 사용할 수 없는 환경에서는 목록 조회는 계속하고 기본 브라우저 스크롤 동작에 맡깁니다.

## 화면과 서버 사이에서 준비 중인 기능

베스트글은 `CommunityParts.BestPosts()`로 분리했습니다. 현재는 선정 API가 없어 안내만 표시하며 목록 필터와 독립적입니다. 댓글·좋아요·조회수·이미지 업로드·수정·삭제·질문 해결 상태도 이번 API에 없으므로 모의 버튼과 숫자를 실제 기능처럼 노출하지 않습니다.

홈과 검색은 예시 콘텐츠를 유지하되 예시 ID를 실제 상세에 넘기지 않습니다. 이를 실제 목록으로 연결하는 변경만 했으며 홈 전체를 데이터 API에 연결하지 않았습니다.


## 2026-10-14 후속 학습: 이탈 승인과 요청의 수명

위 첫 구현 설명 이후 공개 목록/상세의 최초 조회는 Server Component로 이동했다. 현재 최초 HTML은 서버 조회 결과를 받고 클라이언트는 필터 변경/재시도를 처리한다. 최신 검증은 [리뷰 기록](Community-Review-Fixes-2026-10-14.md)의 추가 리뷰 절을 기준으로 한다.

### “화면에서 나가겠다”와 “컴포넌트가 사라졌다”는 다른 시점이다

`router.push()`는 이동 요청이다. 목적지 서버가 느리면 React는 지금 폼을 계속 표시한다. 이때 `mounted.current`는 true이므로 이것만 확인한 등록 응답은 사용자의 새 이동을 덮어쓸 수 있다.

공통 guard의 `proceed()`는 나가기 확정을 받는 순간 승인 번호를 올린다. 등록 함수는 시작할 때 번호를 기억하고 `await createPost(...)` 이후 같은 번호인지 확인한다. 번호가 다르면 그 응답의 자동 이동/오류 표시는 더 이상 유효하지 않다. “계속 작성”은 번호를 바꾸지 않으므로 이후 정상201 응답을 기존대로 처리한다. 번호를 React state 대신 ref로 보관한 이유는 다음 렌더를 기다리지 않고 이벤트 시점부터 달라져야 하기 때문이다.

실제 서버 저장이 취소된 것은 아니다. 사용자가 승인한 화면 이동을 우선할 뿐이며 POST를 다시 보내지 않는다. 늦은401에서 `auth.refresh()`도 건너뛰어 이미 떠난 요청이 다른 화면 상태를 바꾸지 않게 한다. 요청이 끝났다는 busy 정리는 남긴다.

### 화면의 현재 값과 이미 보낸 요청의 값

일반 복귀 주소는 이전 탐색 문맥이다. 반면 저장 결과를 확인할 주소는 방금 보낸 글의 유형·주제와 첫 페이지여야 한다. `submitted = { ...values }`로 요청 시점 값을 보관하고 `listHref(...page:0)`도 그때 계산한다. 오류가 난 뒤 사용자가 다른 주제로 수정해도 이전 POST의 조건은 변하지 않는다. 새 요청을 보낼 때만 새 조건으로 바꾼다.

### 서버 안내와 클라이언트 복귀 동작을 나눈 이유

404 제목/설명/기본 목록 링크에는 브라우저 상태가 필요하지 않다. `from` 검증과 sessionStorage 스크롤 복원에만 클라이언트 동작이 필요하므로 작은 링크 컴포넌트로 분리했다. fallback에도 실제 href가 있는 링크를 둔다.

다만 **코드가 서버 컴포넌트라는 사실과 실제 응답 HTML에 보이는 내용이 있다는 사실은 같지 않다.** 이번 production 검사에서는 동적 상세 `notFound()`의 오류 복구 응답이 빈 body여서 무JS 안내가 여전히 실패했다. script 안에 한글 문자열이 있다는 검사로 이를 통과시켜서는 안 된다. `community-404-browser.mjs`는 JS를 끈 브라우저에서 표시되는 heading/앵커를 검사하며 현재2개 실패를 반환한다. HTTP404를 잃는 실험은 채택하지 않았다. 상세한 재현 조건과 남은 작업은 리뷰 기록을 참조한다.
