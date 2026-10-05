# IYUM 통합 웹

기존 Figma Make React/Vite 통합본을 **Next.js 16.3.6 App Router**로 이전한 현재 개발 앱입니다. React/React DOM 19.2.4, TypeScript 5.9.3, Tailwind 4.2.2를 사용합니다. 디자인·P03 정책·제품 데이터는 기존 로컬 구현이 기준입니다. 커뮤니티와 회원 화면은 모의 구현이며 실제 API·DB 연결은 없습니다.

## 로컬 실행

Node 22.13 이상(22.x) 또는 24.x, pnpm **10.34.3**을 사용합니다. `.mise.toml`은 Node 22, 이번 검증 런타임은 Node 24.19.0입니다. pnpm이 없으면 기존 mise 설정을 사용하거나 `npx --yes pnpm@10.34.3`으로 아래 명령의 `pnpm`을 대체할 수 있습니다.

```sh
cd /Users/tackeunoh/Developer/iyum/web
pnpm install --frozen-lockfile
pnpm dev
```

http://127.0.0.1:8443/ 에서 확인합니다. 이미 실행 중인 8443 서버가 있다면 중복 실행하지 않습니다. 프로덕션 검증 서버는 자동 갱신되지 않으므로 수정할 때는 해당 서버를 종료한 뒤 `pnpm dev`를 실행합니다.

```sh
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm start
```

`pnpm start`가 실행 중인 상태에서 다른 터미널에서 `pnpm test:smoke`를 실행합니다. 다른 포트라면 `BASE_URL=http://127.0.0.1:포트 pnpm test:smoke`를 사용합니다.

## 구조와 화면

- `src/app/layout.tsx`: 서버 공통 레이아웃과 기존 헤더/내비게이션/푸터.
- `src/app/**/page.tsx`: 기존 공개 URL마다 개별 App Router 엔트리.
- `src/features/`: 기존 `src/pages/`의 화면과 P03 콘텐츠. 화면 수정은 이 폴더에서 진행합니다.
- `src/components/`: 공통 UI, Next Link, 작성 중 이탈 방지, 검색/내비게이션.
- `src/lib/`: URL 쿼리·안전한 내부 복귀·모의 게시글 상태.
- `src/data/hearingAidProducts.json`: 기존 제품 206개 스냅샷.
- `src/index.css`, `src/features/HearingAidGuide.css`: 기존 스타일; Tailwind는 PostCSS로 처리합니다. Google Fonts CSS 연결은 유지했습니다.

홈·커뮤니티 시작·복지·공통 레이아웃은 Server Component를 사용합니다. 검색/목록/로그인 등 쿼리를 읽는 화면은 요청 시 서버 렌더링되며, 상호작용 부분만 Client Component입니다. P03은 기존 상호작용 컴포넌트를 서버에서 사전 렌더링합니다. 전체 기준과 보호자 안내 본문도 초기 HTML에 포함하고 선택하지 않은 영역은 `hidden`으로 감춥니다. 전체 앱을 브라우저 전용으로 렌더링하지 않습니다.

유지한 경로: `/`, `/community`, `/community/posts`, `/community/posts/create`, `/community/posts/[postId]`, `/search`, `/login`, `/register`, `/register/info`, `/register/verify`, `/welfare`, `/validation/hearing-aid-health-insurance`, `/forgot-password`, `/hospitals`, `/devices`. 준비 중 화면도 기존 경로를 유지하며, 그 외 없는 경로는 HTTP 404입니다.

P03 안내 왕복은 같은 페이지 안의 상태 전환입니다. 답변·현재 단계·선택 제품·한쪽/양쪽 보기 상태를 유지하지만 새로고침하면 설문 답변은 초기화됩니다. `#one-or-two`, `#registration-help`, `#benefit`, `#center-product-check` 등의 직접 진입을 지원합니다. 다른 앱 경로로 나갔다가 재진입할 때 답변을 영구 보관하는 기능은 없습니다.

임시 게시글은 React 상태와 현재 브라우저 방문 기록에만 유지합니다. 서버에 저장하지 않으며, 다른 기기나 새 탭에 공유해도 실제 저장된 게시글이 되지 않습니다. 임시 댓글은 새로고침하면 사라집니다. 모의 로그인은 실제 인증이 아닙니다.

## 환경변수

현재 앱에 필요한 API 환경변수는 없습니다. 기존 `.env.local`의 배포용 인증 값은 브라우저에 공개하지 않습니다. `VITE_*` 앱 환경변수는 발견되지 않았으며 Vite 전용 실행 설정은 제거했습니다. 향후 비밀값은 서버 전용으로 두고, 공개 가능한 API 주소 등만 `NEXT_PUBLIC_*`로 정의합니다. `.env*`와 `.vercel`은 Git에 포함하지 않습니다.

## Vercel: 기존 iyum / iyum-validation 사용

현재 `web/.vercel/project.json`이 `iyum-validation`에 연결되어 있습니다. 실제 대시보드 값은 이번 작업에서 조회·변경하지 않았습니다. 아래는 사용자가 적용할 설정이며 **이번 작업에서 배포하지 않았습니다**.

### 권장: web 폴더 자체 업로드

Vercel의 `iyum` 팀 → `iyum-validation` 프로젝트에서 설정합니다.

| 항목 | 값 |
| --- | --- |
| Framework Preset | Next.js |
| Root Directory | 비워 두기 또는 `.` — 업로드 루트가 web 자체이기 때문 |
| Build Command | `pnpm build` (`vercel.json`에도 지정) |
| Output Directory | Override 끄기, Next.js 기본값. `dist`, `out` 또는 수동 `.next` 지정 제거 |
| Install Command | Override 끄기, 자동 설치 |
| Node.js Version | `24.x` 권장(이번 로컬 검증과 같은 주 버전) |
| 빌드 환경변수 | `ENABLE_EXPERIMENTAL_COREPACK=1` — packageManager의 pnpm 10.34.3 사용 |

기존 Install Override에 `pnpm install`만 넣어 놓았다면 제거합니다. Vercel의 Corepack 지원을 사용하여 잠금 파일과 pnpm 버전이 일치하게 합니다. 필요할 때 대시보드 설치 명령의 대안은 `npx --yes pnpm@10.34.3 install --frozen-lockfile`이며, 두 방법을 혼합할 필요는 없습니다.

설정 확인 후 사용자가 실행할 배포 명령:

```sh
cd /Users/tackeunoh/Developer/iyum/web
npx vercel --prod --scope iyum
```

상위 폴더에서 실행하더라도 **web 자체를 업로드**하려면 다음과 같이 지정합니다. 이 경우에도 Root Directory는 `web`이 아니라 빈 값/`.`입니다.

```sh
npx vercel --cwd /Users/tackeunoh/Developer/iyum/web --prod --scope iyum
```

`.vercel` 연결 정보가 사라졌을 때만 해당 web 폴더에서 `npx vercel link --project iyum-validation --scope iyum`으로 다시 연결합니다.

### 별도 방식: 상위 저장소 전체를 업로드하거나 Git 연동

업로드 루트가 `/Users/tackeunoh/Developer/iyum`인 경우에만 Root Directory를 **`web`**으로 설정합니다. 이 방식은 현재 `web/.vercel` 연결과 다릅니다. 상위 폴더에는 별도 연결 정보가 없으므로, CLI로 이 방식을 택하면 상위 폴더에서 프로젝트 연결 후 배포해야 합니다.

```sh
cd /Users/tackeunoh/Developer/iyum
npx vercel link --project iyum-validation --scope iyum
npx vercel --prod --scope iyum
```

이 방식은 보관용 `prototypes/`, `archive/`까지 업로드 범위가 될 수 있어 현재는 web 자체 업로드를 권장합니다. **`--cwd web`과 대시보드 Root Directory `web`을 함께 사용하면 경로가 어긋납니다.**

`vercel.json`에는 Next.js 프레임워크와 빌드 명령만 있습니다. SPA rewrite, static export, 수동 outputDirectory는 없습니다. 기존 noindex/robots 차단은 유지했습니다. 실제 서비스 공개 시 검색 노출 정책은 별도 작업으로 결정합니다.

공식 참고: [Vercel 빌드 설정](https://vercel.com/docs/builds/configure-a-build), [패키지 매니저·Corepack](https://vercel.com/docs/package-managers), [CLI --cwd](https://vercel.com/docs/cli/global-options).

## 검증 및 남은 한계

실행 결과·브라우저 검증·기존 화면 문제는 [이전 기록](docs/Next-App-Router-Migration-2026-09-29.md)에 구분했습니다. 프런트 실행 기반은 회원가입·로그인과 Spring Boot 연결 설계로 넘어갈 수 있는 상태입니다. 실제 인증 완료 또는 서비스 출시 준비 완료를 뜻하지 않습니다.
