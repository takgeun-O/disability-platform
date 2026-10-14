# 주제 공간 검증 자료

[구현·학습 문서](../../Community-Topics-2026-10-15.md)

- `validation-summary.json`: 전체 결과. 23개 주제 동작 통과와 무JS 주제 404 본문 실패 1건을 구분한다.
- `topic-results.json`, `topic-404-nojs.json`: SSR 및 브라우저 모의 API 검사. 오류를 숨기지 않고 스크립트 종료 코드 1.
- `community-results.json`: 기존 커뮤니티 49개 모의 회귀.
- `login-results.json`: 기존 인증 38개 모의 회귀. 실제 생성 계정 없음.
- `real-results.json`: 기존 로컬 Spring/DB 공개 GET과 8443 UI 확인. 쓰기 0건.

## 캡처

모든 PNG는 실제 Next.js 프로덕션 화면에 **모의 API 데이터**를 넣어 촬영했다. 게시글의 `[검증 예시]`·회원 표시는 테스트 데이터이며 DB나 서비스 데이터가 아니다. `topics-entrance-*`는 전체 입구, `topics-hearing-aid-*`는 보청기 공간이다.

| 너비 | 전체 입구 | 주제 공간 |
|---|---|---|
| 1440px | [첫 화면](topics-entrance-1440-viewport.png) / [전체](topics-entrance-1440.png) | [첫 화면](topics-hearing-aid-1440-viewport.png) / [전체](topics-hearing-aid-1440.png) |
| 390px | [첫 화면](topics-entrance-390-viewport.png) / [전체](topics-entrance-390.png) | [첫 화면](topics-hearing-aid-390-viewport.png) / [전체](topics-hearing-aid-390.png) |
| 320px | [첫 화면](topics-entrance-320-viewport.png) / [전체](topics-entrance-320.png) | [첫 화면](topics-hearing-aid-320-viewport.png) / [전체](topics-hearing-aid-320.png) |

viewport 캡처 높이 900px, fullPage는 목록·하단 탐색까지 포함한다. 1440px는 전체 유형, 390·320px의 주제 화면은 모바일 필터에서 경험·후기를 적용한 상태다. 원본 PNG를 직접 열어 글자를 확인할 수 있다. 비밀번호·쿠키·토큰·원시 API 로그는 포함하지 않았다.

## 전체 게시글 복귀 후속 수정

최신 UI는 [all-posts-return/](all-posts-return/)의 1440·390·320px 캡처와 결과를 확인한다. 상위 폴더의 기존 캡처는 전체 게시글 항목 추가 전이다. 후속 실제 공개 조회 브라우저 3개 검사와 타입 검사·lint 통과. 기존 무JS 실패는 미해결이다.

## 주제 무JS 404 수정 후 결과

이 폴더의 실패 기록은 수정 전 이력으로 보존한다. [최신 결과·캡처](../2026-10-15-nojs-404/)와 [원인 및 검증 문서](../../Community-Topic-404-Fix-2026-10-15.md)를 확인한다. 주제 무JS 안내는 해결했고, 기존 게시글 무JS 실패 2건은 남아 있다.
