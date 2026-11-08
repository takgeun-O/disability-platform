# 관리자 상세 패널 스크롤 보완 — 2026-11-08

## 범위와 기준
- 브랜치: feat/admin-operation-log
- HEAD: d9decf224a46453c4e7e188a0299bc7096c44eb7
- 기존 미커밋 목록/상세/필터 작업을 그대로 보존했다. task-only.patch는 HEAD가 아닌 **이번 작업 시작 시 파일 스냅샷**과 비교한다.
- 이번 변경: AdminSelectionPanel.tsx, admin.css, 새 admin-panel-scroll-browser.mjs, 이 보고서.
- 백엔드·DB·API·인증·댓글 상세·공개 페이지를 변경하지 않았다. 커밋/푸시/배포 없음.

## 원인과 수정 전 재현
기존 패널은 position:static이고 제목 포커스는 preventScroll이었다. 데스크톱에서는 선택 후 스크롤 이동도 없어, 목록 하단의 선택 결과가 화면 위에 남았다.
20개 항목, 1440×1000에서 수정 전 테스트 두 건이 실제로 실패했다.
- 게시글: 문서 scrollY=1102, 선택된 패널 제목 top=-773px.
- 운영 기록: 문서 scrollY=1545, 제목 top=-1216px.
검증 자료의 pre-final/before에 원래 실패 로그·좌표·캡처가 있다.

## 처리 방식
- 1101px 이상에서 패널을 sticky로 배치한다. 실제 헤더의 화면상 하단과 16px 여백을 측정하고, 패널의 현재 화면 위치와 뷰포트 높이로 최대 높이를 계산한다. 헤더가 문서 스크롤로 사라진 뒤에는 그 높이를 계속 예약하지 않는다.
- scroll/resize는 requestAnimationFrame으로 묶고, 헤더 ResizeObserver 및 이벤트를 unmount에서 해제한다. 인증/조회 상태에는 관여하지 않는다.
- 패널 제목과 닫기는 스크롤 영역 밖에 둔다. 내용만 한 영역에서 스크롤하며 데스크톱의 기존 본문/사유별 높이 제한·overflow를 해제해 중첩 스크롤을 없앴다.
- 운영 조치는 잘리지 않은 본문 끝에서 접근한다. 링크는 내용 상단에서, 닫기는 항상 패널 상단에서 사용할 수 있다. 고정 footer로 본문을 덮지 않는다.
- 최대 높이만 적용하므로 짧은 상세는 내용 높이만 사용한다. 내용 영역에는 키보드 포커스와 영역 이름을 제공한다.
- 선택 ID 변경 시에만 내부 스크롤을 0으로 초기화한다. 운영 기록은 기존 logId 키를 그대로 사용하므로 같은 게시글의 다른 기록도 초기화된다. 동일 선택/비동기 갱신은 문서 및 읽기 위치를 초기화하지 않는다.
- 기존 close의 행 포커스 복귀, URL/뒤로가기, 수정키 링크, 요청 소유권, 조치 잠금은 변경하지 않았다.
- 1100px 이하에서는 기존 단일 열과 자연 높이를 유지한다. 브라우저 scrollIntoView의 소수점 반올림으로 320px 제목 상단이 -0.375px가 된 경우를 실제 확인하여 제목에 scroll-margin-top:12px를 부여했다. 허용 오차를 늘리지 않고 실제 가시 영역을 확보했다.

## 최종 소스 검증
Node 24.21.0, pnpm, 프로덕션 Next 서버 127.0.0.1:8479, Playwright Chromium. 사용자 개발 서버 8443은 그대로 보존.

| 명령 (web에서 실행) | 결과 |
|---|---|
| pnpm lint | 성공, 경고 없음 |
| pnpm typecheck | 성공 |
| pnpm test | 492/492 성공 |
| pnpm build | 성공 |
| node tests/admin-panel-scroll-browser.mjs | 10/10 성공 |
| node tests/admin-master-detail-browser.mjs | 26/26 성공 |
| node tests/admin-history-browser.mjs | 13/13 성공 |
| node tests/admin-posts-browser.mjs | 32/32 성공 |
| node tests/admin-operation-logs-browser.mjs | 32/32 성공 |
| node tests/admin-detail-browser.mjs | 49/49 성공 |
| node tests/admin-moderation-browser.mjs | 31/31 성공 |
| BASE_URL=http://127.0.0.1:8479 pnpm test:smoke | 실패: 기존 /community 문구 기대값 불일치 |

브라우저 합계 193/193. 모든 위 로그는 이번 최종 제품 소스로 다시 실행했다. 각 브라우저의 환경변수·명령은 verification/commands.txt 참조.

새 테스트는 1440×1000, 1920×1000, 1440×500, 320×700에서 실제 20개 행을 사용한다. 마지막 행 선택 전후 scrollY, 제목·식별정보의 bounding rect, 패널 크기·scrollTop·scrollHeight, 마지막 텍스트의 Range rect를 검증한다. 긴 사유/본문 끝, 조치 모달, 링크 접근, 동일 항목 읽기 위치 유지, 다른 항목/같은 게시글의 다른 logId 전환, close 포커스 복귀, 페이지 선택 해제, 응답 gate를 통한 로딩/503/재시도, 짧은 내용 무스크롤, 320px 가로 넘침을 확인했다. 고정 sleep은 사용하지 않았다.

최종 게시글 1440×1000 실측: 선택 전후 scrollY 모두 1102, 제목 top=29.4375px. 스크롤 하단에서도 scrollY=1102, 내부 scrollTop=4175, clientHeight=869, scrollHeight=5044. 긴 본문 마지막 줄과 조치 버튼이 실제 뷰포트에 보인다.

## 기존 smoke 실패와 제한
smoke는 / 통과 후 /community의 과거 문자열 '질문과 경험을 함께'를 기대하여 중단된다. 현재 공개 화면은 '서로의 질문과 경험을 나눠요.'이다. 공개 라우트와 PostList는 HEAD 대비 변경이 없고 이번 작업 범위도 아니다. assertion을 변경/삭제하지 않았다. 이후 smoke 단계는 실행되지 않았다.
관리자 브라우저 검증은 **mock API 기반**이며 실제 Spring 관리자 계정/콘텐츠를 사용하지 않았다. 실제 계정/역할/콘텐츠 변경 없음. 외부 폰트 네트워크를 차단한 기존 Playwright wrapper로 폰트 실패에 따른 비결정성을 줄였으며, 실제 배포 폰트/다른 브라우저에서의 실서버 수동 검증은 수행하지 않았다. 사용자 서버를 대신 재시작하지 않았다.

## 캡처 및 검토 구성
- verification/pre-final/before: 수정 전 실패, 목록 하단 선택 전후.
- verification/pre-final/diagnostic: 320px 소수점 좌표 진단(최종 결과와 구분).
- verification/final/final-scroll: 최종 하단 선택 전후, 긴 상세 상단/하단, 높이500, 로딩/오류/짧은 내용, 320px 및 geometry JSON.
- review/task-only.patch: 이번 보완만. review/pre-existing.patch 및 pre-existing-untracked: 기존 작업. review/cumulative-from-head.patch: 누적 변경, 이번 diff와 구분.
- README와 변경 전체 파일은 저장소 상대 경로를 유지한다. 기존 ZIP은 덮어쓰지 않는다. 환경변수/실제 인증정보/빌드 산출물 제외. 테스트의 mock-only 문자열은 가짜 CSRF fixture이다.

## 수동 확인
관리자 로그인 후 한 페이지 20개에서 마지막 행을 선택하고, 문서 위치를 유지하며 상세가 보이는지 확인한다. 상세 본문 끝까지 스크롤 후 다른 행/다른 운영 기록을 선택하여 내부 상단 초기화, 닫기 후 행 포커스를 확인한다. 1440/1920 및 낮은 높이, 320px에서 반복하고 날짜·정렬 적용 후 상세 관리로 이동/복귀를 확인한다.
