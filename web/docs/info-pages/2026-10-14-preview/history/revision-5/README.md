# IYUM 정보 페이지 개편 · 검토 시안 · 5차 수정

**서비스 반영 전 독립 디자인 시안이다.** `web/src`, 서비스 라우트·스타일·테스트·환경설정은 변경하지 않았다. 로컬 `main`, HEAD `1ceb848723b3aee16c6c77d056c2af863c52184e`를 유지한다. 이번 시작 시 이미 있던 `AGENTS.md`, `docs/PRODUCT-CONTEXT.md`, `web/docs/info-pages/` 변경도 보존했다.

## 바로 확인하기

- [보청기 안내 · 답변 미선택 시작](http://127.0.0.1:8450/?revision=5#guide)
- [전체 기준 · 절차 설명부터 보기](http://127.0.0.1:8450/?revision=5#detail/steps)
- [검수·위임·직접 청구 설명](http://127.0.0.1:8450/?revision=5#detail/claim)
- [IYUM 소개](http://127.0.0.1:8450/?revision=5#about)
- [복지 목록 · 현재 한 건](http://127.0.0.1:8450/?revision=5#welfare) / [확장 예시](http://127.0.0.1:8450/?revision=5#expanded)
- [홈 연결부](http://127.0.0.1:8450/?revision=5#home)

**결과 예시는 위쪽 ‘화면 → 예시 불러오기’에서 직접 선택한다.** 예전 `#result-registration` 등의 주소를 직접 열거나 새로고침해도 자동으로 답변을 채우지 않는다. 실제 답변처럼 보였던 예시 자동 선택을 제거했다. `#guide`로 새 진입할 때도 미선택이며, 전체 기준에서 돌아올 때는 작성하던 답변·체크·위치를 유지한다.

이 해시는 시안 전용이다. 실제 `/about`, `/welfare`, `/validation/hearing-aid-health-insurance` 변경이 아니다. 인증·커뮤니티는 기존 서비스 새 탭 링크이며 시안에서 API 성공을 모의하지 않는다.

## 5차 반영 내용

1. **미선택 시작:** 첫 질문에 기본 답변을 넣지 않는다. 결과 예시는 명시적으로 선택할 때만 불러온다.
2. **문의 후 확인 체크:** 자연스러운 질문 문장 아래에 ‘문의 후, 이것을 알게 됐나요?’를 배치했다. 검사·진단 중에는 ‘남은 검사와 진단 자료를 받는 날짜’, ‘주민센터에 제출할 시점’을 각각 체크한다.
3. **준비 체크:** ‘할 일과 준비할 것’에도 작은 체크 행을 두었다. 선택·해제는 마우스, 터치, 키보드 Space로 가능하다. 체크해도 설문 결과, 현재 단계, 자격 판정이 바뀌지 않는다. 답변 변경·새 진입·새로고침 때 초기화하며 서버·브라우저 저장소에 기록하지 않는다.
4. **문의 메모 복사:** 현재 상황 → 기관 → 질문 → 확인 체크 → 준비 체크 → 다음 단계 순으로 줄바꿈·구획을 넣고 `[ ]`/`[x]` 상태를 함께 복사한다. [실제 복사 예시](copy-example-revision-5.txt). 권한이 없으면 선택 가능한 텍스트 입력란을 보여준다.
5. **끊기지 않는 안내:** 공단 문의가 필요한 기존 분기에는 공단 글 문의 링크, 의료급여에는 129의 일반 제도 채팅상담 안내를 추가했다. 개인별 신청·결정 상태는 주민센터 담당자에게 확인하도록 구분한다. 병원·센터별 온라인 창구는 확인되지 않았으므로 만들지 않았다.
6. **전체 기준 디자인:** 두 개 중간 요약 카드를 제거했다. 전체 절차를 첫 본문으로 두고 등록·대상·금액·제품 확인·상담·출처를 이어서 배치했다. 단계별 펼침, 한쪽/양쪽 금액 선택, 보호자 질문 복사도 동작한다. ‘청구 방법’ 링크는 검수·청구와 직접 청구 서류 설명을 펼친 위치로 이동한다. 출처는 문서 아래에 두며 목차에서 바로 이동할 수 있다.

## PC·모바일 캡처

| 화면 | 1440px | 390px | 320px |
|---|---|---|---|
| 첫 질문 · 미선택 | [PC](captures/revision-5/guide-unselected-1440.png) | [모바일](captures/revision-5/guide-unselected-390.png) | [좁은 화면](captures/revision-5/guide-unselected-320.png) |
| 검사·진단 중 · 체크리스트 | [PC](captures/revision-5/assessment-checklists-1440.png) | [모바일](captures/revision-5/assessment-checklists-390.png) | [좁은 화면](captures/revision-5/assessment-checklists-320.png) |
| 전체 기준 · 첫 화면 | [PC](captures/revision-5/reference-header-1440.png) | [모바일](captures/revision-5/reference-header-390.png) | [좁은 화면](captures/revision-5/reference-header-320.png) |
| 청구 링크 도착 위치 | [PC](captures/revision-5/reference-claim-1440.png) | [모바일](captures/revision-5/reference-claim-390.png) | [좁은 화면](captures/revision-5/reference-claim-320.png) |
| 전체 기준 · 전체 페이지 | [PC](captures/revision-5/reference-full-1440.png) | [모바일](captures/revision-5/reference-full-390.png) | [좁은 화면](captures/revision-5/reference-full-320.png) |

[캡처 모아보기](gallery.html). 체크된 모습은 브라우저 검증 중 직접 선택해 촬영한 상태이며 초기 기본값이 아니다. 이전 캡처와 `history/revision-1`~`revision-4`는 보존했다.

## 검증 결과

2026-10-14, Chrome headless 실제 브라우저에서 **47개 통과**. 설문 완료 28개 조합을 순회하는 검사를 포함한다.

- [신규 18개](revision-5-check-results.json): 미선택 시작, 체크·복사·상세 왕복, 모든 상세 링크 목표, 온라인 링크 분기, 금액 전환, 보호자 복사, 3개 너비, 대비.
- [기존 기본 10개](revision-5-compat-preview-check-results.json): 메뉴·목록·검색·의료급여/모름·복귀·API 미호출.
- [3차 회귀 10개](revision-5-compat-revision-3-check-results.json): 기존 서비스 P03 읽기 전용 비교, 부드러운 스크롤, 색상·토글·키보드·움직임 줄이기·소개.
- [4차 회귀 9개](revision-5-compat-revision-4-check-results.json): 28개 완료 조합, 4단계 안내·기관·기존 문구·센터 질문·복사·대비.
- `git diff --check` 통과. 첫 시안 작업 전 해시와 서비스 소스·테스트 125개가 모두 동일하다.
- 최초 신규 검사에서 테스트 기대값을 `15세`로 잘못 적어 실패했다. 실제 서비스와 시안에는 원래부터 `19세 미만`이었으며, 원본을 확인해 검사 기대값을 수정했다. [최초 실행 기록](history/revision-5-first-check.json). 정책 문구를 테스트에 맞춰 바꾸지 않았다.

실행: `checks/*.mjs`는 Playwright와 Chrome이 필요하다. 이번 환경의 Node 24.21.0과 별도 제공 Playwright를 사용했다. 프로젝트 의존성을 변경하지 않았다.

```sh
cd /Users/tackeunoh/Developer/iyum/web/docs/info-pages/2026-10-14-preview
# Playwright가 통상 모듈 경로에 설치되어 있으면 변수 생략 가능
export PLAYWRIGHT_MODULE=/path/to/playwright/index.mjs
export CAPTURE_REVISION=revision-5 RESULT_PREFIX=revision-5-compat-
node checks/preview-browser.mjs
node checks/preview-extra.mjs
node checks/revision-3-browser.mjs
node checks/revision-4-browser.mjs
node checks/revision-4-contrast.mjs
node checks/revision-5-browser.mjs
```

## 링크 점검과 한계

- ‘알아두세요’의 전체 기준은 실제 전체 절차로, 양쪽 지원은 전체 기준의 양쪽 조건·금액으로 연결한다. 13개 상세 목표에서 내용 표시·열림·초점을 검사했다.
- [공단 온라인 문의](https://www.nhis.or.kr/nhis/minwon/retrieveCvaplCstInfoColctAgreView.do)는 로그인 화면으로 연결되는 것을 확인했다. 로그인 이후 상담 접수는 하지 않았다.
- [129 채팅상담 안내](https://129.go.kr/counsel/counsel03.do), [공단 보청기 제출서류 안내](https://www.nhis.or.kr/static/html/wbma/c/wbmac0206_2.html)는 공개 페이지 응답을 확인했다. 상담·청구를 실제 제출하지 않았다.
- [복지로 주민센터 찾기](https://www.bokjiro.go.kr/ssis-tbu/twatca/wlfcl/wlfclPage.do)는 페이지는 열리지만 웹 도구에서 본문이 추출되지 않았다. 실제 검색·개별 기관 연락 기능은 검증하지 않았다.
- 전체 기준의 정책 문구·금액·조건·공식 출처/확인일은 현재 서비스 스냅샷이다. 이번 점검은 링크 연결과 디자인 검증이며 정책 최신성 재검증이 아니다. 현재 출처 확인일을 오늘 날짜로 바꾸지 않았다.
- 제품·판매처 설명과 공식 링크는 시안에서 읽을 수 있다. 실제 제품 검색은 기존 서비스의 해당 구간으로 연결한다. 검색 엔진·206개 제품 데이터를 복제하지 않았다.
- 실기기·스크린리더, 인증·게시글 API, 전체 서비스 pnpm 테스트/타입/lint/빌드는 이번 시안 변경에서 재실행하지 않았다. 기존 커뮤니티 무JS 404 문제를 이번 실패·해결로 취급하지 않는다.

## 문서·파일 및 다음 단계

- `preview/preview.js`: 첫 진입·예시 구분, 체크·복사·상세 연결과 복귀.
- `preview/p03-action-guidance.js`: 기존 결과의 문의·준비 내용을 짧은 체크 항목으로 분리.
- `preview/p03-reference-snapshot.js`: 현재 서비스 전체 기준의 읽기용 HTML, 양쪽 금액과 질문 복사 원문, 원본 해시. 스타일은 시안 CSS로 재구성.
- `preview/preview.css`, `preview/index.html`: 체크·전체 기준의 공통 디자인과 5차 표시.
- [설계 기록](DESIGN-NOTES.md), [결과 문구·근거](RESULT-GUIDANCE.md), [소개 문구](ABOUT-COPY.md), [현재 서비스 조사](CURRENT-REVIEW.md), [제품 맥락](../../../../docs/PRODUCT-CONTEXT.md).

확정된 메뉴 ‘IYUM 소개 → 커뮤니티 → 정보 찾기’, 소개·복지 목록 방향, 기존 질문·결과·스크롤·색상·토글을 유지한다. **시안 확인 뒤에만 실제 서비스 반영을 진행한다.**

기존 :8443(Next PID 96054), :8450(시안 Python PID 30092)를 유지했다. 새 서버를 시작하거나 사용자 서버를 재시작하지 않았다. 커밋·push·PR·병합·배포 없음.

서버가 없는 경우에만 아래로 시안을 다시 열 수 있다.

```sh
./preview/serve.sh
# 다른 포트: IYUM_PREVIEW_PORT=8451 ./preview/serve.sh
```

Python 정적 서버이며 localhost 전용이다. `preview/index.html`을 파일로 열 수도 있다. 폰트는 기존과 같은 Noto Sans KR Google Fonts를 사용하며 연결이 없으면 sans-serif로 대체된다. 새로 확보한 이미지·정책 데이터나 로그인 자격 정보는 없다.
