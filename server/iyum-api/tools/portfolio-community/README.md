# IYUM 포트폴리오 커뮤니티 시드

공개 게시글 12개를 넣는 수동 PostgreSQL 시드다. 앱 시작, Gradle 빌드, Flyway와 독립적으로 실행한다. 기존 `tools/community-seed`는 로컬 개발 계정과 정리용 파일을 만드는 도구이므로 원격 실행 제한을 그대로 둔다. 이 디렉터리에는 실행 영수증과 실제 콘텐츠 ID를 같은 DB 트랜잭션에 기록하는 데모용 시드를 두었다.

## 맥에서 Render 데모와 같은 Neon DB에 적용하기

1. Render의 **iyum-api-demo** 서비스에서 현재 배포가 사용하는 Environment(연결한 환경 그룹 포함)를 연다. Spring의 최종 datasource URL·사용자·비밀번호를 확인한다. 일반적인 오버라이드 이름은 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`다. 기본 `application.yaml`은 `localhost`, `IYUM_DB_PORT`, `IYUM_DB_NAME`, `IYUM_DB_USER`, `IYUM_DB_PASSWORD`를 조합하며 `application-demo.yaml`에는 별도 DB 주소가 없다. 운영 설정에서 다른 변수나 시작 인자로 연결하면 그 **실제 최종값**을 따른다.
2. Neon 콘솔에서 후보 프로젝트와 브랜치를 선택하고 Connect의 **호스트 전체(엔드포인트와 pooler 여부 포함), database, role**을 Render 값과 대조한다. 연결 옵션 및 스키마도 확인한다. 이 스크립트는 `public` 스키마를 사용한다. 다른 브랜치에도 같은 이름의 DB와 role이 있을 수 있다. 호스트까지 일치하거나 같은 브랜치·엔드포인트임을 식별한 뒤 진행한다. 저장소에는 대상 브랜치나 DB 이름이 확정되어 있지 않다.
3. 맥의 새 zsh 터미널에서 아래를 실행한다. PostgreSQL 17의 `psql`과 Python 3가 필요하다. 각 프롬프트에는 Render와 대조한 개별 값만 입력한다. JDBC URL 전체를 넣지 않는다. PostgreSQL 17의 시스템 신뢰 저장소와 호스트 이름 검증을 사용하고, Render에서 채널 바인딩 등 추가 보안 옵션을 요구하면 libpq의 동등한 옵션도 적용한다(예: `PGCHANNELBINDING=require`). 비밀번호는 매 명령의 숨김 프롬프트에 Render가 사용하는 DB 비밀번호를 입력한다.

```zsh
cd /Users/tackeunoh/Developer/iyum/server/iyum-api
read -r 'PGHOST?확인한 Neon 호스트: '
read -r 'PGPORT?데이터소스 포트 (생략된 경우 5432): '
read -r 'PGDATABASE?데이터베이스 이름: '
read -r 'PGUSER?데이터베이스 역할: '
export PGHOST PGDATABASE PGUSER
export PGPORT="${PGPORT:-5432}" PGSSLMODE=verify-full PGSSLROOTCERT=system PGCLIENTENCODING=UTF8
: "${PGHOST:?호스트 필요}" "${PGDATABASE:?DB 이름 필요}" "${PGUSER:?DB 역할 필요}"
psql -X -W -f tools/portfolio-community/inspect.sql
```

4. 조회 결과의 DB·role, 성공한 Flyway 버전(V19 이상), 기존 회원·게시글·원댓글·답글·좋아요 건수를 확인한다. 같은 터미널에서 아래 두 명령을 순서대로 실행한다. 첫 반영에 필요한 권한은 앱 테이블의 조회·삽입, ID 시퀀스 사용, 임시 테이블 및 `public.iyum_portfolio_seed_runs` 생성이다.

```zsh
psql -X -W -f tools/portfolio-community/apply.sql
psql -X -W -f tools/portfolio-community/inspect.sql
```

첫 성공 시 회원 +14, 글 +12, 원댓글 +13, 답글 +5, 게시글 좋아요 +54, 댓글·답글 좋아요 +65가 기준이다. 운영 중의 다른 활동은 전체 건수에 별도로 반영될 수 있다. 시드 출력에 이번 DB의 대표 글·원댓글·답글 ID가 나온다.

## 재실행과 식별

`community-v1` 영수증이 있으면 삽입 단계를 건너뛴다. 최초 실행 시각에 상대적인 과거 시각을 한 번만 사용하며 재실행으로 순위를 올리지 않는다. PostgreSQL 트랜잭션 잠금이 같은 시드의 동시 실행을 직렬화한다. 같은 이메일·닉네임을 기존 회원이 쓰고 있으면 고유 제약으로 전체 트랜잭션이 실패한다. 회원은 `데모_` 닉네임의 일반 회원이며 임의 비밀값에서 생성한 해시만 저장한다.

`public.iyum_portfolio_seed_runs`의 다섯 ID 배열은 이 시드가 삽입한 행만 식별한다. 이름이나 제목을 기준으로 기존 콘텐츠를 수정하거나 삭제하지 않는다. 나중에 정리할 때에는 영수증을 기준으로 이용자가 이후 추가한 답글·좋아요 등 참조 관계도 먼저 확인한다. 데이터가 운영 중 수정되더라도 시드를 재실행해 복원하지 않는다.

## 배포 화면에서 확인

홈의 최근 글에서 **여러 명이 동시에 말하는 회의, 실시간 자막은 어떻게 활용하세요?**를 찾는다. 검색창에 **자막**을 넣으면 제목 일치 글과 **소극장 맨 뒷줄에서 두 장면을 다 본 날**의 본문 발췌가 함께 보인다. 회의 글에서 베스트 댓글 카드의 **해당 답글로 이동**을 누르면 전체 댓글의 같은 답글이 펼쳐지고 강조된다. 이후 새 글이 쌓이거나 일주일이 지나 첫 화면 순위가 달라져도 검색은 그대로 사용할 수 있다.
