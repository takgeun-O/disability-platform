# 댓글 등록 토스트 · 좋아요 404 복귀 테스트 (2026-10-26)

브랜치 `feat/community-post-like`, HEAD `7b0fe586886c64bd45d17ced58e781385b9c6de8`.
시작 시 이미 미커밋 좋아요 구현·읽기 전용 목록·댓글 이동·복귀 문구·접근성 보완이 존재했다. `/private/tmp/iyum-toast-start`에 src/tests 및 git status를 보존했다. 이번 변경은 아래 7개 코드/테스트 파일과 이 검증 기록뿐이다. 백엔드/DB/브랜드/제품 캐시 로직 변경 없음, 커밋·push·배포 없음.

## 변경과 근거
- `tests/post-likes-browser.mjs`: 기존 row999는 캐시에도 있어 완료 신호가 아니었다. 갱신된 목록의 aria-busy=false + row999 존재 + row1000 부재를 함께 기다린 뒤 기존 삭제 항목 부재 및 feed 조회 증가 assertion을 유지했다. 별도 gate 사례는 feed 요청 도달을 확인하고 응답을 보류한 동안 최종 조건이 false임을 확인한 뒤 명시적으로 해제한다. sleep/제품 캐시 변경 없음.
- `src/components/community/Comments.tsx`: POST 성공만 토스트 이벤트로 변경. inline notice는 조회/수정/삭제 용도로 유지. 서버가 확정한 성공에만 표시하며 navigation current 및 인증 generation/revision snapshot 검사. 로그아웃/재로그인 뒤 이전 성공은 표시하지 않는다. 등록 결과 receipt/초안 초기화/목록 갱신은 기존대로 유지.
- `src/components/community/CommentSuccessToast.tsx`: event id별 독립 컴포넌트, 5초 뒤 250ms opacity 퇴장. 부모 dismiss도 id를 비교하여 이전 콜백이 새 성공을 지우지 않는다. 닫기 버튼/초점 이동 없음. 지속되는 polite role=status 안에 이벤트별 텍스트를 삽입, inline 중복 성공 문구 제거.
- `src/lib/toast-lifetime.ts`: visible/exit 타이머 모두 cleanup, disposed guard. Strict Mode effect cleanup/replay 및 교체 시 이전 타이머 차단.
- `src/styles/community.css`: 기존 청록/남색 토큰, 화면 중앙 overlay, 좌우16px, pointer-events:none. opacity만 사용, reduced-motion은 transition:none. 기존 헤더는 실제로 relative이므로 현재 보이는 header bottom+12px과 safe-area를 기준으로 배치하고 scroll/resize/visualViewport resize에 갱신한다. 헤더를 fixed로 바꾸지 않았다.
- `tests/toast-lifetime.test.mjs`: fake timers 3개(5000+250ms, 연속 교체/Strict Mode 정리, unmount).
- `tests/community-comments-browser.mjs`: 기존50개 유지, 토스트6개 추가. 3너비 실제 표시/퇴장·스크롤/초점 불변·단일 status, fake-clock 연속 성공, 늦은 POST/logout, 화면 이탈 정리. 오류/후속조회실패/기존 늦은응답 검사에 토스트 조건도 보완.

기존 member/SuccessToast는 마이페이지 stylesheet/닫기 초점 복귀/이동 애니메이션에 결합되어 있다. 기존 기능을 바꾸지 않고 이번 작은 전용 컴포넌트를 사용했다. 새 의존성/전역 알림 구조 없음.

## 이번 실제 실행 결과
Node24, pnpm10.34.3.
- `pnpm --dir web lint`: 통과.
- `pnpm --dir web typecheck`: 통과. typegen의 next-env 경로 부수 변경은 시작 상태로 복원.
- `pnpm --dir web test`: 351/351 통과, 실패/skip 0. 새 타이머3 포함.
- `pnpm build`: 최종 소스를 별도 임시 디렉터리에 복사하여 통과. 최초 Turbopack 실행은 sandbox 포트 바인딩 오류; 같은 캐시 재시도에도 오류가 재현돼 로그 보관. webpack 빌드 통과 후 새 임시 디렉터리에서 기본 Turbopack 빌드도 통과. 운영/개발 서버 변경 없음.
- `BASE_URL=http://127.0.0.1:8464 LIKE_ARTIFACT_DIR=... PLAYWRIGHT_MODULE=... node web/tests/post-likes-browser.mjs`: 최종29/29, webpack 프로덕션. 일반/게이트404, 인증/오류/늦은응답, readonly 접근성, PC/모바일, 무JS 포함.
- `BASE_URL=http://127.0.0.1:8465 COMMUNITY_ARTIFACT_DIR=... PLAYWRIGHT_MODULE=... node web/tests/community-comments-browser.mjs`: 최종56/56, 최종 Turbopack 프로덕션. 조회/더보기/작성/수정/삭제/CSRF/초안/무JS 성공·빈목록·실패 포함.
- `node .../toast-position-browser.mjs` (동일 fixture를 추출한 별도 자료): 3/3. 1440/390/320 헤더 아래 위치, 좌우여백, pointer-events:none, 접근성 스냅샷의 단일 status 텍스트. 이 파일은 docs 폴더에 재현용으로 포함.
- `git diff --check`: 통과.

모든 브라우저는 mock API로만 실행했다. 실제 Spring/회원/DB 변경 없음. 개발8443 PID3115 보존; 직접 시작한8464/8465 및 mock 서버 종료.

초기 댓글 실행은 신규 assertion이 등록 성공+후속조회실패에도 토스트0을 기대해 1개 실패했다. 이는 테스트 작성 오류로, 등록 성공1과 조회 오류 공존을 검증하도록 수정했다. 초기 로그 `logs/comments-initial.txt` 보존; 최종 `comments/results.json` 및 `logs/comments.txt` 56개 전부 통과. 과거 검증을 이번 통과로 계산하지 않았다.

## 캡처와 한계
`comments/toast-visible-{1440,390,320}.png`, `toast-removed-*`, `toast-below-header-*`, `toast-accessibility-*`, `toast-position-*` 참조. 대표 캡처를 직접 열어 배치 확인했다. 320은 reduced-motion, 1440/390은 일반 모션으로 표시/퇴장 검사했다. 타이머3개는 Node fake time, 연속 성공은 Playwright clock, 3너비 퇴장은 실제 시간으로 검증.
실제 iPhone Safari/소프트 키보드 및 화면낭독기 낭독은 미검증이다. 접근성 트리/DOM 검사와 실기기 검증을 혼동하지 않는다. safe-area/visualViewport 대응은 있으나 실제 모바일 키보드 동작을 검증했다고 주장하지 않는다.
구형 `community-browser.mjs`, `community-topics-browser.mjs` 전체 이식은 요청대로 제외하고 이번에 실행하지 않았다. 이전 page/feed mock·문구 불일치는 기존 미해결 사항이다. 실제 계정 연동도 이번 범위에서 미실행.

## 재현
검증용 Next 빌드: `NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm build`.
likes 서버 internal8858, comments 서버 internal8856을 지정해 각각 `pnpm exec next start -p 8464` / `-p 8465`. `PLAYWRIGHT_MODULE`은 설치된 Playwright index.mjs 절대 경로, Chrome channel 사용. 각 mock 포트를 쓰는 suite는 순차 실행한다. 실제 로컬 계정용 테스트는 실행하지 않는다.
