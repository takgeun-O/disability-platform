# 상세 진입 스크롤·시드 댓글 소유 기록 — 2026-10-19

## 기준과 범위

- `feat/community-post-cursor`, HEAD `2adf11bff2e58394899d33157aa0a556df3306fa`.
- 기존 미커밋/미추적 피드·댓글·무JS 메뉴 작업을 보존했다. API·DB 스키마·인증 계약은 변경하지 않았다. 아래 검증용 DB 행 조치만 수행했다.
- 상세 API 중복 GET, 무JS 404 렌더링 재설계는 제외했다. 기존 기록의 알려진 제약을 이번 작업에서 해결했다고 주장하지 않는다.

## 변경

- `src/index.css`: 전역 `html` smooth→auto. Next의 기존 상세 진입 스크롤이 즉시 동작하게 했다. 렌더/effect마다 scrollTo를 추가하지 않았다. 명시적 해시는 유지한다.
- `src/lib/reading-focus.ts`의 필요한 in-page smooth/reduced-motion 처리와 `PostList.tsx`의 instant 앵커 복원은 그대로 유지한다.
- `tests/community-feed-browser.mjs`: 일반 모션(`no-preference`) 1440/390, 누적60개 하단 글 클릭, 상세가 DOM에 나타난 첫 RAF부터4프레임 이상 scrollY=0, 링크/뒤로 복귀 위치·60개 유지, 해시/댓글 새로고침 위치 검사를 추가했다.
- `tests/helpers/seed-ownership.mjs`: manifest 기반 대상·회원·표식 검증, 배타적 lock, POST 전 UUID 저널, 유효 ID 즉시 저널/manifest 저장, 기존 ID union 및 원자적 교체, 논리 삭제 후 소유 유지.
- `tests/community-comments-ux-local.mjs`, `community-feed-seed-local.mjs`: 공유 helper 사용. 고정14~18 및 고정 댓글 총수 대신 검증된 manifest 사례/현재 조회값 사용. 생성·소유 등록·논리 삭제를 결과에서 구분한다. 새로 관찰된 사용자 댓글은 소유로 편입하지 않는다.
- `tests/seed-ownership.test.mjs`: 기록 실패/중단, 변경된 ID, 중복 기록, lock, 잘못된 ID/대상 거부 등6개 추가.
- `server/iyum-api/tools/community-seed/CommunitySeed.java`, `tools/README.md`: 운영 서비스 밖의 수동 개발 도구. atomic manifest, 공유 lock, `dry-run`/`recover`, 명시적 case IDs 및 별도 `IYUM_SEED_DIR`. 기존 작성자·게시글·표식 검사와 미등록 연관 데이터 거부 유지.

## 소유 기록·복구

POST 전에 `[개발 테스트] ... [run:UUID]` 내용과 post/member를 비공개 저널에 기록한다. 201의 안전한 양수 ID 확인 직후 저널에 ID→기존 manifest와 union→등록 완료 플래그 순서로 기록한다. 논리 삭제 후에도 소유 ID는 제거하지 않는다. 파일 갱신 실패 시 정리 성공으로 표시하지 않으며, 저장 전 intent 또는 수신 ID로 복구할 수 있다.

강제 중단 시 프로세스 종료를 확인하고 남은 lock을 수동 처리한 뒤 `recover`를 실행한다(도구 README 명령). 정확한 본문/UUID·작성자·게시글이 하나의 행과 일치할 때만 manifest에 편입한다. 논리 삭제 행도 조회한다. 임의의 연결 댓글을 전부 인수하지 않는다. 이어 `dry-run`으로 소유 집합과 실제 연관 데이터가 일치하는지 검사한다. 진행 중 요청의 결과를 기다리지 않은 상태에서 복구 완료라고 단정하지 않는다. 결과 JSON을 잃은 강제 종료에서는 저널+manifest가 근거이며, 과거 결과를 덮어써 성공으로 바꾸지 않는다.

## 개발 DB 조치

1. 이전 `community-comment-ux-2026-10-18/actual/first-run-results.json`의117·118 및 `actual/results.json`의119·120 기록을 현재 DB와 대조했다.
2. 네 행 모두 작성자34(시드 첫 계정), 대상14(시드 소유·작성자34), 본문이 각각 이전 테스트의 ‘댓글 UX 실제 검증 — 첫 등록/연속 등록’과 정확히 일치, deleted_at 존재를 확인했다. 표식은 없었다.
3. 위 조건/행수4를 트랜잭션에서 재확인하고 **117~120만 물리 삭제**했다. manifest에 허위 표식을 추가하거나 다른 댓글을 수정하지 않았다. 이전 DELETE는 논리 삭제였고, 이번 조치는 남은 행의 한정 물리 정리다.
4. 새 실제 테스트 댓글125·126: 실제 API201→즉시 manifest/저널 기록→DELETE204로 **논리 삭제**. DB 행과 `[개발 테스트]` 표식·소유 기록 유지 확인. 시드 게시글85개 유지.
5. 116은 대상18, 작성자21, 표식 없음, 미삭제다. 시드 작성자가 아니므로 소유 편입/삭제/수정하지 않았다. 최종 개발 DB dry-run은 `Additional data references seed accounts/posts; cleanup refused`로 거부했다. 이 결과는 보호 장치의 정상 동작이며 ‘개발 DB cleanup 가능’으로 보고하지 않는다. **개발 DB 전체 cleanup 미실행**.

## 직접 실행한 검증

| 구분 | 결과 |
| --- | --- |
| `pnpm lint` | 통과 (src/tests) |
| `pnpm test` | 258/258 통과, 새 helper6개 포함 |
| `pnpm typecheck` | 통과 |
| `pnpm build` | 통과 |
| `git diff --check` | 통과 |
| 프로덕션 Next + 모의 API `community-feed-browser.mjs` | 22/22 통과. 실제 Spring 검증 아님 |
| 기존8443/8082 실제 Spring `IYUM_RUN_LOCAL_COMMENT_UX=true ... community-comments-ux-local.mjs` | 9개 통과. 실제 세션, cursor, 현재0/1/20/22 경계, 새 댓글125·126, 소유 유지, 로그아웃/초안/모달 |
| 격리 PostgreSQL+Java 도구 | 6개 통과. 생성ID를 member501/post1001부터 시작, ID 없는 intent로 논리 삭제 댓글 복구, unknown 댓글 연결 시 dry-run과 실제 cleanup 모두 거부 및85개 유지, 검증용 unknown 제거 후 전체cleanup 성공. 임시 DB 폐기 완료 |
| 개발 DB dry-run | 미등록 연관 댓글로 예상된 거부. 데이터 변경 없음 |

실제 검사 최초 실행은 대표 글 댓글 수를65로 고정한 과거 가정 때문에66에서 중단되었고 생성은0건이었다. 현재 API의 개수를 기준으로 변경한 후9개 모두 실행했다. 과거65를 맞추기 위해 사용자 댓글을 삭제하지 않았다.

모의 브라우저 최초 실행은 임시 프로덕션 서버의 `IYUM_API_INTERNAL_URL` 누락으로 실제 API를 읽어 중단됐다. mock8856으로 명시 설정한 실행의22/22를 최종 결과로 기록한다. 통과시키기 위해 화면 조건을 약화하지 않았다.

빌드/typecheck는 기존 개발 `.next`와 서버를 건드리지 않도록 외부 임시 복사본에서 실행했다. 운영 `src` 전체가 원본과 바이트 단위로 동일함을 확인했다. 원본에서 lint/unit 실행. `community-feed-seed-local.mjs`는 공통 helper 적용·동적 기준 수정 및 문법/lint 확인했고, 전체 실제 브라우저 실행은 이번에 반복하지 않았다. 실제 생성/기록/삭제는 UX 스크립트로 검증했다.

## 캡처와 제한

`community-scroll-seed-2026-10-19/feed/detail-instant-{1440,390}-{link,back}.png`는 모의 데이터의 실제 프로덕션 브라우저 캡처다. 첫 표시 위치는 정지 캡처뿐 아니라 RAF 검사로 확인했다. 동일 폴더 results.json, actual/results.json, isolated-results.json에 실행 결과를 구분했다. 개인 계정 파일·저널·DB 덤프·비밀값은 문서에 복사하지 않았다.

댓글 CRUD·상세 갱신·늦은 응답·목록 복원은 기존 피드 회귀22개에 포함되며, 새 프레임 단위 검사는 상세 진입·해시·댓글 새로고침에 집중했다. 모든 인증/댓글 조합의 프레임별 위치를 전부 측정한 것은 아니다. 전체 인증 브라우저 검사와 무JS404 전용 검사는 범위 밖이라 재실행하지 않았다. 실제 휴대폰 기기는 사용하지 않았고 Chrome1440/390/320 viewport로 확인했다.

직접 확인: 목록을60개 이상 불러온 뒤 하단 글 제목 클릭→처음부터 상세 상단→목록으로/뒤로→동일 길이와 읽던 위치. 댓글 영역에서 새로고침/로그아웃해도 경로와 초안 보존. 의도된 P03 내부 이동 코드는 변경하지 않았다.

임시 검증8451 서버만 종료하고 기존8443/8082를 보존한다. 커밋·push·병합·배포 없이 변경 상태로 남긴다.

## 사용자 재보고 후 8443 실제 화면 재검증

이전 완료 보고는 별도 프로덕션 검증 결과만으로 사용 중인 개발 서버의 CSS 반영을 확인하지 못했다. 새 Chrome 컨텍스트에서도8443의 computed scroll-behavior는 smooth였고, 상세 첫 표시 프레임은949px 이후939→922→894…로 이동했다. 소스 auto와 실행 서버가 제공하는 이전 CSS가 달랐다. 사용자의 재현이 맞았다.

index.css에 라우트 즉시 이동 의도를 설명하는 주석을 추가하여 개발 서버가 CSS를 다시 컴파일하도록 했고, 실제 응답의 computed style이auto로 바뀌었다. 서버 종료나 재시작은 하지 않았다. 별도 강제 scroll effect는 추가하지 않았다.

`tests/community-scroll-local.mjs`를 추가했다. 현재8443+8082를 대상으로 DB 쓰기 없이 일반 모션,1440/390px,60개 누적 후55번째 글 클릭을 검증한다. 첫 상세 DOM 표시부터20 RAF 모두0px, 목록 링크/뒤로 각각60개 및기존위치 복원까지4/4통과. 원래 위치 PC12561px, 모바일13120px. 캡처와 결과는 `community-scroll-seed-2026-10-19/live-8443/`. 실행 명령: `PLAYWRIGHT_MODULE=<installed playwright module> node tests/community-scroll-local.mjs`.

이번 후속에서는 새 테스트 eslint와git diff --check를 실행했다. 제품 동작 변경 없이 동일CSS의 개발 서버 반영을 확인한 작업으로 전체 빌드/단위 검사를 반복하지 않았다. 이전258개/빌드 결과는 이전 실행 결과다. 기존 미커밋 작업/서버/데이터 보존, 커밋·push 없음.
