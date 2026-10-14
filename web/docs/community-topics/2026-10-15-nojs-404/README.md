# 주제 무JavaScript 404 수정 검증

[원인·변경·명령·검증 범위](../../Community-Topic-404-Fix-2026-10-15.md)

- `topic-404-results.json`: 프로덕션 전용 7개 통과. 기존 Spring 공개 조회 사용, 데이터 쓰기 없음.
- `dev-results.json`: 기존 8443 개발 서버 전용 7개 통과.
- `topic-regression-results.json`, `topic-nojs-required-result.json`: 모의 API 주제 23개 및 필수 무JS 검사 통과.
- `community-regression-results.json`: 모의 API 기존 커뮤니티 49개 통과.
- `existing-post-404-results.json`: 기존 게시글 검사 5개 통과, 무JS 2개 실패.
- `baseline.json`, `plain-link.json`, `segment-layout.json`, `isolated-error-route.json`: 원인 비교 실험의 상태/표시 여부만 남긴 요약.

## 실제 프로덕션 오류 화면

- [무JS 1440px](topic-404-nojs-1440.png) / [무JS 390px](topic-404-nojs-390.png)
- [JS 1440px](topic-404-js-1440.png) / [JS 390px](topic-404-js-390.png)
- [수정 전 무JS 1440px](before-nojs-1440.png)

오류 화면 캡처에 모의 게시글이나 개인정보는 없다. 원시 서버 로그·쿠키·인증 토큰·환경파일은 포함하지 않았다.
