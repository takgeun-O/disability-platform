# 정보 페이지 · 실제 Next.js 반영 캡처

2026-10-14 / `refactor/info-pages` / HEAD `1ceb848723b3aee16c6c77d056c2af863c52184e`.

[구현·학습 문서](../../Info-Pages-Integration-2026-10-14.md) · [검증 상세 JSON](verification.json)

## 먼저 확인할 실제 화면

- [소개 PC](captures/about-header-1440.png) / [소개 모바일](captures/about-header-390.png)
- [복지 목록 PC](captures/welfare-1440.png) / [복지 목록 모바일](captures/welfare-390.png)
- [P03 시작 PC](captures/guide-start-1440.png) / [P03 시작 모바일](captures/guide-start-390.png)
- [결과·체크 PC](captures/guide-actions-window-1440.png) / [결과·체크 모바일](captures/guide-actions-window-390.png)
- [전체 기준 PC](captures/guide-reference-header-1440.png) / [전체 기준 모바일](captures/guide-reference-header-390.png)
- [양쪽 지원 PC](captures/guide-bilateral-window-1440.png) / [양쪽 지원 모바일](captures/guide-bilateral-window-390.png)

## 촬영 조건과 구분

Chrome headless, 1440×1000 / 390×844 / 320×844, 한국어 UI, reduced motion. 별도 production 빌드와 `:8451`에서 촬영했다. 단위 테스트용 HTML/정적 시안이 아니라 실제 Next.js 컴포넌트다. 헤더의 GET /me만 정확한 비로그인 응답으로 모의했다. P03는 실제 기존 로컬 정책 계산·제품 스냅샷을 사용하며 서버 정책 판정/신청 API를 만들지 않았다. 계정/DB 데이터는 생성하지 않았다.

`guide-result-*`는 병원 검사 중 응답(R02)에서 문의 확인 체크 1개를 표시한 전체 페이지다. `guide-actions-window-*`는 읽기 좋은 결과 구간이다. `guide-reference-*`는 첫 절차를 펼친 전체 기준이며, `*-header-*`는 긴 페이지의 상단이다. `guide-bilateral-window-*`는 전체 기준에서 기존 양쪽 금액·조건을 펼친 모습이다. 자료 확인일을 촬영일로 바꾸지 않았다.

기존 개발 서버 `http://127.0.0.1:8443`에서도 `/about`, `/welfare`, P03가 최신 구현을 제공하는 것을 확인했다. 현재 서비스에서 직접 사용할 수 있다. 시안 `:8450`은 독립 검토 자료로 그대로 보존했다.

## 직접 확인 순서

1. 홈의 소개 링크 또는 상단 **IYUM 소개**로 이동한다. 모바일 메뉴의 순서와 정보 하위 3개를 확인한다.
2. **복지·지원정보 → 안내 보기**: 첫 질문은 미선택이다. ‘병원에서 검사·진단을 받고 있어요’를 선택한다.
3. 답변이 위에 남고 결과로 이동하는지, 문의 확인·준비 항목을 체크하고 **이 질문 복사하기**에 줄바꿈과 체크가 포함되는지 확인한다.
4. **전체 기준 보기**에서 전체 절차가 곧바로 보이는지 확인하고 돌아온다. 체크, 답변, 스크롤, 누른 링크의 초점이 복원된다.
5. 답변을 바꾸면 이후 답변과 메모가 초기화된다. 등록 완료 → 건강보험 → 처방 있음 → 처음을 고르면 R08의 구체적인 청구 링크가 나온다.
6. 결과가 있는 상태에서 첫 **본문 바로가기** 링크를 키보드로 실행한다. 화면·체크는 유지하며 본문으로 이동한다.
7. 시안의 확장 목록 `http://127.0.0.1:8450/#expanded`에서 모바일 필터를 닫고 PC 폭으로 바꾼다. 펼침 조작이 남는다. 이 필터는 실제 서비스에 추가하지 않았다.

## 전체 캡처 목록

PNG 시그니처·청크 CRC·크기를 검증했고 대표 화면을 직접 열어 확인했다. 화면에 인증 토큰/쿠키/비밀번호/실제 개인정보를 포함하지 않았다.

| 파일 | 크기 |
|---|---|
| [about-1440.png](captures/about-1440.png) | 1440 × 2729 |
| [about-320.png](captures/about-320.png) | 320 × 4382 |
| [about-390.png](captures/about-390.png) | 390 × 4028 |
| [about-header-1440.png](captures/about-header-1440.png) | 1440 × 1000 |
| [about-header-320.png](captures/about-header-320.png) | 320 × 844 |
| [about-header-390.png](captures/about-header-390.png) | 390 × 844 |
| [guide-actions-window-1440.png](captures/guide-actions-window-1440.png) | 1440 × 1000 |
| [guide-actions-window-320.png](captures/guide-actions-window-320.png) | 320 × 844 |
| [guide-actions-window-390.png](captures/guide-actions-window-390.png) | 390 × 844 |
| [guide-bilateral-window-1440.png](captures/guide-bilateral-window-1440.png) | 1440 × 1000 |
| [guide-bilateral-window-320.png](captures/guide-bilateral-window-320.png) | 320 × 844 |
| [guide-bilateral-window-390.png](captures/guide-bilateral-window-390.png) | 390 × 844 |
| [guide-reference-1440.png](captures/guide-reference-1440.png) | 1440 × 4671 |
| [guide-reference-320.png](captures/guide-reference-320.png) | 320 × 7611 |
| [guide-reference-390.png](captures/guide-reference-390.png) | 390 × 7012 |
| [guide-reference-header-1440.png](captures/guide-reference-header-1440.png) | 1440 × 1000 |
| [guide-reference-header-320.png](captures/guide-reference-header-320.png) | 320 × 844 |
| [guide-reference-header-390.png](captures/guide-reference-header-390.png) | 390 × 844 |
| [guide-result-1440.png](captures/guide-result-1440.png) | 1440 × 3488 |
| [guide-result-320.png](captures/guide-result-320.png) | 320 × 4583 |
| [guide-result-390.png](captures/guide-result-390.png) | 390 × 4091 |
| [guide-start-1440.png](captures/guide-start-1440.png) | 1440 × 1332 |
| [guide-start-320.png](captures/guide-start-320.png) | 320 × 2044 |
| [guide-start-390.png](captures/guide-start-390.png) | 390 × 1881 |
| [welfare-1440.png](captures/welfare-1440.png) | 1440 × 1104 |
| [welfare-320.png](captures/welfare-320.png) | 320 × 1443 |
| [welfare-390.png](captures/welfare-390.png) | 390 × 1394 |
