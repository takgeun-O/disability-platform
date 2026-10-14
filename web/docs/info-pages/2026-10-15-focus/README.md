# 정보 페이지 초점·스크롤 후속 검토 — 2026-10-15

기준: `refactor/info-pages`, HEAD `1ceb848723b3aee16c6c77d056c2af863c52184e`. 정보 페이지 개편/모달의 기존 미커밋·미추적 작업에 이어서 수정했다. 아래 내용은 이번 **초점·스크롤·키보드** 작업의 기록이며 이전 시안·통합·모달 기록을 보존한다.

## 원인과 해결

### 1. 04 검수·청구의 Tab 순서

변경 전 실제 브라우저에서 `summaryTabIndex: "-1"`, `04 → Tab 05 → Shift+Tab 03`을 확인했다([변경 전 재현](baseline.json)). `focusReference()`가 정적 제목과 조작 가능한 `<summary>`를 구분하지 않고 `tabindex=-1`을 추가한 것이 원인이다.

`focusForReading()`은 summary/button/link/input 등 기본 조작 요소의 tabindex를 변경하지 않는다. 정적 읽기 대상에만 필요한 음수 tabindex를 부여한다. 모달은 실제 첫/마지막 조작 요소에서만 Tab을 순환시키고, 중간 제목에서는 기본 DOM 순서를 따른다. Enter/Space로 접은 후, 다시 열기, 목차 이동 뒤에도 04의 기본 tabindex=0과 05→04 복귀를 검사했다.

### 2. 제목 테두리와 자동 이동

전역 `:focus-visible`은 이미 있었다. 별도의 P03 제목 `:focus` 규칙, 매 답변 뒤 제목 focus + scrollIntoView(start), 초기화/복사/기존 양쪽 질문의 지연 타이머가 함께 동작했다. 일부 결과 제목에는 inline outline:none도 있어 제목마다 표시가 달랐다.

- **일반 진입/새로고침:** 새 제목 초점을 추가하지 않는다. 기존 명시적 hash 진입은 원하는 안내를 연다. 초기 hash RAF는 이탈이 승인되면 무효화하며, 이후 사용자가 요청한 hashchange는 정상 처리한다.
- **마우스/터치 답변:** 제목에 초점을 강제로 주지 않는다. 다음 제목과 짧은 읽기 공간(최대 180px)이 보이면 스크롤 호출 0회. 화면 밖이면 부족한 만큼만 이동한다. 매번 화면 맨 위로 정렬하지 않는다.
- **키보드 답변/변경/초기화:** 다음 질문/결과의 읽기 초점을 유지한다. 다음 Tab은 실제 다음 조작 요소로 이어진다. 큰 사각형 대신 제목 왼쪽 4px 청록 표시를 사용한다.
- **초점과 스크롤 분리:** focus({preventScroll:true}) 이후 필요한 스크롤만 한 번 수행한다. 정적 읽기 초점의 transition을 끄고 첫 프레임의 테두리 잔상도 방지한다. 기존 reduced-motion CSS는 유지하고 revealForReading도 해당 설정에서 instant를 사용한다.
- **모달:** 열기/목차의 명시적 내부 이동, Tab 순환, Esc/닫기, 원래 버튼·문서 위치 복원은 유지한다. 실제 페이지 이탈 시 cleanup은 스크롤 잠금만 해제하여 새 페이지/히스토리의 복원을 덮지 않는다.
- **복사 실패:** 두 복사 버튼이 useGuidanceClipboard를 사용한다. 타이머 대신 fallback textarea가 렌더된 직후 선택·초점을 처리한다. 늦은 완료/실패는 현재 요청·화면·열린 모달인지 확인한다. 사용자가 다른 조작 요소로 이동했다면 초점을 빼앗지 않는다. 직접 복사 대안 및 반복 재시도는 유지한다.
- **커뮤니티:** 최신 조회가 성공했을 때 목록 시작 이동은 유지한다. 마우스는 제목 초점을 생략하고, 키보드는 현재 조작 흐름이 유지된 경우에만 작은 읽기 초점을 제공한다. 모바일 필터를 닫고 복귀한 버튼도 고려한다. 승인된 페이지 이탈 후 늦은 조회는 초점/스크롤을 변경하지 않는다. 실패·상세 복귀·Back/Forward 복원은 유지한다.
- **인증:** 로그인/재전송의 기존 오류·상태 초점 effect에 기존 NavigationGuard의 요청 범위 확인만 추가했다. 목적지 RSC 응답이 지연되어 폼이 아직 마운트된 경우에도 승인된 이탈 후 초점을 빼앗지 않는다. API 호출·응답 처리 계약·세션 상태 관리 구조는 변경하지 않았다.

버튼·링크·입력·체크박스·summary의 키보드 outline과 오류 입력 초점, 본문 바로가기는 유지했다. 전체 outline 제거, 반복 blur, 양수 tabindex는 사용하지 않았다.

## 변경 파일과 읽을 함수

| 파일 (web 기준) | 역할 |
| --- | --- |
| `src/lib/reading-focus.ts` | `focusForReading`, `readingScrollDelta`, `revealForReading`: 기본 Tab 순서와 최소 스크롤 |
| `src/features/HearingAidGuide.tsx` | `focusReference`, 답변 follow layout effect, `handleReset`, `editSelectedProduct`, 초기 hash RAF |
| `src/features/HearingAidReferenceDialog.tsx` | 경계 Tab 순환, `useLayoutEffect`의 모달 종료/페이지 이탈 구분 |
| `src/features/HearingAidGuide.css`, `src/index.css` | 기존 제목 강제 outline 제거, 명시적 읽기 대상의 작은 키보드 표시 |
| `src/features/useGuidanceClipboard.ts` | 현재 요청의 실패 fallback 초점, 모달/탐색/다른 조작 후 늦은 처리 차단 |
| `src/features/HearingAidBenefits.tsx` | BilateralCopyButton에서 위 hook 재사용; 정적 제목의 inline outline:none 제거 |
| `src/features/PostList.tsx`, `src/styles/community.css` | `navigate`의 조작 방식/이탈 버전 기록, 최신 결과 이동·초점 구분 |
| `src/features/Login.tsx`, `src/features/EmailVerificationResend.tsx` | 기존 오류/상태 focus effect의 유효 화면 확인 |
| `tests/reading-focus.test.mjs`, `tests/focus-scroll-browser.mjs` | 최소 스크롤 단위 검사, 마우스/터치/키보드/지연 응답 회귀 |
| `tests/hearing-aid-dialog-browser.mjs`, `tests/info-pages-browser.mjs` | 04 재접근, 모달 복귀, 기존 28개 결과 조합·제품·복사·메뉴 |
| `tests/community-browser.mjs` | 포인터 조회는 스크롤만, 키보드는 읽기 초점; 모바일 필터와 지연 조회 이탈 회귀 |

NavigationGuard/AppLink/API 클라이언트/정책 계산/백엔드/DB는 수정하지 않았다. 기존 테스트의 ‘마우스 클릭도 무조건 제목 초점’ 기대만 입력 방식에 맞게 분리했다. 기존 키보드 결과 초점 검사는 남기고 모바일 키보드 검사를 추가했다.

정책·선택지·검색/최신성 관련 25개 선언을 HEAD와 비교해 동일함을 확인했다. 금액·양쪽 지원 JSX/문구도 복사 버튼과 outline 변경을 제외하고 AST 비교로 동일하다. DirectClaimGuide/RegistryCheck는 파일 내용이 동일하다([비교 결과](policy-parity.log)).

## 직접 실행한 검증

Node 24.21.0 / pnpm 10.34.3. 현재 작업 트리의 소스와 동일한 package/lock/Next/TS/PostCSS 설정을 `/private/tmp`에 복사해 프로덕션 빌드·8451 검증 서버를 사용했다. 실제 .env는 복사하지 않았다. 일반 typecheck와 마지막 격리 복사본 typecheck 모두 실행했다. 현재 서비스 8443에서도 별도 확인했다.

| 검사 | 최종 결과 | 범위/기록 |
| --- | --- | --- |
| `pnpm test` | 170 통과, 실패/skip 0 | 기존 167 + 최소 스크롤 3 |
| `pnpm typecheck` | 통과 | Next typegen + tsc |
| `pnpm lint` | 통과, 경고 0 | 실제 src/tests |
| `pnpm build` | 통과 | 프로덕션 컴파일·타입·19개 정적 페이지 생성 |
| `git diff --check` | 통과 | 기존 사용자 변경 포함 작업 트리 |
| 새 초점/스크롤 브라우저 | 9 통과 | [focus-results.json](focus-results.json) |
| 모달 브라우저 | 10 통과 | [dialog-results.json](dialog-results.json); 04 Enter/Space·Tab/Shift+Tab 포함 |
| 정보/P03 통합 브라우저 | 15 통과 | [info-results.json](info-results.json); 28개 결과 조합을 포함 |
| 로그인·로그아웃 | 38 통과 | [login-results.json](login-results.json) |
| 가입·인증·로그인 전달/이탈 | 26 통과 | [auth-flow-results.json](auth-flow-results.json) |
| 재전송 | 13 통과 | [resend-results.json](resend-results.json) |
| 커뮤니티 | 49 통과 | [community-results.json](community-results.json); 최신 요청/목록 복귀/작성 보호/늦은 응답 |
| 실제 8443 + Spring 공개 읽기 | 통과 | [live-check.json](live-check.json); 공개 목록 GET 200/기존 5건 표시 비교, R08 모달 실제 조작 |

브라우저 회귀 **160개**는 실제 Next UI를 Chrome으로 조작하되 인증·게시글 API 응답(SSR 포함)을 모의한 검사다. P03의 질문/계산/복사/제품 검색은 실제 프론트 코드를 사용했다. 이를 실제 인증 통합 검사로 보고하지 않는다.

이번 회귀 작성 중 이미 화면에 보이는 질문에는 스크롤이 없어야 한다는 조건, Q2 다음 Tab의 첫 대상이 기본 summary라는 사실을 테스트에 반영했다. 또한 보완 과정에서 동일 페이지 hashchange를 과하게 무효화하는 문제가 검사에 잡혀, 이탈 검사 범위를 초기 예약 RAF로 좁힌 뒤 관련 전체 검사를 다시 통과했다. 중간 실패 이후 미실행 항목을 통과로 집계하지 않았다.

### 실행 명령

```sh
# web/; 프로젝트 의존성과 Playwright/Chrome 필요
pnpm test
pnpm typecheck
pnpm lint
pnpm build
# 별도 빌드/서버에서 IYUM_API_INTERNAL_URL=http://127.0.0.1:8856,
# 공개 API 주소 NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 사용.
# BASE_URL과 PLAYWRIGHT_MODULE(외부 설치 시), 각 *_ARTIFACT_DIR를 지정할 수 있다.
BASE_URL=http://127.0.0.1:8451 node tests/focus-scroll-browser.mjs
BASE_URL=http://127.0.0.1:8451 node tests/hearing-aid-dialog-browser.mjs
BASE_URL=http://127.0.0.1:8451 node tests/info-pages-browser.mjs
BASE_URL=http://127.0.0.1:8451 node tests/login-browser.mjs
BASE_URL=http://127.0.0.1:8451 node tests/auth-flow-browser.mjs
BASE_URL=http://127.0.0.1:8451 node tests/email-verification-resend-browser.mjs
BASE_URL=http://127.0.0.1:8451 COMMUNITY_SSR_MOCK_PORT=8856 node tests/community-browser.mjs
```

실제 계정 생성 opt-in은 켜지 않았다. 필요한 공개 테스트 설정만 사용했으며 실제 환경변수 내용·쿠키·비밀번호·인증 토큰은 기록하지 않았다.

## 화면 캡처

프로덕션 8451, Chrome, 배율 1x, 한국어, reduced motion. P03은 실제 로직, 헤더 /me만 비로그인 모의. 개인정보/계정 데이터가 없는 화면이다. 폼 초점을 강제로 제거해 촬영하지 않았다.

| 화면 | 1440×1000 | 390×844 | 320×844 |
| --- | --- | --- | --- |
| 마우스 답변 뒤 결과 | [PC](mouse-result-1440.png) | [모바일](mouse-result-390.png) | [320](mouse-result-320.png) |
| 마우스로 전체 기준 열기 | [PC](mouse-modal-1440.png) | [모바일](mouse-modal-390.png) | [320](mouse-modal-320.png) |
| 키보드 다음 질문 읽기 표시 | [PC](keyboard-question-1440.png) | [모바일](keyboard-question-390.png) | [320](keyboard-question-320.png) |
| 키보드 04 검수·청구 | [PC](keyboard-modal-04-1440.png) | [모바일](keyboard-modal-04-390.png) | [320](keyboard-modal-04-320.png) |

04의 파란 outline은 조작 가능한 summary에 필요한 키보드 표시다. 비조작 제목의 전체 사각형과 구분한다.

## 직접 확인 순서

1. `/validation/hearing-aid-health-insurance` 새로고침: 미선택/제목 강제 초점 없음. 마우스로 ‘병원에서 검사·진단’을 선택해 결과가 이미 보이면 그대로 읽고, 화면 밖이면 필요한 만큼만 이동하는지 확인.
2. 키보드로 ‘등록 완료 → 건강보험 → 처방전 있음 → 처음 신청’. ‘판매업소 위임·직접 청구 방법 보기’에서 Enter. 04를 Enter로 접기 → Tab 05 → Shift+Tab 04. Space도 반복. Escape로 원래 버튼·위치로 복귀. 재열기와 내부 목차 뒤에도 반복.
3. 키보드 설문은 질문 왼쪽 작은 청록 표시 후 Tab으로 다음 summary/답변에 이동. 모달 닫기, 체크 메모, 직접 복사 대안, 초기화 후 첫 질문을 확인.
4. 커뮤니티의 목록 하단 페이지 변경, 필터 적용, 상세→목록, Back/Forward를 비교. 포인터는 제목 초점을 강제하지 않고 키보드는 새 결과를 읽을 수 있어야 한다. 로그인/재전송 빈 입력 제출은 오류 입력에 도달해야 한다.

## 미실행·남은 범위와 최종 상태

- 실제 회원가입/Mailpit/로그인/로그아웃/게시글 작성은 이번에 새로 실행하지 않았다. 관련 계약은 기존 단위·모의 브라우저 검사로 확인했고, 실제 Spring 검증은 공개 GET 읽기 전용이다. 생성/삭제 DB 데이터 **0건**.
- 실제 휴대폰·Safari·Firefox·스크린리더 음성 출력은 미검증. Chrome 반응형 1440/390/320과 터치 이벤트·키보드, 기본/감소 모션을 검사했다.
- 기존 **커뮤니티 무JavaScript 404 안내/링크 실패 2건**은 별도 알려진 미해결 사항이다. 이번에 해당 실패 검사를 수정·약화·재실행하지 않았으며 신규 실패나 해결로 분류하지 않는다. 커뮤니티 HTTP 404 검사와 무JS 본문 검사는 다르다.
- 정책 최신성·공식 외부 사이트·기관 상담 접수는 검증하지 않았다. 정책/금액/제품 데이터는 변경하지 않았다.
- 기존 8443 서버는 작업 도중 실행이 종료된 것을 포트 조회로 확인했으며 이 작업에서 해당 기존 PID에 종료 명령을 보내지 않았다. 프로젝트의 `pnpm dev`를 재실행했다. 최종 **http://127.0.0.1:8443** 접속 가능(PID 97959). 기존 Spring 8082는 유지했다. 직접 실행한 임시 8451과 모의 8856은 검증 후 정리했다.
- 브랜치/HEAD 그대로, 기존 사용자 변경·미추적 문서/캡처 보존. 커밋·push·PR·병합·배포 없음. next-env.d.ts의 검사 도구 생성 차이는 작업 시작 파일로 되돌려 불필요한 소스 변경으로 남기지 않았다.
