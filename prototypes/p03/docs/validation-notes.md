# PAGE-P03-001 · 구현 및 검증 기록

최근 변경: 문서 마지막의 **2026-09-25 · 보호자 안내의 복사 버튼과 이동 동선 보완**을 참조한다. 아래 초기 구현 기록은 당시 상태를 보존한 것이다.

최신 상태는 §20 Registry Prototype Coverage Clarification을 참조합니다. §19는 최초 Registry 축소 구현 기록입니다. §18은 Document Responsibility / Claim Responsibility Improvement 기록입니다. §17은 Consultation Preparation Implementation 기록입니다. §16은 내구연한 5년 · Detail Return 개정 기록입니다. §14–15는 이전 Guided Check/Journey, §1–13은 최초 정적 Build 기록입니다.

구현일: 2026-09-20. 상태: 로컬 Build Artifact. Evidence: Untested. EXP-001 미실행.

## 1. 구현 요약

Route: `/validation/hearing-aid-health-insurance`.
기존 폴더에 앱 코드·package.json·공통 UI·Git 저장소가 없어 `frontend/`에 최소 실행 구성을 추가했다. 기존 문서는 보존했다. 재사용할 구현 컴포넌트는 없었고 Section, Note, SourceLink, Disclosure는 페이지 내부 전용이다. 홈페이지·전역 제품 내비게이션을 가짜로 만들지 않았다.

## 2. Architecture Alignment

재조회: 03 Product, Product Validation, HYP-003/P03, Content Spec, UI·프로토타입, UX·접근성, 화면 설계, Frontend 허브 및 Runtime/Architecture/Routing/Components/Styling/Accessibility/Testing/ADR/구현 추적표. 대용량 UX·화면 문서는 관련 공통·접근성·검증 부분을 선별 확인했다. Coding Convention은 허브상 Planned / Not Created이고 세부 폴더·토큰은 미확정이다. SEO canonical 등은 Routing 문서상 미확정이다.

- [Content Spec](https://app.notion.com/p/3df2758cf837810eae31da926a6bfd3f)
- [Frontend Architecture](https://app.notion.com/p/3c32758cf83781f68e73d279d5624556)
- [Frontend ADR](https://app.notion.com/p/3c32758cf837810093b6fbce5f7f2661)
- [Routing](https://app.notion.com/p/3c32758cf83781468084f3ca138f6ceb)
- [Styling](https://app.notion.com/p/3ad2758cf837814abb92ef1d59f7fd65)

Next.js App Router, TypeScript strict, Node 24 LTS, CSS Modules + 좁은 Global CSS, CSS Custom Properties, native-first를 따른다. 전용 validation 경로는 정식 Screen/IA와 구분하기 위한 로컬 선택이다. Route registry는 접근 분류·경로·traceability만 갖는다. 상태 저장·Provider·API·인증 계약은 만들지 않았다. 운영 모니터링/전체 테스트 도구를 선제 설치하지 않았다.

### Rendering

빌드 시 정적으로 prerender되는 Server Component다. Client Component, Server Action, runtime data fetch가 없다. Next.js 전체를 static export로 강제하여 향후 hybrid 계약을 바꾸지 않았다. native anchor/details가 상호작용을 제공한다.

## 3. Content 구현

| 순서 | Section | 상태 |
|---|---|---|
| 1 | Hero / 30초 요약 | 목적·3가지 요약·대상/절차 CTA·확인일 |
| 2 | 대상 가능성 | 등록/보험/처방/이력, 구매 조건과 분리 |
| 3 | 편측·양측 | 기본 설명 + 세부 고시 조건 disclosure |
| 4 | 금액 | 91/20/5×4 구성, 131만 원≠실지급액 |
| 5 | 절차 | 8단계 ol, 이유와 다음 행동 |
| 6 | 구입 전 확인 | 제품·업소 별도, 8개 정적 목록 |
| 7 | 서류 | 처방/구입/검수/제품청구/후기관리 dl |
| 8 | FAQ | 9개 native details |
| 9 | 경험 | 실제 데이터 없음, 준비 중 설명 |
| 10 | 출처 | 기관·문서·날짜·공식 링크·최종 판단 안내 |

## 4. Trust UI

공식 기준 / 쉽게 설명하면 / 꼭 확인하세요 / 다음에 할 일은 텍스트 라벨과 별도 구획을 사용한다. 실제 이용 경험은 독립 Section이다. 주의는 접힌 답변에만 숨기지 않는다. Source Box는 정책 확인일 2026-09-18을 유지한다. 내부 Source ID·Issue ID·개발 상태는 사용자 UI에서 제외한다.

## 5. C01–C04

- C01: 현행 고시의 양측 조건을 disclosure에 근거와 함께 표시. 정적 안내와 연령 표기가 다르다는 주의를 본문·FAQ에 유지. 정합성 해소 주장 없음.
- C02: 구입 1년 후 관리/연간 청구 구조와 개인별 최초 청구일 확인을 구분. 날짜 계산 없음.
- C03: 6개월 확정 문구 없음. 구입 전에 공단에 구입 가능 기간 확인.
- C04: 확인된 서식명만 사용. 최신 제출 조합·확인자료의 필수성·접수방법은 공단 확인. 미확정 서식번호 보충 없음.

모두 Needs Review이며 Frontend에서 정책 결정을 대체하지 않는다.

## 6. Accessibility

lang=ko, h1 하나, 논리적 h2/h3, main/nav/section/footer, ul/ol/dl, table caption·scope, skip link, focus-visible, 44px 이상 주요 탐색 링크, native disclosure, 설명적인 링크, 원문 같은 탭 열기를 구현했다. Anchor 대상의 tabindex=-1로 키보드 이동 후 다음 읽기·탐색 위치를 이어간다.

실제 브라우저에서 Enter 앵커 이동과 target focus, FAQ Enter 열기·Space 닫기, Tab 다음 질문 이동, focus outline을 확인했다. AX tree에 heading/list/disclosure 상태가 나타난다. 실제 스크린리더 음성 출력 테스트는 미실행이다.

주요 텍스트 색상 대비 계산: 본문13.10:1, 보조텍스트6.01:1, 링크7.23:1, CTA7.65:1, 주의 본문13.01:1, 설명 본문12.49:1. 전체 axe 감사는 실행하지 않았다.

## 7. Responsive

1280×900, 768×1024, 375×812 및 320×780 실제 IAB viewport에서 확인했다. 모바일은 단일열, 데스크톱은 본문+sticky 목차. 가로 넘침 없음. 좁은 화면에서 금액표·절차·FAQ·Source Box와 긴 링크의 줄바꿈을 확인했다. DOM 순서 변경 없음.

200% 실제 브라우저 zoom은 미확인이다. IAB 단축키 시도에서 zoom 배율 변화가 관측되지 않아 통과로 기록하지 않는다. 좁은 viewport 확인이 실제 zoom 검증을 대체하지 않는다.

## 8. SEO / Indexability

페이지 metadata의 `noindex, nofollow`가 실제 DOM에 존재한다. sitemap 생성 자체가 없고 경로는 포함되지 않는다. 글로벌 SEO/canonical/robots 규칙은 새로 정하지 않았다. 현재 127.0.0.1에서만 실행 중이며 외부 배포·검색 공개는 하지 않았다. noindex는 접근 제어가 아니다.

## 9. Verification

- Node 24.19.0 / Next 16.3.5 / React 19.2.4 / TypeScript 5.9 계열, 정확한 transitive 버전은 lockfile.
- `npm run lint`: PASS.
- `npm run typecheck`: PASS.
- `npm run build`: PASS, route Static prerender 확인.
- 기존 unit/component test suite: 없음. 별도 대규모 프레임워크 추가 없음.
- 빌드 HTML smoke: h1=1, section=10, 모든 anchor target 존재, noindex/nofollow, 입력 form 없음 PASS.
- 실제 DOM: 8단계, FAQ 9개, 깨진 내부 anchor 0, 브라우저 warn/error 로그 0.
- 공식 URL 6개 중 5개는 웹 조회 도구에서 문서 응답 확인. 시행규칙 URL은 실제 브라우저에서도 문서명/시행일/본문이 정상 표시되지 않아 **NEEDS REVIEW**. 정책 최신성·개별 급여 적용을 재검증했다는 뜻은 아니다.

## 10. 변경 파일

- package.json / package-lock.json: 최소 런타임·검사 의존성과 재현 가능한 버전.
- tsconfig.json / next-env.d.ts(자동생성): strict TypeScript·Next 타입.
- eslint.config.mjs: Next 공식 ESLint 설정.
- .nvmrc / .gitignore: Node 24 및 생성물 제외.
- src/app/layout.tsx / globals.css: 한국어 문서·baseline.
- src/lib/routes.ts: 단일 검증 경로 metadata.
- src/app/validation/hearing-aid-health-insurance/page.tsx: 화면 composition·route metadata.
- 동일 폴더 content.ts: 반복 콘텐츠·근거·확인일.
- 동일 폴더 components.tsx: 페이지 전용 의미 구획·disclosure·근거 링크.
- 동일 폴더 page.module.css: 반응형·타이포그래피·focus 주변 여백.
- README.md / docs/validation-notes.md: 재실행·검증·미확인 사항.

## 11. 발견한 문제

- BLOCKER: 로컬 Build Artifact에는 없음.
- NEEDS REVIEW: C01–C04. 추가로 시행규칙 Source URL이 빈 문서를 표시하므로 Content Spec에서 공식 연결 경로를 먼저 검토해야 함. 로그인 없는 등록업소 조회 직접 링크·텍스트 문의 경로도 아직 확정되지 않음. 200% zoom·실제 스크린리더·다른 브라우저 검증 미완료.
- LOCAL FIX: 코드 없는 문서 폴더에 격리된 최소 bootstrap, 전용 noindex route, 긴 링크 wrapping, 키보드 anchor focus, Source 연결 미확인 표시.

시행규칙 URL은 임의 대체하지 않았다. UI에서는 연결 확인 필요를 알리고 기존 Spec의 공단 서식 안내를 함께 제공한다. Notion 정책 원본은 수정하지 않았다. 실행 도구의 기본 Node23 대신 제공된 Node24 LTS를 사용했다. Git 저장소가 없어 commit hash는 없다.

## 12. Founder Self Review

실제 페이지에서 다음을 검토한다. UI에는 노출하지 않는다.

1. 첫 화면만 봐도 페이지 목적이 이해되는가?
2. 대상 여부 확인 위치가 바로 보이는가?
3. 131만 원을 현금 일시 지급으로 오해할 가능성이 있는가?
4. 가장 먼저 해야 할 행동이 명확한가?
5. 등록 제품 / 등록 업소가 Eligibility와 분리되어 이해되는가?
6. 진행 절차가 너무 긴가?
7. 공식 기준과 IYUM 설명이 구분되는가?
8. Source가 충분히 신뢰감을 주는가?
9. 모바일에서도 쉽게 읽히는가?
10. 실제 이용 경험이 추가되면 어디에 들어갈지 이해되는가?

## 13. 하지 않은 작업

Backend, DB, Auth, 경험 저장, 개인 데이터 입력, 자동 자격 판단, 계산기, 추천, CMS, AI, Search API, EXP Result/Evidence 판정, Product Decision, MVP/R1 변경, 정식 Notion 계약 수정, 공개 배포 없음. Founder review와 당사자 사용자 검증은 별도 미실행이다.


## 14. Guided Check 개정 — 2026-09-20

### Source of Truth 순서

Notion 최신 HYP-003, Content Spec, Product Validation, UX 접근성, UI Prototype, Frontend Architecture/허브/ADR와 기존 구현을 먼저 조회했다. Coding Convention은 Frontend 허브의 Planned / Not Created 상태다. Content Spec에 §7.1 Guided Check, §7.2 Detail Layer를 추가하고 Hero CTA, 단계별 노출, §12 관찰, §13 Frontend Scope를 정렬했다. 수정 후 **재조회로 실제 반영 및 §10 Source Governance 원문 보존을 확인한 다음** 코드 수정을 시작했다. 정책 확인일은 2026-09-18, HYP-003 Untested / First Validation Candidate는 유지했다.

Founder가 기존 화면에서 긴 설명의 피로 가능성을 판단한 것은 Founder Interpretation이다. 질문형 UX의 가치나 사용성 개선을 검증한 Evidence가 아니다. 기존 Source 이슈를 해결한 작업도 아니다.

### 구현

- 기존 route, noindex/nofollow, sitemap 제외를 유지한다.
- Hero에서 Guided Check / 전체 기준 두 경로를 제공한다.
- `GuidedCheck.tsx`만 Client Component다. React local memory state 외 저장소·상태 라이브러리를 추가하지 않았다. 나머지 페이지·출처는 정적 Server Component다.
- `guided-content.ts`: 4개 질문/각 선택지 안내/최종 다음 행동/4단계 요약을 분리했다.
- 선택 즉시 다음 질문 또는 안내로 이동한다. 결과는 ‘먼저 이것을 확인해보세요’이며 최종 판단은 공식기관의 몫임을 항상 표시한다.
- 이전 질문에서 선택됨 텍스트와 aria-pressed로 이전 답을 확인할 수 있다. 이전 질문을 다시 답하면 이후 답변과 안내는 폐기된다. 다시 시작과 새로고침은 Q1 미선택 상태로 돌아간다.
- 전체 기준 링크는 질문·안내 상태와 관계없이 항상 사용할 수 있다.
- 금액 상세·기존 8단계·단계별 서류는 기존 Disclosure로 감쌌다. 4단계 요약을 추가하고 핵심 주의·공식 확인·출처는 계속 기본 노출한다. 양측 상세·FAQ는 기존 형태를 유지한다.

### Flow 검증

실제 로컬 production build를 IAB에서 실행했다. 입력은 QA용 가상 선택이며 참여자의 건강정보가 아니다.

| Scenario | 확인한 동작 | 결과 |
|---|---|---|
| A | 예 → 건강보험 예 → 처방 예 → 처음 → 5개 다음 행동 | PASS |
| B | 등록 아니오 → 등록·적용 여부 확인 안내 | PASS |
| C | 등록 모름 → 등록 상태 확인 | PASS |
| D | 의료급여 → 적용 범위·별도 확인 안내 | PASS |
| E | 처방 아직 없음 → 이비인후과 확인 안내 | PASS |
| F | 이전 급여 있음 → 이력·내구연한·재지급 확인 | PASS |
| G | 무응답 → 전체 기준 바로 보기 | PASS |
| 추가 | Q2 모름 / Q3 모름 / Q4 기억 안 남 | PASS |
| 수정 | 결과 → 이전 질문 선택 표시 → 다른 답 → 새 안내 | PASS |
| 초기화 | restart 및 reload → Q1, 선택된 답 0 | PASS |
| 후속 폐기 | 이전 답 재응답 후 다음 질문의 선택 0 | PASS |

A–F 및 추가 분기는 375px에서 Enter/Space로 실행했다. 결과의 이전/다시 시작 및 의료급여 분기는 1280/768/375/320px 각각에서도 확인했다. G는 키보드 Enter로 full-criteria에 이동하며 focus도 같은 대상에 놓인다.

### Privacy 검증 범위

| 항목 | 구현/코드 검사 | 실제 브라우저 직접 검사 |
|---|---|---|
| Backend·DB | 존재하지 않음 | 해당 기능 없음 |
| Network/API | Guided/전체 src에 fetch·axios·beacon 등 답변 전송 코드 없음 | Network 패널 직접 관측 미완료 |
| LocalStorage | 사용 코드 없음 | 직접 저장소 검사 미완료 |
| SessionStorage | 사용 코드 없음 | 직접 저장소 검사 미완료 |
| Cookie | 답변 Cookie 작성 코드 없음 | 직접 저장소 검사 미완료 |
| URL | state serialization 없음 | 응답 전후 hash는 정적 anchor, 답변 노출 없음 |
| Analytics | SDK/event/payload/logging 없음 | 별도 분석 기능 없음 |

IAB의 제한된 evaluate 환경에는 저장소 API가 노출되지 않았다. Chrome 개발자 도구를 통한 추가 확인은 컴퓨터 제어의 Accessibility/Screen Recording 권한 대기로 진행되지 않았다. 따라서 ‘저장소·네트워크 runtime 검사 PASS’라고 기록하지 않는다. 새로고침 초기화 동작과 코드상의 메모리 전용 구조는 확인했다. 실제 사용자 검증 전에 Network/Storage 탭 직접 점검을 마쳐야 한다.

### 접근성·반응형·기술 검사

- `npm run lint`, `npm run typecheck`, `npm run build`: PASS. 새 의존성 없음.
- 질문은 h3, group은 질문의 aria-labelledby·설명 aria-describedby 연결. Native button의 Tab/Enter/Space 조작 확인.
- 질문 및 안내 전환 뒤 focus는 guided-question heading. 첫 로드 강제 focus 없음. focus outline solid 확인.
- 1280/768/375/320px에서 질문·안내 가로 넘침 없음. 선택 버튼 높이 약 54.8px, 최소 48px.
- 금액 표 4행, 기존 상세 절차 8단계, 새 요약 4단계, 서류 5단계 보존. 네 상세 disclosure의 Enter 열림 확인.
- h1 1개, 깨진 내부 anchor 0, 실제 robots noindex/nofollow. 브라우저 warning/error 로그 0.
- Motion을 추가하지 않았고 기존 reduced-motion CSS 보존. OS preference 전환 검증은 미실행.
- 200% 실제 zoom, 실제 스크린리더 음성 출력, 전체 axe 감사는 미검증. 해당 항목을 PASS로 처리하지 않는다.
- 기존 테스트 suite 없음. 브라우저 상호작용 검증으로 분기·수정·초기화를 확인했다.

### 변경 파일

- `src/app/validation/hearing-aid-health-insurance/GuidedCheck.tsx`: 작은 Client 영역·메모리 상태·이전/초기화·focus.
- 같은 폴더 `guided-content.ts`: 질문·분기 Copy·4단계 요약.
- 같은 폴더 `page.tsx`: 두 진입 경로·Guided 삽입·전체 기준 anchor·progressive disclosure.
- 같은 폴더 `page.module.css`: 질문/결과·선택 상태·모바일 button.
- `README.md`, 이 문서: 현재 구현과 검증 한계 기록.
- 기존 content.ts, components.tsx, layout.tsx, globals.css, routes.ts, package/lock/lint/tsconfig는 변경하지 않았다.

### 발견한 문제

- BLOCKER: 로컬 화면 구현·빌드에는 없음.
- NEEDS REVIEW: C01–C04, 기존 시행규칙 Source 연결 문제, 직접 Network/Storage 점검, 200% zoom·스크린리더. 문서형보다 부담이 적은지는 아직 미검증 가정이다.
- LOCAL FIX: 이전 답 변경 시 후속 답 폐기, 명확한 선택됨 표시, 질문/안내 focus, 단계별 정보 접기와 중요 주의 기본 노출.

### Founder Self Review — Guided 버전

1. 질문형 시작이 긴 글보다 부담이 적은가?
2. 첫 질문의 의미가 바로 이해되는가?
3. 의료급여 분기가 자연스러운가?
4. Result가 자격 판정처럼 보이지 않는가?
5. 다음 행동이 명확한가?
6. 전체 기준으로 바로 이동하기 쉬운가?
7. 131만 원 설명이 이해되는가?
8. 4단계 요약만으로 전체 흐름이 이해되는가?
9. 상세정보가 필요할 때 쉽게 펼칠 수 있는가?
10. 질문형 구조가 IYUM다운 가치로 느껴지는가?

### 하지 않은 작업

Backend/DB/Auth/API/분석/답변 영속저장/의료판단/자격판정/지급액 계산/경험 입력/공개 배포 없음. EXP Record/Result·Product Decision·HYP 상태·MVP/R1·정식 IA/Screen/ADR 변경 없음. Notion 변경은 PAGE-P03-001 Content Spec에만 한정했다.

## 15. Terminology · Journey · Deep Link 개정 — 2026-09-20

### 1. 작업 요약과 작업 순서

최신 Content Spec, HYP-003/P03, Product Validation, UX·접근성, UI·프로토타입, Frontend 허브/Architecture/ADR/구현 추적 문서를 재조회했다. Coding Convention은 여전히 Planned / Not Created이다. 현재 소스를 읽고 **Notion Content Spec만 먼저 수정 → 재조회로 실제 반영 검증 → Frontend 수정** 순서를 지켰다. Notion이 반환한 tilde escape 차이 외에는 추가 문구·매핑을 재조회에서 확인했다. HYP-003 Untested, Candidate, 로컬 Build Artifact 상태를 유지한다.

### 2. Founder Feedback 6건

| 문제 | 변경 | 구현 결과 |
|---|---|---|
| 진단과 장애인등록 혼동 | Q1에 장애인등록 명시·진단과 구분 | 질문 아래 기본 노출 설명 |
| 미등록 시 다음 행동 불명확 | 한 문장 행동 + 등록 확인 4항목 + 주민센터·공식 안내 경로 | Q1 no/unknown에서 표시, 등록 도움말로 직접 이동 |
| 피부양자 용어 어려움 | 가족 건강보험에 등록된 경우라는 설명 | Q2 아래 표시·선택 그룹 설명과 연결 |
| 처방 의미 모호 | 건강보험 급여 목적 명시·권유/맞춤과 구분 | Q3 설명, 상세 처방 의미, 실제 병원 단계 링크 |
| 결과에서 현재 위치 부재 | 8단계 Journey·현재 단계 표시 | 번호/텍스트/테두리·배경으로 함께 표시 |
| 목적 상세정보 탐색 부담 | 분기별 실제 고정 anchor | 접힌 상세를 열고 목적지로 focus/scroll |

이는 Founder Interpretation을 반영한 구현 결과다. 이해도 개선을 실제 사용자에게 검증했다는 뜻이 아니다.

### 3. Content Spec 수정

§7.1 Q1~Q3 Supporting Copy와 Next Action, §7.3 Journey/매핑/등록 mini/Deep Link/접근성 신설, §8.2 registration-help·previous-benefit, §8.5 prescription-help 및 실제 처방/서류 anchor, §10 S10, §12 용어·Journey·링크 관찰 10개를 반영했다. 기존 §7.2, 상세 금액/편측·양측/4단계·8단계/서류/FAQ/경험 Empty State, S01–S09, C01–C04, 공개 전 Gate를 보존한다. 다른 Notion 문서는 변경하지 않았다.

### 4. 최종 질문과 Supporting Copy

1. **장애인등록에서 ‘청각장애’로 등록되어 있나요?** — 병원에서 난청 진단을 받은 것과 장애인등록에서 청각장애로 등록된 것은 다릅니다.
2. **현재 건강보험 가입자 또는 피부양자인가요?** — 직장·지역가입자이거나, 가족의 건강보험에 피부양자로 등록된 경우를 포함합니다.
3. **건강보험 급여 절차를 위한 보청기 처방을 이비인후과에서 받았나요?** — 단순히 ‘보청기를 써보라’는 권유나 보청기센터 맞춤과는 다를 수 있습니다.
4. **이전에 보청기 건강보험 급여를 받은 적이 있나요?** — 이전 급여 이력에 따라 내구연한·재지급 조건을 확인할 필요가 있습니다.

Q4 질문/선택 의미는 유지했다. 짧은 설명은 tooltip 없이 질문 바로 아래에 있다.

### 5. Guided Journey

1 청각장애 등록 상태 → 2 건강보험·의료급여 자격 → 3 이비인후과 진료·급여 처방 → 4 이전 급여 이력·내구연한 → 5 등록 제품·업소 → 6 구입·착용 → 7 이비인후과 검수 → 8 청구·이후 적합관리.

| 분기 | 현재 확인할 단계 |
|---|---|
| Q1 아니오/모름 | 1 |
| Q2 의료급여/모름 | 2 |
| Q3 미처방/모름 | 3 |
| Q4 이전 급여 있음/기억 안 남 | 4 |
| Q1~Q3 예 + Q4 처음 | 5 |

이전 단계는 ‘함께 확인할 항목’, 바로 다음은 ‘다음에 살펴볼 단계’, 나머지는 ‘이후 살펴볼 단계’다. 통과/완료 표시는 없다. 현재 aria-current=step, ol/listitem, 번호·단계명·상태를 사용한다. 모든 너비에서 세로 목록으로 읽으며 별도 라이브러리·이미지·애니메이션이 없다. 설명용 묶음이며 공식 절차 순서를 새로 정의하지 않는다. 이전 급여 이력은 구입 전에 확인한다. 기존 4단계 요약과 8단계 상세는 보존했다.

### 6. Registration Orientation

내 등록 상태 확인 → 청각장애 등록 여부 확인 → 필요하다면 장애인등록 절차 확인 → 보청기 건강보험 급여 조건 확인. 첫 항목에 ‘현재 확인할 단계’를 표시한다. 등록 행정절차 전체가 아닌 확인 방향 안내임을 명시한다.

registration-help는 진단≠등록, 자신의 등록 상태 확인, 불확실하면 주소지 관할 읍·면·동 주민센터에 문의, 공식 신청 경로, 등록 이후 별도 급여 기준 확인으로 한정했다. 새 공식 근거는 [보건복지부 장애인등록/장애정도 심사제도](https://www.mohw.go.kr/menu.es?mid=a10710010900). 개인 상태 온라인 조회 화면으로 소개하지 않는다. 별도 등록 Knowledge Page는 Candidate Note만 남기고 생성하지 않았다.

### 7. Contextual Deep Links

| 결과 | 실제 목적지 |
|---|---|
| Q1 no/unknown | #registration-help, #eligibility, #sources |
| Q2 medical-aid/unknown | #scope, #eligibility, #sources |
| Q3 no | #prescription, #document-details, #sources |
| Q3 unknown | #prescription-help, #prescription, #document-details, #sources |
| Q4 past/unknown | #previous-benefit, #steps, #sources |
| 기본 완료 | #before-buying, #benefit, #steps, #sources |

prescription은 기존 8단계 상세의 2단계, document-details는 단계별 서류 목록 자체다. 클릭/Enter/직접 hash/History에서 조상 details를 열고 tabindex=-1 목적지를 focus/scroll한다. native href/history를 보존한다. URL에는 고정 문서 위치만 있으며 답변은 없다. no-JS에서는 원래 상세와 native disclosure로 접근 가능하다.

### 8. Accessibility

실제 확인: Enter/Space 선택, Tab 이동과 파란 focus outline, 질문·결과 heading focus, 이전/다시 시작, 설명 aria-describedby 연결, anchor 목적지 focus, 처방·서류 details 자동 열림, 직접 hash 재로드, 뒤로/앞으로 focus, 320px reflow. 320px 결과의 제어 버튼 높이는 약 52px, 기본 버튼 최소 48px, 링크 최소 44px. 현재 단계는 색 외 텍스트/테두리를 포함한다. ol에 list 및 li에 listitem 역할을 명시해 CSS 장식과 무관하게 목록 의미를 유지한다.

미검증: VoiceOver/NVDA 실제 낭독, 실제 터치 기기, 브라우저 200% zoom. 뷰포트 축소를 200% 확대 검증으로 계산하지 않았다. 접근성 완전 준수 판정은 하지 않는다.

### 9. Privacy

| 항목 | 코드 검토 | 실제 런타임 확인 |
|---|---|---|
| Network | fetch/XHR/beacon/socket/API 호출·답변 전송 코드 없음 | DevTools Network 기록 직접 검사 미완료 |
| LocalStorage | 읽기·쓰기 없음 | 저장소 직접 검사 미완료 |
| SessionStorage | 읽기·쓰기 없음 | 저장소 직접 검사 미완료 |
| Cookie | 답변 cookie 코드 없음 | cookie jar 직접 검사 미완료 |
| URL | 답변 serialize 없음, 문서 anchor allowlist만 사용 | 질문 선택으로 답변 URL 생성 없음; 고정 hash 이동 확인 |
| Analytics/Logging | SDK·이벤트·console 답변 기록 없음 | 외부 분석 요청 직접 관찰 미완료 |

IAB 도구는 DOM 중심 읽기만 제공하고 저장소·Network 패널을 직접 검사할 수 없었다. 일반 브라우저 앱 접근도 앞선 작업에서 OS 권한 대기였으므로 통과로 주장하지 않는다. 선택값은 useState 메모리에만 존재한다. 새로고침 Q1 초기화, 이전 답 수정 후 후속 선택/결과 폐기는 브라우저에서 확인했다. 정적 페이지/JS/CSS 로딩 자체와 답변 전송은 구분한다. 테스트는 가상 선택 경로로 수행했으며 실제 개인 건강정보를 사용하지 않았다.

### 10. Responsive

1280 / 768 / 375 / 320px viewport에서 확인했다. 문서 scrollWidth는 각각 1265 / 753 / 360 / 305px(스크롤바 제외)로 가로 넘침이 없었다. 320px 등록 mini, 375px Q3 긴 문구/버튼, 768px 결과 상단, 1280px Journey/링크를 시각 검토했다. 작은 화면은 세로 흐름, 컨트롤은 줄바꿈한다. 임시 viewport는 검증 후 해제한다.

### 11. Verification

Node 24.19.0, Next 16.3.5 production build. lint / tsc --noEmit / next build 통과. 해당 route는 정적 prerender를 유지하고 실제 DOM robots는 noindex,nofollow다. 기존 테스트 프레임워크/테스트 script가 없어 새 dependency를 설치하지 않았다.

| Browser Scenario | 결과 |
|---|---|
| A 예/예/예/처음 | 현재 5, 구매 확인 anchor/focus 통과 |
| B Q1 아니오 | mini + 현재 1, registration-help focus 통과 |
| C Q1 모름 | 현재 1, 출처 이동 통과 |
| D 의료급여 | 현재 2, scope 범위 안내 focus 통과 |
| E Q3 미처방 | 현재 3, 실제 prescription 상세 열림/focus 통과 |
| F Q3 모름 | 처방 의미 + 단계별 서류 열림/focus 통과 |
| G 이전 급여 있음 | 현재 4, previous-benefit focus 통과 |
| H 기억 안 남 | 현재 4, 동일 이력 안내 통과 |
| I 건너뛰기 | Q1 미응답 그대로 full-criteria focus 통과 |
| J 이전 답 변경 | Q3 결과에서 Q2 의료급여로 변경 → 현재 2/링크 재계산; Q2 예 재선택 시 Q3 이전 선택/결과 제거 통과 |

추가 Q2 모름, 새로고침 초기화, 고정 hash 직접 로드·History도 확인했다. 화면 밖 링크의 자동 click은 두 차례 위치 불일치가 있었으며 이를 통과로 세지 않았다. Tab으로 링크를 화면에 표시한 뒤 마우스 클릭 및 Enter로 각각 목적지 open/focus를 확인했다. 앱에서 관찰된 console error/warn은 없었다.

### 12. 변경 파일과 목적

- GuidedCheck.tsx: 지원 설명·한 문장 행동·등록 mini·8단계 Journey·상황별 링크.
- guided-content.ts: 질문 문구·매핑·고정 링크 label·Journey 데이터.
- DetailNavigation.tsx (신규): 작은 client DOM 향상; hash 목적지의 disclosure 열기·focus, memory 답변 접근 없음.
- page.tsx: 등록/처방/이력 도움말과 실제 anchor, navigation enhancer 삽입.
- content.ts: S10 공식 출처/개별 확인일 추가; 기존 정책 데이터 보존.
- page.module.css: Journey/mini/도움말 표현, 작은 화면 세로 구성.
- README.md 및 이 문서: 최신 구조, 검증, 제한, Founder 검토 질문.

components.tsx, layout.tsx, globals.css, routes.ts, dependencies와 lockfile은 이번 개정에서 변경하지 않았다.

### 13. Source Governance

S10 Official: 보건복지부 등록 안내, 표시 최종수정일 2026-06-30, 직접 확인일 2026-09-20. 읍·면·동 경로, 진단·심사·등록의 구분만 사용한다. 기존 S01–S09 정책 확인일 2026-09-18과 C01–C04(양측 연령, 후기 청구 시점, 처방 후 기간, 서류 패키지)는 변경하지 않았다. S05 원문 연결 문제도 해결된 것으로 표시하지 않는다. 등록 출처 확인일은 기존 급여 검토일과 별도로 화면에 노출한다. Public Release Gate 유지.

### 14. 발견한 문제

- Blocker: 로컬 build/주요 flow 구현을 막는 항목 없음.
- Needs Review: C01–C04 및 S05 원문 연결, 런타임 Network/Storage/Cookie 검사, 실제 화면낭독기·200% zoom, 사용자 이해도.
- Local Fix: 닫힌 상세의 anchor를 열고 focus하도록 보완; 목록/항목 역할 명시; 등록 링크 위치 표현 정정. 자동화 화면 밖 클릭 불일치는 보이는 상태의 클릭·키보드로 재검증.

### 15. 하지 않은 작업

Backend, DB, Auth, 답변 저장, Eligibility Engine/자동 판정, 금액 계산기, 건강정보 입력, 정부 API, AI, 경험 입력, Figma Production Design, 전체 Design System, 공개 배포를 추가하지 않았다. EXP Record/Result, Product Decision, HYP-003 Supported 판정, MVP/R1, 정식 Requirement/Feature/IA/Screen/API Contract는 변경하지 않았다.

### 16. Founder Self Review — 아직 사용자 Evidence 아님

1. Q1이 장애인등록을 뜻함이 명확한가?
2. 미등록 사용자가 다음 행동을 이해하는가?
3. 등록 mini가 문장만 읽는 것보다 쉬운가?
4. 피부양자 설명이 충분하고 방해되지 않는가?
5. 급여 처방과 단순 권유를 구별하는가?
6. 결과 직후 현재 안내 위치를 찾는가?
7. “지금 뭘 해야 하지?”가 줄었는가?
8. 현재 단계 강조가 명확한가?
9. Highlight를 자격 판정으로 오해하지 않는가?
10. 필요한 상세정보로 바로 이동하는가?
11. 이동 직후 목적 정보가 보이는가?
12. 전체 Journey가 너무 복잡해 보이지 않는가?

## 16. 내구연한 5년 · Detail Return 개정 — 2026-09-20

### 1. 순서와 범위

최신 Notion HYP-003/P03, Content Spec, Product Validation, UX·접근성, Frontend 허브/Architecture/ADR 재조회 → 기존 파일 검토 → 공식 별표 재확인 → Content Spec 선행 수정 → 수정 문서 Fetch·문구 검증 → Frontend 순서를 지켰다. Coding Convention은 허브상 Planned / Not Created다. Notion 변경은 PAGE-P03-001에 한정했다. HYP-003 Untested, First Validation Candidate, Validation Artifact를 유지한다.

### 2. 공식 내구연한 확인

기존 **S03** 재사용: [장애인보조기기 보험급여 기준 등 세부사항](https://www.law.go.kr/admRulLsInfoP.do?admRulSeq=2100000276312), 제2026-56호, 시행 2026-03-25. 실제 브라우저에서 별표 5 문서뷰어를 열어 6쪽 중 5쪽 ‘10) 보청기(Hearing aid)’의 내구연한(년) 열 **5**를 확인했다. 제2조의2는 별표 5를 내구연한 근거로 연결한다. S01 제4조·제5조의 내구연한 경과/급여제한/개인 조건 확인도 읽었다. 숫자 확인은 자동 재지급 또는 개인별 기산일 계산 승인이 아니다. 신규 S11 없음.

사용자에게 ‘별표/서식 → 별표 5 → 5쪽 보청기 행’이라는 발견 경로도 제공한다. Claim 재확인일 2026-09-20을 별도 노출하며 기존 정책 전체 검토일 2026-09-18은 바꾸지 않았다.

### 3. Q4 변경

질문은 유지한다. 이전의 추상적인 ‘내구연한·재지급 조건을 확인하세요’에 직접 답을 더했다.

- Supporting Copy: ‘보청기 건강보험 급여의 내구연한은 5년입니다. 5년이 지났다고 자동으로 재지급되는 것은 아닙니다.’
- 받은 적 있어요: 이전 급여 시점이 대략 5년 전인지 확인하는 Next Action, 공식 기준 5년, 자동 재지급 아님, 이전 급여일·개인 적용·예외 공단 확인을 표시한다. 이전급여/전체절차/출처 링크.
- 기억나지 않아요: 이전 급여 이력·시점 확인을 Next Action으로 표시하고 같은 기준/주의를 제공한다. 이전급여/출처 링크.
- 처음이에요: 기존 제품·업소 확인, Journey 5단계 유지.

정책 문구는 content.ts의 previousBenefit에서 단일 관리한다. PreviousBenefitInfo를 결과와 #previous-benefit에서 재사용한다. 새 날짜 입력/기산일 계산/판정 없음.

### 4. Founder Observation

‘Official Source Discoverability Gap’: 공식 정보에 답이 있어도 Founder가 고시 메인에서 ‘몇 년인가?’를 즉시 찾지 못하고 별표를 탐색했다는 경험을 기록했다. Founder Observation이며 독립 사용자 Evidence가 아니다. EXP-001의 내구연한 4개·Return 5개 관찰 질문만 추가했고 EXP Result나 우월성 판정은 만들지 않았다.

### 5. Return Navigation

결과 heading id는 #guided-result. 결과 존재 시 ‘← 내 확인 단계로 돌아가기’, 결과가 없을 때는 ‘← 빠른 확인으로 돌아가기’ → #guided-check. 지역 server-compatible ReturnToGuidedCheck를 재사용하고 CSS :has(:global(#guided-result))로 표시를 전환한다. 숨은 링크는 display:none이라 Tab/접근성 트리에 남지 않는다. 답변을 별도 DOM 속성·전역 상태로 복제하지 않는다.

적용: scope, registration-help, eligibility, previous-benefit, benefit, steps, prescription-help, prescription, before-buying, document-details, sources. 두 접힌 상세 목적지에는 내부 내용 끝에 둔다. 나머지 Section/도움말 끝에만 둔다. Sticky UI 없음.

현재 history entry는 결과의 상세 링크를 따를 때 고정 #guided-result 위치로만 교체한 후 native anchor 이동을 유지한다. 기존 history.state는 보존하며 답변 값을 넣지 않는다. Browser Back도 결과로 돌아온다. Meta/Ctrl/새 탭 클릭은 변경하지 않는다.

### 6. Focus / Accessibility

상세 h2/h3로 focus, 처방 조상 details 열기, 결과 heading 복귀, Tab/Enter/Space, focus outline을 실제 브라우저에서 확인했다. 결과 heading 바로 아래 현재 단계 번호·이름이 있어 모바일 복귀 시 Journey 전체를 다시 스크롤하지 않아도 된다. 320px Return 링크 높이 46.4px로 44px 이상이며 잘리지 않았다.

실제 200% browser zoom, VoiceOver/NVDA 낭독은 미검증이다. 도구에 viewport 조절만 있고 실제 zoom/DevTools 기능이 없다. 뷰포트 축소를 200% 확대 검사로 대신 계산하지 않는다.

### 7. Responsive

320px/375px에서 상세 → 결과 왕복 확인. scrollWidth 305/360px(스크롤바 제외), 가로 넘침 없음. 320px 결과 screenshot에서 heading focus ring, 현재 4단계, 다음 행동, 5년 공식 기준과 주의가 읽히며 겹치지 않았다. 임시 viewport는 작업 종료 전 reset한다.

### 8. Privacy — 코드와 Runtime 구분

| 항목 | 코드 검토 | Runtime |
|---|---|---|
| Network | 답변 fetch/XHR/beacon/socket/API 호출 없음 | Network 요청 목록 직접 검사 미완료 |
| LocalStorage | 읽기/쓰기 없음 | 저장소 직접 검사 미완료 |
| SessionStorage | 읽기/쓰기 없음 | 저장소 직접 검사 미완료 |
| Cookie | 답변 cookie 코드 없음 | cookie jar 직접 검사 미완료 |
| URL | 공개 문서 위치 hash만 사용, 답변값 직렬화 없음 | #guided-result·상세 hash 이동과 새로고침 Q1 초기화 확인 |
| Analytics / Logging | 답변 분석 이벤트·console 없음 | 외부 분석 요청 직접 검사 미완료 |

현재 IAB capability는 pageAssets/webmcp 및 visibility/viewport다. DevTools Network/Application을 직접 열어 검사할 기능이 없으므로 저장소/네트워크 통과를 주장하지 않는다. 서버는 loopback production QA용이며 Backend 데이터 처리/API를 추가하지 않았다. 테스트 선택은 가상 경로다.

### 9. Verification

Node 24, lint/typecheck/build 통과, Next 정적 prerender 유지. 기존 관련 test runner/script 없음. 새 dependency 없음. noindex/nofollow 유지, sitemap route 없음.

| Scenario | 확인 결과 |
|---|---|
| A Q4 받은 적 있음 | 5년/주의 표시 → previous-benefit → 결과 heading, 현재 4 유지 |
| B 기억 안 남 | 이력·시점 안내/5년 → 상세 → 결과 유지 |
| C 처음 | 기존 제품·업소 단계 5, 이력 전용 블록 없음 |
| D benefit | 상세 → 명시적 Return → #guided-result |
| E before-buying | 상세 → Return → #guided-result |
| F prescription | details 열림·처방 h3 focus → Return → 결과 heading |
| G sources | 출처 → Return → 결과 |
| H Browser Back | 결과에서 상세 이동 후 Back → #guided-result 및 heading focus |
| I 새로고침 | #guided-result에서도 Q1, 결과 없음, 결과 링크 0개, 빠른 확인 fallback |
| J 답변 변경 | 이전 결과에서 Q3 미처방으로 수정 → 현재 3/새 행동, 이전 5년 블록 제거, Return은 최신 결과 |

초기 로드 직후 hydration 전 자동 키 입력과 연속 anchor 작업의 focus 완료 전 입력은 통과로 세지 않았다. 렌더/heading 상태를 확인한 뒤 사용자 입력을 다시 수행해 검증했다. 실제 runtime 오류/경고는 관찰되지 않았다.

### 10. 변경 파일

- content.ts: 5년 기준·주의·분기 행동·출처 경로 단일 데이터.
- guided-content.ts: Q4 past/unknown 분리와 Supporting Copy.
- GuidedCheck.tsx: 결과 anchor/현재 단계 요약/공통 내구연한 블록.
- components.tsx: 지역 ReturnToGuidedCheck, PreviousBenefitInfo, 대상 Section Return.
- page.tsx: 기존 previous-benefit 보강·세부 목적지 Return.
- DetailNavigation.tsx: heading focus·없는 결과 fallback·고정 위치 History.
- page.module.css: 상태에 따른 Return 표시와 터치 크기.
- 이 문서·README.md: 최신 기록과 이전 Client 경계 설명 정정.

### 11. Source Governance

S01/S03 재사용, 신규 Source ID 없음. S01–S10, C01–C04 Needs Review, S05 원문 연결 문제, Public Release Gate, Official/IYUM Explanation/Experience 구분 유지. 5년 재확인이 양측 나이/후기 청구/처방 기간/서류 문제를 해소하지 않는다.

### 12. 발견한 문제

- BLOCKER: 로컬 구현/필수 흐름에 남은 blocker 없음.
- NEEDS REVIEW: C01–C04/S05, 실제 200% zoom·화면낭독기·런타임 privacy 검사·실사용 이해도/반복 링크 부담.
- LOCAL FIX: CSS Modules가 #guided-result를 local ID로 바꾸어 조건이 맞지 않는 문제를 발견했고 :global selector로 수정 후 결과/질문 fallback 모두 재검증했다. 상세 heading focus와 Browser Back 복귀를 보완했다.

### 13. 하지 않은 작업

Backend/DB/Auth/Persistence/자격판정/계산기/정부 API/AI/Figma/Sticky/전역 상태/전체 디자인 변경/경험 입력/공개 배포 없음. EXP Result/Product Decision/HYP Supported/MVP·R1/정식 Requirement·IA·Screen 변경 없음.

### 14. Founder Self Review

1. 5년이라는 답을 바로 찾는가?
2. 자동 재지급으로 오해하지 않는가?
3. 이전 급여 시점 확인을 이해하는가?
4. 상세에서 결과로 쉽게 돌아오는가?
5. Return 문구가 자연스러운가?
6. 복귀 후 현재 위치를 다시 이해하는가?
7. Return 반복이 화면을 방해하지 않는가?
8. 모바일에서도 편하게 복귀하는가?

실제 사용자에게 확인할 질문이며 아직 Evidence가 아니다.

## 17. Consultation Preparation Implementation — 2026-09-20

### 목적 / Important Boundary

Check → Prepare를 실제로 사용해 상담 질문 준비의 추가 가치를 검증할 수 있는 로컬 Artifact를 구현했다. 실제 사용자 EXP가 아니다. HYP-003 = Untested / First Validation Candidate, Consultation Preparation = Solution Hypothesis / Not Approved Product Feature, Release Not Assigned를 유지한다. Product Decision·EXP-001 Record/Result·Evidence 승격·MVP/R1·정식 Product 계약 변경 없음. 이번 작업에서 Notion 수정 없음.

### Source of Truth

구현 전에 최신 HYP-003, PAGE-P03-001, Product Validation, UX·접근성 원칙, Frontend Architecture, Frontend 허브·ADR를 fetch했다. PAGE의 §2·2.1·6·7·7.1·7.5·9.1·11·12·13·14·16을 기준으로 구현했다. 대용량 UX/ADR는 관련 state ownership·native controls·focus·storage·CSS Modules·interactive leaf 원칙을 선별 확인했다. Coding Convention 검색과 Frontend 허브 확인 결과 Planned / Not Created로, 별도의 완성된 Convention 문서는 없었다. 기존 TypeScript strict / CSS Modules / narrow Client boundary를 따랐다. 새 dependency 없음.

### Component / Question Data

- `GuidedCheck.tsx`: 기존 질문·Journey·결과 유지. 결과 CTA와 Prepare의 좁은 page-local 메모리 경계.
- `ConsultationPreparation.tsx`: 추천 편집, Include/Exclude, 사용자 질문, 상담 완료 체크, 상담용 화면, 인쇄용 트리. page 전체를 Client로 바꾸지 않음.
- `consultation-content.ts`: Notion의 14개 질문 Copy·reason·target·base/contextual·source metadata, 정적 Rule Mapping. NHI-04는 별도 예외 맥락을 수집하지 않으므로 자동 추천하지 않음.
- INS-01은 이번 요청의 Q2 공식기관 질문 Scenario를 충족하도록 기존 Q2 안내(보험·의료급여 자격과 적용 경로 확인)를 질문으로 표현한 로컬 Copy다. 새 정책 Claim이 아니다. 의료급여 전용 절차나 자격 결과를 생성하지 않음.
- `DetailNavigation.tsx` / `components.tsx`: Prepare → Detail → Prepare native anchor/history/focus 연결. 준비 상태 없는 새로고침에서는 Guided로 fallback.
- `page.module.css`: 편집·상담·print layout. globals/page/content/guided-content의 기존 정책·SEO 정의는 변경하지 않음.

### 추천 / Rule Mapping

| 입력 | 추천 변화 |
| --- | --- |
| 미응답 / 첫 급여 | 핵심 기본 질문 4개: CTR-01/02/03, MED-02. 다른 기본 질문 4개는 Disclosure에서 직접 선택 |
| Q1 no / unknown | ADM-01 추가, 실제 목적지 읍·면·동 주민센터 명시 |
| Q2 medical-aid / unknown | INS-01 우선. 건강보험 기본 질문 자동 포함 없음; 접힌 참고 목록에서만 직접 선택 |
| Q3 no | MED-02 포함, 개인별 필요한 의료 절차 확인 |
| Q3 unknown | MED-01 추가 |
| Q4 past | NHI-01/02/03 추가, 5년 일반 기준·자동 재지급 아님 먼저 설명 |
| Q4 unknown | NHI-01/02만 추가; 과거 급여가 있었다고 확정하지 않음 |

14개를 한꺼번에 제시하지 않는다. 일반 분기의 첫 노출은 4–7개(의료급여/보험 불명은 1개). '다른 기본 질문'은 처음에는 미포함이며 사용자가 펼쳐 선택한다. 질문 원본은 삭제하지 않는다. 규칙은 질문 연결이며 자격 판정이 아니다.

### 편집 / State lifecycle

추천 Include/Exclude·재선택 가능. 자기 질문은 300자 plain textarea, trim/빈 입력 검증, 추가·삭제와 상태 안내. React가 텍스트로 출력하며 HTML 해석 없음. 삭제 후 추가 버튼으로 focus 복귀. 직접 추가한 질문은 IYUM 검토 질문과 분리.

Guided 답변이 바뀌면 사라진 상황 질문은 제거되고 현재 상황 질문은 기본 포함으로 다시 계산한다. 기본 질문의 수동 포함/제외와 Custom Question·작성 중 입력은 보존한다. 상황 질문 선택과 상담 완료 체크는 초기화하며 안내한다. Guided '처음부터 다시 시작'은 Guided 답변을 지우고 같은 재계산 정책을 적용한다. 전체 새로고침은 모든 메모리 상태를 지운다.

### Answer-First / Consultation / Print

확인된 제품·업소 등록 구분과 일반 절차를 먼저 설명하고, 이전 급여 분기에서는 기존 PreviousBenefitInfo를 재사용한다. 5년 자체를 외부에 재질문하지 않고 실제 급여일·이력·개인 적용을 질문한다. 센터를 부정적으로 단정하지 않는다.

상담용 View는 포함 질문+Custom만 대상 그룹별로 표시한다. 별도 메모리 완료 Checkbox는 '상담에서 질문 완료/미완료'이며 편집의 '상담 목록에 포함/미포함'과 다른 상태다. 편집 복귀 시 선택 유지. 원답·추천 이유는 상담용에 출력하지 않음.

인쇄 버튼은 명시적 `window.print()`이며 별도의 print-only 트리는 선택 질문·사용자 질문·그룹·빈 5mm 완료 체크 영역·최소 제목/목적만 포함한다. Hero/Nav/Guided/원답/추천 rule·reason/긴 정책 본문/버튼/Footer 제외. 서버 PDF·파일 API 없음. 브라우저 자체 URL/날짜 header/footer는 브라우저 설정 영역임을 안내한다.

### Verification

최종 코드 Lint / Type Check / Build PASS. Next.js route 정적 prerender 유지. 기존 테스트 runner/script 없음. Bundled Playwright + 별도 Chrome context로 A–N과 경계/회귀 검증을 실행했다(프로젝트 package 추가 없음). 테스트 데이터는 가상의 일반 질문이며 사용자 의료정보를 사용하지 않았다.

| Scenario | 결과 |
| --- | --- |
| A 첫 급여 | PASS — Base 중심, 이전 이력 없음, 제외·추가·상담·편집 |
| B 이전 급여 | PASS — 5년 Answer-First, 날짜·이력·조건부 재지급 질문 |
| C 이력 모름 | PASS — 날짜·이력, 경과 사실 단정 없음 |
| D 처방 없음 | PASS — 의료기관 질문·상세 이동 |
| E 처방 모름 | PASS — 급여용 처방 확인 질문 |
| F 보험 모름 | PASS — 공식기관 질문, 건강보험 preset 방지 |
| G Guided 없이 | PASS — Base 접근, 빠른 확인은 선택사항 |
| H 제외/재선택 | PASS — 상담용 반영 |
| I 사용자 질문 | PASS — 추가·삭제·인쇄 대상 동기화 |
| J 상담용 | PASS — 선택 질문만, 그룹·완료 체크·편집 복귀 |
| K Print | PASS — print media 검사 + Chrome 실제 Preview 시각 확인 |
| L 새로고침 | PASS — Guided Q1, 준비 닫힘, Custom 소멸 |
| M Guided 변경 | PASS — 상황 목록 갱신, Custom 보존 |
| N Detail 왕복 | PASS — 상세 heading/Prepare heading focus 및 목록 유지 |

추가 확인: Q1 목적지 분리, 의료급여 분기, 전체 질문 제외 시 빈 상태/상담·인쇄 버튼 비활성, 기본 질문 Disclosure, 빈 입력 안내, 300자 unbroken text의 320px reflow, 인쇄 체크 영역이 항상 빈 상태, 기존 Guided Back/Restart·Journey current step·Q4·결과 Return·FAQ Disclosure·공식 Source 7개 링크·noindex/nofollow. sitemap 파일/노출 경로 추가 없음. 공식 외부 사이트 정책·링크 전체의 재검증을 했다는 뜻은 아니다.

### Responsive / Accessibility / Print QA

- 1280 / 768 / 375 / 320px: 편집·상담 화면 document scrollWidth 검사 PASS, 가로 overflow 없음. 모바일 스크린샷 확인. 긴 300자 단어도 320px overflow 없음.
- Keyboard Space로 선택 변경, visible focus outline, 보이는 Checkbox Label의 44px 이상 조작 영역 확인 PASS. Native fieldset/legend·label·button·details와 상태 안내 확인. 입력 진입·삭제·상담/편집 전환·Detail 왕복 focus 구현.
- 실제 Chrome 200% 확대: 편집 질문·체크·입력·추가/삭제·하단 CTA/내비게이션의 줄바꿈/잘림 시각 확인 PASS. 상담용 200%의 별도 실제 조작은 미검증(상담용은 320px reflow와 native 의미구조 확인).
- Chrome native AX tree에서 그룹·체크 상태·입력 Label 확인. 실제 VoiceOver/다른 화면낭독기 낭독 세션은 미검증. 접근성 전체 준수 PASS를 주장하지 않음.
- 실제 Chrome Browser Print Preview: 기본 4개+Custom 1개가 1쪽, 빈 체크 영역·그룹 순서·사용자 질문 정상, Navigation/Guided/원답/긴 설명 없음. Browser Print renderer의 추가 PDF QA에서도 selected/custom 동기화 및 원답·rule 부재 확인. 저장/물리 인쇄를 실행하지 않았으며 테스트용 자동 렌더 파일은 `/tmp/iyum-prepare-qa/`에만 있음.

### Privacy — Code Review vs Runtime

| 항목 | 코드 검토 | 별도 Chrome context Runtime |
| --- | --- | --- |
| Network | fetch/API/Server Action/전송 경로 없음 | 로드 완료 후 체크·Custom 추가·상담 View 전환 동안 요청 0 |
| LocalStorage | 사용 없음 | key 0 |
| SessionStorage | 사용 없음 | key 0 |
| Cookie | 사용 없음 | context cookie 0 |
| URL | public content anchor만, 답변 serialize 없음 | 테스트 질문/응답값 미포함 |
| Console | 사용자 질문 logging 없음 | 테스트 marker 로그 없음, pageerror 0 |
| Analytics | SDK/관련 dependency/질문 payload 없음 | 관찰 동작 중 요청 0; Production 외부 monitoring 검증을 의미하지 않음 |

새로고침 초기화 확인. 인쇄/PDF 저장은 사용자 로컬 동작이며 전송 경로 없음. 브라우저 확장 프로그램이나 OS의 기록까지 앱이 통제한다고 주장하지 않는다.

### 발견 사항 / Remaining Needs Review

- BLOCKER: 현재 구현 범위에서 없음.
- LOCAL FIX: Prepare 진입/모드 전환 시 heading을 화면 상단으로 정렬; 새로고침 후 준비 anchor는 Guided로 fallback.
- NEEDS REVIEW: 기존 C01–C04, S05 원문 연결 문제, 실제 사용자 Utility, 공식기관/업소별 운영 확인. 정책 Copy 임의 변경·새 Source 추가 없음.
- 검증 제한: 실제 Screen Reader, 상담용 200% 별도 조작, 다른 브라우저/물리 인쇄는 미검증. Chrome 검증과 동일시하지 않는다.
- Coding Convention은 Planned이며 정식 계약을 만들지 않았다. Git 저장소가 없어 commit/PR 없음.

### Founder Self Review — 사용자 Evidence 아님

1. 상담 질문 준비 CTA의 목적이 바로 이해되는가?
2. Guided Check 결과와 질문 추천이 연결됐다고 느껴지는가?
3. 추천 질문 수가 부담스럽지 않은가?
4. 센터 / 병원 / 공단 그룹 구분이 이해되는가?
5. 왜 물어봐야 하는지 충분히 이해되는가?
6. 불필요한 질문을 쉽게 제외할 수 있는가?
7. 질문을 다시 포함하기 쉬운가?
8. 자기 질문 추가가 자연스러운가?
9. 상담용 View가 실제 현장에서 쓸 수 있을 것 같은가?
10. Print 결과가 실제 들고 갈 만한 형태인가?
11. IYUM이 이미 답한 내용을 외부 기관에 다시 묻게 하는 중복이 있는가?
12. “그냥 센터에 맡기면 되지”라는 생각이 여전히 강한가?
13. 이 질문 목록을 실제 상담에 가져가고 싶은가?
14. 가져가고 싶다면 어떤 질문 때문에 그런가?
15. 가져가고 싶지 않다면 왜 그런가?

## 18. Document Responsibility / Claim Responsibility Improvement

작업일: 2026-09-20. Validation Artifact / HYP-003 Untested / First Validation Candidate / Founder Priority 4.60 / Not Approved Product Feature / Release Not Assigned 유지.

### Founder Observation과 공식 사실 구분

Founder는 기존 단계별 서류 목록이 병원·판매업소 발급 자료까지 사용자가 모두 직접 준비해야 하는 것처럼 느껴질 수 있다고 지적했다. 이는 설계 입력인 Founder Observation이며, 다른 사용자의 반복 행동이나 EXP 결과가 아니다. 이번 개선은 청구 주체 → 내가 할 일 → 기관 발급 자료 → 전체 서류 참고 순서로 재구성했다. 센터에 맡기는 것이 충분하다는 반증 가능성도 유지한다.

### Source of Truth / Notion 선행 변경

HYP-003, PAGE-P03-001, Product Validation, UX·접근성 원칙, Frontend Architecture, Frontend 허브, 관련 ADR을 새로 fetch했다. 대용량 문서는 관련 원칙을 선별 읽었으며 전체 완독으로 주장하지 않는다. Coding Convention은 허브 및 검색에서 Planned / Not Created로 재확인했다. Repository에 적용 AGENTS.md 없음.

PAGE만 13개 정확한 본문 패치: §5, §7, §7.1, §7.5, §8.5, §8.7, §9.1, §10, §12, §13, §15, §16. HYP·Hub·정식 계약은 수정하지 않았다. 수정 후 재fetch한 본문은 Notion의 빈 줄 정규화 외에 예정 본문과 일치했다. 청구 주체 우선, 역할 구분, 직접 청구 보존, 센터 일괄 처리 단정 금지, C04 유지, 질문·Answer-First·Observation을 확인한 다음에만 Frontend를 수정했다. 이전 Prepare 계획 시점의 미구현 표현은 과거 기록임을 §13에서 명시했다.

### 공식 근거 재확인 / C04 유지

기존 S05 동일 시행규칙의 [현행 제26조 접근 경로](https://www.law.go.kr/LSW/lsLinkProc.do?chrClsCd=010202&datClsCd=010102&gubun=admRul&joNo=002600001&lsId=22267&lsNm=%EA%B5%AD%EB%AF%BC%EA%B1%B4%EA%B0%95%EB%B3%B4%ED%97%98%EB%B2%95%EC%8B%9C%ED%96%89%EA%B7%9C%EC%B9%99&mode=10&print=print)을 보완했다. 시행 2026-08-11 / 보건복지부령 제1187호. S01 [NHIS 고시 대조본 제5조](https://www.nhis.or.kr/lm/lmxsrv/law/lawFullContent.do?SEQ=87&SEQ_HISTORY=)도 확인했다. 신규 Source ID 없음. S01–S10, C01–C04, Public Release Gate 유지.

- 직접 청구: 제26조제2항의 지급청구서, 전문의 처방·검사자료, 검수확인서, 구매 지출 증빙, 해당 제품 식별 사진 등 첨부자료 구조를 확인했다.
- 판매업소 위임 청구: 제26조제5항의 위임장과 가입자·피부양자 신분증 사본 추가를 확인했다. 업자 자격 증명 서류에는 등록 업자 등의 예외가 있으므로 사용자에게 일괄 요구하지 않는다.
- 병원 발급: 처방·검사·검수 관련 자료. 판매업소 발급: 구매·지출 증빙. 사용자는 직접 청구의 작성·제출, 위임 시 본인 제공 자료·서명을 확인한다.
- 사용자 화면은 문서 파일별 작성 매뉴얼을 추가하지 않았다. 최신 개별 서식 파일·서명란·구매 계약과 적합관리까지 포함한 전체 제출 조합·접수 운영 정합성은 미완료다. **C04 Needs Review 유지**. C01·C02·C03도 이번 확인으로 해소하지 않았다.
- 기존 S05 별표 링크의 본문 표시 문제 및 S06·S07 정적 안내 최신성 주의는 보존했다. 이번 제26조 보조 경로 확인을 원래 모든 링크 문제 해결로 기록하지 않는다.

### Information / Interaction 변경

- 기본 제목: “내가 직접 준비하거나 확인할 것은 무엇인가요?”
- 일반 발급 역할과 청구 주체에 따른 차이를 먼저 설명하고 “내가 지금 할 일” 3개를 노출한다.
- 센터 지원: 지원 범위, 사용자 자료·서명·직접 수령, 남은 일·진행 상태 확인.
- 직접 청구: 병원 자료 수령 → 판매업소 증빙 수령 → 필요 자료 모으기 → 공단 제출 경로 확인.
- 단일 Disclosure에 사용자 / 병원 / 판매업소 역할, 기존 5개 단계별 서류 그룹을 보존했다. 기본 닫힘이며 중첩하지 않는다.
- CTR-07: “급여비 청구는 제가 직접 해야 하나요, 이 센터에서 위임받아 진행해주나요?”
- CTR-08: “센터에서 급여 청구를 지원한다면 제가 직접 준비하거나 서명해야 하는 것은 무엇인가요?”
- 핵심 기본 질문은 센터 5개 + 의료 1개, 나머지 기본 4개는 펼친 뒤 직접 선택한다. 의료급여·보험 미확인 분기는 건강보험 기본 질문을 자동 포함하지 않는다. 기존 상황 질문과 사용자 편집 유지.
- Answer-First: 일반 역할 설명은 IYUM이 먼저 답하고, 해당 센터의 지원 범위·개인 제공 자료만 상담 질문으로 연결한다.
- documents / document-details 호환 유지. claim-method / user-preparation은 지역 anchor일 뿐 정식 Routing Contract가 아니다. Prepare 문맥에서는 복귀 링크 우선, 복귀 시 선택 및 사용자 질문 유지.
- 8단계 Journey 유지. 구매 직전 Next Action과 청구 단계에 지원 범위·내 할 일을 추가했다.
- 새 입력값·상태·저장·의존성 없음. README와 CSS는 기존 동작으로 충분해 변경하지 않았다.

### 검증 결과

최종 코드에서 Node 24 환경 `npm --prefix frontend run lint`, `typecheck`, `build` 모두 PASS. 정적 prerender route 유지. 검증 서버는 127.0.0.1:3001.

| 시나리오 | 결과 |
| --- | --- |
| A Guided → Prepare → 청구 질문 → 상담용 | PASS · 두 질문 기본 포함 및 표시 |
| B 제외 → 재선택 → 상담 반영 | PASS |
| C 질문 이유 → 두 역할 anchor → Prepare | PASS · Enter로 이유 열기, 목적지 heading focus, Prepare heading 복귀·선택 보존 |
| D documents 직접 진입 | PASS · 내가 지금 할 일 우선, 전체 서류 기본 닫힘 |
| E 직접 청구 | PASS · 수령·준비·공단 경로 설명 |
| F 센터 지원 | PASS · 업소별 지원 범위·사용자 남은 일 명시 |
| G 전체 서류 | PASS · Space로 펼치기, 세 역할·기존 5개 서류 그룹 유지 |
| H 320px | PASS · 서류·역할·긴 상담 질문 가로 넘침 없음 |
| I 200% Zoom | PASS · 독립 Chrome 프로필의 native page zoom 2배. outerWidth1280 / innerWidth640 / DPR2 / scrollWidth640. 서류·역할·편집·상담 모드 overflow 없음. Headless 캡처 축척 한계가 있어 DOM 측정 결과와 일반 배율 시각 검수를 구분함 |
| J Print Preview | PASS · 독립 headed Chrome chrome://print 실제 미리보기 캡처. 청구 질문 2개 포함 기본 6개가 한 장에 표시. 전체 행정서류·원답·이유 자동 출력 없음. 물리 인쇄는 하지 않음 |

추가: 기존 Prepare 회귀 19개 보고 항목 통과. Guided 이전/다시 시작, Q4 5년·이력 분기, 보험/의료급여/등록 조기 종료, 사용자 질문 추가·삭제·빈값·300자, 상담 완료와 포함 상태 분리, 빈 목록, 기타 기본 질문, 새로고침 초기화, 상세 왕복, FAQ·출처 링크 보존. noindex/nofollow 확인, sitemap.xml404, 브라우저 pageerror0. 정책 금지 문구를 새로 도입하지 않았다.

### Accessibility / Responsive

Semantic h2/h3/h4, 목록·역할 텍스트, native details/summary와 checkbox를 사용한다. 실제 keyboard Enter/Space, visible focus, anchor 자동 펼치기·heading focus 및 복귀를 확인했다. 질문 label·서류 summary 44px 이상 확인. IAB 접근성 트리에서 제목 단계·접힘 상태·역할 텍스트를 확인했다. 실제 VoiceOver/NVDA 낭독은 미검증이며 접근성 적합성 전체 PASS로 주장하지 않는다.

1280 / 768 / 375 / 320px에서 서류·기관 역할·질문 편집·상담용 화면 가로 overflow 없음. 일반 배율 320 및 1280 스크린샷 검토. 200% native browser zoom은 위 DOM 측정·조작으로 별도 확인. 다른 브라우저·실제 모바일 단말은 미검증.

### Runtime Privacy

초기 페이지 로드 완료 후 청구 질문 포함·제외, 이유 펼치기·anchor 왕복, 사용자 테스트 질문 추가·상담 모드 전환 동안 추가 network request0. 새 isolated context cookies0 / localStorage0 / sessionStorage0. URL에는 공용 content anchor만 있고 원답·선택값·사용자 질문 없음. 테스트 marker console logging 없음. 새로고침 후 준비 내용 초기화. 인쇄 검증의 테스트 질문은 임시 로컬 PDF에만 있으며 서비스 저장이 아니다.

### BLOCKER / NEEDS REVIEW / LOCAL FIX

- BLOCKER: 현재 요청의 로컬 구현·검증 범위에서 없음.
- NEEDS REVIEW: C01–C04, 센터별 지원 범위, 실제 사용자 Utility, 실제 스크린리더·물리 인쇄·다른 브라우저. 최종 서류 조합/접수 보장 없음.
- LOCAL FIX: 청구 역할 설명, 질문·이유·anchor 연결, 청구 단계 안내. 테스트의 접힌 link 탐색 선택자를 수정했다(제품 결함 아님).
- 자동 승인 검토가 기존 Chrome 접근성 트리 읽기를 개인 대화 노출 위험으로 차단했다. 해당 창은 더 읽지 않았고 독립 QA Chrome 프로필·프로세스에서 Zoom/Print를 완료했다. 사용자 승인 대기 작업은 남지 않았다.
- 구현 검증은 실제 사용자 Evidence가 아니다. EXP-001 미실행 / HYP-003 Untested 유지.

### 변경 파일

content.ts: 역할 설명·사용자 행동·기존 S05 조문 경로·청구 단계. page.tsx: 기본/상세 구조. guided-content.ts: Next Action·청구 요약·anchor 타입. consultation-content.ts: CTR-07/08과 우선순위. ConsultationPreparation.tsx: Answer-First·질문별 상세 링크 라벨. DetailNavigation.tsx: 두 anchor focus 지원. docs/validation-notes.md: 이 기록.

Backend·DB·Persistence·AI·Product Decision·EXP Record/Result·Evidence 승격·MVP/R1·Requirement·Feature·정식 IA/Screen/Architecture/API 계약 변경·외부 배포는 하지 않았다. Git 저장소가 아니므로 commit/PR 없음.

### Founder Self Review — 관찰 결과가 아닌 검토 질문

1. 서류 Section을 봤을 때 전부 내가 준비해야 한다고 느껴지는가?
2. 내가 실제로 할 일이 먼저 보이는가?
3. 급여 청구를 누가 하는지 먼저 확인해야 한다는 점이 이해되는가?
4. 센터가 청구를 지원할 수도 있다는 점을 이해하는가?
5. 그렇다고 센터가 모든 것을 해준다고 오해하지 않는가?
6. 내가 직접 청구하는 경우의 차이가 이해되는가?
7. 병원 / 센터에서 발급하는 자료를 굳이 처음부터 볼 필요가 없는가?
8. 필요한 경우 전체 서류를 쉽게 찾을 수 있는가?
9. “급여 청구를 센터가 지원하나요?” 질문이 실제 상담에 유용해 보이는가?
10. “제가 직접 준비하거나 서명할 것은 무엇인가요?” 질문이 유용한가?
11. 상담 질문 리스트가 더 현실적으로 느껴지는가?
12. 여전히 행정정보가 너무 많아 부담스러운가?


## 19. Official Registry Lookup Candidate Implementation

작업일 2026-09-20. **제품 2개 공식 표본으로 축소 구현. 센터 공식 표본 0개: 등록 여부 판정 미제공.** Solution Hypothesis / Untested / Validation Prototype Candidate / Not Approved Product Feature / Release Not Assigned. HYP-003 Untested / First Validation Candidate / Priority 4.60 유지. 구현 검수는 사용자 Evidence가 아니다.

### Founder Insight / Notion-first / 구현 상태 Alignment

공식 공개정보로 확인할 수 있는 등록 사실을 사용자가 외부에서 다시 찾거나 센터에 다시 묻는 부담을 줄이면 사전 확인·상담 준비 가치가 커지는지 검증한다. 제품 검색·상담 질문 변화가 실제 유용한지는 아직 관찰하지 않았다.

HYP-003 → PAGE-P03-001 → Product Validation Hub를 순서대로 새로 fetch한 뒤 IA·제품 개요의 Cross-domain 원칙, 현재 Frontend, 공식 등록 구조를 확인했다. PAGE의 Prepare 미구현/다음 iteration/구현 검증 NOT STARTED 표현을 현재 구현 사실로 정렬했다. Guided Check/Journey, Prepare, Include/Exclude, 사용자 질문, Consultation Mode, Browser Print, Documents Responsibility는 Implemented Validation Prototype이다. 사용자 관찰 NOT STARTED는 유지한다. HYP의 Prepare 미구현 표현과 Registry Note만 최소 정렬했고 Core·Priority·Evidence는 변경하지 않았다. Hub/IA/제품 개요/정식 계약은 수정하지 않았다.

PAGE §7.6에 상태·역할·표본·정확 매칭·최신성·상담·인쇄·개인정보·운영 보류·관찰 계획을 기록했다. 수정한 PAGE와 HYP를 재fetch하여 구현 상태, Candidate, Untested/Not Approved/Release Not Assigned, 미구현 표현 제거, 상태 정의·정확 식별·범위 축소·Backend/IA 승인 없음 확인 **후에만** Frontend를 수정했다. 구현 후 로컬 검증 결과와 센터 BLOCKER를 다시 기록하고 재fetch했다. Notion이 파일명을 자동 링크로 정규화해 완료 패치 1회가 불일치로 거절됐으나 최신 fetch 문자열로 재적용·검증했다.

### Answer-First / Cross-domain

| 수준 | 책임 |
| --- | --- |
| General Rule | IYUM이 공식 일반 기준을 설명 |
| Public Official Registry Fact | 정확히 확인된 공식 표본에 한해 IYUM이 결과 제공 |
| Individual Administrative Fact | 개인 이전 급여일·재지급·예외는 공단 |
| Medical Judgment | 개인 의학적 필요·적합성은 의료기관/전문가 |
| Commercial / Service Condition | 실제 가격·청구 지원·조절관리 범위는 센터 |

기존 커뮤니티·복지지원·병원전문기관·보조기기를 잇는 Cross-domain Problem Guide 후보. 새 Domain/GNB/5번째 Category/정식 IA/Route는 없다. 기존 10개 Detail 목차·내용 순서와 8단계 Journey 유지.

### 공식 Source / Verified Static Snapshot

- S01 [현행 세부사항](https://www.nhis.or.kr/lm/lmxsrv/law/lawFullContent.do?SEQ=87&SEQ_HISTORY=): 등록·변경·탈퇴·공개 체계 확인. 개별 센터의 등록 증거로 사용하지 않는다.
- S08 [현행 급여제품 및 결정가격 고시](https://www.nhis.or.kr/lm/lmxsrv/law/lawFullContent.do?SEQ=1619&SEQ_HISTORY=): 보건복지부고시 제2026-86호, 2026-04-16 시행. NHIS 원문 제품군 I 이미지의 1·2행을 브라우저에서 직접 시각 확인했다.

| 모델 | 공식 제품코드 | 고시 업체명 | 공식 확인일 |
| --- | --- | --- | --- |
| Starkey Picasso 1000 CIC | D22018010015 | (주)스타키코리아 | 2026-09-20 |
| Starkey Picasso-P 1000 CIC | D22018010016 | (주)스타키코리아 | 2026-09-20 |

제조공장/원산지/개인 적합성을 추정하지 않고 원문 열 이름인 ‘고시 업체명’을 사용한다. 코드의 sourceUrl/checkedAt은 각 실제 표본에 포함한다. 가격·제품 추천·재고 정보는 추가하지 않는다. 가짜 VERIFIED 데이터 없음.

센터: 공단 현행 법령·정적 보조기기 안내·통합검색·전체 메뉴·서비스찾기를 확인했으나 업소명+주소가 함께 나온 개별 등록 결과를 확보하지 못했다. 요청 §49의 축소 허용에 따라 **센터 VERIFIED/AMBIGUOUS 표본을 만들지 않았다.** 조회 시 SOURCE_UNAVAILABLE 안내만 제공하며 미등록/NOT_FOUND로 위장하지 않는다. 센터 조회의 성공 가치·주소 선택 UX·CTR-01 제거는 표본 확보 전 미구현/미검증이다. 긴 실제 센터명·주소의 화면 검수도 해당하지 않는다.

신규 Source ID 없음. 기존 S01–S10 및 C01(연령)·C02(후기 적합관리)·C03(처방 기한)·C04(최종 서류 패키지) Needs Review 유지. Registry 확인일 2026-09-20은 전체 정책 검토일 2026-09-18을 갱신하지 않는다.

### UX / Matching / State

Guided 뒤, Prepare 앞에 항상 접근 가능한 #registry-check를 추가했다. Journey 5, 구입 전 확인, Prepare에 진입 링크. DetailNavigation의 public anchor focus/Return을 재사용한다. 검색값은 hash/history에 쓰지 않는다.

trim + 대소문자 + 연속 공백 정규화. 정확한 모델명/코드 우선, 없으면 모델/코드/업체명의 contains 후보. punctuation을 임의 제거하거나 fuzzy 추정을 하지 않는다. 브랜드/부분명/정확명 입력 모두 후보의 모델·업체·코드를 보고 **명시적으로 선택한 뒤** VERIFIED. 단일 후보도 확인 전 자동 VERIFIED가 아니다. 검색어 변경 즉시 이전 선택·결과·인쇄 요약 무효화. 빈 입력 오류는 전송 없이 안내한다.

| 상태 | 의미 / 처리 |
| --- | --- |
| VERIFIED | 정확히 선택한 공식 표본, 최신성 범위 내. 날짜·출처 표시 |
| NOT_FOUND | 일부 제품 표본에 없거나 표기가 다를 수 있음. 미등록 확정 아님 |
| AMBIGUOUS | 제품 후보 선택 대기. 복수/부분 결과를 구체 모델로 좁힘 |
| STALE | 실제 확인일로부터 7일 이상, 현재 등록 확정 문구 제거·질문 유지 |
| SOURCE_UNAVAILABLE | 공식 센터 표본 미확보. 등록 여부 판단 불가 |
| IDLE | 아직 검색 안 함 / 검색어 변경 뒤 이전 결과 취소 |

7일은 검증용 분기이고 운영 SLA가 아니다. 로컬 30초 타이머·탭 focus/visibility 복귀·beforeprint 갱신. ‘최신성 지연 시뮬레이션’ 체크는 실제 checkedAt을 변경하지 않고 화면/상담/인쇄에 시뮬레이션임을 표시한다. 실제 시계 2026-09-28로 전진 시 STALE와 CTR-02 복원을 검증했다. 처음 시계 테스트는 로드 후 mock을 설치해 기존 타이머를 제어하지 못했으며 로드 전에 설치하는 올바른 테스트로 수정했다(제품 결함 아님).

### Prepare / Consultation / Print

최신 VERIFIED 제품이면 CTR-02 등록 질문을 제외하고 CTR-09 ‘왜 이 제품을 추천하나요?’로 바꾼다. 기본 6개 유지, 비용 CTR-03·청구지원 CTR-07·내 준비서명 CTR-08 유지. 비교 CTR-06·관리 CTR-05는 다른 기본 질문에서 직접 선택한다. 보험/의료급여 불명확 분기는 건강보험 구매 질문 자동 포함을 막는 기존 보호 유지.

센터 표본이 없으므로 CTR-01 유지. not-found/ambiguous/stale는 CTR-02 유지. Registry 변경은 사용자 포함/제외·추가 질문을 보존하고 상담 완료 표시를 초기화하여 재확인하도록 한다. Entity 변경/검색어 수정 뒤 이전 제품을 인쇄하지 않는다.

상담/인쇄: 선택 제품 모델·업체·코드, 상태, 날짜, 공식 출처, 일부 표본·실시간 아님·개인 자격 아님 요약. 센터 확인을 눌렀으면 표본 미확보도 표시. 검색어·Guided 원답·검색 UI·전체 정책·전체 서류는 출력하지 않는다. 선택/직접 추가 질문과 빈 체크박스만 출력한다. 독립 Chrome 실제 chrome://print 미리보기에서 요약+기본6질문이 1장에 표시됨을 시각 확인했다. 물리 인쇄·외부 저장·공유는 하지 않았다.

### Browser A–O 결과

| 시나리오 | 결과 |
| --- | --- |
| A 센터 정확 선택 VERIFIED | BLOCKED · 공식 업소명/주소 표본 미확보; 거짓 성공 없음 |
| B 제품 정확 모델 | PASS · 표기 정규화/정확 후보 선택/VERIFIED/출처·날짜/CTR-02 제거 |
| C 센터 부분/복수 후보 | BLOCKED · 같은 이유. 주소 선택을 검증했다고 주장하지 않음 |
| D 제품 부분/브랜드 | PASS · 2개 후보/확정 전 AMBIGUOUS/정확 모델 선택 |
| E 없는 센터 | 축소 경로 PASS · SOURCE_UNAVAILABLE, NOT_FOUND 또는 미등록 판정 아님 |
| F 없는 제품 | PASS · NOT_FOUND 범위 제한, 식별 질문 유지 |
| G STALE | PASS · 명시적 시뮬레이션 + 실제 시계 만료, 실제 날짜 보존 |
| H Guided 없이 사용 | PASS |
| I Registry → Prepare | PASS · 질문 변경·사용자 편집 보존 |
| J Prepare/Registry → Detail → 복귀 | PASS · heading focus/선택 유지 |
| K Consultation Mode | PASS · Entity·상태·일자·출처 요약, 완료 상태 분리 |
| L Print Preview | PASS · print media/PDF 렌더 + 실제 native Chrome 미리보기 시각 확인 |
| M 새로고침 | PASS · 검색어·선택·Prepare·사용자 질문 초기화 |
| N 320px | PASS · 제품 후보·결과·상담·긴 질문 가로 overflow 없음 |
| O 200% Zoom | PASS · 독립 Chrome native zoom: outer1280 / inner640 / DPR2 / scroll640; 후보·결과·Prepare·상담 조작 가능 |

Native zoom의 Headless 스크린샷 축척 한계와 DOM/조작 확인을 구분한다. 화면리더 실제 낭독·전 브라우저·실물 모바일·당사자 테스트는 미검증.

### Trust / Privacy / Accessibility / Responsive

등록제품≠개인자격, 등록업소≠모든거래급여, NOT_FOUND≠미등록, snapshot≠실시간, IYUM≠공단공식시스템을 UI/상담/인쇄 문구로 구분했다. 실제 사용자가 오해하지 않는다는 Evidence는 없다.

Code review: React local state만 사용. query/result/entity/Guided/questions에 fetch/XHR/beacon/storage/cookie/analytics/log 호출 없음. source link는 사용자가 클릭할 때 공식 원문으로 이동하며 입력값을 붙이지 않는다. 타이머는 로컬 날짜 비교만 하고 요청하지 않는다.

Runtime: 새 isolated context에서 초기 HTML/CSS/JS 9개 요청과 분리하여 검색·선택·상태변경·질문 편집·왕복·상담 동안 **추가 요청0**, localStorage0/sessionStorage0/cookie0. URL에 검색어/질문 없음, console에 sentinel 없음, pageerror0. 초기 렌더 요청을 0이라고 주장하지 않는다. 테스트 출력 PDF는 로컬 QA 산출물이며 제품의 데이터 저장 기능이 아니다.

Accessibility: label-input, h2/h3/h4, role=status/aria-live, ul 후보/native button, text status. 키보드 Enter 후보선택 후 결과 heading으로 focus 이동, Space checkbox, Detail/Return heading focus, visible focus, 44px 이상 input/button/link/summary 확인. IAB 접근성 트리에서 입력 이름·결과 heading·후보 두 버튼·코드 확인. 실제 VoiceOver/NVDA는 미검증.

Responsive: 1280/768/375/320px 후보·결과·상담 요약 모두 가로 넘침 없음. 일반 배율 데스크톱 Registry와 320 상담 요약 시각 검수. 긴 실제 센터명/주소는 표본 부재로 미검증. 200% native zoom은 별도 수치·조작 확인.

### 검증 도구 / Regression

Node24 `npm --prefix frontend run lint`, `typecheck`, `build` PASS. dependency 추가 없음. 기존 정적 prerender/noindex/nofollow/경로 유지, sitemap.xml404.

임시 Playwright QA: `/tmp/iyum-registry-qa/registry.cjs` 17개 보고 항목 PASS(센터 제한 별도), `zoom.cjs`, `print-preview.cjs`. `/tmp/iyum-claim-qa/check.cjs`, `claim.cjs`, `regression.cjs` 모두 PASS. 원시 결과 `/tmp/iyum-registry-qa/results.json`, 실제 인쇄 미리보기 `/tmp/iyum-registry-qa/native-print-preview.png`. 임시 산출물은 외부 배포하지 않았다.

회귀: Hero, Guided 이전/재시작/조기종료, Journey8/Q4 5년, Documents Responsibility·청구 질문·다섯 서류그룹, Include/Exclude·재선택·Custom 추가/삭제/빈값/300자, 상담 완료 체크, 기타 질문·빈 목록, Browser Print, Detail/Return, 기존 공식출처7개, FAQ, noindex/sitemap. 새 Registry가 미사용일 때 기존 기본질문6/전체10 유지.

### 변경 파일

- `registry-content.ts`: 실제 공식 제품2개·출처·확인일·정규화/매칭/7일 최신성.
- `OfficialRegistryLookup.tsx`: 검색·선택·상태·센터 미확보 안내·상담/인쇄 공통 요약.
- `GuidedCheck.tsx`: 페이지 로컬 Registry 상태·freshness clock·Journey5 연결·Prepare 전달.
- `consultation-content.ts`: 제품 VERIFIED 시 CTR-02→CTR-09, 기존 보험 분기 유지.
- `ConsultationPreparation.tsx`: Registry 요약·질문 갱신·편집 보존·완료 리셋·print 요약.
- `DetailNavigation.tsx`: registry-check heading focus/history return.
- `components.tsx`: 기존 Return에 Registry 복귀 링크.
- `page.tsx`: 구입 전 Registry 진입 링크.
- `page.module.css`: 페이지 로컬 responsive 검색/결과/후보/print 스타일.
- `docs/validation-notes.md`: 본 기록.

### Deferred / BLOCKER / NEEDS REVIEW / LOCAL FIX

**BLOCKER:** 공식 센터 표본 미확보. 센터 정확명+주소 VERIFIED, 복수 센터 후보, CTR-01 해결 제거, 실제 센터 이름/주소 responsive 검증은 완료하지 못했다. 전체 요청이 모두 PASS라고 보고하지 않는다.

**NEEDS REVIEW:** C01–C04, 표본 확대/공식 업소 공개 조회의 안정적 접근 경로, 실제 사용자 모델명 인지·검색 매칭·출처/날짜 이해·상담 행동 영향, VoiceOver/NVDA·다른 브라우저·실제 모바일·물리 출력.

**LOCAL FIX:** stale Prepare 문서 정렬, 입력 수정 시 old verified 제거, 후보 선택 후 focus, 제품 확인시 질문의 상위 의사결정 질문 대체, 날짜 만료 시 재검토, 인쇄요약. 테스트 시계 설치시점 및 Notion 문자열 정규화 수정.

장기 구조는 Source → Raw Snapshot → Normalization → Diff → Validation → Registry의 후보 기록만. 센터/제품 Daily Sync·Source 변경감지·구매 직전 On-demand Recheck는 향후 Evidence 이후. source/source_version/effective_date/source_checked_at/snapshot_created_at/normalized_at/registry_status/raw_source_reference는 Metadata Candidate일 뿐 DB Schema 설계 아님. Crawler/실제 수집/Backend/DB/API/Cron/Scheduler/Cache/Admin/Notification/AI/Full Search/추천/Eligibility/개인급여조회 없음. Product Decision/EXP Record·Result/HYP Supported/MVP/R1/정식IA/새Category/Backend Architecture 변경/외부배포 없음. Git 저장소가 아니므로 commit/PR 없음.

### Founder Self Review — 아직 사용자 관찰 결과가 아님

센터 질문 1·8·10·14·15는 현재 센터 표본 제한을 전제로 읽고, 제품에서 관찰한 반응을 센터 가치 증거로 대신하지 않는다.

1. 센터 등록 여부를 IYUM에서 바로 확인하는 것이 이해되는가?
2. 공식 사이트를 직접 검색하지 않아도 된다는 점이 편한가?
3. 제품 등록 여부 확인이 실제로 유용한가?
4. VERIFIED를 개인 급여 자격으로 오해하지 않는가?
5. NOT_FOUND를 미등록 확정으로 오해하지 않는가?
6. 마지막 공식 데이터 확인일이 이해되는가?
7. 공식 Source Link가 신뢰에 도움이 되는가?
8. 비슷한 센터 / 모델 선택이 자연스러운가?
9. Registry 확인 후 상담 질문이 더 현실적인가?
10. “등록 업소인가요?” 질문을 더 이상 센터에 물을 필요가 없다고 느껴지는가?
11. “등록 제품인가요?” 질문도 동일한가?
12. 대신 제품 추천 이유·비용·비교 질문이 더 중요하게 느껴지는가?
13. Prototype Snapshot과 실시간 공식 조회의 차이가 명확한가?
14. 이 기능이 없다면 다시 공단 사이트에서 직접 검색할 것 같은가?
15. 이 기능 때문에 IYUM을 센터 상담 전에 사용할 이유가 더 생기는가?


## 20. Registry Prototype Coverage Clarification

2026-09-20. **제품 2개 표본 / 센터 0개 / 전체 Registry·실시간 조회 미구현**. HYP-003 Untested / Not Approved Product Feature / Release Not Assigned 유지. 이번 범위 안내 UX 개선은 완료했으며 센터 실제 조회 구현을 완료했다는 뜻이 아니다. §19의 센터 검색 동작·summary 기록은 이전 버전이다.

### 문제와 Notion 선행 정렬

기존 UI는 표본 수를 안내해도 센터 입력이 활성 상태이고 제품 직접 선택 경로가 없어 사용자가 임의 검색의 NOT_FOUND를 공식 미등록 또는 검색 고장으로 오해할 수 있었다. 최신 PAGE fetch → 현재 코드/Fixture 확인 → 오해 분석 → PAGE §7.6 Coverage Rule/Consult·Print 및 §12 관찰 최소 패치 → 재fetch 검증 → 코드 변경 순서로 진행했다. 사용자 요청대로 센터 크롤링·표본 확대는 하지 않았다.

제품: Implemented Validation Prototype / 공식 Fixture2개. 센터: UI Candidate / SOURCE_UNAVAILABLE / Fixture0개 / 실제 Lookup 미구현. 사용성·가치 검증과 전체 데이터 Coverage·정확도 검증을 분리했다. HYP/Hub/정식 IA 변경 없음.

### Source 재확인

[S08 공식 제품 고시](https://www.nhis.or.kr/lm/lmxsrv/law/lawFullContent.do?SEQ=1619&SEQ_HISTORY=)를 새 브라우저 탭으로 열고 제2026-86호(2026-04-16 시행) 제품군 I 원본 이미지 1·2행과 다시 대조했다.

| 모델 | 코드 | 고시 업체명 | 공식 확인일 |
| --- | --- | --- | --- |
| Starkey Picasso 1000 CIC | D22018010015 | (주)스타키코리아 | 2026-09-20 |
| Starkey Picasso-P 1000 CIC | D22018010016 | (주)스타키코리아 | 2026-09-20 |

Fixture 값·출처·확인일 변경 없음. 새 Source ID·정책 Claim 없음. C01–C04 및 전체 정책 검토일 2026-09-18 유지.

### 구현

- 제품 검색 바로 위: **검증용 데모** / 공식 확인 제품2개만 조회 / 전체 등록제품 조회 미구현. input의 aria-describedby에도 Coverage 설명 연결.
- 입력 아래 ‘테스트할 수 있는 제품’ native button2개: 정확 모델·제품코드·업체명 표시. 클릭/Enter 시 해당 Entity 선택 및 기존 VERIFIED/STALE 계산을 사용하고 결과 heading focus. 정확 모델 입력과 달리 버튼 자체가 명시적 Entity 선택이므로 별도 후보 재선택 없음.
- 직접 검색 유지: 코드/모델 일치 시 후보 확인 후 VERIFIED. 브랜드/부분명은 후보 상태이며 자동 VERIFIED 금지.
- NOT_FOUND: ‘현재 검증용 데이터에서는 찾지 못했어요.’ + ‘공식 확인 제품2개만 포함 / 이 결과만으로 미등록 제품이라고 판단할 수 없음 / 표본 선택 안내’.
- VERIFIED 바로 옆에 선택 표본 등록 사실과 개인 급여 자격의 차이 명시. 7일 freshness·지연 시뮬레이션은 유지, 새 shortcut이 STALE를 우회하지 않는다.
- 센터: ‘센터 등록정보 조회 준비 중’, 공식 표본 부재와 미등록/공식사이트 오류/입력 오류가 아님을 항상 표시. native disabled input에 label과 이유 설명 연결. 확인 button·센터 검색어 state·centerChecked·callback 제거. 센터 조회 요청 동작 없음.
- CTR-01은 항상 기존 질문으로 유지. CTR-02는 정확 최신 제품 VERIFIED일 때만 제외하고 CTR-09 추천 이유 질문으로 대체. NOT_FOUND/AMBIGUOUS/STALE는 CTR-02 유지. 보험 분기 및 사용자 편집 보존.
- 상담/Print: ‘검증용 공식 표본에서 등록 확인됨’, 모델/코드/업체/확인일/출처 + ‘공식 확인 제품2개 / 전체 실시간 조회·개인자격 확인 아님’. 센터는 결과가 없어 summary에서 제외. 조회 실패 표현 없음. 검색 UI·원시 검색어·Guided 답변 출력 없음.

### Browser / Accessibility / Responsive

| 시나리오 | 결과 |
| --- | --- |
| A 제품 진입·데모·표본2개 | PASS |
| B 첫 표본 / C 둘째 표본 | PASS · 키보드 Enter·VERIFIED·결과 focus·개인자격 구분 |
| D 제품코드 입력 | PASS · 정확 후보 명시 선택 후 VERIFIED |
| E 임의 제품 / J 질문 유지 | PASS · 검증용 데이터 NOT_FOUND / CTR-02 복원 |
| F 브랜드 일부 | PASS · 복수 후보, VERIFIED 오판 없음 |
| G 센터 미제공 | PASS · disabled label+이유 / submit button 없음 |
| H 센터 질문 / I 제품 질문 | PASS · CTR-01 유지, VERIFIED CTR-02→CTR-09 |
| K 상담용 | PASS · 표본 범위 명시, 센터 실패 요약 없음 |
| L Print Preview | PASS · print media/PDF + 실제 native Chrome 미리보기 시각 검수 |
| M 320px | PASS · 모델명/코드/데모/NOT_FOUND/센터 disabled 줄바꿈 |
| N 200% | PASS · 독립 Chrome native zoom outer1280/inner640/DPR2/scroll640, 제품·Prepare·상담 조작 |
| O 새로고침 | PASS · query/selected/Prepare 초기화, 센터 미제공은 정적 유지 |

1280/768/375/320px 가로 overflow 없음, 표본 버튼44px 이상. 320 제품 및 1280 센터·데모 화면 시각 검수. Keyboard/visible focus/result heading focus 확인. native disabled라 keyboard Tab 대상에서 제외하며 읽을 label·설명을 보존한다. 색상만으로 미제공을 전달하지 않는다. 실제 VoiceOver/NVDA 낭독·당사자 이해도·다른 브라우저/실물 모바일·물리 출력 미검증. 200% 캡처 축척 한계가 있어 DOM 측정·조작 결과와 일반 배율 시각 검수를 구분한다.

### Privacy / Regression / Verification

Code: 센터 입력/state/callback 삭제, 제품·Prepare는 기존 React local memory. fetch/storage/cookie/beacon/console/analytics 호출 없음. Runtime: 초기 HTML/CSS/JS9건과 구분하여 interaction 요청0, localStorage0/sessionStorage0/cookie0. URL/console에 검색 sentinel 없음. pageerror0. Source 링크는 입력값을 붙이지 않는다.

최종 lint/typecheck/build PASS, 새 dependency 없음. 첫 build에서 idle early return 뒤 중복 상태 비교 TS2367 발견→불필요 조건 제거→정상 build. 실패한 build 뒤 첫 서버/브라우저 시도는 실행되지 않았으며 정상 빌드 후 서버 재시작과 전 시나리오를 완료했다.

기존 `/tmp/iyum-claim-qa/check.cjs`, `claim.cjs`, `regression.cjs` PASS: Guided/Journey/Q4·Prepare/Include/Exclude/Custom·Documents Responsibility·청구 질문·다섯 서류그룹·상담/인쇄·Detail/Return·FAQ·기존 출처7개. noindex/nofollow 및 sitemap404 유지.

Coverage QA `/tmp/iyum-coverage-qa/check.cjs`, `zoom.cjs`, `print-preview.cjs`, `stale.cjs`. 결과 `/tmp/iyum-coverage-qa/results.json`, 실제 인쇄 미리보기 `/tmp/iyum-coverage-qa/native-print-preview.png`. 물리 인쇄/공유 없음.

### 변경 파일

`OfficialRegistryLookup.tsx`: 범위·두 표본 선택·센터 disabled·상담/print copy. `registry-content.ts`: NOT_FOUND/VERIFIED copy 및 불필요 센터 context 제거. `GuidedCheck.tsx`: 센터 state/callback 제거. `ConsultationPreparation.tsx`: 센터 state signature 제거. `page.module.css`: disabled input 스타일. `docs/validation-notes.md`: 이 기록.

### Observation Plan / 남은 검토

§12: Coverage 이해, NOT_FOUND의 데이터 부족 가능성, 센터 미제공≠미등록, 표본 선택의 자연스러움, VERIFIED 경험 가치, 데모 안내의 가치 저평가 가능성, 전체 Registry 미래 사용 의향을 관찰하도록 추가했다. 미래 의향/과업 행동/실제 이용 Evidence를 분리한다. 사용자 반응을 아직 기록하지 않았다.

BLOCKER: 이번 Coverage 명확화 범위에는 없음. 센터 실제 Lookup과 전체 Registry는 미구현 범위로 유지.
NEEDS REVIEW: 실제 사용자 오해 감소와 가치, VoiceOver/NVDA, C01–C04.
LOCAL FIX: 활성 센터 입력 제거, 표본 발견성, NOT_FOUND·상담·인쇄 범위 문구, 타입 조건 정리.

Backend/DB/API/Full Registry/센터 크롤링/Scheduler/Daily Sync/NHIS Live Query/AI/Fuzzy Library/추천/개인급여조회/Product Decision/EXP Record·Result/HYP Supported/MVP/R1/정식 IA/외부배포 없음.

## 21. Focused Validation Flow Refactor — 2026-09-21

### 목적과 문서 선행 정합성

Founder Observation: 기존 긴 한 페이지에서는 Guided 질문과 Journey·Registry·Prepare·정책 상세가 동시에 보여 현재 과업에 집중하기 어려웠다. 질문 이해 문제와 화면 혼잡 문제를 분리하기 위해 노출 시점을 재구성했다. 인지 부담 감소는 아직 사용자 Evidence가 아니다.

HYP-003 → PAGE-P03-001 → Product Validation Hub를 최신 조회하고 UX/Accessibility 및 Frontend 문서의 관련 원칙을 확인했다. PAGE §7/§7.5/§7.6 정합성, §7.7 Focused Flow 설계, §12 Focus/Cognitive Load 관찰 항목을 먼저 수정하고 재조회한 뒤 코드를 변경했다. Page Goal과 Check → Prepare → Consult → Verify는 유지했다. 실제 구현 핵심은 Check + Prepare이며 Consult는 외부 상담, Verify는 후속 검증 대상이다.

### Main Flow / Reference

Summary → Guided(한 질문) → Result(Next Action·Journey) → Registry when relevant → Prepare → Consultation.

- Summary: 제목, 핵심 기준 3개, 개인 자격 판정 아님, “내 상황 확인하기”와 “전체 기준 보기”. 질문·Registry·FAQ·전체 목차를 함께 노출하지 않는다.
- Guided: Q1–Q4 중 한 질문과 설명·선택지·이전/상세 기준. Journey와 다른 기능은 숨긴다.
- Result: 먼저 할 일 → 8단계 현재 위치 → 주요 행동 2–3개. 초기 등록/보험/처방/이력 분기에서는 해당 기준 행동 우선, Prepare 보조. 구매 단계 5에서 Prepare·Registry·관련 기준을 제공한다.
- Registry: 구매 단계 Result의 CTA, 또는 Prepare/Reference에서 자발적으로 진입. 제품 공식 표본 2개, 센터 조회 미제공을 유지한다.
- Prepare: 독립 화면에서 질문 포함/제외, 다른 기본 질문, 직접 추가, 상담용 보기와 인쇄. Consultation에서는 선택 질문·기관별 그룹·완료 표시와 최소 Registry 요약을 제공한다.
- Reference: 대상·양측·금액·전체 절차·구입 전 확인·서류/청구 책임·FAQ·경험 Empty State·공식 출처 및 목차를 보존했다. 등록 안내 mini는 Result에서 Reference로 옮겼다. 기존 131만원·5년·S01–S10·C01–C04와 출처 검토 상태를 변경하지 않았다.

### 구조와 상태 정책

기존 `/validation/hearing-aid-health-insurance` 유지. `FocusedFlow.tsx`의 local state 7개(summary/guided/result/registry/prepare/consultation/reference)로 전환한다. 정적 Summary/Reference JSX는 Server page에서 slot으로 전달한다. 기존 GuidedCheck·OfficialRegistryLookup·ConsultationPreparation 로직을 재사용하며 새 의존성/전역 store/router는 없다.

비활성 구성요소는 상태를 보존하도록 mounted 상태에서 native `hidden` + `display:none` 처리한다. 접근성 트리와 탭 순서에서도 제외한다. `FlowReturnContext`는 page-local 복귀 label/callback만 제공한다.

- 일반 Back/Reference 왕복: 응답·검색·선택·사용자 질문·완료 표시 유지. Reference 내부 상세 이동에도 처음 진입한 과업 문맥 유지.
- Guided 이전 답변 수정: 이후 답변/결과를 무효화하고 기존 추천 질문 재계산 규칙 적용. 사용자가 직접 추가한 질문은 유지한다.
- Restart: Guided/Result/Registry/Prepare/사용자 질문/완료 표시를 모두 초기화하고 Q1으로 이동. 해당 버튼이 있을 때 전체 초기화 범위를 안내한다.
- Refresh: 메모리 초기화 후 Summary. 공용 Reference anchor로 접근하면 해당 상세로 열리고 복귀 기본은 Summary.
- 화면 내 Back 우선. view history를 push하지 않는다. 공용 Reference hash만 replace하며 Main Flow로 돌아오면 제거한다. 응답/view를 URL 또는 history.state에 직렬화하지 않는다.

### Focus / Accessibility / Responsive

명시적 전환 시 새 화면 heading에 focus와 즉시 scroll을 적용한다. 일반 초기 로드는 강제 focus 없이 BODY를 유지한다. 닫힌 상세로 이동하면 ancestor details를 열고 heading에 focus한다. Result의 영역 이름은 실제 결과 heading을 가리킨다. 초기 Q1에 불필요한 재시작 설명은 숨겼다.

1280/768/375/320 CSS px에서 Guided·Result·Registry·Prepare·Consultation 및 Reference 왕복을 확인했다. 수평 overflow 없음. 320px Guided는 한 질문만 노출하고 Prepare도 다른 과업 없이 질문 준비에 집중한다. 기관별 질문/Registry 설명 자체는 기존 내용이므로 수직 스크롤은 남는다.

Chrome native 200%: outerWidth 1280, innerWidth 640, DPR 2, scrollWidth 640. Q1–Q4 설명/선택지, Result/Registry/Prepare/Consultation reflow 및 Guided 버튼 44px 이상 확인. Tab/Enter/Space, visible outline, 비활성 화면 탭 제외, 전환 heading focus 확인. 접근성 snapshot에서 숨긴 Registry/FAQ 등이 제외됨을 확인했다. 실제 VoiceOver/NVDA 및 모든 브라우저/실기기 검증을 완료했다고 주장하지 않는다.

### Browser Scenario A–T

| 시나리오 | 결과 |
| --- | --- |
| A | 초기 Summary만 노출, 강제 focus 없음, Guided 진입 |
| B | Q1–Q4 한 질문, Detail/Registry/Prepare 비노출 |
| C | 중간 분기별 Next Action, 구매 전 단계 Registry 주요 CTA 없음 |
| D | Q4 완료 → 별도 Result/Journey → Prepare |
| E | Registry 공식 표본 확인 → Result/Prepare 복귀 및 선택 유지 |
| F | 질문 선택/직접 추가 → Consultation |
| G | 상담용 완료 표시 → 질문 수정 복귀 및 상태 유지 |
| H | Result → 관련 Reference → Result/heading 복귀 |
| I | Prepare → Reference → Prepare/heading 복귀 |
| J | Summary → 전체 기준 → Summary 또는 Guided |
| K | Q4 수정 → 최신 결과/추천 질문 반영 |
| L | VERIFIED 시 CTR02 대신 CTR09, 센터 확인 질문 유지 |
| M | Reference 왕복 시 질문 포함/제외·사용자 질문 유지 |
| N | Restart 전체 초기화/Q1 |
| O | Refresh 메모리 초기화/Summary, 공용 anchor 직접 진입 |
| P | 320px Guided reflow |
| Q | native 200% 확대 Q1–Q4/reflow |
| R | 320px Prepare reflow |
| S | Reference 진입/복귀 및 Consultation 문맥 복귀 |
| T | 브라우저 실제 Print Preview 및 print CSS/PDF |

### Privacy / Print / Regression

새 화면 구조에서 초기 정적 리소스 요청 9건과 사용자 조작 구간을 분리해 확인했다. 검사한 조작 구간 추가 요청 0, localStorage/sessionStorage/cookie 0, 입력/응답의 URL·console 노출 0, pageerror 0. noindex/nofollow 유지, sitemap 경로 404로 검증 페이지가 포함되지 않음을 확인했다. 새 Backend/API/analytics 없음.

nested shell에 맞춰 print CSS를 수정했다. Summary/Guided/Reference/Navigation을 제외하고 준비한 질문과 최소 Registry 요약만 출력한다. 실제 Chrome 인쇄 미리보기 1쪽에서 질문·표본 제품 정보가 잘리지 않음을 확인했고 선택/직접 추가한 질문의 PDF 출력도 확인했다. 물리 인쇄는 하지 않았다.

기존 최초/이전/이력 모름, 등록 아니오/모름, 보험 모름/의료급여, 처방 없음/모름 분기와 Journey mapping 확인. Registry 빈 입력·NOT_FOUND·AMBIGUOUS·표본 2번 선택·STALE 및 CTR02 복구, VERIFIED의 CTR09 전환 확인. 사용자 질문 빈 입력·300자 제한·추가·삭제, 선택/완료 보존 및 reset 확인. 서류 책임 5개 묶음·관련 details 자동 열림·공식 출처 영역 유지. 정책 내용/fixture/rule 데이터 파일은 이번 변경 대상이 아니다.

### 실행 검증과 근거

최종 `npm run lint`, `npm run typecheck`, `npm run build` 모두 PASS. package.json에 별도 test script는 없다. 임시 브라우저 회귀 스크립트를 Focused Flow에 맞춰 사용했다. 초기에 생성된 `.next/types/routes.d 2.ts` 중복으로 typecheck가 실패했으나 build의 타입 재생성 후 통과했다. 소스 계약/의존성 변경은 필요하지 않았다.

로컬 검증 근거: `/tmp/iyum-flow-qa/check.cjs`, `regression.cjs`, `keyboard.cjs`, `zoom.cjs`, `print-preview.cjs`, `results.json`, viewport별 screenshot, `print.pdf`, `native-print-preview.png`. 핵심 흐름은 마지막 접근성/설명 수정 후 재실행해 PASS했다. 이 경로들은 임시 QA 산출물이며 사용자 Evidence가 아니다.

### 변경 파일과 남은 검토

- `FocusedFlow.tsx`, `flow-context.tsx`: local view·복귀 문맥·초기화·focus.
- `page.tsx`: Summary와 Reference server slot 분리, 기존 내용 보존.
- `GuidedCheck.tsx`: 기존 로직 재사용, view별 표시/Result CTA/Registry·Prepare 독립화.
- `DetailNavigation.tsx`, `components.tsx`: 공용 상세 anchor와 문맥별 복귀.
- `ConsultationPreparation.tsx`: 상담/수정 모드와 shell view 연결.
- `page.module.css`: 비활성 화면 숨김·주요 CTA·nested print.
- `README.md`, 이 문서: 사용 방법과 검증 기록.

BLOCKER: 구현/로컬 검증 범위에서 발견된 미해결 blocker 없음.
NEEDS REVIEW: 실제 인지 부담 감소, 전환 횟수의 번거로움, 긴 Prepare/Result의 체감 길이, 실제 화면낭독기, C01–C04. Focus/Cognitive Load 10개 항목을 PAGE §12에 추가했고 아직 사용자 반응이나 EXP 결과로 기록하지 않았다.
LOCAL FIX: 한 페이지 동시 노출, 숨긴 DOM의 접근성 노출 방지, 문맥 복귀, 결과 영역 label, 첫 질문의 불필요한 reset 설명, nested print CSS.

상태는 Implemented Validation Prototype / Untested / Not Approved Product Feature / Release Not Assigned. HYP-003 Evidence 변경 없음. 정식 IA/User Flow/Screen·Product Decision·Requirement/Feature·MVP/R1·Backend/DB/API·AI·Figma·새 Route·정식 Frontend Architecture 계약·외부 배포는 수행하지 않았다. 추가 기능보다 Founder 과업 확인 후 실제 Pilot을 우선한다.

마지막 Notion 재조회: §7.7 Implementation Note(2026-09-21), §8.1의 최신 Summary CTA, §12 Focus/Cognitive Load, Untested/Not Approved/Release Not Assigned 경계를 확인했다. PAGE만 최소 갱신했고 HYP/Hub/Product Contract는 수정하지 않았다.

## 2026-09-23 · 인라인 안내와 현재 단계 액션

사용자 승인에 따라 GuidedCheck, FocusedFlow, page.module.css를 수정했다. 질문/선택값을 보존하고 안내를 아래에 펼치며 결과 제목으로 focus + smooth scroll한다. prefers-reduced-motion에서는 instant를 사용한다. 공통 설명은 상단 한 번, 다른 단계는 상태 문구 없이 제목만 표시한다. 현재 단계 카드에 주 안내와 보조 상담 버튼을 배치한다. Step 5는 등록제품 확인을 주 행동으로 둔다. 기존 질문 분기·급여 조건·상담/등록 자료 내용은 변경하지 않았다.

검증: lint/typecheck/build 통과. 브라우저에서 아니오→인라인 결과, 잘 모르겠어요로 답변 변경, 상세 보기→결과 복귀, 예로 수정→이전 안내 제거, 4개 질문 완료→인라인 Step 5를 확인했다. PC 현재 단계 오른쪽 버튼 배치를 스크린샷으로 확인했고 390px viewport에서 같은 카드 내 세로 배치 및 가로 넘침 없음을 확인했다. 실제 화면낭독기·모든 상담 편집/인쇄 경로·reduced-motion 설정의 실기 테스트는 이번 검사 범위 밖이다.

## 2026-09-23 · 초보자용 공식 자료 요약 토글

사용자 요청에 따라 JourneyGuide의 8개 단계별 외부 링크를 SourceExplainer로 교체했다. source-explainers.ts에서 단계별 쉬운 용어 풀이·핵심 기준·준비 서류·출처를 관리한다. 등록 안내의 RegistrationHelp도 같은 요약을 사용한다. 원문 링크는 기관명과 함께 토글 안에 표시하고 새 탭임을 알린다. 설문 답변은 기존대로 메모리에만 남는다.

청구 문의처는 ‘위임 청구 시 → 판매업소’, ‘직접 청구 시 → 국민건강보험공단’으로 구분했다. 검수 설명에는 실제 착용 효과 확인과 검수확인서의 목적을 넣었다. SVG 아이콘과 텍스트로 절차를 시각화하며 모바일에서는 세로로 배치한다. 제품/업소 확인과 서류 책임은 순서를 암시하는 화살표 없이 병렬로 표시한다.

보건복지부 등록 안내, 공단 보청기 절차·서식 목록, 제품가격 고시, 시행규칙 제26조를 다시 열어 해당 설명의 근거를 확인했다. 정책 전체를 새로 검토한 것으로 기록하지 않는다. 기존 C01–C04(양측 연령·후기 관리 첫 청구일·구입 가능 기간·최신 서류 조합)는 해결한 것으로 간주하지 않고 해당 요약에 확인 필요 사항을 유지했다. 전체 reviewedAt은 변경하지 않았다.

검증: lint/typecheck/build 통과. 실제 브라우저에서 8개 단계의 토글 열기·출처·그림, Enter 열기/Space 닫기, 단계 전환 후 기본 접힘, 청구 방식별 문의처를 확인했다. 1280px 화면 배치 및 390px 화면의 세로 그림/가로 넘침 없음 확인. 실제 화면낭독기와 최신 개인별 급여 자격 검증은 범위 밖이다.

## 2026-09-23 · 제품 데이터만 연결한 인라인 검색

사용자 요청에 따라 공단 연계 요청과 업소 검색은 현행을 유지하고 제품 목록만 연결했다. 보건복지부고시 제2026-86호(2026-04-16 시행)의 206개 제품을 2026-09-23 확인 기준 JSON으로 포함했다. 공개 원문 표 11장의 칸별 OCR 결과를 시각 대조하고 오인식을 교정했다. 제품군별 수 23/25/94/64와 코드 중복 없음을 확인했다. 원문 이미지 식별자·행 위치·이미지 해시를 보존했고 갱신 방법은 data/README.md에 기록했다.

ProductLookup을 5단계 안내 안에 넣어 모델명·제품코드·업체명을 검색하고 정확한 모델을 선택하게 했다. 부분 검색은 6개씩 더 볼 수 있고, 모델명이 비슷해도 자동 확정하지 않는다. 선택 결과에 업체·코드·고시가격·원문·시행일·목록 확인일을 표시한다. 결과 없음은 미등록 판정으로 표현하지 않는다. 고시가격과 실제 판매가·개인 지원금의 차이를 설명한다. 모델명을 찾는 방법과 판매업소에 물어볼 문구도 접어서 제공한다.

선택 결과는 기존 상담 준비에 전달하며 업소 등록 확인 질문은 유지한다. 별도 조회 도구도 같은 컴포넌트를 재사용한다. 공식 공단 API, 백엔드, 저장, 자동 갱신 또는 외부 배포는 추가하지 않았다. 기존 최신성 7일 분기·검증용 지연 시뮬레이션은 운영 SLA가 아닌 프로토타입 분기로 유지했다.

검증: lint/typecheck/build 통과. `npm run test:products`로 전체 수량·제품군·중복·코드 형식·유사 모델 구분·전각/하이픈 입력·부분 검색·한글 별칭·미검색·고시가격·최신성 경계를 확인했다. 실제 브라우저에서 빈 입력, 13개 Picasso 결과의 더 보기, 정확한 제품코드 검색, 포낙 검색, 결과 없음, 모델 선택, 별도 조회 도구/상담 준비/결과 복귀 시 선택 유지, 업소 조회 비활성 및 업소 질문 유지, 처음부터 다시 시작 후 제품 초기화를 확인했다. 1280px 배치와 390px/320px 화면의 줄바꿈·가로 넘침 없음을 확인했다. 실제 화면낭독기와 사용자 테스트 결과는 아직 없다. 이 기록은 구현 검증이며 HYP Evidence나 정식 기능 승인이 아니다.

## 2026-09-23 · 131만 원 구성과 청구 시점 표

사용자 요청에 따라 지원 비용을 다시 조사한 뒤 BenefitBreakdown을 추가했다. 6단계 ‘보청기 구입·착용’을 열면 ‘처음 청구 / 이후 청구 / 전체 합계’ 표가 바로 보인다. 제품 91만 원과 초기 관리 20만 원은 처음 청구하는 기준액 111만 원으로 묶고, 후기 관리는 연 5만 원씩 최대 4회(20만 원)로 구분했다. 일반 건강보험 90% 적용 시의 최대액은 처음 999,000원, 후기 연 45,000원씩 최대 4회, 전체 1,179,000원으로 함께 표시했다. ‘전체 기준 → 지원금액’도 동일한 컴포넌트를 사용하며 이전 중복 표 데이터와 구매 토글의 반복 문구를 제거했다.

원문 재확인 근거:

- [현행 고시 제2026-56호, 제5조의2 및 별표 4·5](https://www.nhis.or.kr/lm/lmxsrv/law/lawFullView.do?SEQ=87&SEQ_HISTORY=613408). 공단 첨부 목록에서 별표 4(FILE_SEQ=2319827)와 별표 5(FILE_SEQ=2319828)를 내려받아 HWPX XML 본문을 확인했다. 별표 5의 보청기 1,310,000원·관리비 400,000원 포함·내구연한 5년, 별표 4의 초기 200,000원 1회·후기 50,000원 최대 4회 및 지급 조건을 대조했다. 제품 910,000원과 첫 청구 합계 1,110,000원은 이 구성에서 산출했다.
- [법제처 생활법령정보의 90% 및 100% 예외 설명](https://easylaw.go.kr/CSP/CnpClsMain.laf?ccfNo=3&cciNo=4&cnpClsNo=1&csmSeq=1063&popMenu=ov). 최대 지급액은 해당 비율을 기준액에 적용한 산술값이며 실제 개인별 지급액을 계산한 결과가 아니다.
- 별표 4 파일 SHA-256: `188009a351ca6eaf241995a1761ec56c2c009f215bb480be086cb65230e87c87`. 별표 5: `29de0e7ba5161687785fcb02403324536de803d9b62dcb8fa482588b8f8c2fd0`.

처음 청구는 구입 후 1개월 경과 뒤 검수확인 조건을 함께 표시했다. 후기 관리의 서비스 기간은 구입 1년 후부터 내구연한까지이며, 실제 관리 후 연 1회 별도 청구라는 현행 고시 기준을 사용한다. 자동 입금이나 개인별 첫 청구일을 단정하지 않는다. 100% 적용 예외, 실제 가격에 따른 감액, 고시가격에 초기 관리비가 이미 포함된다는 점, 위임 지급 계좌 등은 보조 토글에 담았다. 후기 서비스 기간을 청구권 행사 만료일로 표현하지 않는다. 기존 C01–C04 전체나 개별 신청의 행정 처리를 해결한 것으로 보지 않으며 전체 reviewedAt은 변경하지 않았다. 비용 표에만 2026-09-23 확인일을 표시했다.

검증: lint/typecheck/build 통과. 브라우저에서 설문 응답 후 6단계를 열면 표가 바로 표시되는 것, 실제 지급액 설명의 펼침/접힘, 전체 기준의 동일 표를 확인했다. 데스크톱과 390px/320px 화면에서 가로 넘침 없이 읽을 수 있으며 금액은 가능한 한 단위와 함께 줄바꿈한다. 실제 화면낭독기·사용자 이해도 평가는 수행하지 않았다.

## 2026-09-23 · 센터 방문 전 준비 중심 재구성

사용자가 승인한 초보자 관점의 검토 방향을 구현했다. 목적은 서류를 직접 준비하게 하는 것이 아니라 전체 과정에서 먼저 확인할 위치를 파악하고, 센터 상담에 가져갈 질문을 준비하는 것이다. 첫 질문은 검사·신청 전, 병원 검사·진단 중, 등록 심사 대기, 등록 완료, 진행 상태 모름으로 나눴다. 답변 값은 선택지 배열 번호 대신 명시적인 문자열로 관리한다. 필요한 정보가 모이면 설문 완료를 표시하고 질문 아래로 부드럽게 이동한다.

결과는 내 상황 → 5개 구간의 전체 과정과 현재 위치 → 지금 할 일 → 센터 질문 3개 순서다. 과거 단계가 완료됐다고 표시하거나 급여 자격을 판정하지 않는다. 등록 전·보험 자격 모름·의료급여 대상도 상담 질문을 받는다. 보험 자격을 확인하지 않은 상태에서 건강보험 전용 질문을 기본 추천하지 않는다. 센터에 모든 행정 업무를 맡길 수 있다고 가정하지 않고 센터 지원 범위와 직접 방문할 기관을 구분하는 질문을 제공한다.

단계별 확인 방법, 비용 표, 추천받은 모델 검색은 질문 아래 접힌 도구로 제공한다. 이전 급여 이력 상세는 해당 응답에서만 표시한다. 기존 공식 자료 요약·출처·131만 원 구성 표·206개 제품 검색과 업소 조회 준비 중 상태는 보존했다. 별도의 참고 화면은 작성 중인 질문으로 돌아갈 수 있는 버튼을 상단 한 번만 표시한다. 요약에서 참고 화면으로 들어온 경우에는 기존 왼쪽 30초 요약 링크를 사용한다.

질문 선택 화면은 추천 질문부터 표시하며 다른 질문·기존 등록 확인 상태는 보조 영역으로 옮겼다. 결과 및 편집/상담 화면에서 질문 복사와 UTF-8 텍스트 파일 저장을 제공한다. 복사 실패 시 수동 복사 영역을 표시하도록 구현했다. 질문 제외·추가·상담 완료 표시·인쇄를 유지하며 답변 변경 시 문맥에 맞는 추천을 갱신한다. 서버 전송, 영속 저장, 외부 연락, 공단 API, 새 경로, 배포는 추가하지 않았다.

검증:

- `lint`, `typecheck`, `build`, `test:products`, 새 `test:visit-flow` 통과. 11개 도달 가능한 결과, 미완료 답변, 각 결과의 센터 질문 3개, 보험 자격 불명확 시 질문 범위, 재지원 이력, 제품 확인 상태 변경을 검사했다.
- 실제 브라우저에서 검사 중·심사 대기·신청 전, 등록 완료→보험 모름/의료급여/건강보험, 처방 전→처방 후, 이전 지원→처음 경로를 확인했다. 답변 선택 시 결과 제목으로 초점이 이동하며 이전 분기 안내는 교체된다.
- 질문 제외·직접 추가 후 상담용 목록 반영, 상세 기준 왕복 후 유지, 다시 시작 후 직접 추가 질문 제거와 기본 질문 3개 복구를 확인했다. 상세 기준 상단 복귀 버튼을 추가해 질문 편집 문맥으로 돌아오게 했다.
- 질문 복사의 성공 상태를 확인했다. IAB의 가상 클립보드는 웹 Clipboard API와 분리되어 있어 가상 클립보드로 붙여 넣기 검증은 할 수 없었다. 파일 저장은 실제 다운로드된 `IYUM-센터-상담질문.txt`의 상황 설명과 질문 3개를 직접 읽어 확인했다. 다운로드 이벤트 대기는 IAB에서 반환되지 않았으므로 파일 내용으로 확인했다.
- 제품코드 검색·정확한 모델 선택 후 추천 질문 변경, 비용 표 펼침, 청구 방식별 문의처와 기존 설명을 확인했다. 데스크톱 및 390px/320px 결과·비용·상담 화면을 확인했고 좁은 화면에서 가로 넘침이 없었다. 마지막 브라우저 콘솔 오류·경고 0건.

실제 화면낭독기, 복사 실패 환경, 이번 개정의 실제 인쇄 미리보기 및 사용자 이해도 평가는 수행하지 않았다. 동작 줄이기 설정 대응과 기존 인쇄 전용 구조는 유지했다. 정책 전체의 재검토나 사용자 Evidence가 아니며 기존 C01–C04와 전체 정보 확인일은 그대로 둔다.

## 2026-09-24 · 질문 위 고정 과정 그림과 버튼 간소화

사용자 승인에 따라 전체 과정을 먼저 보면서 설문에 응답하도록 재구성했다. VisitOverview의 결과 아래 세로 목록을 제거하고, 등록 확인 → 병원 처방 → 구입·착용 → 검수·청구 → 이후 관리의 SVG 그림을 첫 질문 위에 한 번만 표시한다. 그림은 클릭을 요구하지 않는 읽기용 목록이다. 질문 중에는 ‘지금 확인 중인 단계’, 결과에서는 ‘지금 먼저 할 단계’로 표시하며 이미 절차를 완료했다고 표시하지 않는다. 등록/보험 질문은 1구간, 처방 질문은 2구간, 이전 지원·구입 준비 질문은 3구간을 강조한다.

데스크톱과 모바일 모두 다섯 단계가 한눈에 보인다. 질문·결과·상세 설명을 읽는 동안 상단에 고정되며 실제 그림 높이로 scroll-margin을 계산한다. 첫 진입, 응답 전환, 참고 화면 복귀, 글자 줄바꿈에 따른 높이 변경을 처리한다. 매우 낮은 창(450px 이하)에서는 질문을 읽을 공간을 확보하기 위해 고정을 해제한다. 질문 제목에 초점을 두되 이전 질문 동작까지 그림 아래에 보이도록 질문 묶음을 기준으로 스크롤한다. 동작 줄이기 설정은 기존대로 즉시 이동한다.

버튼 간소화:

- 질문 선택·추가하기와 독립 편집/상담 화면 및 연결 경로를 제거했다. 기존 consultation-preparation anchor는 결과 또는 첫 질문으로 연결한다.
- 추천 질문 3개는 결과에서 바로 읽으며 ‘질문 복사하기’만 제공한다. 파일 저장·인쇄·별도 상담 모드·직접 추가 동작은 제거했다. 자동 복사 실패 시 텍스트를 직접 선택할 수 있다.
- 단계별 ‘확인 방법 보기/안내 접기’ 반복 버튼을 제거했다. 단계 제목 자체가 기본 details 펼침/접힘 동작이며 한 번에 한 설명을 연다. 기존 공식 요약·이렇게 물어보세요·원문 출처를 유지했다.
- 이전 질문은 질문 바로 위, 처음부터 확인은 하단의 작은 텍스트 동작으로 배치했다. 중복 답변 수정 버튼과 하단 요약 복귀 버튼을 제거했다. 요약 복귀는 상단 링크 한 곳이다.
- 요약의 전체 기준 링크와 참고 화면의 복귀 동작을 큰 테두리 버튼 대신 보조 링크 모양으로 정리했다. 참고 화면에서 복귀와 설문 진입 링크가 중복 노출되지 않도록 했다.
- 조회 화면에서 별도 상담 편집으로 이어지는 버튼을 제거했다. 제품 검색, 공식 업소 조회 준비 중 상태, 지원 비용 표와 출처는 유지했다.

검증: lint/typecheck/build 및 test:visit-flow/test:products 통과. 기존 11개 결과 경로와 각 질문·결과의 단계 매핑을 검사했다. 실제 브라우저에서 최초 진입, 등록→보험→처방→이전 지원, 검사 중·보험 모름·처방 전 조기 결과, 이전 답변 수정, 처음부터 확인을 점검했다. 그림은 한 개만 존재하며 앞 단계 완료 표시가 없고, 결과의 질문 영역에는 복사 버튼만 존재한다. 복사 성공 상태를 확인했다. 실제 Clipboard API 내용의 외부 붙여넣기 및 복사 실패 환경은 이번 검증 범위 밖이다.

단계 제목의 Enter 열기 및 다른 단계 선택 시 기존 단계 닫힘, 비용 표·청구 방식별 문의처, 제품코드 검색·모델 선택 후 질문 반영, 전체 기준 왕복 후 응답·제품 선택 유지와 결과 제목 노출을 확인했다. 390px/320px 화면에서 가로 넘침 없이 5단계와 질문·이전 동작이 표시되며, 고정 그림 하단보다 질문/결과 제목이 아래에 있는지 좌표로 확인했다. 640×360에서는 고정 해제와 가로 넘침 없음을 확인했다. 브라우저 콘솔 오류·경고 0건.

정책 내용·공식 데이터·정보 확인일은 이번 UX 변경으로 갱신하지 않았다. 새 저장·전송·외부 연계·배포 없음. 실제 사용자 이해도나 화면낭독기 검증은 별도이며 이 기록은 로컬 구현 검증이다.

## 2026-09-24 · 반복 문구 제거와 구입 후 절차 연결

사용자 요청으로 설문 결과의 ‘답변을 바탕으로 먼저 확인할 위치…’와 ‘상단 과정은 건강보험 기준…’ 문구를 제거했다. ‘단계별 확인 방법 더 보기’와 해당 JourneyGuide 컴포넌트도 제거했다. 전체 기준과 공식 출처는 기존 참고 화면에 남긴다. 적용 제도가 다른 의료급여 응답은 기존의 주민센터 문의 행동으로 계속 구분한다.

비용 구성 표는 전체 결과 아래의 공통 도구에서 빼고 ‘이전에 보청기 지원금을 받은 적이 있나요?’ 질문 바로 아래의 닫힌 토글로 옮겼다. 질문 메타데이터 benefitHelp로 제어하며 등록·보험·처방 질문 및 해당 조기 결과에는 표시하지 않는다. 전체 기준 화면의 지원금액 표는 유지한다.

마지막 질문에 답하면 구입 준비 결과의 ‘지금 할 일’ 아래에 AfterPurchaseGuide가 바로 표시된다. 상단 5단계와 맞춰 4번 검수·청구, 5번 이후 조절·관리를 이어서 설명한다. 별도 버튼·추가 설문·화면 전환은 없다. 검수의 목적과 시기, 판매업소에 위임하는 청구와 공단에 직접 하는 청구, 초기·후기 관리와 별도 청구를 짧게 요약한다. 과거 지원·이력 모름 응답에서는 이력 및 이번 지원 가능성 확인이 먼저라는 문맥을 붙인다. 현재 단계 강조는 구입 준비에 유지하며 미래 절차를 완료한 것으로 표시하지 않는다.

기존 정책 문구를 재구성하면서 국민건강보험공단의 보청기 안내, 고시 제5조의2와 기존에 내려받아 확인한 별표 4·5, 시행규칙 제26조를 대조했다. 공식 고시와 직접·위임 청구 근거를 새 안내 하단에 링크했다. 정적 안내의 후기 청구 시점 표기를 새로 채택하지 않고 기존 검토한 고시의 서비스 기간과 실제 관리 후 별도 청구라는 설명을 사용했다. 전체 정보 확인일·C01–C04를 새로 해결한 것으로 변경하지 않았다.

근거:
- https://www.nhis.or.kr/lm/lmxsrv/law/lawFullContent.do?SEQ=87&SEQ_HISTORY=613408 — 제5조의2의 초기 1년/이후 내구연한까지의 관리 및 별도 청구. 별표 4·5 확인 기록은 앞선 비용 표 개정 기록 참조.
- https://www.nhis.or.kr/static/html/wbma/c/wbmac0206_2.html — 보청기 검수 목적·청구 자료 대조. 후속 청구의 구체 날짜 문구는 현행 고시 및 기존 검토 결과를 우선.
- content.ts의 claimRuleUrl — 시행규칙 제26조 제2항·제5항의 직접/위임 청구와 검수확인서.

검증: lint/typecheck/test:visit-flow/build 통과. 11개 결과 경로에 대해 비용/후속 안내가 건강보험의 마지막 질문에서만 제공되는 조건을 추가 검증했다. 실제 브라우저에서 검사 중·의료급여·처방 전에는 비용과 후속 절차가 없고, 마지막 질문에서만 비용 토글이 나타나는 것을 확인했다. 첫 지원·이전 지원 결과의 후속 절차와 이력 확인 문맥, 추가 버튼 0개, 이전 질문으로 돌아갈 때 비용/후속 안내가 제거되는 동작을 확인했다. 데스크톱과 390px/320px에서 고정 과정 그림·새 안내·비용 표의 가로 넘침 없음 확인. 실제 화면낭독기·사용자 이해도 평가는 별도다. 새 저장·전송·외부 배포 없음.

## 2026-09-24 · 현재/미래 절차 구분과 읽는 위치 강조

사용자 승인에 따라 현재 결과의 ‘지금 할 일’과 미래 절차 사이를 큰 여백·구분선·옅은 배경으로 나눴다. 미래 영역에는 ‘앞으로의 과정’과 ‘구입 후 진행할 과정과 지원 비용’ 제목을 표시한다. 이전 지원 이력 확인이 필요한 응답의 문맥은 유지했다.

‘지원 비용은 어떻게 나뉘나요?’ 토글을 제거했다. 4번 검수·지원금 청구 안에는 처음 청구하는 111만 원의 구성과 일반 건강보험 최대액을, 5번 이후 관리 안에는 연 5만 원씩 최대 4회의 관리 비용과 일반 건강보험 최대액을 바로 표시한다. 합계·실제 지급액 조건·공식 출처는 미래 영역 하단에 한 번 제공한다. 별도 버튼이나 페이지 이동은 추가하지 않았다. 전체 기준의 표는 보존하고 같은 BenefitBreakdown의 금액·조건·출처 컴포넌트를 공유해 두 안내의 불일치를 방지한다. 기존에 확인한 정책·금액을 재배치한 변경으로, 전체 정보 확인일이나 C01–C04 상태를 갱신하지 않는다.

고정 그림 아래의 읽는 위치가 미래 안내에 들어오면 검수·청구, 이후 관리 제목을 기준으로 4·5단계를 강조한다. 긴 설명에서 제목이 위로 사라져도 해당 단계 강조를 유지하고 역방향 스크롤에도 대응한다. 이때 문구는 ‘지금 보고 있는 안내’, aria-current는 location으로 표시해 실제 진행 단계와 구별한다. 미래 영역 밖에서는 설문 답변에 따른 기존 1–3단계 표시로 돌아간다. 참고 화면 전환·답변 수정·다시 시작 시 읽기 문맥을 초기화한다. scroll 이벤트는 requestAnimationFrame으로 묶고 화면 크기와 안내 높이 변경도 반영한다.

검증:

- lint/typecheck/build 통과. test:visit-flow의 11개 결과·질문/결과 단계 매핑과 새 순방향/역방향 읽기·구간 시작/끝·긴 구간·빈 구간 경계 검사 통과.
- 실제 브라우저에서 등록→보험→처방→이전 지원 질문의 기존 강조, ‘처음이에요’ 결과의 현재/미래 구분, 비용 토글 제거와 단계별 비용·합계·출처를 확인했다.
- 실제 스크롤로 검수·청구→이후 관리→검수·청구 전환 및 설문/추천 질문 영역에서 구입·착용 강조 복원을 확인했다. 화면에서 동시에 강조되는 단계는 한 개다.
- 전체 기준 왕복 후 선택값 유지·결과 제목이 고정 그림 아래에 나타남, 이전 질문에서 병원 처방 강조 복원, 처방 전 조기 결과에 미래 안내 없음, 처음부터 확인 시 등록 질문으로 초기화를 확인했다.
- 데스크톱 및 390px/320px에서 구분선·비용 카드·고정 그림을 시각 확인했고 가로 넘침이 없었다. 좁은 화면의 금액과 원 단위 줄바꿈도 정리했다. 브라우저 콘솔 오류·경고 0건.

실제 화면낭독기·사용자 이해도 평가는 수행하지 않았다. 새 저장·전송·공단 연계·외부 배포는 없다. 구현 검증 기록이며 사용자 Evidence 또는 정식 기능 승인으로 기록하지 않는다.

## 2026-09-24 · 전체 기준 보기 재조사와 간소화

사용자 요청에 따라 장애 등록과 보청기 지원을 공식 자료로 재조사하고 참고 화면을 전면 정리했다. [재조사·편집 기록](reference-review-2026-09-24.md)에 근거, 원문 해시, 기존 C01–C04의 확인 범위와 미확인 범위, 예상되는 사용자 혼란과 수정 이유를 기록했다.

ReferenceGuide는 등록 → 대상 → 비용 → 설문과 동일한 5단계 → 센터 질문 3개 → 출처 순서의 6개 항목이다. 중복 4/8단계 설명, FAQ, 상세 검사 수치·서식 번호 목록, 빈 이용 경험을 제거했다. RegistrationHelp는 추가 조작 없이 신청 흐름을 읽는 카드로 바꾸고 ‘이렇게 물어보세요’를 유지했다. 비용 표는 바로 표시하며 직접 청구 서류는 발급처 중심의 보조 설명으로 간추렸다. 사용하지 않는 SourceExplainer와 과거 설명 데이터는 제거했다.

현행 고시 별표 2·4·5와 시행규칙 제26조를 재확인했다. 19세 미만의 양쪽 지원 추가 조건과 구입 1년 경과 후 실제 관리에 따른 후기 청구를 설명하고, 구형 정적 페이지와의 차이를 사용자가 직접 판단하게 하는 문구는 제거했다. 기존 131만 원 구성은 일치해 유지했다. 실제로 조사한 등록·핵심 급여 기준 확인일만 2026-09-24로 갱신하고 제품 목록의 2026-09-23 확인일은 보존했다.

기존 공용 anchor는 통합 항목으로 연결한다. 접힌 항목으로 바로 들어가면 내용을 열고 summary로 초점을 옮긴다. 참고에서 제품 조회 후 참고의 상담 준비로 복귀하며, 그 전에 설문 결과를 보고 있었다면 다시 해당 결과로 돌아갈 수 있다. 외부 출처는 새 탭으로 바꿔 읽던 문맥을 보존한다.

검증: lint/typecheck/build, 11개 설문 경로와 읽기 위치 검사, 제품 목록 검사 통과. 브라우저에서 목차·상세 토글·예전 링크 5종·제품 검색·설문/참고/제품 왕복·응답 보존을 확인했다. 1280px/390px/320px 배치와 가로 넘침 없음, 한글 줄바꿈 및 44px 링크 영역을 확인했다. 콘솔 오류·경고 0건. 실제 사용자 실험·화면낭독기 검증은 별도이며, 서버 저장·외부 연락·공단 연계·배포·Notion 수정은 수행하지 않았다.

## 2026-09-24 · 건강보험 지원 비율 표현 명확화

사용자 승인에 따라 ‘일반 건강보험’을 ‘건강보험 기본 지원(90%)’으로 바꾸고, 바로 아래에 ‘본인부담 경감 대상(100%)’의 최대 지원액을 표시했다. 초기 청구 99만 9천 원/111만 원, 후기 연 4만 5천 원/5만 원(최대 4회), 전체 합계 117만 9천 원/131만 원을 같은 방식으로 비교한다. 같은 건강보험 안의 적용 비율 차이라는 설명과 개인별 경감 적용 확인 안내를 추가하고 설명 속 중복 금액 나열은 줄였다. 금액 계산·정책 기준은 변경하지 않았다.

공유 BenefitBreakdown 구성요소를 사용하므로 설문 결과의 후속 절차와 전체 기준의 표에 함께 적용된다. lint/typecheck/build 통과. 브라우저에서 세 구간의 비율·금액, 기존 표현 제거, 390px 화면의 줄바꿈과 가로 넘침 없음을 확인했다.

## 2026-09-24 · 등록 전 결과 구조·미확인 분기 개선

배포 화면과 로컬 코드를 비교한 뒤 결과를 현재 상태 → 기관별 전체 절차와 내 위치 → 다음 행동·문의 문장 → 센터 질문 3개와 확인 목적 → 선택형 상세로 재구성했다. 검사·진단 중과 심사 대기 결과에 등록 내부 순서를 표시한다. 스크롤에 따라 진행 단계가 바뀌던 읽기 감지는 제거했고, 보험 자격·이전 지원 이력 미확인은 특정 단계의 완료나 구입 가능으로 표시하지 않는다.

보험 자격 모름도 처방과 해당하는 이전 지원 질문을 이어간다. 15개 결과 경로를 제공하며, 미확인 자격에 건강보험 금액을 확정해 노출하지 않는다. 처방 용어를 통일하고 공단 공식 온라인 개인 상담 메뉴·로그인 경로를 확인해 연결했다. 현재 상태를 포함한 질문 복사, 실패 시 수동 복사, 결과/참고 왕복과 제품 선택 유지도 확인했다. 상세 구현·공식 출처·검증 범위와 제한은 [이번 개정 기록](p03-ux-revision-2026-09-24.md)을 참조한다. 커밋·푸시·배포·Notion 변경은 수행하지 않았다.

최종 lint/typecheck/test:visit-flow/test:products/build 모두 통과했다. 실제 브라우저로 15개 결과, 이전 답변 변경, 참고 왕복, 제품 선택, 복사 성공·실패, 키보드 조작을 확인했다. 데스크톱·390px·320px에서 가로 넘침과 고정 그림의 제목 가림이 없었고, 낮은 화면의 고정 해제도 확인했다. 실제 사용자 이해도·화면낭독기와 공단 로그인 이후 상담 접수는 미검증이다.

## 2026-09-25 · 지원 이력 표현·문장 조합·결과 내부 이동 보완

전체 구조와 질문 분기는 유지하고 다음 세 부분만 수정했다.

- `guided-content.ts`, `consultation-content.ts`: 과거 지원 있음과 기억나지 않음을 구분한다. 미확인 상태는 지원 기록의 존재부터 확인하고, 기록이 있는 경우에만 날짜·신청 조건을 묻는다. 과거 지원 있음에서도 현재 기기 보유를 단정하지 않고 ‘사용 중인 보청기가 있다면’으로 조절·수리 상담을 조건부로 표현한다. 화면과 복사문은 기존의 동일한 안내·질문 데이터를 사용한다.
- `consultation-content.ts`: 보험 자격, 선택적인 처방 설명, 상담 질문을 배열로 조합해 빈 항목을 제외하고 공백 하나로 연결한다. 처방 여부 3종과 처방 질문을 하지 않은 의료급여에서도 문장 구분이 유지된다.
- `GuidedCheck.tsx`, `page.module.css`: 현재 상태 제목 바로 아래에 기관·행동 문장과 ‘지금 할 일 보기’ 링크를 추가했다. 상세 영역의 `result.action.institution`과 `instruction`을 그대로 사용하고 문의 문장·센터 설명은 위에 중복하지 않는다. 기존 `focusContent`로 목적지 제목에 초점을 옮기고 실제 고정 그림 높이를 반영해 스크롤한다. URL에 응답 상태를 넣거나 새로운 결과 화면을 만들지 않는다.

검증:

- `npm run lint`, `npm run typecheck`, `npm run test:visit-flow`, `npm run test:products`, `npm run build` 모두 통과했다. 기존 15개 결과와 제품 검사를 유지하며, 지원 이력 있음/모름 구분·기기 보유 조건·문장 구분·답변 수정 후 복사문 교체를 회귀 검사에 추가했다.
- 실제 브라우저에서 건강보험+처방 완료의 이력 처음/있음/모름을 연속 변경했다. 화면 상태·문의 문장·첫 질문이 실제 Clipboard API에 전달되는 복사문과 일치했고 복사 성공 상태가 표시됐다.
- 이전 질문으로 돌아가 보험 자격을 미확인으로 바꾼 뒤 처방 없음/모름/있음(이력 처음)을 각각 확인했다. 세 조합 모두 공백이 유지되고 화면과 복사 내용이 일치했다. 보험 자격은 미확인으로 남았다.
- 데스크톱 1280×900, 모바일 390×844·320×740에서 링크 이동 후 ‘지금 할 일’ 제목이 고정 그림 아래 약 16px 간격으로 보이며 가로 넘침이 없었다. 상단의 기관·행동 문장이 상세 데이터와 같은지도 DOM으로 비교했다.
- 키보드로 결과 제목에서 Tab을 누르면 바로가기 링크로, Enter를 누르면 목적지 제목으로 이동했다. 초점 테두리를 확인했고 다음 Tab은 목적지 이후의 링크로 이어졌다. 기존 현재 단계 강조와 5단계 설명은 유지됐다.
- 브라우저 콘솔 오류·경고 0건. 복사 전달값을 관찰하기 위한 로컬 QA 탭의 임시 설정은 원상 복구했다. 외부 앱 붙여넣기와 실제 화면낭독기 검증은 하지 않았다.

지원 기준·금액·법적 절차·확인일은 변경하지 않았다. 서버 저장·전송 기능이나 새 외부 연계, 커밋·푸시·배포는 추가하지 않았다. 개발 서버가 실행 중일 때 [로컬 수정본](http://localhost:3000/validation/hearing-aid-health-insurance)에서 검토할 수 있다.

## 2026-09-25 · 청구 단계별 비용 카드와 본인부담경감 도움말

현재 로컬 코드의 `BenefitBreakdown.tsx`를 수정했다. 전체 기준과 Guided Result의 ‘지원 금액과 지급 조건 자세히 보기’는 기존대로 이 컴포넌트를 공유한다. `AfterPurchaseGuide.tsx`는 결과 내 제목 단계와 비용 영역의 중복 좌우 여백만 조정했다. 배포 사이트는 수정하지 않았다.

화면 구성:

- 처음 청구 카드: 기준액 111만 원 → 제품 91만 원 + 초기 관리 20만 원 → 구입 1개월 경과 후 검수확인과 두 비용의 함께 지급 → 자격별 최대 지원액(90% 99만 9천 원, 100% 111만 원).
- 이후 청구 카드: 후기 적합관리, 연 5만 원 × 최대 4회와 기준액 합계 20만 원 → 관리 기간·실제 관리 후 연 1회 청구 → 자격별 연/4회 합계 최대액(90% 4만 5천 원/18만 원, 100% 5만 원/20만 원).
- 두 카드 다음에 전체 기준액 131만 원, 자격별 총 최대액 117만 9천 원/131만 원과 일시 지급이 아니라는 설명을 한 번 묶었다. 기준액·적합관리의 뜻과 실제 지급액의 제한은 기본 노출한다.
- 컴포넌트 자체에 충분한 너비가 있으면 구성·시점과 지원액을 좌우로, 좁으면 같은 카드 안에서 위아래로 배치한다. CSS 시각 순서를 바꾸지 않아 DOM도 처음 청구 전체 → 이후 청구 전체 → 합계 순서다.
- 기존 `BenefitConditions`, `BenefitSources` 내용과 링크는 변경 전 백업과 비교해 동일함을 확인했다. 상세 제목에서 ‘표’를 ‘안내된 최대 금액’으로 바꿨다.

도움말 근거와 편집 판단:

- [공단 건강보험 웹진 2023년 9월 안내](https://www.nhis.or.kr/static/alim/paper/oldpaper/202309/sub/section4_7.html)는 일반 건강보험 가입자의 90%와 차상위 본인부담경감 대상자의 지급기준금액 100%를 구분한다. 과거 자료이므로 이 자료만으로 현재 자격 범위나 새로운 금액을 확정하지 않았다.
- [찾기쉬운 생활법령정보](https://easylaw.go.kr/CSP/CnpClsMain.laf?ccfNo=3&cciNo=4&cnpClsNo=1&csmSeq=1063&popMenu=ov)의 2026-08-15 기준 본문에서 지급기준금액, 질환·연령·소득인정액·부양 요건과 경감 인정 관련 설명을 확인했다. 페이지의 향후 시행 법령 알림을 현행 지급 조건 변경으로 적용하지 않았다.
- [국민건강보험법 시행규칙 제14·15조](https://www.nhis.or.kr/lm/lmxsrv/law/lawFullContent.do?SEQ=29)에서 신청·확인 후 공단의 경감 인정, 소득·재산 및 부양 요건을 확인했다. 사용자의 초안에 ‘부양 요건’을 짧게 보완했다. ‘청각장애 등록만으로 자동 적용되지 않는다’는 설명은 별도의 경감 인정 절차가 있다는 근거를 쉽게 풀어 쓴 것이다. 소득표·새 자격 질문이나 대상 판정은 추가하지 않았다.
- 도움말에서 의료급여와 건강보험의 경감 제도를 구분하고 공단에 그대로 물어볼 문장을 제공한다. 100%가 제품 가격 전액 무료라는 뜻으로 읽히지 않도록 인정되는 금액 기준이라는 요약을 유지한다. 기존 조건의 ‘차상위 본인부담경감 대상 등’ 문구도 유지하므로 카드의 두 표기를 모든 법적 예외의 완전한 목록이라고 단정하지 않는다.
- 도움말 확인일은 2026-09-25이며 기존 등록·급여 기준 및 제품 목록의 확인일은 갱신하지 않았다.

실제 검증:

- 최종 `lint`, `typecheck`, `test:visit-flow`, `test:products`, `build` 모두 통과했다. 기존 15개 결과 분기, 이력 있음/모름의 조건부 표현, 문장 조합과 복사문 회귀 검사도 통과했다. 새로운 계산·자격 분기 로직은 없으며 이번 보완은 브라우저의 실제 렌더링·조작 검사에 집중했다.
- 두 화면의 비용 컴포넌트 전체 텍스트가 같고, 반복 도움말 6개의 본문이 동일하며 ID/`aria-controls` 연결에 중복·누락이 없음을 확인했다.
- 1280×900에서 전체 기준의 열은 각각 약 367px이며 결과도 좌우 카드로 표시됐다. 390×844·320×740에서 카드마다 구성·시점 → 지원액 순서로 쌓이고 가로 넘침이 없었다. 인라인 도움말의 너비·높이가 잘리지 않고 본문을 따라 스크롤되는 것을 확인했다. 320px 결과에서는 중복 여백을 줄인 최종 카드 너비가 215px다.
- 도움말의 접근 가능한 이름은 ‘차상위 본인부담경감 대상자 설명’이다. 클릭, Enter, Space로 여닫고 `aria-expanded`와 설명 영역의 숨김 상태가 함께 바뀌며 초점 테두리가 보였다. 처음·이후·합계의 도움말을 각각 열어 독립 동작을 확인했다.
- 결과의 비용 상세는 처음에 닫혀 있고 상담 질문 3개 뒤에 위치했다. 답변을 바꾸면 비용 상세가 다시 접힌다. 상단 ‘지금 할 일 보기’는 목적지 제목으로 초점을 옮기며 고정 그림이 제목을 가리지 않았다.
- 건강보험+처방 완료의 이력 있음 → 모름 → 처음을 연속 변경하고 화면 상태·문의·질문이 실제 복사 API에 전달되는 값과 일치함을 확인했다. 이력 미확인에서 과거 지원이나 현재 기기 보유를 단정하는 이전 문구가 돌아오지 않았다.
- 보험 자격 미확인으로 답변을 수정한 뒤 처방 없음/모름/있음(이력 모름)을 확인했다. 세 결과 모두 건강보험 지원율·지급액·비용 카드를 노출하지 않았고 화면·복사문의 문장 구분이 유지됐다. 복사 관찰용 로컬 탭 설정은 원상 복구했다.
- 브라우저 콘솔 오류·경고 0건. 실제 화면낭독기, 실제 모바일 기기의 터치, 외부 앱에 복사문 붙여넣기와 사용자 이해도 검증은 수행하지 않았다. 모바일 검증은 브라우저 화면 크기 변경과 클릭으로 진행했다.

Q1~Q4 분기·정책 금액·법적 절차·답변 저장/전송 방식은 변경하지 않았다. 수정한 구현 파일은 `BenefitBreakdown.tsx`, `AfterPurchaseGuide.tsx`, `page.module.css`이며 README와 이 기록을 갱신했다. 커밋·푸시·배포는 수행하지 않았다.

## 2026-09-25 · 계산 기준과 최대 지원액의 위계 보완

공통 `BenefitBreakdown.tsx`와 `page.module.css`만 수정해 전체 기준과 Guided Result에 함께 반영했다. 처음 청구에는 ‘지원금 계산 기준’, 이후 청구에는 ‘관리비 계산 기준’을 표시하고, 기준액 바로 아래에 ‘이 금액이 그대로 지급되는 것은 아니에요. 자격별 최대 지원액을 확인하세요.’를 기본 노출한다. 도입부에서 두 건강보험 자격이 같은 계산 기준을 사용한다는 점을 명시했다.

왼쪽 기준액은 본문 크기·보통 굵기로 낮추고, 오른쪽은 ‘자격별 최대 지원액’ 제목 아래 자격명·지원율·최대액을 묶었다. 지원 최대액은 더 큰 굵은 글씨로 강조한다. 전체 합계도 같은 좌우 구분을 적용하며 모바일에서는 계산 기준 → 자격별 최대액으로 쌓인다. `CostReductionAmount`가 자격명·버튼 → 금액(후기는 연/4회 합계 모두) → 도움말 본문 순서를 보장한다. 버튼과 `useId`, `aria-expanded`, `aria-controls` 연결은 유지했다.

실제 검증:

- `npm run lint`, `npm run typecheck`, `npm run test:visit-flow`, `npm run test:products`, `npm run build` 모두 통과했다. 기존 15개 결과·미확인 분기·조건부 기기 보유·문장 연결·복사문 및 제품 목록 검사를 유지했다. 이번 변경에 계산이나 분기 로직은 없으므로 별도 구현 반복 테스트 대신 실제 렌더링·키보드 검증을 진행했다.
- 1280×900에서 세 카드가 좌우로 표시됐다. 전체 기준의 각 열은 367px, 결과 화면은 366px였다. 기준액은 16px/400, 최대액은 19.2px/700으로 확인했다. 제목·배경·구분선·배치도 함께 의미를 구분한다.
- 390×844와 320×844의 두 화면에서 처음 청구 → 이후 청구 → 전체 합계, 각 카드 안의 계산 기준·시점 → 자격별 최대액 순서를 확인했다. 세 도움말을 모두 연 상태에서도 카드와 페이지에 가로 넘침이 없었다. 가장 좁은 320px 결과 카드의 안쪽 너비는 213px였으며 금액 단위와 도움말이 잘리지 않았다.
- 처음·이후·합계의 도움말 본문이 각 자격의 마지막 금액 아래에 배치되는 것을 DOM 순서와 실제 위치로 확인했다. 도움말을 열어도 자격명과 금액 사이에 설명이 끼지 않았다. Enter 열기·Space 닫기, 클릭, 접근 가능한 이름, 열림/숨김 상태, 3px 초점 테두리와 도움말 출처 링크로의 Tab 이동을 확인했다.
- 결과 화면과 전체 기준 비용 컴포넌트의 전체 텍스트가 일치했다. 함께 마운트되는 도움말 버튼 6개와 ID 중복 없음을 확인했다. 비용 상세는 처음에 접혀 있고 센터 질문 3개 뒤에 유지됐다.
- 건강보험+처방 완료에서 이력 처음 → 있음 → 모름으로 바꿔 화면과 실제 복사 API 전달값을 확인했다. 이전 결과가 남지 않았고, 이력 있음의 조건부 기기 표현과 모름의 기록 존재 확인 안내가 유지됐다. 변경 후 비용 상세는 다시 접혔다.
- 보험 자격을 미확인으로 변경한 뒤 처방 없음/모름/있음(이력 모름) 결과를 확인했다. 지원율·지원액·비용 카드가 노출되지 않았고 화면·복사문의 문장 사이 공백이 유지됐다.
- 320px와 데스크톱에서 ‘지금 할 일 보기’를 키보드로 실행하면 목적지 제목에 초점이 이동하고 고정 그림 아래 약 16px 여유가 있었다. 브라우저 콘솔 오류·경고 0건. 임시 화면 크기와 복사 관찰용 설정은 원상 복구했다.
- 작업 시작 전 백업과 비교해 `BenefitConditions`, `BenefitSources`, 도움말 본문·출처가 동일함을 확인했다. 나머지 페이지 소스·분기·복사·이동 코드는 변경하지 않았다. 기존 금액·구성·청구 시점·정책 확인일도 유지했다.

실제 화면낭독기, 실제 모바일 기기 터치, 외부 앱 붙여넣기와 사용자 이해도는 미검증이다. 모바일 검증은 브라우저 화면 크기 변경으로 수행했다. README와 이 검증 기록을 갱신했으며, 커밋·푸시·배포는 하지 않았다. 기존 개발 서버의 [로컬 비용 안내](http://localhost:3000/validation/hearing-aid-health-insurance#benefit)에서 검토할 수 있다.

## 2026-09-25 · 아동·청소년 양쪽 보청기 지원 안내

### 범위와 구현

현재 로컬 변경을 보존한 상태에서 작업 전 소스·문서·스크립트·manifest를 `/tmp/iyum-p03-before-bilateral-20260925.tar.gz`에 백업했다. 프로젝트와 상위 경로에서 적용할 AGENTS.md는 발견되지 않았다. 기존 README, 정책 재조사 기록, 검증 기록과 화면 전환·설문·비용·복사 컴포넌트를 확인했다.

- `BilateralGuide.tsx` 추가: 기존 `#one-or-two`를 전체 기준의 독립된 세 번째 항목으로 확장했다. 쉬운 요약 → 선택형 검사 조건 → 두 자격의 양쪽 합산 예시 → 선택형 공식 근거·계산식 → 기관별 질문·복사 → 별도 사업 링크 → 원래 작업 복귀 순서다.
- `page.tsx`, `GuidedCheck.tsx`, `ReferenceGuide.tsx`, `content.ts`: 요약 보조 링크·7개 항목의 목차·모든 결과의 보조 링크를 연결했다. 설문 상단에는 보호자가 보청기를 사용할 사람의 상태를 기준으로 답하도록 안내한다. 개인의 연령·양쪽 자격을 추정하지 않는다.
- `BenefitBreakdown.tsx`, `benefit-content.ts`: 한쪽 카드의 구조와 계산 기준/최대 지원액 위계를 유지한다. 범위를 ‘보청기 한쪽(1개) 기준’으로 명시하고 보호자 안내 링크를 추가했다. 기준액·관리비·횟수·지원율로부터 한쪽과 양쪽 최대액을 계산해 두 화면이 공유한다. 개인 입력을 받거나 개인 지급액을 계산하지 않는다. 도움말과 금액 표시도 재사용한다.
- `AfterPurchaseGuide.tsx` 및 요약·전체 기준의 도입 문구: 131만 원이 한쪽 기준이라는 설명을 보완하고 공통 수치를 사용한다. 기존 금액·지급 시점과 상세 기본 접힘은 유지한다.
- `bilateral-content.ts`, `QuestionTakeaway.tsx`: 보호자 질문의 화면·복사문은 같은 기관명·질문 데이터에서 생성한다. 기존 복사 성공/실패·수동 복사 패턴에 보호자용 성공 문구만 선택적으로 전달한다. 기존 결과 복사의 기본 동작은 같다.
- `page.module.css`: 기존 색상·카드·글자 크기와 컨테이너 분기를 사용한다. 양쪽 금액은 데스크톱에서 자격별 두 카드, 좁은 화면에서는 일반 대상 카드 전체 → 본인부담경감 대상 카드 전체로 읽는다. 두 자격의 최대액은 동일한 크기로 표시한다.
- `scripts/check-visit-flow.mjs`: 한쪽 금액 보존, 양쪽 ×2 관계, 처음/후기/전체 합계, 기관명을 포함한 보호자 복사문을 추가 검사한다. 기존 15개 결과와 제품 검사는 유지한다.

`DetailNavigation.tsx`의 기존 공개 anchor `one-or-two`와 `FocusedFlow.tsx`의 복귀 문맥을 그대로 사용했다. 이동 후 제목에 초점이 가고, 안내 하단의 ‘내 확인 결과로 돌아가기’는 기존 결과 제목으로 복귀한다. 요약에서 진입했다면 ‘30초 요약으로 돌아가기’다. 설문/결과 분기, 현재 단계 계산, 제품 검색, 사용자 답변의 메모리 보관 방식은 변경하지 않았다. 기존 `BenefitConditions`·`BenefitSources`의 지급 조건과 출처 본문도 작업 전 파일과 동일하다.

### 공식 원문 재확인 — 2026-09-25

검색 요약·판매업체 글 대신 다음 원문을 확인했다. 사용자 제시 조건·합산 예시와 불일치는 없었다. 이전의 전체 등록 기준 확인일(9월 24일)과 제품 자료 확인일(9월 23일)을 이번 조사로 일괄 갱신하지 않고 보호자 안내에 별도 확인일을 표시했다.

1. [국민건강보험법 시행규칙 별표 7 원문 PDF](https://www.law.go.kr/flDownload.do?bylClsCd=110201&flSeq=162807869&gubun=): 별표의 개정일은 2026-03-25. 제1호 라목 및 라목 1)의 양쪽 각각 인정, 제3호의 90%·해당 경감 대상 100%와 지급기준금액 산정 규정을 확인했다. [공단 현행 시행규칙](https://www.nhis.or.kr/lm/lmxsrv/law/lawFullView.do?SEQ=29)의 선택 버전도 제1187호, 2026-08-11 시행/2026-07-10 개정(SEQ_HISTORY 614306)이었다. 이 버전에서 연결하는 별표 7(FILE_SEQ 2329850)을 새로 내려받아 HWP 본문을 읽었고 동일 조항·별표 개정일을 재확인했다.
2. [장애인보조기기 보험급여 기준 등 세부사항](https://www.nhis.or.kr/lm/lmxsrv/law/lawFullView.do?SEQ=87&SEQ_HISTORY=613408): 현행 목록의 선택값이 여전히 SEQ_HISTORY 613408, 제2026-56호(2026-03-24 개정, 2026-03-25 시행)임을 확인했다. 제5조의2와 별표 2·4·5를 새로 내려받아 HWPX 본문을 대조했다.
3. 별표 2 제4호: 기본 대상은 청력장애에 해당하는 청각장애인으로서 전문의가 보청기 사용이 일상생활에 도움이 된다고 판단한 경우다. 양쪽은 기본 요건에 더해 19세 미만, 양쪽 80dB 미만, 양쪽 말소리명료도 50% 이상, 양쪽 순음청력역치 차이 15dB 이하, 양쪽 말소리명료도 차이 20% 이하를 모두 충족해야 한다. 의식이 명료하지 않거나 보청기를 사용할 수 없다는 전문의 판단에 따른 제외도 선택형 상세에 보존했다. 수치 입력·자가 판정 체크리스트는 없다.
4. 별표 5 아목 10): 기준액 1,310,000원, 그 안에 적합관리급여 400,000원 포함, 내구연한 5년. 제5조의2·별표 4: 초기 관리 200,000원 1회, 후기 관리 50,000원 최대 4회. 구입 1개월 경과 후 검수확인된 초기 관리비는 제품 급여와 함께 지급하며 제품별 고시금액에 이미 포함된다. 후기 급여는 지원받은 기기를 지속 사용하고 구입 1년 후부터 실제 관리를 받는 조건과 연 1회 청구를 적용한다.
5. [복지로 ‘선천성 난청검사 및 보청기 지원’](https://www.bokjiro.go.kr/ssis-tbu/twataa/wlfareInfo/moveTWAT52011M.do?wlfareInfoId=WLF00001130): 실제 브라우저에서 사업명·2026 기준연도와 지원대상 본문을 확인했다. 청각장애 등급을 받지 못하는 난청 아동의 별도 보청기 지원 내용이 있어 짧은 안내와 링크를 제공한다. 이 사업의 별도 연령·검사 조건·금액을 건강보험 계산의 근거로 사용하지 않으며, 상세 신청 흐름은 구현하지 않았다.

새로 받은 공식 첨부 SHA-256:

- 시행규칙 별표 7 / 2329850: `c1ff09e169b2556e3aecfc618b5db1454bd3208bbb901ab12a8daa5e1b19b1bb`
- 고시 별표 2 / 2319825: `057568c9f2b36aeb7b049c9599c3a760b10a5487b1e37239784e654232c7eba0`
- 고시 별표 4 / 2319827: `188009a351ca6eaf241995a1761ec56c2c009f215bb480be086cb65230e87c87`
- 고시 별표 5 / 2319828: `29de0e7ba5161687785fcb02403324536de803d9b62dcb8fa482588b8f8c2fd0`

### 공식 명시와 계산 결과 구분

공식 문서가 직접 정하는 내용은 양쪽 각각의 급여 인정, 한쪽 기준액·적합관리 금액과 횟수, 지급 비율·최저금액 원칙, 대상·지급 조건이다. ‘양쪽 최대 262만 원’이라는 문구가 원문에 직접 기재되어 있음을 확인한 것은 아니다. 화면과 이 기록은 이를 **공식 기준을 적용해 계산한 합산 상한**으로 구분한다.

한쪽 제품 91만 원 = 131만 원 − 관리비 40만 원. 한쪽 처음 청구 기준 111만 원 = 제품 91만 원 + 초기 관리 20만 원. 이 구성도 공식 숫자에 따른 산출값이다.

| 자격 | 양쪽 처음 최대액 | 양쪽 후기 연 최대액 | 후기 최대 4회 | 전체 합산 상한 |
| --- | --- | --- | --- | --- |
| 일반 대상 90% | 111만 원 × 90% × 2 = 199만 8천 원 | 5만 원 × 90% × 2 = 9만 원 | 9만 원 × 4 = 36만 원 | 199만 8천 원 + 36만 원 = 235만 8천 원 |
| 차상위 본인부담경감 대상 100% | 111만 원 × 100% × 2 = 222만 원 | 5만 원 × 100% × 2 = 10만 원 | 10만 원 × 4 = 40만 원 | 222만 원 + 40만 원 = 262만 원 |

양쪽 지원이 인정되고, 두 기기 각각에 최대 지급 기준이 적용되며, 후기 관리의 실제 서비스·지급 조건까지 충족하는 경우다. 실제 지급액은 각 제품 고시금액·구입금액과 관리 조건 등에 따라 달라진다. 전체 합계는 일시 지급액이 아니고, 아동이라는 이유로 100% 자격이 되는 것도 아니다. 보험 미확인 결과에는 개인 적용 금액을 표시하지 않고, 보호자 공통 안내를 명시적으로 열었을 때만 위 예시를 읽게 한다.

### 실제 검증과 한계

- 최종 `npm run lint`, `npm run typecheck`, `npm run test:visit-flow`, `npm run test:products`, `npm run build` 모두 통과했다. 기존 15개 결과/복사/미확인 검사에 한쪽/양쪽 계산 관계 및 보호자 질문 복사 데이터 검사를 추가했다.
- 실제 브라우저에서 요약 링크, 7개 목차의 보호자 항목, 전체 기준과 결과의 한쪽 비용 카드 링크, 비용 상세 밖의 결과 링크가 모두 같은 `one-or-two-title`로 초점을 옮김을 확인했다. 본문 ID는 한 개다.
- `#one-or-two`에서 새로고침하고 초기 로딩이 끝난 뒤 참고 화면과 해당 제목 초점을 확인했다. 조건·근거는 기본 접힘 상태이며, 이전 답변이 없는 직접 진입의 복귀 버튼은 ‘30초 요약으로 돌아가기’였다. 요약·참고·결과의 131만 원 도입 문구도 한쪽 기준을 표시한다.
- 등록 검사 전, 검사·진단 중, 심사 결과 대기, 보험 자격 미확인, 건강보험+처방 완료 결과에서 안내 왕복 전후 선택 답변·결과 요약·현재 위치를 비교해 동일함을 확인했다. 키보드 Enter로 안내 하단 복귀가 가능하며 결과 제목이 고정 과정 그림 아래에 보였다.
- 건강보험 결과의 상담 질문 3개와 비용 상세 기본 접힘을 확인했다. 전체 기준과 결과의 한쪽 비용 컴포넌트 전체 텍스트가 일치하며, 반복 도움말 7개와 전체 DOM ID에 중복이 없었다. 한쪽 기존 금액과 계산 기준의 시각적 위계를 유지했다.
- 1280×900에서는 양쪽 예시가 두 자격의 같은 크기 카드로 표시됐다. 390×844·320×844에서는 자격별 카드 전체를 순서대로 읽으며, 검사 조건·근거 상세·도움말을 펼쳐도 카드·금액·링크·페이지의 가로 넘침이 없었다. 320px 예시 카드 안쪽 본문 너비는 239px였다.
- 검사 조건과 근거 토글의 Enter/Space, 도움말의 접근 가능한 이름·열림/숨김 연결·초점 표시, 도움말 출처로 Tab 이동을 확인했다. 도움말 본문은 자격 금액 목록 아래에 유지됐다.
- 보호자 질문 3개의 기관명·질문과 복사 API 전달값이 모두 일치했다. 성공 안내, 실패를 의도적으로 발생시켰을 때의 수동 복사 textarea 및 원문 일치, 다시 성공했을 때 fallback 제거를 확인했다. 기존 결과의 보험 미확인/지원 이력 있음·모름 화면과 브라우저 클립보드 내용도 일치했다. 미확인에서 금액 확정, 이전 이력 단정 또는 현재 기기 보유 단정이 돌아오지 않았다.
- 제품코드 D22018010015 검색·Starkey Picasso 1000 CIC 선택 후 보호자 안내를 왕복해 기존 선택 모델을 반영한 상담 질문이 유지됐다. 다음 행동 링크는 키보드로 목적지 제목에 초점을 옮겼으며 고정 그림 아래에 보였다. 콘솔 오류·경고 0건.
- 검증 중 `.next/types`의 `* 2.ts` 생성 파일 4개가 원본과 같은 선언을 중복해 타입 검사 충돌을 일으켰다. 원본과 바이트가 같음을 확인하고 `/tmp/iyum-p03-duplicate-generated-types-20260925`로 이동했다. 프로젝트 소스나 타입 검사 설정을 우회하지 않았다.

화면 크기 변경은 실제 모바일 기기 시험이 아니며, 실제 보호자 이해도·화면낭독기·모바일 터치·외부 앱 붙여넣기는 미검증이다. 복사 관찰/실패 재현을 위한 QA 탭의 임시 설정과 화면 크기 override는 복구했다. 정책 문서 읽기를 위한 `olefile`은 `/tmp/iyum-policy-libs`에만 설치했으며 프로젝트 의존성은 추가하지 않았다. 서버 저장·답변 전송·외부 신청·Notion 수정·커밋·푸시·배포는 하지 않았다. [로컬 보호자 안내](http://localhost:3000/validation/hearing-aid-health-insurance#one-or-two)에서 검토할 수 있다.

## 2026-09-25 · 보호자 안내의 복사 버튼과 이동 동선 보완

### 확인한 원인과 수정

- `QuestionTakeaway`의 복사 버튼에는 클래스가 없었고, 기본 버튼 스타일은 `.guided button`에만 있었다. `.guided` 밖의 보호자 안내에서는 이 선택자가 적용되지 않았다. 수정 전 로컬 브라우저 실측은 높이 22px, 글자 13.33px, padding 1px 6px, 브라우저 기본 outset 테두리였다. 배포본을 직접 수정하거나 재측정한 것은 아니다.
- 공통 복사 버튼에 `copyButton` 클래스를 직접 부여하고 기존 결과 버튼의 기본·hover 스타일을 함께 사용했다. disabled 스타일도 해당 클래스에 연결했다. 전역 button 스타일이나 복사 처리 로직은 추가·변경하지 않았다. CSS 최소 높이는 48px이며, 수정 후 보호자·결과 버튼 모두 높이 약 54.8px, 글자 16px, padding 12px 16px, 1px 녹색 테두리로 일치했다. 키보드 초점은 기존 3px 파란 테두리를 사용한다.
- 안내 제목 바로 아래에 기존 `ReturnToTask`를 재사용했다. 상·하단이 같은 context와 복귀 함수를 사용하므로 결과 진입은 ‘내 확인 결과’, 요약·직접 진입은 ‘30초 요약’으로 돌아간다. `FocusedFlow`, `GuidedCheck`, 답변·제품 상태와 결과 계산 로직은 수정하지 않았다.
- 대상 요약 다음에 ‘지금 확인할 일’과 ‘병원·센터 질문 보기 ↓’를 배치했다. 기존 `focusContent`로 질문 제목에 초점을 옮기고 `tabIndex=-1`, `scroll-margin-top: 1rem`으로 이동 위치를 명확히 했다. 기존 개인별 ‘지금 할 일’ 데이터와 질문 본문은 재사용하거나 덮어쓰지 않는다.
- ‘안내를 읽어도 상태는 바뀌지 않는다’는 구현 설명을 제거하고, 개인별 자격·금액 확인이 필요하다는 안내로 줄였다. 직접 검사 수치를 해석할 필요가 없다는 설명은 상단 행동 안내 한곳으로 모았다. 금액 도입부는 짧은 최대 금액 예시 설명으로 정리하고, 고시의 명시 기준과 IYUM 계산 합계의 차이는 공식 근거 상세로 옮겼다. 기존 조건·금액·계산식·출처·확인일은 유지했다.

### 실제 검증

- `npm run lint`, `npm run typecheck`, `npm run test:visit-flow`, `npm run test:products`, `npm run build`: 모두 통과. 기존 15개 결과/문장 연결/미확인/복사 검사, 한쪽·양쪽 계산/보호자 질문 검사 및 제품 목록 검사를 실행했다. 변경 범위는 공통 UI 재사용과 문구·스타일이므로 구현 문자열을 반복하는 새 테스트는 추가하지 않았다. 상태 보존·초점 회귀 위험은 아래 브라우저 경로로 확인했다.
- 실제 로컬 브라우저에서 첫 지원 결과 + Starkey Picasso 1000 CIC(제품코드 D22018010015) 선택, 등록 심사 대기, 보험 자격·지원 이력 모두 미확인의 세 경로를 각각 상단과 하단으로 왕복했다. 왕복 전후 선택 답변·현재 상태 요약·과정 표시·상담 질문 텍스트를 비교해 모두 같음을 확인했다. 복귀 초점은 `guided-result`였으며 고정 과정 표시 아래에 노출됐다.
- 첫 지원 결과의 선택 제품을 포함한 복사문, 이후 미확인 경로의 화면·복사문을 확인했다. 미확인 결과에 지원율·금액·비용 상세가 노출되지 않았고, 이전 지원 사실이나 기기 보유를 단정하지 않았다. 설문 결과의 질문 3개와 보호자 질문은 구분돼 있었다.
- 직접 `#one-or-two` 새로고침 후 결과가 없는 상태에서 상·하단이 모두 요약 복귀로 표시되고 검사 조건·근거가 기본 접힘임을 확인했다. 요약·목차·한쪽 비용 카드·결과 링크가 같은 안내 제목으로 이동하며 안내 ID가 한 개였다. 전체 DOM에 중복 ID가 없었다.
- 질문 바로가기 Enter → `caregiver-questions-title` 초점(화면 상단 약 16px), Tab → 질문 복사 버튼, Enter/Space → 복사 동작을 확인했다. 상·하단 복귀도 Enter/Space로 조작했다. 기존 ‘지금 할 일 보기’는 데스크톱과 390px에서 목적지 제목을 고정 과정 표시 약 16px 아래로 이동시켰다.
- 보호자 복사 성공 안내와 브라우저 클립보드의 기관명·질문을 대조했다. QA 탭에서만 클립보드 쓰기 실패를 의도적으로 발생시켜 실패 안내·읽기 전용 수동 복사 textarea의 원문 일치를 확인했다. 원래 쓰기 함수를 복구한 뒤 Space로 다시 복사해 성공 및 fallback 제거를 확인했다.
- 1280×900·390×844·320×844에서 보호자 안내의 DOM/시각 읽기 순서, 제목 아래 복귀, 질문 바로가기와 복사 버튼을 확인했다. 가로 넘침·버튼 겹침이 없고 작은 화면에서도 복사 버튼 높이는 약 54.8px였다. 390px·320px에서는 검사 조건·공식 근거·자격 도움말을 모두 펼쳐도 안내 요소가 화면 너비를 벗어나지 않았다. 복귀·복사 버튼과 이동한 질문 제목의 초점 테두리를 확인했다.
- 브라우저 콘솔 경고·오류 0건. QA의 임시 클립보드 설정과 viewport override를 복구했다. 시작 전 백업과 대조해 구현 변경은 `BilateralGuide.tsx`, `QuestionTakeaway.tsx`, `page.module.css` 세 파일로 제한됨을 확인했고, 이 검증 기록을 함께 갱신했다.

실제 보호자 이해도, 화면낭독기 음성 출력, 실제 모바일 기기의 터치, 외부 앱 붙여넣기는 검증하지 않았다. 모바일 확인은 브라우저 viewport 변경으로 수행했다. 이번 작업에서 지원 정책을 재판정하지 않았으며 기존 정책 데이터·계산·공식 근거를 변경하지 않았다. 커밋·푸시·배포는 하지 않았다.
