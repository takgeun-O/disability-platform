# IYUM 댓글 좋아요 후속 보완 검토 — 2026-10-27

## 기준과 범위
- 실제 브랜치: `feat/community-comment-like`
- 실제 HEAD/기준: `64547e3763f509f8d8be515331e170ee991754c6`
- 시작 당시 댓글 좋아요·댓글 버튼 배치·새로고침 토스트 및 관련 테스트가 미커밋/미추적 상태였습니다. 그대로 보존했습니다.
- 이번 제품 변경은 `web/src/features/PostList.tsx`만입니다. 백엔드/DB/브랜드/마이페이지 댓글 개수/댓글 폐기 확인 UX를 변경하지 않았습니다. 커밋·push·병합 없음.
- `review/existing-at-followup-start.patch`, `review/git-status-followup-start.txt`: 기존 작업.
- `review/followup-only.patch`: 이번 보완만(제품1파일, 테스트2파일). 신규 검증 문서/캡처/로그는 원래 web/docs 경로에 별도 포함.
- `review/64547e3-to-current.patch`: 기존 작업을 포함한 누적 변경 및 신규 소스/테스트/문서. `review/untracked-files.txt`와 실제 파일도 포함.

## 1. 오류 표시 직후 위치 이동
재시도 전 첫 실패 시점의 문제입니다. 복귀 캐시60개와 앵커 복원 뒤 브라우저 feed GET을 gate로 보류했습니다. 955번의 top120px를 확인한 후503을 반환하고 응답 finished, 오류 DOM, aria-busy=false를 기다렸습니다.

시작본은 오류 안내 삽입 전에 앵커를 저장하지 않았습니다. 기존 retryPosition은 오류를 지우기 전 위치를 저장하지만, 다음 요청 성공까지 복원을 미뤄 사용자의 새 스크롤도 덮을 수 있었습니다.

수정: 상태 변경 직전 현재 앵커를 저장하고, `useLayoutEffect`에서 오류/로딩 DOM 변경과 같은 commit에 즉시 보정합니다. 네트워크 완료까지 앵커를 잡아두지 않습니다. 재조회 성공 시에는 그때의 현재 위치를 기존 누적 목록 복원에 사용합니다. 요청 valid 검사, navigation scope, cache 소유 및 커서 병합은 유지했습니다. 새로운 타이머/지연 복원을 추가하지 않았습니다.

| 실행 | 결과 |
|---|---|
| 시작본 + 제어된503 3회 | 3회 모두 실패,120 →260.09375px |
| 수정 후 같은 독립 검사3회 | 3회 모두 통과 |
| 최종 전체 피드 내 독립 초기화3회 | 3회 모두 통과; 오류 표시/오류 제거/재조회 후 위치 확인 |
| 세 번째 재시도 중 사용자 wheel | 기대-60.296875px, 실제-61.296875px; 기존10px허용 기준 그대로 |

고정 sleep이나 실패 재시도를 추가하지 않았습니다. 실패를 숨기지 않고 `before/`, `after-target/`, `community-feed/`의 JSON·PNG·로그에 남겼습니다. 피드28개는 기존26개 중 해당 시나리오를3회로 늘린 결과입니다. 원문 행/개수/첫ID1000/60개유지/새1001미삽입을 계속 검증합니다.

**원인 귀속 한계:** 이번 작업 시작본에서 재현된 기존 결함임을 확인했습니다. 댓글 좋아요 도입 전 전체 코드로 이 시나리오를 재실행하지 않았으므로 좋아요 신규 회귀라고 단정하지 않습니다.

## 2. 댓글 수정 폐기 확인과 테스트 정합성
기존 테스트는 편집 중 다른 댓글 삭제 클릭 후 바로 ‘댓글 삭제하기’를 기다려 시간초과했습니다. 시작 테스트를 별도 실행해 같은 실패를 확인했습니다(`comments-before/`, logs/comments-before.log). 실제 첫 단계는 ‘수정한 내용을 버릴까요?’입니다.

`web/tests/community-comments-browser.mjs`의 기존 count POST/PUT/DELETE 시나리오에서:
- ‘계속 수정’: 수정 초안/새 작성 초안 유지, DELETE 없음, 최종 삭제 버튼 없음.
- 다시 삭제 클릭→‘수정 내용 버리기’: 기존 편집칸 종료, 작성 초안 유지, 아직 DELETE 없음.
- 최종 ‘댓글 삭제하기’: 정확히 DELETE1회, 작성 초안 유지, 폐기한 편집칸 복원 안 함.
- 기존 POST/PUT/DELETE 후 서버 목록 개수2→1, 주제/QUESTION 필터, 뒤로/링크 복귀 및 위치 검증 유지.

이 실패는 테스트와 기존 정상 UX의 불일치입니다. 좋아요 회귀로 분류하지 않았고 제품 모달을 수정하지 않았습니다.

## 이번에 직접 실행한 최종 검사
Node24.21.0, pnpm10.34.3, 설치된 Next16.3.6, Chrome headless/Playwright. 모두 실제 실행이며 UP-TO-DATE 재활용 결과가 아닙니다.

| 명령/스위트 | 결과 |
|---|---|
| pnpm lint (테스트 최종 수정 뒤 재실행 포함) | 통과 |
| pnpm typecheck | 통과 |
| pnpm test |379/379, 실패/skip0 |
| pnpm build (격리 검증 복사본) | 통과 |
| comment-likes-browser.mjs |25/25 |
| post-likes-browser.mjs |29/29 |
| community-comments-browser.mjs |62/62 |
| community-feed-browser.mjs |28/28 |
| git diff --check | 통과 |

수정 전 실패3회와 기존 댓글 테스트 실패1회는 원인 재현 결과이며 최종 실패와 구분합니다. 전체 테스트는 최종 제품 코드로 실행했고 피드 전체는 사용자 스크롤 gate 검증까지 추가된 최종 테스트로 실행했습니다.

별도 알려진 `logout failure: detail URL/body, comment drafts, focus and dirty protection remain`는 이번 전체 실행에서 통과했습니다. 코드/허용오차를 바꾸지 않았습니다. 이전 간헐 실패가 영구 해결되었다는 의미는 아니므로 후속 관찰 대상으로 유지합니다.

## 재실행
```sh
cd web
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm test
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8856 pnpm build
IYUM_API_INTERNAL_URL=http://127.0.0.1:8856 pnpm exec next start --hostname 127.0.0.1 --port 8464
# 별도 터미널, 아래3개는 mock8856을 공유하므로 순서대로 실행
BASE_URL=http://127.0.0.1:8464 node tests/comment-likes-browser.mjs
BASE_URL=http://127.0.0.1:8464 node tests/community-comments-browser.mjs
BASE_URL=http://127.0.0.1:8464 node tests/community-feed-browser.mjs
# 게시글 좋아요는 다른 mock포트용 서버 필요
IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm exec next start --hostname 127.0.0.1 --port 8465
BASE_URL=http://127.0.0.1:8465 node tests/post-likes-browser.mjs
```
Playwright 설치 경로가 별도이면 PLAYWRIGHT_MODULE을 지정합니다. 이번 실행은 `/Users/tackeunoh/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs`를 사용했습니다. COMMUNITY_ARTIFACT_DIR/LIKE_ARTIFACT_DIR로 결과 경로를 지정했습니다. 기존8443 서버를 피하기 위해 `/private/tmp/iyum-toast-clean-build`에 최종 src를 동기화해 위 환경값으로 빌드/실행했습니다. 제품 소스와 빌드 사본의 일치를 확인했습니다.

수정 전/후 단독 실행은 임시 사본 check함수에서 해당 시나리오만 선택했습니다. 최종 저장소 파일에는 필터/skip을 남기지 않았습니다. 최종 전체 피드 자체가3개의 독립 fixture 반복을 실행하므로 추가 도구 없이 재현할 수 있습니다.

수동: 피드60개→955진입→댓글등록→복귀 feed503→오류 표시 후에도 원래 행 위치 유지→재시도→대기 중 스크롤→완료 후 새 위치 유지. 댓글: 작성 초안+수정 초안→다른 댓글 삭제→계속 수정/수정 내용 버리기 두 분기 확인.

## 한계와 보존
- 모든 이번 브라우저 검증은 모의 API입니다. 실제 Spring 연동 미실행, 실제 계정/DB 변경 없음.
- PC/390/320 Chrome viewport, 무JS 및 관련 기존 회귀는 각 JSON에 기록했습니다. 실물 iPhone Safari/소프트키보드/화면낭독기 미검증.
- 현재 최종 검사에 남은 실패 없음. 기존 로그아웃 스크롤 간헐성은 관찰 필요.
- 이전 검증 문서는 덮어쓰지 않았으며 과거 실제 연동 기록을 이번 실행으로 간주하지 않습니다.
- 기존 개발 서버는 보존하고 이번8464/8465 검증 서버만 정리했습니다.

## 패키지
최신 web/src 전체, tests/helper, public, package/lock/config/patch/AGENTS, 관련 문서·최종 로그·화면·작업 전후 상태를 원래 상대 경로로 포함합니다. 백엔드 참조는 읽기 전용 계약 검토용이며 이번 변경은 없습니다.
.git/node_modules/.next/build/cache/.env/실제계정자료/세션·토큰/인증서/DB덤프/기존ZIP은 제외합니다. mock의 example.test 및 test-only 값은 실제 비밀이 아닌 테스트 fixture입니다. 원본에 마스킹 수정은 하지 않았습니다. ZIP CRC, 최신 파일 해시, 신규 파일 누락 여부를 확인합니다.
