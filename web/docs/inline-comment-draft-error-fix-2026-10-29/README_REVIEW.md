# 인라인 댓글 초안 보존·상세 등록 오류 위치 보완

## 기준과 범위
- 브랜치 feat/mypage-activity-redesign, 시작 HEAD 63b12f4041e79d273cba1ed169383fa9ef34abb7.
- 작업 시작부터 인라인 작성/초안 보호/토스트/상세 작성란 이동 등 미커밋 변경이 존재했습니다. 그대로 보존했으며 이번에는 아래 두 문제만 보완했습니다.
- 백엔드/DB/API/브랜드/실제 계정 수정, 커밋/푸시/병합/배포 없음. 사용자 개발 서버를 종료하지 않았습니다. 직접 시작한8466/8467 서버만 종료했습니다.

## 1. 페이지 누락은 삭제 증거가 아님
이전 MyActivity 재조회는 이전 items에 있었지만 새 items에 없는 게시글을 commentDrafts.prune으로 정리했습니다. 새 글이 앞에 추가되어991번이 다음 페이지로 밀리는 경우에도 미등록 입력이 소실됐습니다.

변경:
- `web/src/lib/comment-drafts.ts`: prune 제거. `discardDeleted(memberId, postId)`는 소유자가 일치하며 명확한 삭제 근거가 있는 호출만 정리합니다. 다른 게시글 초안/다른 회원 초안은 그대로입니다.
- `MyActivity.tsx`: 목록 재조회에서 초안 삭제하지 않음. 화면에 없는 초안도 provider 저장소와 전체 이탈 보호에 남고, 다음 페이지에서 다시 렌더링되면 복원됩니다. 목록 조회/커서/정렬은 그대로입니다.
- `MemberPostActions.tsx`: 실제 DELETE 성공 또는 status404+POST_NOT_FOUND에서만 대상 초안 폐기. 네트워크 실패/불확실한 결과/일반404는 폐기하지 않습니다. 기존 session.isCurrent 이후에만 적용합니다.
- `InlineComments.tsx`: 현재 소유 GET의 status404+POST_NOT_FOUND 확인 시 대상 초안 폐기. 기존 요청/인증/게시글별 버전 검사 유지.
- `InlineCommentForm.tsx`: 현재 인증 세션의 작성 응답이 status404+POST_NOT_FOUND이면 대상 초안 정리. 이미 보내진 요청의 화면 이탈 후 결과는 기존 정책대로 인증 수명을 검사합니다.
- 등록 확정 성공의 해당 입력 초기화, 승인된 화면 이탈/확정 로그아웃/새 로그인의 전체 폐기 정책은 기존 유지합니다. 단순 인증 revision/로그아웃 실패는 폐기 근거가 아닙니다.
- 삭제 확인을 위한 추가 조회/폴링은 도입하지 않았습니다. 목록에서 빠졌지만 삭제가 확인되지 않은 초안은 승인된 이탈/인증 종료까지 유지할 수 있습니다.

## 2. 등록 오류는 작성창에서 확인
`Comments.tsx`의 공유 error는 아래 목록 끝에 렌더링되어, 상단으로 옮긴 작성창에서 멀어졌습니다.

- POST 오류는 별도 createError에 저장하고 작성 textarea 바로 아래 지속적인 role=alert 영역에 표시합니다. textarea aria-describedby에 해당 error ID를 연결합니다.
- PUT/DELETE 오류는 기존 error 및 수정/삭제 UI 경로를 유지합니다. POST 작업 시작/검증/실패는 등록 오류만 정리하거나 갱신합니다.
- 필드 오류는 기존 fields와 입력 아래 표시, 빈 입력은 기존 주의 토스트, GET 오류는 readError와 목록 재시도 영역으로 구분합니다.
- 불확실 결과의 안내는 작성창 근처에 계속 표시하며 자동 POST 재전송하지 않습니다. 위치에 맞춰 '위의 목록' 표현을 없앴습니다.
- 성공 후 GET 실패는 createError가 아니라 목록 오류이며, 성공한 초안을 되살리지 않습니다. 기존 CSRF/인증/화면 유효성 조건을 변경하지 않았습니다.

## 실제 검증 결과
실행 Node24.21.0 / pnpm10.34.3. Spring 대신 프로덕션 Next + 제어 가능한 mock API, 실제 DB 데이터 변경 없음.

수정 전:
- 시작 시점 src와 기존 검증 빌드 src를 파일 바이트 기준 비교하여 차이 없음 확인.
- boundary-before: 0/1. 새 첫 페이지에991이 없어진 뒤 초안 보호 확인창이 뜨지 않아 실패.
- error-before: 0/1. 작성창 근처 등록 오류 영역이 없어서 실패.
- 외부 임시 테스트 사본에서 해당 시나리오만 선택했으며 최종 저장소 테스트에는 필터/skip 없음. baseline source snapshot은 start-to-current.patch의 이전 내용으로 확인할 수 있습니다.

최종 코드:
- `pnpm test`: **417/417 통과**.
- `pnpm lint`: 통과.
- `pnpm exec tsc --noEmit`: 통과.
- `pnpm typecheck`: 동일 최종 src의 별도 실행 폴더에서 next typegen + tsc 통과.
- `pnpm build`: 동일 최종 src의 /private/tmp/iyum-inline-build에서 통과. 기존 개발 .next에 덮어쓰지 않음.
- `zsh /private/tmp/run-draft-error.sh tests/inline-comments-browser.mjs 8467 inline`: **61/61 통과**.
- `zsh /private/tmp/run-draft-error.sh tests/community-comments-browser.mjs 8466 detail`: **76/76 통과**.
- `git diff --check`: 통과.

새 테스트:
- 단위: 페이지 경계 누락 시 저장소 유지/다른 게시글 성공 초기화/명시적 삭제 및 소유자 검사/확정 인증 변경/승인 이탈 정리.
- 인라인5개: gated POST 후 새 글1001이 추가되어991이 경계 밖으로 이동 → 현재 목록에서 사라져도 이탈 취소 → 더 보기 → 원문 초안 복원; DELETE 성공/POST_NOT_FOUND/일반404/네트워크실패 각각의 폐기 여부. 기존 로그아웃 실패/성공/같은 회원 재로그인/다른 회원 지연응답 검사도 재실행.
- 상세14개:1440/390/320 각각 CSRF준비실패/403권한/네트워크/손상된201의 gate 해제 후 오류 표시. 댓글40개 로드, 작성란 근처에서 제출하며 오류 locator로 스크롤하지 않음. 실제 rect로 textarea 아래60px 이내, viewport 안, 가로 넘침 없음, scrollY 동일, 초안 유지, alert1개, POST0/1회 검사. PUT/DELETE 오류가 create-error에 들어가지 않는2개 검사.
- 기존 성공 후 GET실패/초안/등록 및 수정/삭제/토스트/자동조회/정렬 관련 suite 내 검사도 보존·실행.

## 알려진 항목과 한계
- 상세 logout failure 스크롤 유지 검사는 이번에는 통과했습니다. 간헐 문제의 원인을 별도 분석하여 해결한 것은 아니므로 알려진 후속 항목을 닫지 않습니다. 이번에는 그 뒤의 요청 수·이탈 보호 assertion까지 실행됐습니다.
- 댓글 좋아요의 취소된 이전 GET 응답 대기 실패는 이번 실행 범위에 포함하지 않았습니다. 이전 작업에서 확인된 항목이며 이번 결과로 통과했다고 주장하지 않습니다.
- 실제 Spring/CORS/DB, 실제 모바일 Safari·소프트 키보드·화면낭독기 미실행. Chromium viewport와 DOM 접근성 연결 검사로 한정합니다.
- 새 댓글 별도 표시/실시간 감지/등록 후 위치 강조는 이번 범위에 추가하지 않았습니다.

## 파일과 실행 안내
이번 제품 변경6개: comment-drafts.ts, MyActivity.tsx, MemberPostActions.tsx, InlineComments.tsx, InlineCommentForm.tsx, Comments.tsx.
테스트3개: comment-drafts.test.mjs, inline-comments-browser.mjs, community-comments-browser.mjs.

web에서 pnpm install --frozen-lockfile 후 lint/typecheck/test/build. 브라우저 검사는 Playwright+Chrome 필요. 공개 API base http://127.0.0.1:8082, SSR mock은 인라인8858/상세8856. 각 테스트가 mock 서버를 시작하며 Next는 별도8467/8466 포트에서 IYUM_API_INTERNAL_URL을 해당 mock origin으로 설정합니다. 로컬 실행 스크립트의 절대 경로는 검토 환경에 맞게 조정해야 합니다.

## 검토 자료
- review/start-to-current.patch: 이번 작업 시작 대비 신규 파일 포함 차이.
- review/HEAD-to-current-tracked.patch: 이전 미커밋 작업 포함 HEAD 대비 추적 파일 차이.
- 최신 web/src 전체 및 tests, public, 설정/lockfile/patch, AGENTS 제공.
- 시작·종료status, HEAD/브랜치, 신규 파일 목록, 변경 파일목록 제공.
- review/evidence: 최종 검사 로그/JSON, 수정 전 실패 로그, 대표 오류 화면. 스크린샷은 모의 회원/댓글만 사용합니다.
- 제외: .env/실제 인증정보·세션·쿠키·토큰/DB덤프/node_modules/.next/.git/빌드 산출물/이전ZIP.
- 포함 파일 SHA-256 목록과 ZIP CRC 및 원본 소스 일치 검사를 수행합니다.
