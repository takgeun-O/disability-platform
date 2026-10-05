# IYUM 통합 웹 Next.js App Router 이전

- 작업/검증: 2026-09-28 ~ 2026-09-29 (KST)
- 기준: `/Users/tackeunoh/Developer/iyum/web`의 기존 로컬 파일. Figma 게시본이나 `prototypes/p03`로 덮어쓰지 않음.
- 범위: 실행 구조·라우팅·SSR·빌드/배포 기반. 정책·금액·디자인 재설계, 실제 인증/API/DB 연결 제외.
- 커밋·푸시·배포·Vercel 대시보드 수정 없음.

## 1. 시작 상태와 오류 원인

| 항목 | 확인한 값 |
| --- | --- |
| 기존 런타임/라우터 | React/DOM 19.2.4, Vite 8.0.3, React Router DOM 7.18.2 |
| 도구 | TypeScript 5.9.3, Tailwind 4.2.2, oxfmt, pnpm 잠금 파일 v9 |
| 진입점 | `src/main.tsx` → `src/App.tsx` / createBrowserRouter |
| 버전 지정 | `.mise.toml`: Node 22 / pnpm 10.34.3 |
| 이번 실제 검증 | Node 24.19.0 / pnpm 10.34.3 |
| 기존 검사 | tsc와 Vite build 통과. 약 561.43KB JS에 기존 500KB 경고. lint/저장소 자동 테스트 스크립트 없음 |
| Vercel | web 안 `.vercel/project.json`의 projectName은 iyum-validation. 상위 .vercel 없음 |
| 배포 설정 파일 | 기존 root/web에 vercel.json 없음 |
| 환경변수 | 앱에서 사용하는 VITE_* 값 없음. `.env.local`의 변수 이름만 확인; 인증 값은 출력/공개하지 않음 |
| Git | 최초 커밋/원격 설정 전, 기존 프로젝트 전체가 untracked. 기존 파일을 그대로 작업 기준으로 보존 |

`package.json`에 Next.js가 실제로 없던 Vite 앱이므로, Next.js로 판별하는 Vercel 빌드와 불일치했다. 다만 원래 대시보드 Framework/Root Directory 값을 직접 확인하지 않았으므로 이 중 어느 설정까지 잘못돼 있었는지는 단정하지 않는다. 배포 오류와 기존 소스 빌드 실패는 구분한다. 기존 소스 빌드는 통과했다.

변경 전 소스와 설정 53개를 임시 백업하고 해시로 대조했다. 백업에는 `.env`/배포 인증 정보가 없다. 위치: `/var/folders/vp/fcz5z75n5n143mr4c2yqbwk00000gn/T/iyum-next-migration-df6goyum` (임시 경로이므로 영구 보관이나 Git 이력의 대체물이 아님).

## 2. 최종 구조

- Next.js **16.3.6** App Router. 기존 React 19.2.4와 pnpm 잠금 파일 유지.
- `src/app/layout.tsx`의 서버 레이아웃과 경로별 page.tsx 사용. 홈·커뮤니티 시작·복지 안내는 서버 컴포넌트로 두고 검색, 내비게이션, 폼, 설문 등 상호작용에 클라이언트 경계를 둠.
- 쿼리를 사용하는 목록/검색/로그인/글 작성/상세 페이지는 `connection()` 이후 요청 시 렌더링. 쿼리별 본문도 초기 HTML에 포함.
- `src/pages`를 `src/features`로 이동. Next가 기존 화면 파일을 Pages Router로 자동 해석하는 충돌 방지.
- `AppLink`는 Next Link 기반. URL 쿼리는 Next useSearchParams/useRouter로 연결. 전체 화면을 브라우저 전용 dynamic/no-SSR 컴포넌트로 감싸지 않음.
- P03의 전체 기준·보호자 본문을 처음부터 서버 HTML에 포함하고 선택하지 않은 뷰는 hidden 처리. 브라우저 해시는 hydration 뒤 읽어 해당 안내로 전환. 서버에 없는 window/navigator/document는 이벤트/effect에서 접근. 제품 최신성 시각은 SSR snapshot을 고정해 hydration 시각 차이 방지.
- Tailwind Vite 플러그인을 PostCSS 플러그인으로 교체. 기존 CSS·외부 폰트·SVG·제품 JSON 유지. 이미지 변환/디자인 시스템 추가 없음.
- Next replacement 빌드 성공 후 Vite 진입점·설정·React Router와 Vite 의존성 제거. 기존 dist와 .figma/src/imports 자료는 역사 자료이며 현재 실행에 사용하지 않음.
- Next 설정에는 static export/SPA catch-all/outputDirectory 없음. robots noindex 정책은 기존대로 유지.

### 주요 파일

| 파일/디렉터리 | 변경 역할 |
| --- | --- |
| `src/app/` | 15개 기존 공개 화면 경로, 서버 레이아웃, 커뮤니티 모의 상태 경계, 404/robots |
| `src/features/` | 기존 화면 이동. 상호작용 경계 및 Next 라우팅 대응 |
| `src/components/Shell.tsx`, `GlobalNav.tsx`, `HomeSearch.tsx` | 기존 공통 디자인 유지하면서 서버/클라이언트 역할 분리 |
| `src/components/AppLink.tsx`, `NavigationGuard.tsx` | Next 링크, 작성 중 이탈 확인 및 브라우저 뒤로/앞으로 가기 |
| `src/lib/useQueryParams.ts`, `navigation.ts`, `mock-posts.tsx` | 쿼리 동기화, 외부 실행 URL이 아닌 내부 복귀, 기존 모의 글 상태 |
| `src/features/HearingAidGuide.tsx` | 기존 P03 데이터 보존, 초기 HTML 본문, 안전한 해시 진입/시각 처리 |
| `src/index.css` | 기존 마우스 hover 표현을 서버 컴포넌트에서도 사용할 CSS로 이동 |
| `package.json`, `pnpm-lock.yaml` | Next/ESLint/PostCSS와 실행·검사 스크립트; pnpm 10.34.3 고정 |
| `next.config.ts`, `next-env.d.ts`, `tsconfig.json`, `postcss.config.mjs`, `eslint.config.mjs`, `vercel.json`, `.gitignore` | Next 빌드·검사·배포 설정 |
| `tests/*.mjs` | 기존 임시 P03 검사 정착, 금액/복귀 경로와 HTTP 초기 HTML 검사 |
| `../README.md`, `README.md`, `AGENTS.md`, `docs/` | 현재 구조와 검증 기록 갱신 |

삭제한 기존 엔트리: `src/App.tsx`, `src/main.tsx`, `src/vite-env.d.ts`, `index.html`, `vite.config.ts`. 변경 전 백업에 보존되어 있다.

## 3. 보존 및 필요한 동작 차이

- 비회원 P03, 답변 수정/하위 답변 초기화, 보험·지원 이력 모름, 기기 보유 조건부 문구, 상담 질문과 복사문을 보존.
- 현재 로컬에는 원래 15개 결과 이후 의료급여 보완이 이미 들어 있다. R01~R21 데이터 중 R05는 기존 레거시로 현재 흐름에서 도달하지 않으며 **20개 결과 코드가 도달 가능**하다. Q4 지원 이력과 의료급여 Q5 신청 상태를 포함한 실제 구현을 유지했다.
- 질문/결과/여정/복사/검색 관련 원본 선언 **21개가 변경 전과 동일**함을 AST로 비교. 한쪽·양쪽 AMOUNTS 데이터 동일. P03 CSS, 제품 JSON, DirectClaimGuide 파일은 바이트 단위 동일.
- 전체 기준·보호자 왕복 시 답변, 제품, 금액 보기 유지. 복귀 제목 초점 및 스크롤 유지. 초기 HTML의 숨겨진 본문 때문에 중복 ID가 생기지 않는지 확인.
- 비용 상세 기본 접힘, 상담 질문 우선, 의료급여/보험 미확인 결과의 건강보험 금액 미노출 유지.
- 현재 로컬은 ‘전체 절차와 내 위치’를 설문 위에 두고 결과 다음 바로 ‘지금 할 일’을 보여준다. 과거 Handoff의 퀵배너를 다시 추가하지 않았다.
- React Router의 `location.state`에 있던 질문 상태는 공개 `?questionStatus=`로, 새 모의 글은 메모리/해당 방문 기록의 `iyumMockPost`로 대응. Next의 내부 history 필드는 보존한다. 답변을 서버에 저장하는 기능은 추가하지 않음.
- 목록 필터와 검색창을 URL에서 파생시켜 브라우저 뒤로/앞으로 가기 시 쿼리가 화면과 어긋나지 않게 함.
- 없는 경로는 같은 ‘준비 중’ 표현을 사용하지만 HTTP 상태는 기존 SPA의 200 대신 404.
- Next 공식 lint가 발견한 JSX 따옴표는 entity로 치환하여 표시 문구는 그대로 유지.
- 임시 글의 새로고침에서 초기 서버 예시 댓글 상태가 남던 이전 중 문제는 글 ID/모의 여부에 따른 컴포넌트 key로 수정. 기존 모의 글 본문은 해당 방문 기록에서 복원하고 댓글은 기존처럼 메모리 동작.

## 4. 실제 실행한 검사

| 검사 | 결과 |
| --- | --- |
| 변경 전 tsc / Vite build | 통과, 기존 큰 번들 경고 |
| pnpm 10.34.3 install --frozen-lockfile | 통과 |
| lint (ESLint Next core-web-vitals + TypeScript) | 오류 0, 경고 2. PostCreate/PostDetail의 기존 `<img>`; blob 이미지 모의 미리보기 유지 |
| typecheck (next typegen + tsc --noEmit) | 통과 |
| node --test tests/*.test.mjs | **5/5 테스트 통과** |
| Next.js production build | 통과. App Router 정적/동적 경로 생성 |
| production server + test:smoke | 15개 공개 URL 200, 없는 경로 404, robots 차단, 초기 HTML 본문 확인 |
| 브라우저 콘솔 | 검사한 Next 검증 탭에서 오류/경고 없음; hydration 오류 관찰되지 않음 |

P03 자동 검사에는 기존 임시 스크립트의 **24개 조합**(의료급여 이력×신청 12, 건강보험/보험 모름 이력 6, 보험별 처방 없음/모름 6)과 Q1 조기 결과 4개, 미답변 차단을 포함한다. 질문 순서, 이력/신청의 독립성, 기관/행동, 질문 3개, 화면용 정보와 복사문 일치, 문장 공백을 검사한다. 추가 테스트는 한쪽/양쪽 금액 및 안전한 내부 복귀 경로다. 기존 분기를 삭제하거나 테스트를 완화하지 않았다.

`test:smoke`는 script 태그 내용을 제외한 서버 응답 본문에서 홈·커뮤니티·검색·P03 공개 설명을 찾는다. P03의 청각장애 등록, 처음 청구/구입 후 1개월, 최대 99만 9천 원, 양쪽 안내, 공단·출처가 **초기 HTML에 있음**을 검사했다.

### 프로덕션 실제 브라우저

- 홈·커뮤니티·P03 직접 접속 및 새로고침; 홈 검색 → 쿼리 결과 → 범위 변경 → 브라우저 뒤로 가기에서 선택 복원.
- 모의 로그인 예제 값으로 로그인 → 기존 `from`에 담긴 커뮤니티 보청기 필터 URL 복귀. 실제 사용자 계정·외부 인증 사용 없음.
- 글쓰기 중 링크 이동/브라우저 뒤로 가기 → 기존 이탈 모달, 계속 작성 시 본문 유지, 나가기 후 실제 이동, 앞으로 가기로 빈 작성 화면 복귀.
- 로컬 임시 글/댓글 작성. 새 글 등록 후 새로고침에서 글 본문 유지 및 예시 댓글 혼입 없음. 실제 서버 게시/저장 없음.
- 건강보험 등록 완료+처방 완료+처음/있음/모름 결과, 답변 변경 후 사실 요약·질문 일치.
- 보험 모름+처방 있음/없음/모름과 의료급여 처방 모름/신청 대기 대표 경로: 미확인 유지, 건강보험 금액 미노출, 병원 처방 현재 단계 표시.
- 포낙 검색→19개 결과·6개씩 표시→Phonak Audeo L30-R 선택→R08 상담 질문 반영→전체 기준 왕복 후 유지.
- 상담 질문 복사 버튼 성공 표시와 내용 확인. **로컬 브라우저의 writeText를 검증용 함수로 대체**하여 성공 내용 캡처/강제 실패 → readonly 대체 입력란을 확인했다. 사용자 클립보드를 읽지 않았고 실제 OS 클립보드 기록의 성공 여부는 검증하지 않았다.
- 지원 이력 답변 변경 시 이전 복사 상태/대체 입력란 제거 및 새 질문 반영.
- 결과↔전체 기준의 상단/하단 복귀, 보호자 안내 왕복, 한쪽/양쪽 보기 유지. 검사 중 결과의 보호자 왕복 후 `p03-result-heading` 초점과 상단 80px 위치 확인.
- `#one-or-two`, `#center-product-check`, `#benefit` 직접 진입/새로고침, 대상 섹션 표시. 보호자 질문 바로가기의 제목 초점 이동.
- P03 1280px, 390px, 320px: 결과/비용 읽기 순서, 한쪽/양쪽 전환, 긴 조건 문구, 가로 넘침 없음. 숨김 뷰를 포함한 중복 ID 없음.
- 도움말 Enter 열기/Space 닫기/Tab 다음 요약 이동. 명확한 2px 포커스 테두리와 320px 열린 도움말 가로 넘침 없음.
- 홈과 커뮤니티도 390px/320px 실제 뷰포트로 확인. 아래 기존 화면 문제를 별도로 기록.

## 5. 알려진 한계와 다음 작업

### 이전 완료를 막지 않는 기존 화면/도구 문제

1. **320px 홈**: 기존 고정 다열 배치로 텍스트가 매우 좁게 줄바꿈되고 약 2px 가로 넘침(뷰포트 320, clientWidth 314, scrollWidth 316)을 관찰했다. 변경 전 Vite dist를 별도 로컬 서버에서 열어 같은 좁은 배치와 넘침(clientWidth 314, scrollWidth 326)을 확인했다. 이전이 만든 새 화면 구조 문제로 분류하지 않으며, 별도 모바일 홈/공통 내비게이션 디자인 개선 대상으로 남긴다. 커뮤니티/P03 검사 영역은 넘침 없음.
2. **ESLint 버전**: 현재 공식 Next 설정의 React/import/jsx-a11y 플러그인은 ESLint 9까지 호환된다. ESLint 10.11.0 시도 시 peer 경고와 getFilename 오류가 발생하여 공식 peer 범위의 9.39.5로 고정했다. 이 버전에는 레지스트리의 지원 종료 경고가 있다. 규칙을 끄지 않았고 lint 자체는 정상 실행된다. 상위 플러그인의 ESLint 10 호환 릴리스가 나오면 함께 갱신한다.
3. 기존 두 `<img>` 경고를 남겼다. 실제 파일 업로드/이미지 저장 서비스를 정할 때 Next Image 적용 범위를 검토한다.
4. 제품 JSON은 현재 206개 스냅샷이다. 기존 2026-09-30 최신성 경고 기준을 바꾸지 않았다. 정책·제품의 최신성을 이번 프레임워크 이전에서 재조사하거나 재확정하지 않았다.
5. 커뮤니티 예시 데이터, 모의 계정, 준비 중 링크/푸터 예시 운영 정보는 기존대로다. 실제 회원·게시글 데이터로 오해하지 않는다.

### 미수행

- Vercel 실제 업로드/프로덕션 배포, 대시보드 설정 확인/변경.
- 실제 모바일 하드웨어, 화면낭독기, 보호자/사용자 이해도 평가.
- 실제 OS 클립보드 성공 검증, 이미지 파일 업로드 전 과정, 회원가입 모달/예외의 모든 조합.
- Node 22 런타임 실행, 모든 브라우저/방문 기록 경계 조건에 대한 자동 E2E. 이번 실행은 Node 24.19.0과 Codex 브라우저 뷰포트 검사.
- 새로고침 시 사용자 입력을 영구 보존하는 기능, 실제 인증/권한/서버 검증은 이번 범위가 아님.

### 다음 단계

회원가입·로그인과 커뮤니티를 Spring Boot API에 연결하는 작업으로 넘어갈 수 있다. Next 빌드/SSR/경로 기반이 마련됐고 기존 P03을 공개 상태로 유지했다. 다음 작업에서 인증 계약(세션/쿠키, CORS/CSRF, 로그인 복귀, 오류 응답)과 사용자/게시글 권한을 백엔드와 함께 정한다. 이것을 이번 작업에서 임의로 Next 백엔드 로직으로 구현하지 않았다.

정확한 Vercel 설정과 web 자체 업로드/상위 저장소 업로드의 차이는 [README](../README.md#vercel-기존-iyum--iyum-validation-사용)에 기록했다. 현재 프로덕션 로컬 확인 URL은 http://127.0.0.1:8443/ 이며, 수정 개발을 시작할 때는 기존 서버를 종료하고 `pnpm dev`로 전환한다.

## 6. 근거와 작업 보호

- [Next.js Vite 이전 공식 가이드](https://nextjs.org/docs/app/guides/migrating/from-vite): SPA/static export는 중간 이전 방법이므로 최종 구성에 그대로 사용하지 않았다.
- [Server/Client Components](https://nextjs.org/docs/app/getting-started/server-and-client-components): 초기 서버 HTML과 필요한 상호작용 경계를 구분했다.
- [설치/호환성](https://nextjs.org/docs/app/getting-started/installation), [Next Link](https://nextjs.org/docs/app/api-reference/components/link), [useRouter](https://nextjs.org/docs/app/api-reference/functions/use-router).
- [Vercel 빌드 설정](https://vercel.com/docs/builds/configure-a-build), [패키지 매니저](https://vercel.com/docs/package-managers), [CLI 옵션](https://vercel.com/docs/cli/global-options).

자동 승인 검토가 대량 파일 이동/재작성 및 P03 본문 자동 추출을 처음에는 덮어쓰기 위험으로 거부했다. 그 명령들은 실행되지 않았다. 백업 대조 후 작은 단위 변경으로 진행하고, P03 분리 재작성은 하지 않았다. 초기 HTML 포함에 필요한 뷰 래퍼 변경만 임시 후보의 JSX 구문/원본 선언 보존을 확인한 뒤 적용했다. 남아 있는 승인 차단은 없다.
