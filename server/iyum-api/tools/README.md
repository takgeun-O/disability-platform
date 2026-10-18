# 수동 로컬 커뮤니티 피드 데이터

일반 Spring 실행, `bootRun`, Flyway, `bootJar`에는 포함되지 않는다. `community-seed.init.gradle`을 **명시적으로 적용한 Gradle 실행에만** 별도 소스셋/작업을 등록한다. API·엔티티·스키마·보안 정책 변경은 없다.

## 생성 / 상태 / 정리

`server/iyum-api`에서 Java 21로 실행한다.

```sh
./gradlew -I tools/community-seed.init.gradle seedCommunity -PseedAction=create -PseedConfirm=--confirm-local-development
./gradlew -I tools/community-seed.init.gradle seedCommunity -PseedAction=status -PseedConfirm=--confirm-local-development
# 검토를 마친 뒤 생성 데이터만 제거할 때 명시적으로 실행한다. 이번 작업에서는 실행하지 않았다.
./gradlew -I tools/community-seed.init.gradle seedCommunity -PseedAction=cleanup -PseedConfirm=--confirm-local-development
```

- 현재 application.yaml의 localhost JDBC 템플릿을 확인하고, 접속 호스트를 `127.0.0.1`로 고정한다. `.env` 또는 동일 이름의 환경변수 IYUM_DB_PORT/NAME/USER/PASSWORD만 읽는다. DB 이름에 iyum이 없거나 확인 인자가 없으면 거부한다. 비밀값은 출력하지 않는다.
- Spring 애플리케이션을 기동하지 않고 JDBC 트랜잭션 하나로 생성한다. 스키마 생성/초기화/마이그레이션은 하지 않는다.
- 실제 MemberInputPolicy/PostInputPolicy와 기존 DelegatingPasswordEncoder(BCrypt)를 사용한다. 신규 가상 테스트 회원만 ACTIVE/USER로 만들며 기존 회원을 활성화하거나 권한 정책을 완화하지 않는다. 회원가입/메일 인증 E2E 검증을 대신하는 데이터가 아니다.
- 회원 3명, 게시글 85개(보청기 65, 나머지 기존 주제 20), 댓글 107개(65/0/1/20/21)를 만든다. 보청기 QUESTION 45개, GENERAL 10개, REVIEW 10개다. 작성 시각이 같은 묶음, Unicode, 긴 제목/닉네임, 여러 줄 본문이 있다.
- 고정 이메일과 advisory transaction lock, 소유 ID manifest로 중복 생성을 막는다. manifest가 없는 상태에서 같은 이메일이 이미 있으면 중단한다. 재실행은 사용자가 수정한 데이터를 덮어쓰지 않는다.
- 외부 저장 위치: `~/.local/share/iyum/feed-seed-v1/`. 디렉터리 0700, 파일 0600. `accounts.json`을 로컬에서 열어 로그인 이메일/비밀번호를 확인한다. Properties 파일은 도구용이며 이스케이프가 있으므로 비밀번호를 직접 복사할 때 JSON을 사용한다. Git, ZIP, 공유 로그에 이 폴더를 넣지 않는다.
- `manifest.properties`는 해당 데이터의 모든 회원/게시글/댓글 ID를 기록한다. 검증 스크립트가 추가한 댓글도 즉시 여기에 기록한다.
- cleanup은 ID·작성자·테스트 표식을 확인하고 **manifest에 없는 연관 데이터가 있으면 중단**한다. 순서는 소유 댓글→소유 게시글→소유 회원이며 전체 삭제/연쇄 삭제는 없다. 활성 토큰·약관 등 새로운 FK 연관이 생겨도 DB 제약이 전체 트랜잭션을 중단한다. 정리 성공 시 manifest를 보관 이력으로 옮기고 계정 비밀 파일만 지운다. 다시 create하면 새 ID로 생성한다.
- 커밋 직후 파일 이동 전 프로세스가 중단되면 `manifest-pending.properties`를 확인한다. manifest 없이 이메일만 발견된 상태를 자동으로 인수하지 않는다. 데이터나 파일을 임의로 지우고 재생성하지 않는다.

## 브라우저 검증

`web/tests/community-feed-seed-local.mjs`는 `IYUM_RUN_FEED_SEED_TEST=true`일 때만 실행한다. 실제 API와 CORS를 쓰므로 기존 개발 프런트 `8443`과 Spring `8082`가 필요하다. 기존 테스트 계정으로 로그인하고 새 댓글 1개를 작성·수정·논리 삭제한다. 새 ID를 manifest에 기록하며 원래 공개 댓글 107개를 유지한다. 비밀번호·세션·토큰을 로그에 출력하지 않는다.

현재 생성 ID, 개수, 실행 결과는 `web/docs/Community-Feed-Review-Followup-2026-10-18.md` 참조. 정리는 아직 실행하지 않았으며 브라우저 확인을 위해 데이터를 남겼다.

## 2026-10-19: 실제 댓글 테스트 소유 기록과 복구

두 실제 테스트(`web/tests/community-comments-ux-local.mjs`, `community-feed-seed-local.mjs`)는 `tests/helpers/seed-ownership.mjs`를 공유한다. manifest의 계정·게시글 및 API의 작성자/표식을 확인하고 대표·경계 게시글을 선택한다. 14~18 같은 고정 ID를 사용하지 않는다. 실제 조회 개수는 읽되, 다른 사용자의 댓글을 소유 목록에 넣지 않는다.

POST 전에 UUID를 넣은 `[개발 테스트]` 본문과 대상/작성자 정보를 비공개 `comment-journal/*.properties`에 원자적으로 저장한다. 유효한 201/commentId 확인 직후 저널에 ID를 먼저 기록한 다음 manifest의 기존 ID들과 합친다. 같은 디렉터리의 임시 파일+fsync+rename을 사용하며 `ownership.lock`으로 Java 도구와 브라우저 테스트의 동시 수정을 차단한다. 논리 삭제 뒤에도 ID는 유지한다. 결과 JSON은 생성/소유 등록/논리 삭제를 구분한다. 저널은 공유 ZIP에 넣지 않는다.

```sh
# DB 쓰기 없이 소유권 및 추가 연관 데이터 검사
./gradlew -I tools/community-seed.init.gradle seedCommunity -PseedAction=dry-run -PseedConfirm=--confirm-local-development
# 중단된 POST의 정확한 표식·본문·작성자·게시글을 대조하여 manifest만 복구
./gradlew -I tools/community-seed.init.gradle seedCommunity -PseedAction=recover -PseedConfirm=--confirm-local-development
```

중단/파일 기록 실패 시 결과 JSON이 없더라도 저널을 먼저 보존한다. 요청을 자동 재전송하지 않는다. 소유 manifest에 없는 행은 정확한 저널과 일치하는 단 하나의 행만 recover로 인수하며, 모호한 결과는 거부한다. ID를 받기 전 연결이 끊겨도 UUID 본문으로 찾아 복구할 수 있다. 결과 JSON을 정리 완료의 유일한 근거로 사용하지 말고 recover 이후 manifest와 dry-run을 확인한다. 응답이 아직 진행 중인 상황에서는 테스트 종료 및 서버 처리가 끝났는지 확인한 후 recover를 실행한다.

강제 종료 시 lock이 남을 수 있다. Node의 `ownership.lock/owner.json` PID 및 Java/Node 실행 여부를 확인한 뒤, 실행 중인 소유자가 없는 경우에만 남은 lock을 수동 제거한다. 자동 stale-lock 해제는 하지 않는다. `IYUM_SEED_DIR`로 **격리 DB 전용** 외부 소유 디렉터리를 지정할 수 있다. 개발 DB의 manifest를 격리 DB에 재사용하지 않는다.

이번 개발 DB에서는 117~120만 근거 대조 후 물리 삭제했다. 전체 cleanup은 실행하지 않았다. 소유가 아닌 댓글 116 때문에 dry-run 거부가 유지된다. 별도 폐기 DB에서는 생성 ID를 변경한 뒤 복구/거부/전체 cleanup을 검증하고 DB를 폐기했다. 상세 결과: `web/docs/Community-Scroll-Seed-Ownership-2026-10-19.md`.

## 2026-10-19 후속: 회차 종료와 초기화 예외

cleanup은 삭제 직전 `cleanup-pending.properties`에 대상 manifest와 archive UUID를 기록한다. 소유검증과 미등록 연관 데이터 검사는 기존대로 선행한다. DB commit 후 `history/<UUID>/manifest.properties`와 `comment-journal/`를 함께 보관하고, 계정 파일과 활성 manifest를 제거한 뒤 pending을 마지막으로 제거한다. 새 create는 활성 저널을 물려받지 않는다.

DB commit 뒤 파일 보관이 실패하면 pending과 남은 활성 자료를 보존한다. 원인을 해결하고 **cleanup을 다시 실행**한다. 도구는 동일 DB인지 확인하고 소유 ID가 모두 없을 때 파일 보관만 재개한다. 아직 행이 있으면 전체 기존 소유 관계와 manifest가 일치해야 삭제를 재시도한다. 일부만 남거나 manifest가 다르면 중단한다. pending이 있는 동안 create/recover와 브라우저 테스트는 중단한다. pending/manifest를 임의로 지우고 create하지 않는다.

기존 활성 manifest 및 UUID 저널 형식은 그대로 지원한다. 과거 도구로 이미 cleanup하여 활성 manifest 없이 옛 comment-journal만 남은 경우에는 자동으로 버리거나 건너뛰지 않고 create를 거부한다. 이 경우 기존 `manifest-cleaned-*.properties`와 저널의 member/post/comment ID·본문 및 DB 상태를 대조하여 같은 종료 회차임을 확인한 뒤 둘을 같은 history 하위 폴더로 수동 보관한다. 확인 불가능한 기록은 삭제하거나 새 회차에 편입하지 말고 검토한다. 현재 회차에 잘못된 소유 저널이 있으면 recover는 계속 실패한다.

`withSeedResources()`는 lock 획득 직후 fixture/Chrome/context/page 초기화부터 결과 저장까지 감싼다. 생성된 context와 browser를 정리하고 실패 여부와 무관하게 release를 시도한다. 최초 오류와 후속 cleanupErrors를 구분한다. acquireSeed의 owner 기록 실패는 자신이 만든 lock만 제거한다. 다른 프로세스 lock이나 강제 종료 잔여 lock 자동 해제는 없다.

격리 회차 검증: Java21, Docker 로컬 postgres 컨테이너가 준비된 환경에서 `python3 tools/community-seed/lifecycle-test.py`. 별도 UUID DB와 외부 임시 IYUM_SEED_DIR만 사용하고 finally에서 DB를 폐기한다. 결과/비공개 fixture는 외부 임시 폴더에 남으므로 계정·저널은 공유하지 않는다. 개발 DB 전체cleanup을 실행하는 명령이 아니다.
