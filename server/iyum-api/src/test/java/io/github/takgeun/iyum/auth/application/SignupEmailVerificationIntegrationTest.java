package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.auth.domain.EmailVerificationToken;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenHasher;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenRepository;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.domain.TermsCode;
import io.github.takgeun.iyum.member.infrastructure.MemberAgreementRepository;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 테스트할 항목
 * - 커밋 후 발송 : 메일을 보낼 때 다른 트랜잭션에서도 저장된 데이터가 보이는가
 * - 이벤트 발행 후 롤백 : 메일을 보내지 않는가
 * - 토큰 저장 실패 : 회원 및 약관도 롤백되는가
 * - SMTP 실패 : 가입 데이터와 PENDING 결과가 유지되는가
 * - 중복 가입 : 토큰과 메일이 추가로 생기지 않는가
 */

// 애플리케이션의 Spring 환경을 구성
// 서비스, Repository, 설정 클래스 등 애플리케이션의 빈을 로딩해서 여러 계층이 실제로 연결된 상태로 테스트할 수 있게 한다.
@SpringBootTest(properties = {
        "spring.config.import=",    // 해당 설정을 빈 값으로 덮어써, 기존에 이 키로 지정한 .env 가져오기를 막는다.
        "spring.jpa.hibernate.ddl-auto=validate",   // JPA가 엔티티와 DB 구조가 맞는지 검사한다.
        "spring.flyway.enabled=true",   // Flyway가 마이그레이션 SQL로 테스트 DB 구조를 준비
        // 앞서 작성한 조건부 SMTP 설정 클래스 비활성화 (실제 SMTP 발송 구현체가 자동으로 등록되는 것을 막는 용도)
        // 실제 SMTP 설정은 끄고 리스너(EmailVerificationMailRequestedListener)만 가져올 의도
        "iyum.auth.email-verification.mail.enabled=false"
})
@Testcontainers // 테스트용 PostgreSQL 실행
@Import(EmailVerificationMailRequestedListener.class)   // 테스트할 이벤트 리스너를 명시적으로 등록
public class SignupEmailVerificationIntegrationTest {

    // 테스트용 PostgreSQL 실행
    // static이므로 이 클래스 안의 테스트들은 같은 DB 컨테이너 공유
    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    // @DynamicPropertySource : Spring을 테스트 DB에 연결
    // 컨테이너가 실행되면서 결정된 연결 정보를 Spring 설정에 등록한다.
    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {

        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    SignupService signupService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    MemberAgreementRepository memberAgreementRepository;

    @Autowired
    EmailVerificationTokenHasher tokenHasher;

    @Autowired
    PlatformTransactionManager transactionManager;

    // 이 놈은 @MockitoSpyBean 으로 선언해야 한다.
    // @MockitoBean 으로 선언할 경우 해당 tokenRepository는 DB 작업을 하지 않는 mock으로 만들어버린다.
    // tokenRepository.save(token) -> DB에 token을 저장하지 않는다.
    // tokenRepository.findByTokenHash(...) -> 별도 설정이 없으므로 Optional.empty() 반환
    // tokenRepository.count() -> 기본값 0 반환
    // tokenRepository.deleteAllInBatch() -> 실제 삭제하지 않음
    // @MockitoSpyBean은 실제 빈을 감싸서 기본적으로 실제 메서드를 실행하면서 특정 호출만 테스트용 동작으로 바꿀 수 있게 해준다.
    @MockitoSpyBean
    EmailVerificationTokenRepository tokenRepository;

    // 메일은 실제로 보내면 안되므로 여기서는 @MockitoBean 선언
    @MockitoBean
    EmailVerificationMailSender mailSender; // 리스너에 해당 Mock 주입 -> Mailpit이 실행되지 않아도 이 통합 테스트가 가능하게 함

    @Autowired
    ThreadPoolTaskExecutor emailVerificationMailExecutor;

    @AfterEach
    void awaitMailTasks() {
        await().until(() -> emailVerificationMailExecutor.getActiveCount() == 0
                && emailVerificationMailExecutor.getQueueSize() == 0);
    }

    @BeforeEach
    void cleanDatabase() {
        tokenRepository.deleteAllInBatch();
        memberAgreementRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void 데이터가_커밋된_뒤_저장된_이메일로_발송한다() {

        // 1. 검증 결과를 보관할 변수 준비
        // "메일 발송 시점에 커밋된 데이터가 보였는가?"를 저장할 변수
        // 처음에는 false, 나중에 확인에 성공하면 true로 바뀔 예정
        // AtomicBoolean : 람다 내부에서 값을 변경하고, 람다 바깥에서 확인하기 편하기 떄문에 사용 (반드시 멀티스레드로 실행된다는 뜻은 아님)
        AtomicBoolean committedDataVisible =
                new AtomicBoolean(false);

        // 2. 별도 트랜잭션으로 조회할 도구 준비
        // TransactionTemplate : 코드로 트랜잭션의 실행 범위를 지정하는 도구
        TransactionTemplate independentRead =
                new TransactionTemplate(transactionManager);

        // REQUIRES_NEW : 기존 트랜재겻ㄴ에 참여하지 않고 독립적인 트랜잭션 사용
        // 별도 트랜잭션이 필요한 이유
        // 가입 트랜잭션 안에서는 아직 커밋되지 않은 자기 데이터를 조회할 수 있음.
        // 따라서 같은 트랜잭션에서는 조회하는 것만으로는 커밋을 입증할 수 없다.
        // 여기서는 PostgreSQL의 일반적인 격리 설정에서 다른 트랜잭션에도 데이터가 보이는지 확인한다.
        independentRead.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        independentRead.setReadOnly(true);  // 조회 용도임을 트랜잭션 관리자에게 알림

        // 3. mock의 send()가 호출될 때 실행할 동작 지정
        // mailSender.send(수신자, 발급된 토큰)이 호출되면 실제 발송 대신 이 람다를 호출해라.
        doAnswer(invocation -> {
            // 발송 시점에 수행할 검증
            String recipient = invocation.getArgument(0);   // 수신자 이메일

            IssuedEmailVerificationToken issuedToken =
                    invocation.getArgument(1);      // 발급된 토큰 정보

            // 4. 발송 시점에 독립적인 트랜잭션으로 DB 확인
            // execute가 새로운 조회 트랜잭션을 시작하고 아래 조건을 검사한다.
            // 회원이 존재함
            // && 저장된 이메일이 수신자와 같음
            // && 회원 상태가 PENDING
            // && 약관 전체 건수가 2건
            // && 토큰 해시로 토큰이 조회됨
            Boolean visible = independentRead.execute(status ->

                    // findById(issuedToken.memberId()) : 토큰에 해당하는 회원이 저장됐는가
                    memberRepository
                            .findById(issuedToken.memberId())
                            .map(member ->
                                    // member.getEmail().equals(recipient) : DB에 저장된 이메일로 발송하는가
                                    member.getEmail().equals(recipient)
                                            // member.getStatus() == PENDING : 회원이 인증 대기 상태인가
                                            && member.getStatus() == MemberStatus.PENDING
                            )
                            .orElse(false)
                            // memberAgreementRepository.count() == 2 : 약관 동의가 2건 저장됐는가
                            && memberAgreementRepository.count() == 2

                            && tokenRepository.findByTokenHash(
                            tokenHasher.hash(
                                    issuedToken.rawToken()
                            )
                            // findByTokenHash(hash(rawToken)).isPresent() : 전달할 원본 토큰의 해시가 DB에 저장됐는가
                    ).isPresent()
            );      // 위 조건을 모두 만족해야 visible이 true가 된다.

            committedDataVisible.set(
                    Boolean.TRUE.equals(visible)
            );

            return null;        // 반환값이 없는 send()에 대한 Mockito 응답을 마무리하는 것
        })
                .when(mailSender)
                .send(
                        anyString(),
                        any(IssuedEmailVerificationToken.class)
                );

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        // 5. 가입을 외부 트랜잭션으로 감싸서 실행한다.
        // 이 단계에서 실제로 가입을 실행한다.
        /**
         * 테스트 자체에 별도 트랜잭션이 없고, signupService.signup()이 기본 REQUIRED 전파 방식이라는 전제 하에
         * 1. transactionTemplate이 가입 트랜잭션 시작
         * 2. signupService.signup()이 그 트랜잭션에 참여
         * 3. 회원, 약관, 토큰을 저장하고 메일 요청 이벤트 발행
         * 4. 서비스가 반환돼도 바깥 트랜잭션은 아직 커밋되지 않음.
         * 5. verifyNoInteractions(mailSender)로 메일이 아직 호출되지 않았는지 확인
         * 6. 람다가 created를 반환하면 TransactionTemplate이 커밋을 진행한다.
         */
        SignupResult result = transactionTemplate.execute(status -> {
            SignupResult created =
                    signupService.signup(validCommand());

            // 아직 외부 트랜잭션이 커밋되지 않았습니다.
            verifyNoInteractions(mailSender);

            return created;
        });

        // 6. 가입 결과와 발송 시점 검증 결과 확인
        // - 가입 결과가 반환됐다.
        assertThat(result).isNotNull();
        // - 가입 직후 상태는 PENDING
        assertThat(result.status())
                .isEqualTo(MemberStatus.PENDING);

        // 메일 발송 시점에 별도 트랜잭션에서도 가입 데이터가 조회됐다.
        await().untilTrue(committedDataVisible);

        ArgumentCaptor<IssuedEmailVerificationToken> captor =
                ArgumentCaptor.forClass(
                        IssuedEmailVerificationToken.class
                );

        // 7. 발송 인자를 캡처해서 회원, 토큰 연결 확인
        verify(mailSender, timeout(3000)).send(
                eq("signup-mail@example.com"),
                captor.capture()
        );

        IssuedEmailVerificationToken issuedToken =
                captor.getValue();

        assertThat(issuedToken.memberId())
                .isEqualTo(result.memberId());

        EmailVerificationToken stored = tokenRepository
                .findByTokenHash(
                        tokenHasher.hash(issuedToken.rawToken())
                )
                .orElseThrow();

        assertThat(stored.getMemberId())
                .isEqualTo(result.memberId());

        assertThat(stored.getTokenHash())
                .isNotEqualTo(issuedToken.rawToken());

        assertStoredCounts(1, 2, 1);
    }

    @Test
    void 이벤트_발행_후_롤백되면_메일을_보내지_않는다() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    signupService.signup(validCommand());

                    throw new IllegalStateException(
                            "커밋 전 실패 테스트"
                    );
                })
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("커밋 전 실패 테스트");

        verifyNoInteractions(mailSender);
        assertStoredCounts(0, 0, 0);
    }

    @Test
    void 토큰_저장이_실패하면_회원과_약관도_롤백한다() {
        doThrow(
                new DataIntegrityViolationException(
                        "토큰 저장 실패 테스트"
                )
        )
                .when(tokenRepository)
                .save(any(EmailVerificationToken.class));

        assertThatThrownBy(() ->
                signupService.signup(validCommand())
        ).isInstanceOf(DataIntegrityViolationException.class);

        verifyNoInteractions(mailSender);
        assertStoredCounts(0, 0, 0);
    }

    @Test
    void 메일_발송이_실패해도_가입_결과와_데이터를_유지한다() {
        doThrow(
                new MailSendException(
                        "SMTP 연결 실패 테스트"
                )
        )
                .when(mailSender)
                .send(
                        anyString(),
                        any(IssuedEmailVerificationToken.class)
                );

        SignupResult result =
                signupService.signup(validCommand());

        assertThat(result.status())
                .isEqualTo(MemberStatus.PENDING);

        assertThat(
                memberRepository.findById(result.memberId())
                        .orElseThrow()
                        .getStatus()
        ).isEqualTo(MemberStatus.PENDING);

        verify(mailSender, timeout(3000)).send(
                eq("signup-mail@example.com"),
                any(IssuedEmailVerificationToken.class)
        );

        assertStoredCounts(1, 2, 1);
    }

    @Test
    void 중복_가입은_추가_토큰과_메일을_생성하지_않는다() {
        signupService.signup(validCommand());

        awaitMailTasks();

        // 첫 번째 정상 가입의 호출 기록만 지웁니다.
        clearInvocations(mailSender);

        assertThatThrownBy(() ->
                signupService.signup(validCommand())
        ).isInstanceOf(SignupConflictException.class);

        verifyNoInteractions(mailSender);
        assertStoredCounts(1, 2, 1);
    }

    private void assertStoredCounts(
            long members,
            long agreements,
            long tokens
    ) {
        assertThat(memberRepository.count())
                .isEqualTo(members);

        assertThat(memberAgreementRepository.count())
                .isEqualTo(agreements);

        assertThat(tokenRepository.count())
                .isEqualTo(tokens);
    }

    private SignupCommand validCommand() {
        return new SignupCommand(
                " Signup-Mail@Example.com ",
                "Password123!",
                "SignupMailUser",
                List.of(
                        new SignupAgreement(
                                TermsCode.SERVICE_TERMS,
                                "dev-v1",
                                true
                        ),
                        new SignupAgreement(
                                TermsCode.PRIVACY_COLLECTION_USE,
                                "dev-v1",
                                true
                        )
                )
        );
    }
}
