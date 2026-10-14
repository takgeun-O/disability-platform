# 커뮤니티 캡처·검증 자료

[전체 구현 기록](../../Community-API-Integration-2026-10-14.md) · [React 코드 읽기](../../Community-React-Learning-2026-10-14.md)

2026-10-14, 기존 `http://127.0.0.1:8443` Next 개발 서버, Chrome headless / 한국어 로케일 / 움직임 감소 환경에서 촬영했다. 데스크톱 viewport 1440×1000, 모바일 390×844이며 전체 페이지 캡처라 최종 PNG 높이는 길 수 있다. 필터 캡처는 320×800이다. 이미지 안의 Next 개발 표시(N)는 개발 서버 UI이며 서비스 기능이 아니다.

`mock-`는 **현재 UI 코드에 브라우저 모의 API 응답**을 사용했다. 목록에는 `[검증 예시]` 접두사를 표시했다. UI와 장애·경합 검증을 위한 자료이며 실제 DB 통합 검증으로 해석하지 않는다. 초기/로그아웃 상태는 실제 Spring 오류 응답 형식을 따른다.

`real-`는 **모의 응답 없이 실제 로컬 Spring/PostgreSQL/Mailpit**을 사용했다. 실제 생성한 검증용 회원/게시글만 보이도록 GENERAL / WELFARE_LIFE 조건으로 촬영했다. 본문은 검증용 예시임을 명시했고, 비밀번호·이메일 입력·쿠키·인증 토큰은 캡처하지 않았다.

## 먼저 볼 캡처

[데스크톱 목록 주요 구간](screenshots/mock-list-1440-top.png) · [모바일 목록 주요 구간](screenshots/mock-list-390-top.png) · [320px 필터 패널](screenshots/mock-filter-320-top.png)

이 세 `-top` 파일은 글자를 쉽게 읽을 수 있도록 viewport만 촬영했다. 임시 `127.0.0.1:8444` 프로덕션 빌드에서 **모의 API**를 사용했으며 실제 API 통합 검증이 아니다. 아래 전체 페이지 캡처와 동일한 가상 목록이다.

## 목록

| 화면 | 데스크톱 | 모바일 | 조건 |
| --- | --- | --- | --- |
| 모의 목록 | [1440](screenshots/mock-list-1440.png) | [390](screenshots/mock-list-390.png) | 공개, 전체 조건, 63개 가상 글 중 첫 20개, 베스트 준비 |
| 모의 상세 | [1440](screenshots/mock-detail-1440.png) | [390](screenshots/mock-detail-390.png) | `/community/posts/1`, 줄바꿈·긴 문자열·script 문자열 비실행 |
| 모의 작성 | [1440](screenshots/mock-create-1440.png) | [390](screenshots/mock-create-390.png) | 모의 로그인, QUESTION / HEARING_AID |
| 실제 목록 | [1440](screenshots/real-list-1440.png) | [390](screenshots/real-list-390.png) | `/community/posts?type=GENERAL&topic=WELFARE_LIFE` |
| 실제 상세 | [1440](screenshots/real-detail-1440.png) | [390](screenshots/real-detail-390.png) | 생성 게시글 `/community/posts/2` |
| 실제 작성 | [1440](screenshots/real-create-1440.png) | [390](screenshots/real-create-390.png) | 실제 로그인 후 등록 전, GENERAL / WELFARE_LIFE |

추가: [320px 모의 필터 패널](screenshots/mock-filter-320.png). 320px 목록·상세·작성의 가로 넘침도 브라우저에서 검사했다.

## 검증 기록

- [모의 브라우저 검사 26개](mock-results.json): 필터 선택/적용/취소, 페이지와 스크롤 복귀, 지연 성공/실패 무효화, 오류/중복 전송/세션 만료, 키보드 초점, API 데이터만 표시.
- [실제 브라우저 검사 7개 묶음](real-results.json): 공개 조회, 필터, 가입/인증/로그인, 작성 201과 새로고침, 목록 복귀, CSRF 403, 로그아웃 후 401/입력 보호.

실제 테스트 데이터는 회원 ID 20(가상 닉네임 `UI_mv7nnnbi`), 게시글 ID 2, Mailpit 메일 1건이다. 기존 데이터를 삭제하지 않았다. 계정 비밀번호와 인증 정보는 기록하지 않았다.

이 폴더에는 선택한 PNG와 민감값 없는 결과 요약만 포함했다. trace, 영상, 쿠키 저장 파일, 메일 원문, 원시 서버 로그는 포함하지 않는다. PNG는 `web/.gitattributes`의 기존 Git LFS 규칙 대상이며 현재 실제 PNG 파일로 저장했다. 이번 작업에서 stage/commit/push는 하지 않았다.
