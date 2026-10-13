# 백엔드 패키지 구조 — 2026-10-13

## 기준과 범위

`feat/community-posts`, HEAD `6998b266292fc8b4d253a50bd86650615e1089eb`의 **작업 시작 당시 로컬 파일**을 기준으로 정리했다. GitHub main으로 덮어쓰지 않았다. 사용자가 이미 이동한 12개 파일과 수정 중인 package/import를 보존해 이어서 완성했다. 기존 staged 내용도 그대로이며 이번 변경을 추가 stage/commit/push하지 않았다.

기본 패키지는 `io.github.takgeun.iyum`이다. 메인 `IyumApiApplication`은 루트에 유지한다.

```text
io.github.takgeun.iyum
├── IyumApiApplication
├── auth
│   ├── signup
│   │   ├── api / dto / validation
│   │   └── application
│   ├── emailverification
│   │   ├── api / dto
│   │   ├── application
│   │   ├── domain
│   │   ├── infrastructure
│   │   └── config
│   ├── session
│   │   ├── api / dto / validation
│   │   └── config
│   └── security
│       └── config
├── member
│   ├── domain
│   └── infrastructure
└── global
    ├── config
    └── error
```

위의 `dto`·`validation`은 각각 `api` 아래 하위 패키지다. 테스트도 대상 기능의 패키지에 맞췄다. 존재하지 않는 커뮤니티 패키지, 빈 계층, 새 서비스·로그아웃 컨트롤러·인터페이스는 만들지 않았다.

## 각 영역의 책임

| 패키지 | 역할 |
| --- | --- |
| `auth.signup.api` | 가입 요청/응답 DTO, `SignupController`, 가입 전용 복합 검증 |
| `auth.signup.application` | `SignupService`, 명령·결과·예외, 가입에 필요한 약관 항목/버전/동의 검사 |
| `auth.emailverification` | 인증·재전송 API, 토큰 발급·검증·잠금·만료, 토큰 엔티티/저장소, 난수/해시, 이벤트·SMTP와 전용 설정 |
| `auth.session.api` | 로그인·현재 회원·CSRF API 및 DTO. UTF-8 바이트 검증은 현재 `LoginRequest`만 사용하므로 여기의 `validation`에 둠 |
| `auth.session.config` | 세션 SecurityContext 저장소, CSRF 저장소, 로그인 시 세션 ID/CSRF 갱신 전략 |
| `auth.security` | Spring Security 인증 주체·회원 조회·로그인 상태 검사. `config`는 인증 Provider/Manager 및 가입·로그인이 함께 쓰는 PasswordEncoder 등록 |
| `member.domain` | 회원 상태·역할·입력 규칙과 영속 회원·약관 동의 데이터 |
| `member.infrastructure` | 회원과 동의 기록의 JPA 저장소 |
| `global.config` | 모든 요청의 SecurityFilterChain, CORS, 전체 OpenAPI, 공통 JPA auditing |
| `global.error` | 공통 JSON 오류 계약과 애플리케이션 예외 처리 |

`signup.application`은 인증 메일 기능의 토큰 발급 및 이벤트 타입을 사용한다. 이 실제 의존성은 유지했다. 이를 숨기기 위한 불필요한 공통 패키지나 인터페이스를 추가하지 않았다.

## 기존 사용자 정리와 추가 정리

사용자는 총 **12개 파일의 이동**을 이미 준비했다. 회원가입 영역 5개(`SignupController`, `SignupService`, `AgreementRequest`, `SignupRequest`, `SignupResponse`)를 `auth.signup`으로, 인증/재전송 DTO 4개를 `auth.emailverification`으로, 세션 DTO 3개를 `auth.session`으로 옮겼다. 이 위치들을 되돌리지 않았다.

이번에는 **운영 클래스 37개, 테스트 22개를 추가 이동**하고, 위치를 유지한 13개 Java 파일의 참조를 정리했다. DTO만 이동된 상태를 컨트롤러·서비스·검증·설정·테스트까지 일관되게 완성했다.

아래 경로는 `src/main/java/io/github/takgeun/iyum/` 기준이다.

| 이동 전 | 이동 후 |
| --- | --- |
| `auth/api/{EmailVerificationController,EmailVerificationResendController}.java` | `auth/emailverification/api/` |
| `auth/application/EmailVerification*.java`, `InvalidEmailVerificationTokenException.java`, `IssuedEmailVerificationToken.java` | `auth/emailverification/application/` |
| `auth/domain/EmailVerificationToken.java` | `auth/emailverification/domain/` |
| `auth/infrastructure/`의 토큰 생성·해시·저장소·SMTP 클래스 | `auth/emailverification/infrastructure/` |
| `global/config/EmailVerification*.java` 5개 | `auth/emailverification/config/` |
| `auth/application/Signup{Agreement,Command,ConflictException,Result,TermsException,TermsPolicy}.java` | `auth/signup/application/` |
| `auth/api/validation/{ValidSignupRequest,SignupRequestValidator}.java` | `auth/signup/api/validation/` |
| `auth/api/{CsrfController,CurrentMemberController,LoginController}.java` | `auth/session/api/` |
| `auth/api/validation/Utf8ByteLength*.java` | `auth/session/api/validation/` |
| `global/config/SessionSecurityConfig.java` | `auth/session/config/` |
| `global/config/{AuthenticationConfig,PasswordConfig}.java` | `auth/security/config/` |

`SignupEmailVerificationIntegrationTest`는 가입 시의 트랜잭션·메일 이벤트를 검증하므로 `auth.signup.application`에 둔다. 일반 인증·재전송·SMTP 테스트는 `auth.emailverification`에 둔다.

## 유지한 구조와 동작

- `MemberAgreement`, `TermsCode`, `MemberAgreementRepository`는 저장된 회원 동의 이력을 표현하므로 `member`에 유지한다. 가입 시 필수 동의/버전을 판단하는 `SignupTermsPolicy`만 가입 기능에 둔다.
- `MemberInputPolicy`는 가입·로그인·재전송에서 공유하는 회원 입력 규칙이므로 기존 `member.domain`을 유지한다.
- `SecurityConfig`는 인증뿐 아니라 앱 전체의 접근 권한, 공개 URL, CSRF, 로그아웃 필터를 정의하므로 `global.config`에 유지한다. 옮겨진 `SessionSecurityConfig`의 명시적 `@Import` 참조만 갱신한다.
- CORS는 출처 정책, OpenAPI는 전체 API 명세, JPA auditing은 회원·토큰 공통 기반이므로 이동하지 않는다.
- `Clock` Bean은 현재 이메일 인증 기능에서만 사용하므로 이메일 인증 설정과 함께 이동한다. 미래의 공유 가능성을 이유로 새 전역 설정을 만들지 않는다.
- 기본 루트 아래로만 이동하여 컴포넌트·엔티티·Repository 자동 스캔 범위는 유지한다. 별도 스캔 설정을 추가하지 않았다.
- `clock`, `passwordEncoder`, `emailVerificationMailExecutor` 등 명시적 Bean 이름/Qualifier, 메일 활성화 조건, 설정 prefix, 프로필은 유지한다. `@EnableConfigurationProperties` 타입 등록은 새 위치를 참조한다. 프레임워크가 FQN으로 생성하는 프로퍼티 Bean 이름은 위치에 따라 달라지지만, 프로젝트에는 해당 이름의 문자열 주입이 없고 기존 타입 주입이 유지된다.
- 같은 패키지라 생략됐던 참조, 테스트 `@Import`, static import, 로컬 메일 테스트의 완전한 클래스명 참조도 갱신했다. package-private 접근을 public으로 넓히지 않았다.
- 103개 Java 파일의 package/import와 이동된 완전한 클래스명 외 처리 본문·주석을 시작 스냅샷과 비교했다. API URL·JSON·검증·오류·해시·트랜잭션·잠금 순서·AFTER_COMMIT·비동기 발송 로직은 바꾸지 않았다. SQL 마이그레이션, YAML 설정, 의존성, 프론트엔드는 그대로다.

## 검증

- 시작 당시 `compileJava compileTestJava` 성공. 사용자 부분 이동 때문에 깨진 컴파일은 없었다.
- Java 21로 **전체 clean test: 293개, 292 통과, 실패 0, 선택형 로컬 SMTP 1개 건너뜀**. 이어서 기존 `IYUM_RUN_LOCAL_MAIL_TEST=true` 조건으로 해당 1개도 실행해 통과했다. 테스트를 비활성화하거나 검증을 약화하지 않았다.
- 기존 8082 서버를 보존하기 위해 임시 Gradle init script로 buildDirectory만 분리하여 `./gradlew --init-script <임시 스크립트> clean test`를 실행했다. 빈 빌드 경로여서 이동 전 class가 섞이지 않는다. 실행 JAR에도 이전 auth 패키지 class가 없다.
- 변경 후 dev JAR을 18082, 시작 당시 로컬 소스 스냅샷의 dev JAR을 18083에서 기동했다. 기본 패키지 스캔, JPA Repository 3개, Flyway 4개 검증, 기존 스키마에 추가 migration 없이 시작함을 확인했다.
- 두 새 프로세스의 인증 API 7개 OpenAPI 명세(서버 주소 제외), 요청/응답 스키마, 가입/인증/재전송/로그인 입력 오류, CSRF 거부, 비로그인 401, 로그아웃 204 및 이후 401이 동일했다. dev Swagger, 허용/거부 CORS, 비공개 관리 엔드포인트 차단도 확인했다.
- 기존 8082 프로세스의 OpenAPI에는 가입 요청 스키마 일부가 빠져 있어 단순 비교가 처음 실패했다. 시작 소스와 변경 소스를 각각 새로 기동했을 때는 동일했다. 실행 중인 예전 프로세스의 상태 차이로 분리했으며, 이를 맞추기 위한 서비스 수정은 하지 않았다.
- 실제 Mailpit SMTP 테스트 메일 1건을 추가했다. 기존 메일을 삭제하지 않았고 외부 메일 서버는 사용하지 않았다. dev HTTP 확인은 입력 검증 실패/비로그인 세션만 사용했으며 회원 데이터를 생성하지 않았다. 통합 테스트 DB는 Testcontainers의 별도 DB다.
- `git diff --check`, 이전 패키지 참조/중복 클래스, package와 파일 경로, 본문/주석 보존 및 기존 staged 내용 일치 검사 통과.
- 직접 띄운 18082/18083 서버만 종료했다. 기존 8082 서버·PostgreSQL·Mailpit 컨테이너와 볼륨은 유지했다.

검증 산출물은 `/private/tmp/iyum-package-refactor-20261013/`에 보관한다. 실제 활성 회원을 사용하는 dev 가입→메일 인증→로그인 전체 시나리오와 운영 프로필/배포 검증은 수행하지 않았다. 해당 동작의 기존 자동 통합·컨트롤러 테스트는 전체 테스트에 포함됐다.

## 후속 작업 및 Git

추가 기능 문제를 고치기 위한 로직 변경은 없다. 커뮤니티 구현 시 새로운 기능 패키지의 API/애플리케이션/도메인을 실제 필요에 따라 추가하면 된다. 기존 실행 서버는 사용자가 준비된 시점에 재기동해 최신 클래스를 사용해야 한다.

현재 브랜치를 유지했으며 기존 staged 12개 이동은 그대로다. 이번 정리는 unstaged 변경과 새 경로의 미추적 파일로 남아 있어, 이후 커밋할 때는 삭제 경로와 새 경로를 함께 검토해야 한다.

추천 커밋 메시지: `refactor(auth): 인증 기능별 패키지와 테스트 구조 정리`
