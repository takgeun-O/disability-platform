# 마이페이지·메뉴 후속 검토 — 2026-10-20

## 기준과 변경 범위

- 브랜치 `feat/member-profile`, HEAD `fcbb48e093830d4a6d785587f81e668b129bdb28` 유지. 기존 미커밋·미추적 구현과 캡처 이력을 보존했다.
- 루트/web AGENTS, PRODUCT-CONTEXT, 현재 GlobalNav/AppLink/NavigationGuard/OverlayDialog와 기존 브라우저 검사를 확인했다.
- 설치된 Next16.3.6 `node_modules/next/dist/docs/01-app/03-api-reference/02-components/link.md`의 onNavigate 계약을 확인했다. 수정 키 클릭/새 탭은 onNavigate 대상이 아니다.
- 서비스 변경은 `src/components/GlobalNav.tsx`에 한정한다. 헤더 레이아웃/CSS, 닉네임·인증 처리, 백엔드/API/보안 정책은 변경하지 않았다.

## 1. PC 현재 정보 페이지 선택

### 원인과 재현

수정 전 프로덕션 빌드에서 `/welfare` → 정보 찾기 → 복지·지원정보 선택을 마우스와 키보드로 각각 재현했다. 같은 URL이면 탐색은 취소했지만 `close()`는 모바일 상태만 변경하여 PC 드롭다운이 계속 열렸다. 키보드 초점은 링크에 남았다. 또한 `document.activeElement.href`에 의존하여 선택한 링크와 초점이 다르면 판별이 달라질 수 있었다.

### 수정

`GlobalNav.onNavigate(href, returnToInformation)`에 실제 링크 목적지를 전달한다.

- `new URL(href, window.location.href).href`와 현재 URL 전체를 비교한다. query/hash 차이를 같은 페이지로 무시하지 않는다.
- 정확히 같은 목적지이면 `preventDefault()` → `setInformation(null)` → 모바일 `close()` 처리. PC 정보 링크는 `infoButton.focus({ preventScroll: true })`로 숨겨진 링크에 초점이 남지 않게 한다.
- AppLink는 preventDefault를 확인하면 NavigationGuard 알림 전에 반환한다. 불필요한 탐색 승인/요청 무효화는 호출하지 않는다.
- 다른 목적지이면 기존 AppLink/NavigationGuard 흐름을 그대로 사용한다. 이탈 취소에는 메뉴/초안이 유지되고 승인 후 닫힌다.
- 수정 키 클릭과 새 탭 열기는 기존 Next 링크 동작을 유지한다. 모바일의 같은 URL 클릭 캡처와 잠금 해제도 유지했다.

회귀: 마우스/키보드/초점과 선택 링크가 다른 프로그램 클릭의 세 경우, URL·history·scroll·문서 timeOrigin 유지와 탐색 이벤트0, query/hash 차이, Cmd+클릭 새 탭, PC 다른 페이지 이탈 취소/승인, 모바일 같은 페이지 선택을 확인했다.

## 2. 1440px 하단 헤더 중복 캡처

### 확인 결과

**이번 재현에서는 서비스 중복 렌더링이 아닌 캡처 타이밍 문제로 확인했다.**

- 기존 `after/page-1440.png`에는 하단에 회원 메뉴까지 포함한 헤더가 반복되어 있었다. 원본은 `mypage-menu-followup-2026-10-20/reproduction/reported-page-1440.png`로 보존했다.
- 수정 전 빌드에서도 새1440×1000 컨텍스트 직접 진입/새로고침/편집창 닫기/모바일 메뉴 닫기와1440 복귀 시 사이트 header와 banner는 각각1개, CSS position은 relative, 좌표는 y=0이었다.
- 이전 촬영과 같은 **390px 진입 →1440px 변경 → 즉시 fullPage 캡처**를6회 비교했다. 첫 회에서 동일한 하단 중복 이미지가 재현됐고 나머지5회는 정상이다. 각 회 DOM 헤더는1개였다.
- 같은 페이지에서 `document.fonts.ready`와 두 번의 requestAnimationFrame을 기다린 후 재촬영한6회는 모두 정상이며 PNG SHA256도 서로 일치했다. 이미지 편집이나 DOM/CSS 강제 변경은 하지 않았다.
- 일반 viewport 캡처와 fullPage 캡처를 각각 생성했다. 최종 빌드에서도 새 진입, reload, 편집창 개폐, 메뉴 개폐→폭 복귀, 메뉴를 연 채 PC로 전환한5상태를 모두 검사했다. 각각 header/banner1개이며 스크롤 잠금이 남지 않았다.
- 환경: macOS HeadlessChrome155.0.0.0. Chrome 내부 합성 구현까지 원인을 추적한 것은 아니다. 위 재현 조건과 DOM/이미지 차이가 분류 근거다.

### 촬영 절차 개선

`tests/member-menu-browser.mjs`에서 기준1440/1024/390/320 캡처는 **각 너비로 생성한 새 브라우저 컨텍스트**에서 촬영한다. `settle()`은 폰트와 연속 프레임을 기다린다. 별도 회귀는 기존처럼 화면 크기를 변경하여 실제 잠금·복원 동작도 검사한다. resize 검사를 없앤 것이 아니다.

`mypage-ui-2026-10-20/after/page-1440.png`를 새 정상 fullPage 캡처로 교체하고 비교 HTML의 설명을 갱신했다. 문제 영역을 자르거나 이미지를 보정하지 않았다. 헤더/모달 서비스 코드와 CSS에는 캡처용 우회 수정을 추가하지 않았다.

## 변경 파일

- `src/components/GlobalNav.tsx`: 목적지 기반 같은 URL 처리, PC 닫기·초점 복귀.
- `tests/member-menu-browser.mjs`: 기존15개 유지 + 후속7개, 새 컨텍스트 촬영과 DOM/viewport/fullPage 근거 수집.
- `docs/mypage-ui-2026-10-20/after/page-1440.png`, `comparison.html`: 정상 이미지와 안내 갱신.
- `docs/MyPage-Mobile-Menu-2026-10-20.md`: 이번 후속 기록 링크 추가.
- 이 문서와 `docs/mypage-menu-followup-2026-10-20/`: 재현/정상 캡처, DOM JSON, 실행 결과.

## 이번에 직접 실행한 검증

| 명령/검사 | 결과 |
| --- | --- |
| `pnpm lint` | 통과, 경고0 |
| `pnpm typecheck` | 통과 |
| `pnpm build` | 프로덕션 빌드 통과 |
| `git diff --check` | 통과 |
| `node tests/member-menu-browser.mjs` | 최종22/22 통과. 로컬 프로덕션 Next + 모의 인증/회원 API |
| 수정 전 메뉴/캡처 진단 | 메뉴 오류2경로 재현. 즉시 resize 캡처1/6 이상, 안정화 후6/6 정상 |

기존8443 서버의 `.next`를 보존하기 위해 build/typecheck는 `/private/tmp/iyum-menu-followup-build`에서 수행했다. 서비스 src132개 파일이 저장소와 바이트 단위로 같은지 확인했다.

```sh
# web/ 기준, 모듈·출력 경로는 실행 환경에 맞게 지정
PLAYWRIGHT_MODULE=<installed-playwright>/index.mjs \
BASE_URL=http://127.0.0.1:8451 \
MEMBER_ARTIFACT_DIR=<artifact-dir> \
node tests/member-menu-browser.mjs
```

보존 확인: 모바일 내부 스크롤/배경 잠금/Tab/Escape/초점 복귀, 모바일↔PC 전환, 닉네임 오류·저장 중 닫기 제한·중복 방지·프로필/헤더/메뉴 동기화, 작성 초안 이탈 취소·승인/뒤로가기, 로그아웃 성공·실패·지연, 세션 확인·재시도, 무JS 공개 메뉴.

### 검사 중 발생한 환경/테스트 문제

- 최초 임시 빌드에서 node_modules를 저장소 바깥으로 연결한 심볼릭 링크를 Turbopack이 거절했다. 임시 폴더에 의존성의 실제 복사본을 준비한 뒤 같은 서비스 소스 빌드가 통과했다. 서비스/배포 설정은 변경하지 않았다.
- 최초 브라우저 실행은21개 통과, 새 탭 검사1개 실패였다. Cmd+클릭은 정상적으로 새 탭을 열었지만 opener가 없어 page의 `popup` 이벤트가 없었다. 별도 진단에서 browserContext의 `page` 이벤트와 실제/welfare URL을 확인했다. 해당 이벤트 감지만 수정한 후22개 모두 통과했다. timeout 증가나 조건 삭제로 우회하지 않았다.
- 이 실패 기록은 `checks/browser-first.txt`, `checks/build-copy-first.txt`에 보존했다. 최신 결과는 `final/results.json`과 `checks/browser-final.txt`다.

## 캡처/근거 위치

- [정상1440 전체 화면](mypage-menu-followup-2026-10-20/final/header-fresh-full.png)
- [정상1440 viewport](mypage-menu-followup-2026-10-20/final/header-fresh-viewport.png)
- [기존 변경 전후 비교](mypage-ui-2026-10-20/comparison.html)
- `final/header-reload-*`, `header-after-edit-*`, `header-after-menu-resize-*`, `header-resize-open-menu-*`: 각 후속 조작 상태의 viewport/fullPage 캡처.
- `reproduction/diagnosis.json`, `resize-diagnosis.json`: 수정 전 DOM/메뉴 결과.
- `reproduction/resize-immediate-0.png`, `resize-settled-0.png`: 같은 페이지의 이상/정상 캡처. 이미지 수정 없이 그대로 보존.

## 미검증·최종 상태

- 실제 iPhone Safari·소프트 키보드·실기기 safe area는 미검증이다. Chrome viewport 결과를 실물 통과로 표현하지 않는다.
- 실제 Spring 회원 정보 변경은 이번에 실행하지 않았다. 회원/인증 변경은 모의 응답이며 실제 사용자 데이터를 변경하지 않았다.
- 전체 단위·로그인38개·회원20개·커뮤니티 전체 검사를 다시 실행하지 않았다. 이번 변경을 포함하는 메뉴22개만 실행했다. 과거 결과를 이번 결과로 합산하지 않는다.
- 기존 상세API 중복GET 및 무JS404 별도 문제는 이번 범위가 아니며 새 해결/실패로 보고하지 않는다.
- 기존8443 개발 서버를 유지하고 직접 실행한8451 임시 프로덕션 서버만 종료했다. `iyum-preview`, 백엔드·DB를 변경하지 않았다. 커밋·push·병합·배포하지 않았다.
