# 정보 페이지 6차 시안 · Next.js 서비스 반영

2026-10-14. 승인된 [6차 시안](info-pages/2026-10-14-preview/README.md)을 실제 서비스에 반영했다. 기준은 로컬 `main`의 `1ceb848723b3aee16c6c77d056c2af863c52184e`이며, 작업 브랜치는 **refactor/info-pages**다. 기존 AGENTS 변경, PRODUCT-CONTEXT, 시안·history·이전 ZIP을 보존했다. 커밋·push·PR·병합·배포는 하지 않았다.

## 실제 반영 화면

| 화면 | URL / 변경 |
|---|---|
| 공통 메뉴 | PC·모바일 모두 IYUM 소개 → 커뮤니티 → 정보 찾기. 정보 하위는 복지·지원정보 / 기기 정보 / 병원·전문기관 |
| 소개 | `/about` 신설. 장애 전반의 정보·커뮤니티 비전, 초기 청각장애 집중, 현재 기능과 확장 계획을 구분 |
| 홈 | 기존 `/#iyum-about` 유지, 요약 문구와 `/about` 연결. 홈의 예시 게시글 표기·마감 공고 없는 상태 유지 |
| 복지 목록 | `/welfare`. 실제 보청기 안내 1건, 제도 안내 표기. 불필요한 검색·필터·페이지네이션·가상 공고 없음 |
| 보청기 안내 | `/validation/hearing-aid-health-insurance`. 미선택 질문부터 시작, 답한 질문을 위에 유지하고 결과를 아래에 표시 |
| 전체 기준 | 같은 P03 안에서 전체 절차를 먼저 표시. 금액·양쪽 지원·제품/판매처·공식 출처는 기존 내용 재사용 |
| 준비 중 | `/devices`, `/hospitals`의 기존 제공 상태 유지 |

소개와 복지 목록은 Server Component다. 제목·본문·링크가 초기 HTML에 있으며 JavaScript 없이 읽고 이동할 수 있다. 페이지별 title/description과 메뉴 현재 표시를 추가했고, 루트의 noindex/nofollow 및 robots 차단을 유지했다.

## 핵심 파일과 역할

- [GlobalNav.tsx](../src/components/GlobalNav.tsx): 메뉴 순서·하위 항목·현재 위치만 조정. 기존 메뉴 열기/Escape와 이탈 확인 취소 시 초점 복귀 유지.
- [Shell.tsx](../src/components/Shell.tsx), [HomePage.tsx](../src/features/HomePage.tsx): 소개 페이지 연결. 기존 로고·인증 영역 재사용.
- [AboutPage.tsx](../src/features/AboutPage.tsx), [WelfarePage.tsx](../src/features/WelfarePage.tsx), `src/app/about/page.tsx`, `src/app/welfare/page.tsx`: 실제 콘텐츠와 메타데이터.
- [info.css](../src/styles/info.css): 기존 `--iyum-*` 네이비·청록·화이트 토큰을 사용하는 소개/목록 전용 스타일. 루트 layout은 이 스타일 import만 추가.
- [HearingAidGuide.tsx](../src/features/HearingAidGuide.tsx), [HearingAidGuide.css](../src/features/HearingAidGuide.css): 기존 정책 결과를 승인된 질문·결과·전체 기준 구성에 연결.
- [HearingAidActions.tsx](../src/features/HearingAidActions.tsx): 기관 아이콘 → 문의 문장/확인 체크 → 준비 체크 → 다음 단계. 기관별 문구는 기존 `getResultInfo()` 결과를 그대로 받는다.
- [hearingAidActionGuidance.ts](../src/data/hearingAidActionGuidance.ts): 승인된 6차의 표시용 준비·다음 단계·체크 문구. 결과 판정 함수가 아니다.
- [hearing-aid-notes.ts](../src/lib/hearing-aid-notes.ts): `buildInquiryMemo()`가 현재 상황·기관·문의·체크·조건·다음 단계를 `[ ]`/`[x]` 줄바꿈 양식으로 구성.
- [info-pages-browser.mjs](../tests/info-pages-browser.mjs), [hearing-aid-notes.test.mjs](../tests/hearing-aid-notes.test.mjs): 새 통합 회귀와 메모 단위 검사.
- 루트/web AGENTS의 Git 초기 상태·인증/게시글 모의 설명은 현재 코드에 맞게 최소 수정. 기존 작업 규칙은 유지했다.

## 기존 로직과 새 화면 상태의 경계

**그대로 유지한 것:** `computeResult`, `getResultInfo`, `computeFacts`, `computeJourney`, `computeTopBarSteps`, `getAnswerJourneyStep` 및 선택지·결과 원문·제품 검색·최신성 판단 등 25개 선언을 HEAD와 비교하여 동일함을 확인했다. `HearingAidBenefits.tsx`, `DirectClaimGuide.tsx`, `RegistryCheck.tsx`도 바이트 단위로 동일하다. 금액, 양쪽 지원 조건, 의료급여 Q4/Q5, 보험 모름, 이전 이력에 따른 표현, 제품 206개 스냅샷 및 공식 확인일을 변경하지 않았다.

**새로 추가한 것:** 결과 안의 `checked` Set은 개인 메모다. 부모 ResultSection의 key를 모든 답변으로 구성해 답변이 바뀌면 같은 결과 코드라도 메모를 초기화한다. 상세 화면을 읽는 동안 guided 영역을 unmount하지 않고 `hidden` 처리하므로 답변·체크·제품 선택이 유지된다. 숨은 영역은 초점 순서와 표시에서 제외한다. 새로고침/페이지 이탈 시 메모가 사라지며 서버·분석 도구·브라우저 저장소에 보내거나 저장하지 않는다. 체크는 계산 함수에 전달하지 않는다.

`followQuestionRef`는 답변 선택/수정 때만 다음 질문 또는 결과에 초점을 주고 부드럽게 스크롤한다. reduced-motion은 즉시 이동한다. 상세 복귀와 본문 바로가기는 이 이동을 예약하지 않는다.

`goTo()`는 기존 `NavEntry`의 scrollY·focusId·열린 details를 보관한다. `focusReference()`는 목표와 상위 details를 펼친 후 초점을 이동한다. R08 청구 링크는 검수·청구 및 직접 청구 설명까지 연다. 새 양쪽 지원 링크는 전체 기준의 `benefit`에 양쪽 모드를 열며, 기존 `#one-or-two` 직접 주소는 별도 기존 화면으로 계속 동작한다. 복귀 시 기존의 결과 제목 강제 이동 대신 실제 누른 버튼과 scrollY를 복원한다.

`CopyButton`의 실패 시 textarea 선택 대안을 유지한다. 기관 문의 메모를 우선 복사하고, 기존 센터 질문 3개/전체 복사는 실제 센터 상담 단계 R08·R18의 추가 펼침 안에 둔다. 선택한 제품을 반영하는 기존 조건도 보존한다.

6차에서 없앤 반복적인 준비/다음 단계 상세 링크는 추가하지 않았다. R08의 구체적인 청구 방법, 전체 기준·양쪽 지원, 기존 온라인 상담 및 공식 출처 연결만 유지한다.

## 검토 문제 2건

1. **시안 본문 바로가기:** `#main`이 `render()`의 화면 이름으로 해석되면서 About으로 fallback하고 결과가 사라졌다. `.skip`은 기본 hash 탐색을 막고 현재 main에 focus/scroll만 하도록 변경했다. hashchange로 `#main`이 들어온 경우도 현재 화면을 다시 렌더하지 않는다. 실제 Next 서비스는 기존 `Shell → #main-content-start` 구조를 유지하며 P03의 인식 대상 hash와 분리되어 있다. 소개·복지 목록·설문 결과에서 키보드 실행 후 경로/답변/체크/본문 보존을 확인했다.
2. **시안 필터 크기 변경:** 초기 모바일에서 닫힌 details의 summary를 PC CSS가 숨기면서 내용과 조작이 모두 사라졌다. summary를 폭에 관계없이 조작할 수 있게 했다. DOM을 재생성하거나 선택값을 초기화하지 않는다. 390 → 1440 → 390 → 1440에서 닫힌 필터 접근, 선택 중인 지역과 적용한 유형, 기존 결과 유지 확인. 실제 서비스 목록은 한 건이므로 필터를 추가하지 않았다.

시안 변경 파일은 `preview/preview.js`, `preview/preview.css`이며 기존 `index.html`의 skip 링크는 제거하지 않았다. 새 검사: [integration-review-browser.mjs](info-pages/2026-10-14-preview/checks/integration-review-browser.mjs). 이전 history와 캡처는 덮어쓰지 않았다.

## 검증 결과

| 직접 실행 | 결과 / 범위 |
|---|---|
| `pnpm test` | **167 통과**, 실패·skip 0. 기존 P03 28개 완료 조합, 금액 및 인증/커뮤니티 단위 검사 포함 |
| `pnpm typecheck` | 통과 |
| `pnpm lint` | 통과 |
| `pnpm build` | 통과. `/about`, `/welfare`, P03 정적 HTML 생성 확인 |
| `tests/info-pages-browser.mjs` | **15 시나리오 통과**, 내부 28개 P03 조합·1440/390/320·무JS About/목록·skip·복사 성공/실패·복귀·새 메뉴 dirty 확인 포함 |
| `tests/login-browser.mjs` | **38 통과**, 모의 로그인/로그아웃·지연 응답·추가 작성·알림 타이머/초점 |
| `tests/auth-flow-browser.mjs` | **26 통과**, 모의 가입/인증·늦은 응답·수동 로그인 전환·이탈·기존 세션 |
| `tests/community-browser.mjs` | **47 통과**, 별도 SSR/API 모의. 상세/목록·등록·이탈 승인과 목적지 지연·입력 보호 |
| `tests/production-smoke.mjs` | 통과. 실제 Spring 공개 GET과 초기 HTML 비교, 실제 글 제목/본문/작성자, HTTP 404·noindex 확인. DB 쓰기 없음 |
| 시안 새 회귀 | **4 통과**. 현재 페이지 skip 3화면 + 반응형 필터 |
| 기존 시안 6차 회귀 | **19 통과**, 이전 산출물을 덮어쓰지 않도록 출력 위치만 임시 폴더로 변경하여 실행 |
| 정책 보존 비교 / `git diff --check` | 통과. 실제 결과 문구 대비 최저 5.00:1 |

처음 검사에서 기존 ResultInfo의 null 허용과 표시용 타입 불일치를 바로잡았다. 브라우저 테스트는 답변이 화면에 남는 특성에 맞춰 같은 이름의 선택지를 질문 영역별로 지정했고, React 표시 완료를 기다리도록 했다. 제품 스냅샷은 기존 2026-09-30 제한 이후이므로 최신성 경고와 추천 질문 자동 반영 제한을 기대하도록 검사했다. 정책 제한을 완화하거나 테스트를 비활성화하지 않았다. 임시 빌드 복사 시 의존성 dist까지 제외했던 설정은 해당 의존성을 다시 복사한 뒤 정상 빌드했다.

### 실행 환경과 재현 명령

Node 24.21.0 / pnpm 10.34.3 / Chrome + Playwright. 기존 Next `:8443`, 시안 `:8450`, Spring `:8082`를 확인하고 보존했다. 프로덕션은 `.env*`를 제외한 별도 임시 복사본에서 빌드해 기존 dev `.next`를 건드리지 않았다. 테스트용 환경값은 공개 로컬 주소만 사용했다.

```sh
# web/에서
pnpm test
pnpm typecheck
pnpm lint
pnpm build

# 프로덕션 서버가 실행된 주소로 지정. Playwright가 프로젝트에 없으면
# PLAYWRIGHT_MODULE에 설치된 Playwright 모듈 경로를 전달한다.
BASE_URL=http://127.0.0.1:8451 node tests/info-pages-browser.mjs
BASE_URL=http://127.0.0.1:8451 node tests/login-browser.mjs
BASE_URL=http://127.0.0.1:8451 node tests/auth-flow-browser.mjs
BASE_URL=http://127.0.0.1:8451 node tests/production-smoke.mjs

# 커뮤니티: 별도 Next를 IYUM_API_INTERNAL_URL=http://127.0.0.1:8856 으로 실행
BASE_URL=http://127.0.0.1:8452 COMMUNITY_SSR_MOCK_PORT=8856 node tests/community-browser.mjs
node docs/info-pages/2026-10-14-preview/checks/integration-review-browser.mjs
```

설정 이름은 기존 `NEXT_PUBLIC_API_BASE_URL`(브라우저, 예: `http://127.0.0.1:8082`)과 `IYUM_API_INTERNAL_URL`(서버 전용, 같은 로컬 주소 예시)이다. 새 환경변수/서버 API/라이브러리는 추가하지 않았다. 실제 `.env` 값은 문서에 복사하지 않았다.

## 제한 및 후속 확인

- 인증/게시글 쓰기 검증은 **모의**이며 이번 작업에서 실제 가입·이메일·로그인·게시글 생성을 새로 하지 않았다. 실제 Spring 검증은 기존 공개 데이터의 읽기 전용 GET/SSR 비교다. 생성/삭제한 DB 데이터 0건.
- **기존 커뮤니티 무JS 404 본문·목록 링크 실패 2건**은 별도 알려진 미해결 문제다. 이번 작업에서 해당 소스/검사를 바꾸거나 재실행하지 않았으며 신규 실패 또는 해결로 분류하지 않는다. HTTP 404 상태가 유지된다는 smoke 결과와 혼동하지 않는다.
- 기존 제품 스냅샷은 2026-09-30 이후 최신성 제한이 있다. 데이터/금액/공식 확인일을 임의 갱신하지 않았다. 정책 최신성 조사와 외부 상담 접수는 이번 검증 대상이 아니다.
- 실제 휴대폰, Safari/Firefox, 스크린리더 음성은 미검증이다. Chromium의 키보드·무JS DOM·반응형 뷰포트로 확인했다.
- 공고 수집, 기기/기관 탐색, AI/개인화는 구현하지 않았다. 가상 공고·고정 D-day·시안 도구·개발자 안내·고정 localhost 링크는 서비스 화면에 없다.

[실제 서비스 캡처와 검증 기록](info-pages/2026-10-14-integration/README.md). 기존 개발 서버는 유지하며 직접 시작한 `:8451`, `:8452` 및 모의 `:8856`만 종료했다. 변경은 검토 가능한 미커밋 상태다.


## 2026-10-15 후속 수정: 설문 위에서 읽는 안내 모달

이 절이 앞서 작성된 **상세 전환/복귀 방식** 설명을 대체한다. 이전 검증·시안·캡처는 이력으로 보존했다.

### 원인과 변경

- `기존 30초 요약`은 기존 랜딩 화면의 긴 소개와 별도 시작 버튼을 그대로 렌더했다. 승인된 6차의 두 항목(전체 흐름 / 센터에서 확인할 것)을 네이비·청록 카드로 구성하고, 버튼 이름을 `30초 요약`으로 정리했다. 금액·조건은 원래 전체 기준에서 확인하며 정책 계산을 바꾸지 않았다.
- `goTo()`가 `view`를 변경해 설문을 `hidden` 처리했으므로 사용자에게 페이지 전체가 바뀌는 경험을 주었다. 이제 설문은 계속 표시·마운트되어 있고 요약/전체 기준/양쪽 지원/청구 설명만 **한 개의 native dialog** 안에서 연다. 정상 버튼 클릭은 URL과 history를 변경하지 않는다.
- 새 `HearingAidReferenceDialog.tsx`가 `showModal()`로 배경 조작과 초점을 차단하고, 본문만 스크롤한다. 상단 `닫기`, 하단 복귀 버튼, Esc로 닫으면 원래 버튼과 문서 스크롤로 복귀한다. Tab/Shift+Tab 순환, 모바일 문서 스크롤 잠금, 닫기·컴포넌트 해제 시 기존 body 스타일 복원도 담당한다. 바깥 클릭으로 우발적으로 닫지 않는다.
- `HearingAidGuide.tsx`의 `goTo()`는 안내 내용/목표만 선택하고, `closeReference()`는 설문 상태를 변경하지 않는다. `focusReference()`는 필요한 details를 펼쳐 **모달 내부**에서 해당 설명에 초점·스크롤을 이동한다. 원래 화면을 숨기고 복원하던 `NavEntry` 스택은 불필요해져 제거했다. 요약에서 전체 기준으로 이어 읽어도 최초 설문 버튼이 복귀 대상이다.
- 기존 쿼리 및 `#steps`, `#benefit`, `#sources`, `#already-purchased`, `#one-or-two` 등의 직접 주소는 해당 설명을 모달로 연다. `#one-or-two`의 기존 보호자 콘텐츠도 유지한다. 직접 주소의 hash는 닫을 때 임의로 지우지 않으므로 해당 주소를 새로고침하면 설명이 다시 열린다. 모달 안의 목차는 URL을 바꾸지 않고 내부에서 이동한다. 공통 본문 바로가기는 영향을 받지 않는다.

### 이번 변경 파일

- `src/features/HearingAidGuide.tsx`: 요약 내용, 모달 연결·닫기·설명 초점, 설문 상시 유지.
- `src/features/HearingAidReferenceDialog.tsx`: P03 전용 모달, 키보드/스크롤/초점 생명주기.
- `src/features/HearingAidGuide.css`: 기존 토큰의 모달·요약 카드·320px 배치.
- `tests/info-pages-browser.mjs`: 기존 상세 복귀 검사를 실제 닫기 조작으로 갱신. 정책·복사·제품·이탈 검사는 유지.
- `tests/hearing-aid-dialog-browser.mjs`: 모달 9개 회귀 시나리오.
- 본 문서와 `docs/info-pages/2026-10-15-dialog/`: 새 검증/화면 기록.

인증·NavigationGuard·커뮤니티·백엔드·API 설정은 이번 후속 작업에서 수정하지 않았다. 이전 사용자 미커밋·미추적 변경은 그대로 남겨 두었다.

### 직접 실행한 검증

| 검사 | 결과 |
|---|---|
| `pnpm test` | 167 통과, 실패/skip 0 |
| `pnpm typecheck`, `pnpm lint` | 통과 |
| `pnpm build` | 환경파일 없는 별도 임시 복사본에서 프로덕션 빌드 통과 |
| `tests/hearing-aid-dialog-browser.mjs` | **9 통과**: 모든 안내 진입, 요약→전체 기준, 질문 진행 중/결과 체크 보존, 내부 목차, 닫기/Esc, 키보드 순환, 배경 비활성, 직접 hash, 1440/390/320, 리사이즈, 페이지 이탈 시 스크롤 잠금 정리 |
| `tests/info-pages-browser.mjs` | **15 통과**: 28개 정책 조합, 답변 초기화, 복사 성공/실패, R08 직접 청구, 실제 제품 검색/선택, 본문 바로가기, 소개/복지 초기 HTML, 커뮤니티 작성 이탈 취소/승인 등 |
| 정책 보존 비교 | 25개 정책/검색/최신성 선언 및 `HearingAidBenefits`, `DirectClaimGuide`, `RegistryCheck` 바이트 동일 |
| `git diff --check` | 통과 |

브라우저 검사는 임시 프로덕션 `:8451`에서 실제 Next UI를 조작했다. 헤더 `/me`와 작성 이탈 테스트의 인증 상태는 **모의**이며, 실제 Spring 가입/로그인/게시글 쓰기를 이번에 다시 검사한 것은 아니다. 새 DB 데이터 0건. 기존 커뮤니티 무JS 404 실패 2건은 별도 미해결로 유지하고 재실행하지 않았다. 실제 휴대폰·Safari/Firefox·스크린리더 음성은 미검증이다.

```sh
# web/; Playwright 미설치 환경은 PLAYWRIGHT_MODULE에 외부 모듈 경로 지정
BASE_URL=http://127.0.0.1:8451 node tests/hearing-aid-dialog-browser.mjs
BASE_URL=http://127.0.0.1:8451 node tests/info-pages-browser.mjs
```

작업 시작 당시 `refactor/info-pages` / HEAD `1ceb848723b3aee16c6c77d056c2af863c52184e`, 미커밋 변경이 있는 상태였다. 브랜치/HEAD는 유지하고 커밋·push·배포하지 않았다. 기존 `:8450` 시안과 `:8082` Spring은 유지했다. `:8443`은 처음 실행 중이었지만 최초 브라우저 접속 시 연결이 거절되었고 포트도 비어 있었다(이 작업에서 종료 명령을 보내지 않음). 실제 프로젝트의 `pnpm dev`를 다시 실행해 검토용으로 남기고, 직접 실행한 임시 프로덕션 `:8451`은 검증 후 종료했다. 최종 포트 확인: `:8443`(개발 서버 PID 78741), `:8450`, `:8082` 실행 / `:8451` 비어 있음. 재기동 때 Turbopack은 이전 내부 오류 감지에 따른 캐시 재생성을 안내했으며, 이후 실제 `:8443`에서도 390px 요약→전체 기준→닫기, 초점 복귀와 가로 넘침 없음, 런타임 오류 없음을 확인했다.

[PC·모바일 캡처와 검증 JSON](info-pages/2026-10-15-dialog/README.md).


## 2026-10-15 후속 수정: 기본 Tab 순서와 읽기 초점/스크롤

이번 초점·스크롤 변경 및 기존 검증과의 차이는 [후속 검토 문서](info-pages/2026-10-15-focus/README.md)에 정리했다.

- `focusReference()`가 summary에 tabindex=-1을 덮어쓰던 문제를 수정했다. 04를 접은 뒤 Tab 05 → Shift+Tab 04, Enter/Space, 재열기·목차 회귀를 확인했다.
- 마우스/터치 설문은 제목 초점을 강제로 이동하지 않고, 다음 읽기 영역이 보이지 않을 때만 최소 이동한다. 키보드 설문은 읽기 초점을 유지하되 큰 제목 테두리 대신 작은 청록 표시를 사용한다. 조작 요소의 focus-visible과 오류/본문 바로가기 동작은 유지한다.
- 초기화/복사/기존 양쪽 질문의 지연 초점 타이머를 제거했다. 복사 실패와 로그인/재전송/목록의 늦은 응답이 승인된 이동 후 초점을 빼앗지 않게 보완했다. 모달 닫기와 실제 페이지 이탈의 스크롤 복원을 구분한다.
- 최종 단위 170개, 모의 브라우저 160개, typecheck/lint/프로덕션 빌드/diff-check 통과. 실제 8443 + Spring 공개 목록 읽기 및 R08 키보드 조작 통과. 실제 인증/게시글 쓰기 신규 검증과 DB 생성은 하지 않았다.
- 기존 커뮤니티 무JS 404 실패 2건은 별도 미해결로 유지한다. 이번 작업에서 고치거나 검사를 약화하지 않았다.
- 1440·390·320px의 마우스/키보드 캡처, 실행 명령, 변경 함수, 제한과 서버 상태는 위 후속 문서에 있다. 기존 미커밋·미추적 작업을 보존했고 Git 작업은 하지 않았다.
