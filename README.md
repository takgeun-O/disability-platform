# IYUM (이움)

**[IYUM 바로가기](https://iyum-web.vercel.app/) · [커뮤니티 둘러보기](https://iyum-web.vercel.app/community) · [API 문서](docs/api/README.md)**
> [!NOTE]
> **첫 접속 안내** · 한동안 접속이 없으면 데모 서버가 다시 시작되는 데 약 1분이 걸릴 수 있습니다. 화면은 열렸는데 게시글 목록이 보이지 않는다면, 접속 후 약 1분 뒤에 페이지를 새로고침해 주세요.

IYUM은 장애인이 일상에서 얻은 경험과 정보를 나누는 커뮤니티입니다. 청각장애인의 소통과 생활을 중심으로, 보청기·인공와우 사용 경험이나 실시간 자막 활용법처럼 당사자에게 필요한 이야기를 주제별로 찾고 나눌 수 있습니다.

2026.08.11부터 개발 중인 개인 프로젝트입니다.

## 주요 기능

- **커뮤니티·검색** — 로그인 없이 공개 게시글과 댓글을 읽고, 제목과 본문에서 키워드를 검색할 수 있습니다.
- **댓글·반응** — 게시글에 댓글과 답글을 작성하고 좋아요를 남길 수 있습니다. 베스트 답글은 원댓글과 함께 표시되며, 원문으로 이동해 대화의 맥락을 확인할 수 있습니다.
- **회원·내 활동** — 이메일 인증으로 가입하고, 마이페이지에서 내가 작성한 게시글과 댓글을 확인할 수 있습니다.
- **관리자** — 신고된 콘텐츠를 검토·처리하고, 게시글·댓글의 공개 상태와 회원 이용 상태를 관리할 수 있습니다.

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| 백엔드 | Java 21, Spring Boot 4.1, Spring Security |
| 데이터 접근·관리 | Spring Data JPA, QueryDSL, PostgreSQL, Flyway |
| 프론트엔드 | Next.js 16, React 19, TypeScript, Tailwind CSS |
| 테스트·API 문서 | JUnit 5, Testcontainers, OpenAPI / Swagger UI |
| 배포 | Vercel(Next.js), Render(Spring Boot API), Neon(PostgreSQL) |


## 운영·성장 참고 자료

- [커뮤니티 Cold Start 관련 참고 글](https://kwondoeon.substack.com/p/3-e43)
  - IYUM 초기 사용자 모집과 커뮤니티 활성화를 고민할 때 다시 읽어볼 자료.

[API 문서 목록](docs/api/README.md)
