# 행 클릭 선택 보완

2026-11-08. 기존 미커밋 작업을 유지하고 관리자 게시글/운영 기록의 행 클릭 선택을 추가했습니다.

- 상태 배지·작성자·사유 등 일반 셀을 클릭하면 기존 선택 상태 및 상세 패널을 갱신합니다.
- 링크, 날짜 팝오버, 버튼 등 인터랙티브 요소는 행 선택에서 제외합니다. 수정키 클릭과 텍스트 선택도 제외합니다.
- 기존 선택 버튼을 유지해 키보드 접근 및 선택 상태 안내를 보존합니다. 행의 표 의미를 버튼 역할로 바꾸지 않습니다.
- 조회 중/조치 잠금은 기존 조건을 이벤트에서도 검사합니다. URL 복원·인증·API 계약은 변경하지 않았습니다.
- 선택 가능한 행에 포인터 커서와 옅은 hover 배경을 적용합니다.

변경: AdminPosts.tsx, AdminOperationLogs.tsx, admin-row-selection.ts(신규), admin.css, admin-master-detail-browser.mjs.
검증: pnpm lint/typecheck/test/build 통과. 단위 492건, master-detail 브라우저 19건 및 history 브라우저 13건 통과. 1440px/320px 행 선택·키보드·링크·텍스트 선택·수정키·조회 중 잠금 검증. 브라우저는 모의 API 및 외부 폰트 요청을 분리한 기존 검토 어댑터 사용. 실제 Spring 변경 요청은 수행하지 않았습니다.
이번 보완 로그·화면 캡처·task-only.patch: /private/tmp/iyum-row-selection/.

## 후속: 선택 버튼 제거
사용자 요청으로 미리보기/기록 상세 버튼과 전용 스타일을 제거했습니다. 위 최초 보완의 '선택 버튼 유지'는 이전 상태를 설명합니다.
현재는 표 행에 tabIndex/aria-selected와 Enter·Space 핸들러를 적용합니다. 하위 링크/버튼의 키보드 이벤트는 행 선택으로 처리하지 않습니다. 행 포커스 테두리와 상세 닫힘 후 행 포커스 복귀를 유지합니다.
삭제 후 최종 검증: lint, typecheck, 단위 492건, build 통과. Mock 브라우저 master-detail 19건과 history 13건 통과. 버튼 미노출, 행 Enter·Space, 320px/1440px, 링크, 잠금, 선택 복원 검증 포함. 실행 로그·캡처는 /private/tmp/iyum-row-selection/remove-* 입니다.

행에 aria-disabled를 지정하면 내부 상세 링크까지 비활성으로 간주되는 회귀를 확인했습니다. 해당 속성 대신 tabIndex와 행 핸들러의 잠금 검사를 사용해 조회 중에도 링크 탐색을 유지합니다. 지연 조회 중 상세 이동 및 복귀 테스트를 다시 실행해 통과했습니다.
