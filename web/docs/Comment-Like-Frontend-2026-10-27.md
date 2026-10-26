# 댓글 좋아요 프론트 연동 — 2026-10-27

## 기준과 범위

- 시작/종료 브랜치: `feat/community-comment-like`
- HEAD: `64547e3763f509f8d8be515331e170ee991754c6` (댓글 좋아요 백엔드 완료)
- 시작 작업 트리는 clean. 기존 미커밋 변경은 없었으며 이번 변경만 미커밋으로 남겼다.
- 백엔드·의존성·브랜드·배포 설정은 변경하지 않았다. 커밋/스테이징/push/병합/배포 없음.
- 루트/web AGENTS 및 설치된 Next 16.3.6의 Server/Client Component 문서를 확인했다. 익명 서버 렌더링을 유지한다.

## 구현과 검토 시작점

1. `src/lib/community-comments.ts`: 기존 댓글 DTO에 필수 `likeCount`(0 이상 안전 정수), `liked`(boolean) 검사. 잘못되거나 없는 값은 거절한다. 브라우저 GET은 credentials include/no-store, 서버 GET은 omit. 커서 원문/기존 CRUD 201·204 처리 유지.
2. `src/components/community/CommentLikeButton.tsx` (신규): 댓글별 PUT/DELETE, 본문 없는 동적 CSRF 헤더, 200 응답 ID/형식 검증, 서버 확정 상태만 표시. 본인 선택은 채워진 빨간 하트. 네이티브 button/aria-pressed/상태별 이름/개수 및 처리 중 설명/44px 영역/키보드 초점. 오류는 해당 댓글에 표시하고 로그인은 기존 확인창과 안전한 복귀 경로를 이용한다. 자동 좋아요 등록과 자동 변경 재전송 없음.
3. `src/components/AuthProvider.tsx`: 기존 createPostLikes 상태 저장소를 댓글용 별도 인스턴스로 재사용한다. 게시글과 댓글 ID 공간은 섞이지 않는다. Provider 메모리만 사용하며 서버 전역·브라우저 저장소는 사용하지 않는다.
4. `src/components/community/Comments.tsx`: 회원별 첫 조회, 재조회/추가 조회/미리 읽은 버퍼에 인증 세대와 요청 순서 검사 적용. 새 버튼을 기존 댓글 아래에 배치. 등록 결과 보관/수정 초안/전환 확인/삭제 확인/토스트/무한 커서 조회 유지.
5. `src/styles/community.css`: 기존 게시글 좋아요 스타일을 사용하고 댓글 안의 여백과 오류 폭만 추가.
6. 기존 댓글 모의 DTO에 새 필드 추가: community-comments, community-feed, member-posts, member-comments, member-comments-fix, member-404-race 검사. 사유: 공개 댓글 응답 계약만 변경됐으며 내 댓글의 별도 API 계약은 변경하지 않음.
7. 신규 `tests/comment-likes.test.mjs`, `comment-likes-browser.mjs`, `comment-likes-local.mjs`.

## 경합·복구 방식

- 인증 revision/status/pending/memberId가 바뀌면 댓글 좋아요 저장소를 동기적으로 폐기한다. 같은 회원 재로그인도 다른 세대로 구분한다.
- 변경 요청은 회원 스냅샷 + navigation scope + 댓글별 operation token을 확인한다. 오래된 성공/401/403/404/finally는 새 상태에 적용하지 않는다. 다른 댓글 요청은 독립적이다.
- GET 시작 순번과 변경 완료 순번을 비교하여 오래된 조회가 새 좋아요를 덮어쓰지 못한다. 추가 조회는 새로 가져온 항목만 상태 저장소에 넣고 기존 배열의 오래된 상태를 재수용하지 않는다.
- 자동 조회가 입력 중 보관한 버퍼도 원래 read ticket을 유지한다. 인증 전환 때 버퍼를 폐기한다. 이전 버퍼/성공/finally가 새 회원의 pending/state를 변경하지 않는 단위 검사가 포함된다.
- SSR liked=false는 개인 상태의 근거가 아니다. 브라우저 개인 조회가 완료되기 전에는 상태 확인만 수행하고 PUT을 보내지 않는다. 이미 조회 중인 상태 확인은 추가 오류나 중복 GET을 만들지 않는다.
- 네트워크/500/손상된 성공 응답 및 404는 불확실 상태로 표시한다. 다음 명시적 클릭은 GET으로 확인하며 PUT/DELETE를 반복하지 않는다.
- 재조회는 이미 읽은 페이지 수를 서버 커서로 다시 읽고, 확인 대상 댓글이 아직 없으면 다음 커서를 더 따라간다. 첫 페이지의 부재로 뒤쪽 댓글 삭제를 단정하지 않는다. 모두 성공했을 때만 목록을 교체한다. 실패하면 기존 목록/초안을 보존한다.
- COMMENT_NOT_FOUND를 게시글 삭제로 취급하지 않는다. POST_NOT_FOUND도 댓글 영역에 구분 안내하며 강제로 화면을 이동시키지 않는다.

## 직접 실행 결과

Node 24.21.0 / pnpm 10.34.3. 아래 로그/JSON은 이번 최종 제품 소스 기준 실행이다.

| 검사 | 결과 |
|---|---|
| `pnpm lint` | 통과 |
| `pnpm typecheck` | 통과 |
| `pnpm test` | 379 통과, 실패/skip 0 (기존 351 + 신규 28) |
| `pnpm build` | 통과, Next 16.3.6 프로덕션 빌드 |
| `node tests/comment-likes-browser.mjs` | 25/25 통과, mock |
| `node tests/community-comments-browser.mjs` | 57/58 통과, 1 실패 (아래 기준 커밋 재현) |
| `node tests/post-likes-browser.mjs` | 최종 29/29 통과, mock |
| `IYUM_RUN_COMMENT_LIKE_LOCAL=true node tests/comment-likes-local.mjs` | 실제 Chrome/Spring 연동 1/1 통과 |
| `git diff --check` | 통과 |

검증 환경: 사용자 8443 개발 서버를 보존하기 위해 src를 `/private/tmp/iyum-toast-clean-build`에 동기화해 빌드. 빌드 명령은 `NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8856 pnpm build`. 프로덕션 8464→mock8856(댓글), 8465→mock8858(게시글 좋아요). browser 요청 8082는 Playwright route로 모두 mock 처리. `BASE_URL`, `COMMUNITY_ARTIFACT_DIR`/`LIKE_ARTIFACT_DIR`, `PLAYWRIGHT_MODULE`을 지정해 실행했다. 실제 연동 스크립트만 8443→8082 사용.

mock 검증: 서버 응답 수치/PUT·DELETE·중복 차단/다른 댓글 독립 처리/작성·수정 초안 보존/비로그인 복귀/오류 코드/불확실 결과 재조회/이전 GET/추가 조회/늦은 변경 응답/동일 회원 재로그인/다른 회원 GET/이동 후 복귀/확인 전 PUT 차단/1440·390·320px·키보드/무JS 실제 댓글·개수 가시성. 게이트로 요청 순서를 제어하며 긴 sleep으로 경합을 가리지 않는다.

### 기존 회귀의 실패와 테스트 보완

- 댓글 1건: `count POST/PUT/DELETE uses server list; back/link preserve context and drafts`는 수정 초안이 남은 상태에서 다른 댓글 삭제를 누르고 곧바로 `댓글 삭제하기`를 찾다가 timeout. 현재 UI는 먼저 초안 포기 확인을 요구한다. **64547e3 소스의 별도 프로덕션 빌드(8466)에서도 동일 실패를 직접 재현**했다. 기준 검사는 이 한 시나리오만 임시 사본으로 실행했다. 최종 파일에서 삭제/skip/기대값 완화를 하지 않았으며 이번 작업은 편집 전환 UX를 변경하지 않았다.
- 나머지 기존 댓글 검사는 초기 개인 조회 1회를 포함하도록 정확한 요청 수를 갱신했다. 타인/비로그인에서 버튼 0개를 기대하던 검사는 수정·삭제 버튼 부재를 명시한다(좋아요는 제공). 서버 댓글 지연 테스트의 브라우저 GET도 mock 처리하여 실제 DB에 접근하지 않는다.
- 게시글 좋아요 최초 전체 실행은 28/29. `duplicate blocked, other post independent through navigation, server count 17`의 mock PUT이 17/true를 반환하면서 mock 저장값은 2/false를 유지해 후속 GET이 서로 모순됐다. 최종 테스트는 PUT 성공 시 mock 저장값도 갱신하며 기존 중복·독립 처리·DOM·접근성 assertion을 모두 유지한다. 제품의 게시글 좋아요 코드는 변경하지 않았다. 수정 후 전체 29/29. 기준/현재의 단독 진단 실행은 통과하여 해당 실패를 기준 커밋에서 재현했다고 주장하지 않는다. 초기 실패 JSON도 보존했다.
- 신규 검사 중 확인된 초기화 버그: 개인 조회 진행 중 상태 확인 클릭의 undefined 반환을 실패로 오인하던 메시지 분기를 `ok === false`로 한정했다. 같은 게이트 시나리오가 최종 통과한다.

### 실제 Spring 검증

초기 8082는 새 likeCount/liked 필드가 없는 이전 응답이었다. 사용자 재시작 후 시드 5개 경계 게시글에서 댓글 61개가 새 계약을 만족함을 읽기 전용 확인했다.

그 후 전용 시드 계정으로 로그인하고 manifest에 등록된 댓글 ID, 작성자, `[개발 테스트]` 표식과 대상 게시글 소유를 확인했다. 원래 좋아요하지 않은 1개 댓글에서 실제 브라우저 PUT → 개인 GET → 익명 GET → DELETE → 개인 GET을 수행했다. 쿠키·CORS·CSRF 경로,200응답, no-store, 익명 liked=false, 개수 +1/원복을 확인했다. 테스트의 좋아요는 취소하여 원래 상태로 복원했다. 회원/게시글/댓글 생성·삭제·내용 수정 없음. 개인 intent/복원 receipt는 저장소 밖 시드 디렉터리에 보관했고 ZIP에 포함하지 않는다. DB 덤프/SQL/cleanup 없음.

## 한계와 남은 항목

- 기존 댓글 회귀 1건 실패가 남아 있으며 전체 회귀가 모두 통과한 것으로 보고하지 않는다.
- 위 실제 연동은 정상 등록·취소 경로이다. 오류·인증 경합·네트워크 단절의 실제 Spring 강제 재현은 하지 않았고 mock/단위 검사로 확인했다.
- iPhone Safari 실기기, 실제 소프트 키보드와 화면낭독기는 미검증. Chrome viewport/접근성 트리 검사와 구분한다.
- 404 대상 확인을 위해 매우 긴 댓글 목록은 여러 커서 요청이 필요하다. 조회 실패를 부재 확정으로 처리하지 않는다.
- 기존 다른 피드/마이페이지/404 브라우저 전체 스위트는 이번에 실행하지 않았으며, fixture만 최신 계약으로 맞췄다. 타입/단위/빌드 및 직접 관련 댓글·게시글 좋아요 검사를 실행했다.
- 오래된 문서/기존 테스트 기록은 덮어쓰지 않았다.

## 수동 확인

1. 로그인한 상세에서 댓글 하트를 클릭해 빨간 채움/서버 개수 변화, 재클릭 취소를 확인한다.
2. 다른 댓글에 수정 초안을 열어두고 좋아요를 변경해 초안·입력 위치가 유지되는지 확인한다.
3. 비로그인 하트 → 로그인 안내에서 취소/로그인을 확인한다. 로그인만으로 자동 등록되지 않는다.
4. 390/320px에서 버튼·오류·작성 영역의 넘침과 키보드 초점을 확인한다.

자료: `comment-like-2026-10-27/` 하위 mock(25개 결과·화면·접근성), crud(기존58개), post-like(초기/최종), actual(실제연동), baseline(비교), logs.
