# IYUM 통합 웹

기존 Figma Make React/Vite 통합본을 **Next.js 16.3.6 App Router**로 이전한 현재 개발 앱입니다. React/React DOM 19.2.4, TypeScript 5.9.3, Tailwind 4.2.2를 사용합니다. 디자인·P03 정책·제품 데이터는 기존 로컬 구현이 기준입니다. 회원가입은 로컬 Spring Boot REST API에 연결되어 실제 DB에 PENDING 회원을 저장합니다. 커뮤니티와 로그인은 기존 모의 구현입니다. 이메일 인증·메일 발송·회원 로그인은 아직 구현하지 않았습니다.

## 로컬 실행

Node 22.13 이상(22.x) 또는 24.x, pnpm **10.34.3**을 사용합니다. 현재 `.mise.toml`은 Node 24를 선택하며, 회원가입 통합 검증 런타임은 Node 24.19.0입니다. pnpm이 없으면 기존 mise 설정을 사용하거나 `npx --yes pnpm@10.34.3`으로 아래 명령의 `pnpm`을 대체할 수 있습니다.

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

회원가입은 브라우저에서 Spring API를 직접 호출합니다. [환경변수 예시](.env.example)를 참고하여 `.env.local`에 다음 공개 주소를 추가합니다. 기존 파일에 배포용 값이 있다면 파일을 덮어쓰지 말고 이 항목만 추가하세요.

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082
```

`NEXT_PUBLIC_*`는 브라우저 번들에 포함됩니다. 비밀값을 넣지 마세요. 주소를 변경하면 개발 서버를 재시작하고, `pnpm start`를 사용할 때는 다시 빌드해야 합니다. 실제 `.env.local`은 Git에서 제외하고 `.env.example`만 공유합니다.

백엔드 디렉터리에서 기존 DB 환경변수와 Docker를 준비하고 실행합니다.

```sh
cd /Users/tackeunoh/Developer/iyum/server/iyum-api
./gradlew bootRun --args='--spring.profiles.active=dev'
```

다른 터미널에서 `web/`의 `pnpm dev`를 실행하고 **http://127.0.0.1:8443/register**를 엽니다. 이미 서버가 실행 중이면 중복 실행하지 마세요.

`dev` 프로필은 `http://127.0.0.1:8443` Origin에서 오는 가입·CSRF 요청만 쿠키와 함께 허용합니다. 프론트엔드 주소를 바꾸면 백엔드의 `IYUM_WEB_ORIGIN`도 정확한 Origin으로 지정해야 합니다. `localhost`와 `127.0.0.1`은 다른 호스트이므로 혼용하지 않습니다. 기본 예시는 양쪽 모두 `127.0.0.1`을 사용합니다.

브라우저가 CSRF 토큰을 자동 조회하며 쿠키를 유지합니다. 가입 완료 화면은 실제 `201 / PENDING` 응답이 있을 때만 성공을 표시합니다. 약관 선택·가입 결과는 가입 경로의 메모리에만 있으므로 새로고침하면 초기화되며, 완료 URL 직접 방문으로 가입 성공을 만들지 않습니다.

구조·코드 읽는 순서·오류 처리·검증 결과는 [회원가입 REST API 통합 기록](docs/Signup-API-Integration-2026-10-05.md)을 참고하세요.

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

실행 결과·브라우저 검증·기존 화면 문제는 [이전 기록](docs/Next-App-Router-Migration-2026-09-29.md)에 구분했습니다. 회원가입의 실제 API 연결 결과는 [2026-10-05 통합 기록](docs/Signup-API-Integration-2026-10-05.md)에 추가했습니다. 실제 인증 완료 또는 서비스 출시 준비 완료를 뜻하지 않습니다.
