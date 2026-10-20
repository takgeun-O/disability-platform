# 마이페이지 디자인·공통 모바일 메뉴 개선 — 2026-10-20

## 작업 기준과 범위

- 브랜치 `feat/member-profile`, HEAD `fcbb48e093830d4a6d785587f81e668b129bdb28` 유지.
- 기존 마이페이지 1차 미커밋·미추적 구현과 사용자 참고 이미지 `docs/design/IMG_5498.jpg`를 보존했다. 루트/web AGENTS, PRODUCT-CONTEXT, 실제 구현, 설치된 Next16.3.6의 Link/onNavigate 문서를 확인했다.
- PRODUCT-CONTEXT의 10월15일 기능 목록은 현재 코드보다 오래됐다. 이번 작업은 서비스 비전·메뉴 항목을 다시 정의하지 않고 현재 인증·게시글·댓글 동작을 기준으로 삼았다.
- 백엔드·API 계약·CSRF·DB 스키마·의존성·배포 설정은 변경하지 않았다. 내 게시글·내 댓글 목록은 계속 준비 상태다.

## 변경 내용과 핵심 파일

| 파일 | 이번 변경 |
| --- | --- |
| `src/components/AuthControls.tsx` | 닉네임/님을 별도 요소로 표시, 명시적인 마이페이지 링크·현재 페이지 표시, 낮은 강조의 로그아웃. 인증/로그아웃 처리와 알림 수명은 한 컨트롤러가 유지하고 데스크톱·패널의 표시 위치만 분리한다. |
| `src/components/GlobalNav.tsx` | PC 탐색 유지, 모바일 오른쪽 오버레이. 회원 영역/실제 메뉴/정보 하위 메뉴/하단 로그아웃. 같은 페이지 링크는 실제 탐색 없이 패널을 닫는다. 무JS 공개 링크 유지. |
| `src/components/OverlayDialog.tsx` | 새 공통 표시 컴포넌트. native dialog의 배경 비활성화·모달 스택, Tab 순환, Escape/배경 닫기, 명시적 초점 복귀. body fixed/기존 인라인 스타일 복원, visualViewport 높이와 safe area 대응. |
| `src/components/NavigationGuard.tsx` | `subscribeNavigation()` 추가. 기존 이탈 승인 알림 시점에 구독자를 호출하여 라우터가 이동하기 **전에** 모달·배경 잠금을 정리한다. 확인 취소에는 호출하지 않으며 기존 dirty/이력/요청 무효화 정책은 그대로다. |
| `src/features/MyPage.tsx` | 기존 NicknameEditor 요청 로직을 한 개의 반응형 모달로 감쌌다. PC 모달/모바일 시트에 동일 폼·상태·ID 사용. 저장 중 취소/닫기/Escape 차단. 서버 필드 오류가 반영되고 입력이 활성화된 뒤 초점 이동. |
| `src/styles/iyum.css` | 헤더의 회원 표시·명시적 링크, 패널 폭88vw/최대360px, 배경·내부 스크롤·모션 감소. 공통 버튼 계약은 유지. |
| `src/styles/mypage.css` | 본문 최대840px, 활동 개수 그룹 최대480px, 큰 숫자 사이 여백, 빈 성공 알림의 여백 제거. PC 편집창440px/모바일 bottom sheet. |
| `tests/member-menu-browser.mjs` | 신규15개 메뉴·모달·반응형·무JS 회귀. |
| `tests/member-browser.mjs` | 기존20개 검사 유지. 배경 inert에 맞춰 CSRF 중 이탈은 실제 브라우저 뒤로 가기로 검사. 늦은PUT/로그아웃 경합은 외부 상태 변화 시뮬레이션을 위한 프로그램적 로그아웃 이벤트로 검사. |
| `tests/community-edit-delete-browser.mjs` | 기존29개 유지 + 수정 폼의 모바일 메뉴 이탈 취소/승인·입력 보존 검사1개 추가. |

기존 `AuthProvider.tsx`, `auth-session.ts`, `member-api.ts`의 1차 변경은 보존했으며 이번 표시 개선을 위해 다시 작성하지 않았다. 공통 Shell의 로고·헤더·푸터를 그대로 사용한다.

## 초점·스크롤과 이탈 확인

메뉴와 편집창은 native `dialog.showModal()`을 사용한다. 별도의 전역 focusin 강제 이동은 추가하지 않았다. 작성 중인 페이지가 이탈 확인 dialog를 열면 브라우저 모달 스택에서 그것이 최상단이 되므로 메뉴의 Tab 제한과 경쟁하지 않는다.

- 취소: 작성 내용·메뉴 유지, 기존 CommunityDialog가 메뉴 링크에 초점을 돌린다.
- 승인: NavigationGuard 알림 → 모달 닫기·스크롤 잠금 해제 → 기존 라우터 이동. 이전 화면 위치를 복원한 뒤 라우터가 새 화면의 위치를 관리하도록 한다.
- 일반 닫기: 저장한 본문 좌표와 메뉴 열기/프로필 수정 버튼의 초점을 복원한다. 모바일에서 PC로 넓어지면 패널·잠금을 해제한다.
- 모바일 편집창: 화면 높이·visualViewport 변경을 반영하고 내부 스크롤과 하단 버튼 접근을 제공한다. safe-area inset을 적용했다. 실물 iOS 키보드 동작은 미검증이다.
- 저장 중 닫기 버튼은 `aria-disabled`와 실행 가드로 닫힘을 막되 키보드 초점은 유지한다. 취소/입력/저장은 disabled 처리한다.
- 모바일 로그아웃 성공 시 패널을 닫고 바깥에 알림을 보여준다. 실패는 성공으로 바꾸지 않고 패널 안에서 안내하며, 패널을 닫아도 오류를 확인할 수 있다. 기존5초 타이머·오류 유지·알림 닫기 동작을 회귀 검사했다.

## 실제 실행한 검증

이 표는 **이번 UI 개선 작업에서 새로 실행한 결과**다. 이전 1차 문서는 그대로 보존했다.

| 명령/범위 | 결과 |
| --- | --- |
| `pnpm --dir web lint` | 통과, 경고0 |
| `pnpm --dir web test` |274/274 통과 |
| `pnpm build` | 프로덕션 빌드 통과 |
| `pnpm typecheck` | 통과 |
| `git diff --check` | 통과 |
| `node web/tests/member-menu-browser.mjs` | 프로덕션 Next + 모의 API15/15 통과 |
| `node web/tests/member-browser.mjs` | 프로덕션 Next + 모의 API20/20 통과 |
| `node web/tests/login-browser.mjs` | 기존 모의 로그인·로그아웃 회귀38/38 통과 |
| `node web/tests/community-edit-delete-browser.mjs` | SSR/브라우저 모의 API30/30 통과, pageErrors0 |
| `IYUM_RUN_MEMBER_LOCAL=true node web/tests/member-local.mjs` | 실제 Spring 확인4개 통과. 닉네임 원복 확인 포함 |

빌드·타입 검사는 기존8443 개발 서버의 `.next`를 덮어쓰지 않도록 `/private/tmp/iyum-mypage-build` 복사본에서 실행했다. 최종 `src`가 원본과 바이트 단위로 같은지 확인했다. 단위 검사 뒤 최종 숫자 간격 CSS만 추가 조정했으며, 최종 lint/build/typecheck와 메뉴15개 검사를 다시 실행했다. 관련 없는 전체 검사를 반복한 것은 아니다.

브라우저 실행 예시(작업 환경에서는 설치된 모듈의 절대 경로 지정):

```sh
PLAYWRIGHT_MODULE=<installed-playwright>/index.mjs BASE_URL=http://127.0.0.1:8451 MEMBER_ARTIFACT_DIR=<artifact-dir> node web/tests/member-menu-browser.mjs
PLAYWRIGHT_MODULE=<installed-playwright>/index.mjs BASE_URL=http://127.0.0.1:8451 MEMBER_ARTIFACT_DIR=<artifact-dir> node web/tests/member-browser.mjs
PLAYWRIGHT_MODULE=<installed-playwright>/index.mjs BASE_URL=http://127.0.0.1:8451 LOGIN_ARTIFACT_DIR=<artifact-dir> node web/tests/login-browser.mjs
PLAYWRIGHT_MODULE=<installed-playwright>/index.mjs BASE_URL=http://127.0.0.1:8452 COMMUNITY_ARTIFACT_DIR=<artifact-dir> node web/tests/community-edit-delete-browser.mjs
```

8452 Next는 `IYUM_API_INTERNAL_URL=http://127.0.0.1:8856`으로 SSR도 테스트 스크립트의 모의 API를 사용했다. 8451은 공개 조회만 기존 Spring 주소를 사용하며 회원/인증 변경은 브라우저에서 모의 처리했다.

실제 검증은8443/8082에서 비공개 시드 소유 관계를 확인한 테스트 회원34만 사용했다. 프로필·활동 개수, 실제CSRF PUT200, 프로필/헤더/auth/me 일치, 새로고침을 확인하고 **원래 닉네임으로 복원한200 응답까지 검증**했다. 신규 계정·게시글·댓글은 만들지 않았다. 닉네임의 정상 변경/복원으로 updatedAt이 갱신될 수 있다. 비밀번호·세션·토큰·실제 이메일은 캡처/기록에 포함하지 않는다. DB 직접 수정은 없다.

### 발견하고 바로잡은 사항

- 프로그램/마우스로 메뉴를 열 때 기존 activeElement에만 의존하면 초점 복귀가 빗나갈 수 있어, 메뉴 열기/프로필 수정 버튼을 명시적 복귀 대상으로 지정했다.
- 큰 활동 개수가 중앙 구분선에 가까이 붙던 배치를 내부 여백으로 보완하고 텍스트 범위가 그룹 경계를 넘지 않는 검사를 추가했다.
- 저장 중 aria-disabled 닫기 버튼은 자동화 도구가 정상적으로 클릭을 차단한다. 검사를 키보드 Enter로 실행하여 실제 핸들러도 닫힘을 거부하는지 확인했다.
- 최초 뒤로 가기 테스트의 full-document 진입은 앱 내부 history 경로 검증에 적절하지 않아 실제 메뉴→커뮤니티→글쓰기 링크 경로로 변경했다. 기존 이탈 검사를 삭제하거나 약화하지 않았다.
- Chrome 최초 실행은 샌드박스 제한으로 실패했으며, 허용된 브라우저 실행으로 검사를 진행했다.

## 캡처와 비교

모두 모의 회원 데이터로 촬영했다. 기존 시안의 가상 게시글은 서비스에 추가하지 않았다.

- [변경 전후 비교](mypage-ui-2026-10-20/comparison.html)
- `mypage-ui-2026-10-20/before/`: 이번 변경 전 실제 개발 화면1440/1024/390/320, 카드 내부 편집·기존 메뉴
- `mypage-ui-2026-10-20/after/`: 최종 프로덕션 화면, 모달/시트, 모바일 메뉴, 긴 정보·낮은 높이, 무JS 메뉴
- `results/`: 메뉴15개, 회원20개, 로그인38개, 게시글30개, 실제 Spring 결과를 구분
- `checks/`: 이번 lint·typecheck·build·단위 검사 기록

## 미검증과 남은 범위

- 실물 스마트폰/iOS Safari, 실제 소프트 키보드, 노치 장치의 safe area는 직접 검증하지 못했다. Chrome viewport1440/1024/390/320,320×360·740×320 회전,390×420 내부 스크롤과 모션 감소 환경을 확인했다. 이 결과를 실물 스마트폰 통과로 표현하지 않는다.
- 내 활동 목록/커서·사진 업로드·비밀번호/이메일 변경·탈퇴 등은 계속 후속 범위다.
- 기존 상세API 중복GET 및 무JS404 관련 기록은 별도다. 무JS **공개 메뉴**는 실제 프로덕션 브라우저에서 확인했지만, 무JS404 전용 재검사/재설계는 이번 작업에서 수행하지 않았다.
- 사용자 변경과 `iyum-preview` worktree는 보존했다. 커밋·push·병합·배포하지 않았다. 직접 시작한8451/8452 임시 서버만 정리하고 기존8443/8082는 유지한다.

## 직접 확인 순서

1. `http://127.0.0.1:8443/mypage`에서 로그인 → PC 회원 표시/마이페이지 링크 → 프로필 수정 → 저장/취소.
2.390 또는320px에서 메뉴 열기 → 오른쪽 패널의 회원 영역/주 메뉴 확인 → Escape/배경/닫기 → 원래 위치 확인.
3. 글쓰기 또는 수정 화면에 입력 → 메뉴의 IYUM 소개 → 계속 작성/수정 → 입력 유지 확인 → 다시 이동 승인.
4. 모바일 메뉴에서 로그아웃 → 같은 페이지를 유지하며 패널 밖 완료 안내와 개인정보 제거 확인.


## 2026-10-20 후속 검토

PC 정보 메뉴의 현재 페이지 선택과 1440px 캡처 이상을 재현·보완했다. [후속 검증 기록](MyPage-Menu-Review-Followup-2026-10-20.md)에 이번22개 검사 결과와 캡처 타이밍 근거를 별도로 남겼다. 위 최초 구현 기록과 당시 결과는 그대로 보존한다.
