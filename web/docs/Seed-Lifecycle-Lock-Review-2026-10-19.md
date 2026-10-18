# 시드 회차·브라우저 잠금 후속 검증 — 2026-10-19

기준: feat/community-post-cursor / 2adf11bff2e58394899d33157aa0a556df3306fa. 기존 미커밋·미추적 작업을 보존했다. 이번에는 사용자 화면·스크롤·댓글/API·스키마를 수정하지 않았다.

## 원인과 변경 파일

- `server/iyum-api/tools/community-seed/CommunitySeed.java`: cleanup이 manifest만 옮겨 이전 저널이 다음 create에 남던 문제. 삭제 직전 durable `cleanup-pending.properties`를 저장하고, commit 뒤 `history/<UUID>/`에 manifest와 comment-journal을 함께 보관한다. 계정 파일과 활성 manifest를 제거하고 pending을 마지막으로 제거한다.
- 같은 파일의 `resumeCleanup()`: pending 존재 시 create/recover 차단. cleanup 재실행은 DB 주소와 남은 소유 행을 확인한다. 모두 없으면 파일 보관만 재개한다. 아직 남으면 활성 manifest가 동일하고 전체 소유 검증과 미등록 연관 데이터 검사를 통과해야 삭제 재시도한다. 일부 행만 남는 불일치 상태는 거부한다. DB 실패/보관 실패에서 근거 파일을 잃지 않는다.
- `web/tests/helpers/seed-ownership.mjs`: `acquireSeed()`의 owner 기록 실패 시 자신의 새 lock만 제거. `withSeedResources()`에서 lock 직후 모든 초기화를 보호하고 생성된 context/browser 정리와 release를 각각 시도한다. 최초 오류에 후속 `cleanupErrors`를 첨부하며, 최초 오류가 없으면 정리 오류를 AggregateError로 보고한다. `finish()`는 결과 파일 저장 등의 실패가 최초 실패를 덮지 않게 한다. pending cleanup 동안 fixture 접근 거부.
- `web/tests/community-comments-ux-local.mjs`, `community-feed-seed-local.mjs`: 공통 lifecycle을 사용하여 fixture/Chrome/context/page 초기화까지 포함. 비공개 계정/환경설정은 그대로 사용하며 새로운 POST/DB 동작을 추가하지 않는다.
- `web/tests/seed-ownership.test.mjs`: launch/context/page/save/close/owner 실패, 복합 실패의 최초 원인 보존, 다음 잠금 획득·기존 타 프로세스 잠금 보존 검사7개 추가.
- `server/iyum-api/tools/community-seed/lifecycle-test.py`: 명시 실행하는 폐기 DB 통합 검사. UUID DB를 생성하고 실제 Flyway SQL로 스키마를 구성한다. 개발 DB는 대상으로 쓰지 않는다. 전용 임시 IYUM_SEED_DIR 및 finally dropdb. 비밀값 출력 없음.
- `server/iyum-api/tools/README.md`: 회차 보관·pending 재개·기존 형식·수동 대응 절차 추가.

## 기존 형식과 복구 절차

기존 활성 manifest/저널 필드를 그대로 받아들인다. 회차 식별은 종료 이력 폴더로 분리하므로 새 manifest에 필수 generation 필드를 추가하지 않았다. 현재 회차의 소유 불일치는 계속 오류다.

과거 cleanup으로 이미 manifest-cleaned 파일만 있고 활성 저널만 남은 경우 create를 거부한다. 자동 삭제/일괄 skip하지 않는다. 이전 보관 manifest와 저널의 대상·작성자·ID·본문 및 DB 상태를 대조하고, 확인된 동일 종료 회차 자료만 함께 history로 수동 보관해야 한다. 근거가 부족하면 보존하고 추가 확인한다.

cleanup 보관 실패에는 파일 문제를 해결한 뒤 같은 cleanup 명령을 다시 실행한다. pending을 지우거나 새 create를 강행하지 않는다. 강제 프로세스 종료로 남는 lock은 자동 해제하지 않는다. PID/프로세스 종료를 직접 확인한 후 수동 처리한다. 전원 장애/파일시스템 손상 전반을 완전 복구한다고 주장하지 않는다.

## 이번 직접 실행 결과

- `pnpm test`: **265/265 통과** (이전258 + 신규7). 오류 주입은 mock browser 객체와 실제 임시 파일/lock을 사용했다. 실제 Chrome를 고장 내거나 개발 서버를 중단한 검사가 아니다.
- `pnpm lint`: 통과. 두 실제 스크립트의 node --check도 통과.
- `git diff --check`: 통과.
- Java seed 도구는 격리 실행 중 Gradle로 실제 컴파일·실행했다.
- 격리 DB 통합 **7개 통과**: 기존 형식 저널 복구, 미소유 댓글 연결 시 삭제 거부 및 활성 파일 불변, DB commit 후 archive 실패/새create 차단/cleanup 재개, 동일 디렉터리 재생성 후 옛 저널 분리, 두 번째 회차 ID 없는 저널 정확 복구, 현재 회차 잘못된 소유 저널 거부, 두 회차 이력 보존 및 전체 소유 데이터 cleanup.

격리 실패 주입은 history 경로를 일반 파일로 만들어 commit 후 디렉터리 생성이 실패하게 했다. DB 삭제가 이미 끝난 것을 확인한 뒤 장애물을 제거하여 재실행했고, 파일 보관을 완료했다. 미소유 댓글 검사에서는 cleanup 거절 후85개 게시글과 manifest/저널 바이트가 유지됐다. 이 검증의 사용자 댓글 예시는 격리 DB에서 새로 만든 행만 사용했다.

## 데이터·서버·미실행 범위

이번에는 **기존 개발 DB와 소유 기록 파일을 변경하지 않았다**. 댓글116/기존117~120 처리 내역을 재실행하지 않았다. 개발 DB dry-run도 재실행하지 않았으며,116에 따른 기존 거부 기록은 그대로 유지한다. 전체 cleanup은 별도 폐기 DB에서만 실행했고 임시 DB는 finally에서 삭제됐다. 외부 임시 디렉터리의 소유 저널/이력은 디버깅 근거로 남으며 공개 문서에는 계정·저널을 복사하지 않았다.

기존8443 Node와8082 Spring 서버 PID는 유지했다. 사용자 화면 변경이 없어 전체 UI 브라우저·프런트 타입 검사/프로덕션 빌드·전체 백엔드 테스트를 반복하지 않았다. 실제 Spring 로그인/댓글 POST 테스트도 재실행하지 않았다. 기존 스크롤/댓글UX/API중복GET/무JS404 기록은 이번 결과가 아니다. 커밋·push·병합·배포 없음.
