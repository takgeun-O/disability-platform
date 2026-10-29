# 마이페이지 인라인 댓글 패널 디자인

브랜치 feat/mypage-activity-redesign, 시작 HEAD 63b12f4041e79d273cba1ed169383fa9ef34abb7. 시작 전부터 인라인 작성, 초안/오류 보완, 성공 기록, 토스트 및 재펼침 첫20개 정책이 미커밋 상태였다. 이를 보존했다. 커밋/푸시/백엔드/API/DB 수정 없음.

## 이번 변경 (3개 파일 + 이 문서)

- web/src/styles/mypage.css: 마이페이지 전용 댓글 영역을 옅은 중성 회색(#f7f8f9) 패널로 변경. 기존 border/primary/mist/ink 토큰 유지. PC 외부 들여쓰기28px, 내부20/24px, 모바일 외부8px(360px 이하4px), 내부12/14px(좁은 화면12px). 얇은 단일 테두리, 12~14px 모서리, 그림자 없음. 기존 세로선 제거. 작성창 흰색, 정렬/새로고침 도구 양끝 배치와 wrapping, 긴 닉네임 줄바꿈, 하단 접기 구분선. 펼침 버튼의 aria-expanded=true 상태에 기존 브랜드색 강조.
- web/src/components/member/InlineComments.tsx: 하단 댓글 접기 버튼 추가. 기존 switchOpen을 호출하므로 조회 취소/소유권 무효화/선택 정렬 유지/첫 페이지 재조회 정책은 동일. 접힌 DOM 반영 뒤 useLayoutEffect에서 해당 펼침 버튼으로 preventScroll 포커스 복원. 고정 헤더 아래 가시 범위를 벗어난 경우에만 필요한 거리만 instant 스크롤. 타이머/추가 애니메이션 없음. 사용자 스크롤을 나중에 덮는 지연 작업 없음.
- web/tests/inline-comments-browser.mjs: 1440/390/320에서 복수 펼침, 빈 목록, 긴 닉네임/본문, 작성 오류/조회503 유지, 가로넘침, 하단 접기 키보드와 포커스/가시성, 초안 재복원, 접기 중 GET0/재펼침 첫조회1회, 상세 패널 격리 확인. 캡처 포함.

CommentItem/상세 Comments/댓글 API/정렬/등록/좋아요/캐시의 제품 로직은 변경하지 않았다. 새 영구 등록 표시나 제거했던 제목/안내/상세링크를 추가하지 않았다. 현재 성공 안내는 기존 상단 토스트(미리보기 포함) 그대로다.

## 검증

Node24/pnpm10.34.3. 실제 Spring·회원·DB 쓰기 없음. 프로덕션 Next + 브라우저 mock API, 별도8467/8466, mock8858/8856 사용. 사용자8443 유지.

- pnpm test: 422/422 통과
- pnpm lint: 통과 (최종 테스트 구성 재실행 포함)
- pnpm typecheck: 통과 (격리 빌드 복사본)
- NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm build --webpack: 통과. 직전 환경의 Turbopack 포트 바인딩 제약을 고려해 Webpack으로 실행. 이번에 기본 Turbopack 빌드를 재실행하지 않음.
- zsh review/run-browser-local.sh tests/inline-comments-browser.mjs 8467 inline-final: 77/77 통과
- zsh review/run-browser-local.sh tests/community-comments-browser.mjs 8466 detail: 76/76 통과
- git diff --check: 통과

초기 inline 실행은 신규 테스트가 공통 check helper 내부에 들어간 테스트 배치 실수로 반복 실행되어 중단했다(exit130). 초기 로그를 보존하고 검증 통과 수로 합산하지 않는다. 제품 변경 없이 테스트 배치를 바로잡고 전체를 재실행했다.

스크린샷: panel-* 화면, panels-full-* 복수 패널 전체, panel-error-* 작성오류, 기존 sorting/expanded/toast 회귀 캡처. 실제 화면낭독기/실기기Safari/소프트키보드 미검증. 별도 알려진 상세 로그아웃 실패 스크롤 간헐 문제는 이번 결과와 원인 해결을 구분한다. 별도 comment-like 취소 GET 대기 검사는 재실행하지 않았다.

## 수동 확인

마이페이지 내 게시글에서 여러 댓글 버튼을 연다. 각 게시글 아래 들여쓴 회색 패널이 입력·정렬·목록을 감싼다. 입력한 초안을 유지한 채 하단 접기를 누르면 해당 펼침 버튼에 포커스가 돌아간다. 화면 밖이면 헤더 아래로 필요한 만큼만 이동한다. 다시 열면 초안/정렬은 유지되고 댓글 첫20개부터 조회한다. 더 보기·등록·좋아요 및 상세 화면 디자인은 기존 동작을 유지한다.

## 패키지

start-to-current.patch는 이번 작업만, HEAD-to-current-tracked.patch는 기존 미커밋 구현 포함. src 전체/테스트/설정/lock/AGENTS/미추적 신규 실제 파일/작업전후status/검증로그·JSON·캡처/SHA-256 포함. .env/실제 계정·쿠키·토큰/node_modules/.next/.git/DB/이전ZIP 제외. mock-only 값은 테스트fixture다. 포함된 이전 작업 보고서의 수치는 이번 검증 결과가 아니다.
