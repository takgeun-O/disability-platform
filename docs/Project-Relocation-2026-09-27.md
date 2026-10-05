# IYUM 작업 폴더 정리 · 2026-09-27

사용자 요청에 따라 개발 위치를 정리했다. 서비스 코드·금액·분기·디자인 변경 작업은 아니다.

## 위치 대응

| 이전 | 이후 |
| --- | --- |
| `/Users/tackeunoh/Desktop/dev/myProject/disability-platform/web` | `/Users/tackeunoh/Developer/iyum/web` |
| `/Users/tackeunoh/Desktop/dev/myProject/disability-platform/frontend` | `/Users/tackeunoh/Developer/iyum/prototypes/p03` |
| 루트 공통 문서 5개 | `docs/` |
| Figma 원본 ZIP | `archive/` |

## 파일 보존 확인

- 이동 전 최신 웹(8443)과 기존 P03(3000) 개발 서버를 중지했다.
- 같은 볼륨에서 폴더 이름 변경 방식으로 이동했다. 기존 의존성과 배포 연결 메타데이터도 각 앱과 함께 보존했다.
- 소스·문서·원본 ZIP 등 124개 파일의 SHA-256을 이동 전후 비교했으며 모두 일치했다. 의존성·생성 결과·캐시는 해시 비교에서 제외했다.
- 비교 후 실행 안내, 에이전트 진입점, 공통 문서의 절대 링크를 새 경로에 맞게 갱신했다. 역사적 설명의 옛 폴더 이름은 유지했다.
- 원본 해시 목록은 `/tmp/iyum-relocation-manifest-20260927.json`에 임시 보관했다.

## 후속 검증

이동 후 의존성 실행 스크립트 재연결, Git 초기화·제외 규칙 확인, 타입 검사·빌드·브라우저 확인 결과는 아래에 기록한다.

### 완료 결과

- 현재 웹 의존성을 잠금 파일 그대로 새 위치에 설치했다. 설치된 실행 스크립트의 `NODE_PATH`가 `/Users/tackeunoh/Developer/iyum/web`을 가리키는 것을 확인했다. 이전 의존성은 `/tmp/iyum-web-dependencies-before-relocation-877jbnlw/node_modules`에 복구용으로 임시 보관했다.
- 이동 후 `pnpm exec tsc --noEmit` 통과.
- `pnpm build` 통과. 기존과 동일한 JS/CSS 산출물 이름과 약 562KB JS 묶음을 확인했다. 500KB 초과 경고는 남아 있다.
- 브라우저에서 홈 → P03 → 상황 확인 → 검사·진단 결과를 확인했다. P03 경로 직접 새로고침도 정상이며 검증 탭에서 수집된 error/warn은 없었다.
- 최종 해시 재확인: 124개 기존 파일 중 의도한 문서 4개만 변경됐다. 애플리케이션 소스와 잠금 파일은 변경하지 않았다.
- 루트에 `main` 브랜치로 Git 초기화. 커밋 0개, 원격 연결 0개. 스테이징·커밋·푸시·배포 없음.
- `node_modules`, `dist`, `.next`, `.pnpm-store`, `.env.local`, 기존 `.vercel` 정보가 Git에서 제외되는 것을 확인했다.
- 현재 실행 서버: `web`의 `http://127.0.0.1:8443/`. 보존용 P03의 3000번 서버는 중지 상태로 유지했다.
- 옛 프로젝트 루트와 `IYUM_WireFrame` 폴더가 재생성되지 않았음을 확인했다. 앞으로 편집기에서는 새 루트 `/Users/tackeunoh/Developer/iyum`을 연다.

### 검증 범위

실행 환경은 Node 24.19.0 / pnpm 11.19.0이다. `.mise.toml`의 지정 버전은 변경하지 않았다. 현재 웹의 lint·자동 테스트 명령은 아직 없으며, 보존용 P03의 전체 테스트·빌드나 모바일·화면낭독기 검증을 이번 폴더 이동 때문에 반복하지 않았다. 관련 UX 검증은 `web/docs/P03-Local-UX-Review-2026-09-27.md`에 있다.
