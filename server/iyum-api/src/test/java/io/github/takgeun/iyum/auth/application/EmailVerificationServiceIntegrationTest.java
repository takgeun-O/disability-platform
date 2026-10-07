package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.auth.domain.EmailVerificationToken;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenHasher;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberAgreementRepository;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 이 테스트에서는 서비스 호출의 실제 커밋과 별도 스레드의 트랜잭션 확인할 것. (따라서 @Transactional로 테스트 전체를 감싸지 않는다.)
 *
 * 정상 인증
 * -> 회원 활성화와 토큰 사용 기록
 * 잘못된 형식·없는 토큰
 * -> 인증 거절
 * 만료·발급 전 시각
 * -> 인증 거절
 * 사용·폐기된 토큰
 * -> 인증 거절
 * PENDING 이외 회원 상태
 * -> 상태 유지
 * 같은 링크 재사용
 * -> 투 번째 인증 거절
 * 트랜잭션 롤백
 * -> 회원과 토큰 모두 원래 상태 유지
 * 동시 요청
 * -> 같은 토큰·다른 토큰 모두 한 요청만 성공
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
@Testcontainers
@Import(
        EmailVerificationServiceIntegrationTest.FixedClockConfig.class
)
public class EmailVerificationServiceIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-10-07T00:00:00Z");

    private static final String TOKEN_A = "A".repeat(43);
    private static final String TOKEN_B = "B".repeat(43);

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    // @DynamicPropertySource : Spring을 테스트 DB에 연결
    // 컨테이너가 실행되면서 결정된 연결 정보를 Spring 설정에 등록한다.
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    EmailVerificationService verificationService;

    @Autowired
    EmailVerificationTokenRepository tokenRepository;

    @Autowired
    EmailVerificationTokenHasher tokenHasher;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    MemberAgreementRepository memberAgreementRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long memberId;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAllInBatch();
        memberAgreementRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member member = Member.createPending(
                "verify@example.com",
                "test-only-password-hash",
                "VerifyUser"
        );

        memberId = memberRepository.saveAndFlush(member).getId();
    }

    @Test
    void 정상_토큰이면_회원_활성화와_토큰_사용을_함께_저장한다() {
        saveToken(TOKEN_A);

        EmailVerificationResult result =
                verificationService.verify(TOKEN_A);

        assertThat(result.memberId()).isEqualTo(memberId);
        assertThat(result.status()).isEqualTo(MemberStatus.ACTIVE);

        assertMemberStatus(MemberStatus.ACTIVE);

        EmailVerificationToken saved = token(TOKEN_A);

        assertThat(saved.getUsedAt()).isEqualTo(NOW);
        assertThat(saved.getRevokedAt()).isNull();
        assertThat(saved.isUsable(NOW)).isFalse();
        assertThat(saved.getTokenHash())
                .isEqualTo(tokenHasher.hash(TOKEN_A));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "short", "token with spaces"})
    void 잘못된_형식의_토큰은_거절한다(String rawToken) {
        assertRejected(rawToken);

        assertMemberStatus(MemberStatus.PENDING);
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void 형식이_맞아도_DB에_없는_토큰은_거절한다() {
        assertRejected(TOKEN_A);

        assertMemberStatus(MemberStatus.PENDING);
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void 만료_시각과_현재_시각이_같으면_거절한다() {
        saveToken(
                TOKEN_A,
                NOW.minusSeconds(1800),
                NOW
        );

        assertRejected(TOKEN_A);
        assertPendingAndUnused(TOKEN_A);
    }

    @Test
    void 발급_시각_이전에는_인증할_수_없다() {
        saveToken(
                TOKEN_A,
                NOW.plusSeconds(1),
                NOW.plusSeconds(1800)
        );

        assertRejected(TOKEN_A);
        assertPendingAndUnused(TOKEN_A);
    }

    @Test
    void 이미_사용한_토큰은_거절한다() {
        saveToken(TOKEN_A);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status ->
                        token(TOKEN_A).use(NOW.minusSeconds(1))
                );

        assertRejected(TOKEN_A);

        assertMemberStatus(MemberStatus.PENDING);
        assertThat(token(TOKEN_A).getUsedAt())
                .isEqualTo(NOW.minusSeconds(1));
    }

    @Test
    void 폐기된_토큰은_거절한다() {
        saveToken(TOKEN_A);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status ->
                        token(TOKEN_A).revoke(NOW.minusSeconds(1))
                );

        assertRejected(TOKEN_A);

        assertPendingAndUnused(TOKEN_A);
        assertThat(token(TOKEN_A).getRevokedAt())
                .isEqualTo(NOW.minusSeconds(1));
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = "PENDING",
            mode = EnumSource.Mode.EXCLUDE
    )
    void 인증_대기_이외의_회원_상태는_변경하지_않는다(
            MemberStatus memberStatus
    ) {
        saveToken(TOKEN_A);

        // 다양한 회원 상태를 준비하기 위한 테스트 DB 설정입니다.
        jdbcTemplate.update(
                "update members set status = ? where id = ?",
                memberStatus.name(),
                memberId
        );

        assertRejected(TOKEN_A);

        assertMemberStatus(memberStatus);
        assertThat(token(TOKEN_A).getUsedAt()).isNull();
    }

    @Test
    void 성공한_인증_링크를_다시_사용할_수_없다() {
        saveToken(TOKEN_A);

        verificationService.verify(TOKEN_A);

        assertRejected(TOKEN_A);

        assertMemberStatus(MemberStatus.ACTIVE);
        assertThat(token(TOKEN_A).getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void 트랜잭션이_실패하면_회원과_토큰_변경이_함께_롤백된다() {
        saveToken(TOKEN_A);

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                transaction.executeWithoutResult(status -> {
                    verificationService.verify(TOKEN_A);

                    // UPDATE SQL까지 실행한 뒤 롤백되는지 확인합니다.
                    tokenRepository.flush();

                    throw new IllegalStateException("후속 처리 실패");
                })
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("후속 처리 실패");

        assertPendingAndUnused(TOKEN_A);
    }

    @ParameterizedTest
    // false : 같은 토큰으로 인증 요청 두 개
    // true : 같은 회원의 서로 다른 토큰으로 인증 요청 두 개
    @ValueSource(booleans = {false, true})
    void 같은_회원의_동시_인증은_한_요청만_성공한다(
            boolean differentToken
    ) throws Exception {

        saveToken(TOKEN_A);

        String secondToken;

        if (differentToken) {
            saveToken(TOKEN_B);
            secondToken = TOKEN_B;
        } else {
            secondToken = TOKEN_A;
        }

        // CountDownLatch : 두 스레드의 진행 시점을 조절하는 도구
        // 첫 번째 요청의 커밋을 잠시 보류해서, 두 번째 요청이 들어왔을 때 실제 DB 트랜잭션이 겹치도록 유도할 것
        CountDownLatch firstVerified = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<EmailVerificationResult> first = executor.submit(() ->
                    new TransactionTemplate(transactionManager)
                            .execute(status -> {
                                EmailVerificationResult result =
                                        verificationService.verify(TOKEN_A);

                                tokenRepository.flush();

                                // 첫 번째 인증은 처리했지만 아직 커밋하지 않습니다.
                                firstVerified.countDown();
                                await(allowFirstCommit);

                                return result;
                            })
            );

            await(firstVerified);

            Future<Boolean> second = executor.submit(() -> {
                secondStarted.countDown();

                try {
                    verificationService.verify(secondToken);
                    return true;
                } catch (InvalidEmailVerificationTokenException exception) {
                    return false;
                }
            });

            await(secondStarted);

            // 첫 번째 트랜잭션이 끝나기 전에는
            // 두 번째 인증 처리가 완료되지 않아야 합니다.
            assertThatThrownBy(() ->
                    second.get(200, TimeUnit.MILLISECONDS)
            ).isInstanceOf(TimeoutException.class);

            allowFirstCommit.countDown();

            EmailVerificationResult firstResult =
                    first.get(10, TimeUnit.SECONDS);

            assertThat(firstResult).isNotNull();
            assertThat(firstResult.status())
                    .isEqualTo(MemberStatus.ACTIVE);

            assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();

            assertMemberStatus(MemberStatus.ACTIVE);
            assertThat(token(TOKEN_A).getUsedAt()).isEqualTo(NOW);

            long usedTokenCount = tokenRepository.findAll()
                    .stream()
                    .filter(token -> token.getUsedAt() != null)
                    .count();

            assertThat(usedTokenCount).isEqualTo(1);

            if (differentToken) {
                assertThat(token(TOKEN_B).getUsedAt()).isNull();
            }
        } finally {
            allowFirstCommit.countDown();
            executor.shutdownNow();

            assertThat(
                    executor.awaitTermination(10, TimeUnit.SECONDS)
            ).isTrue();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if(!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("테스트 대기 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "테스트 대기 중 인터럽트 발생",
                    exception
            );
        }
    }

    private void assertPendingAndUnused(String rawToken) {
        // 회원이 여전히 인증 대기 상태인지 확인
        assertMemberStatus(MemberStatus.PENDING);

        // 토큰에 사용 기록이 없는지 확인
        EmailVerificationToken saved = token(rawToken);

        assertThat(saved.getUsedAt()).isNull();
    }

    private void assertRejected(String rawToken) {
        assertThatThrownBy(() ->
                verificationService.verify(rawToken)
        ).isInstanceOf(InvalidEmailVerificationTokenException.class);
    }

    private EmailVerificationToken token(String rawToken) {
        return tokenRepository
                .findByTokenHash(tokenHasher.hash(rawToken))
                .orElseThrow();
    }

    private void assertMemberStatus(MemberStatus expected) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(member.getStatus()).isEqualTo(expected);
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
    }

    private void saveToken(String rawToken) {
        saveToken(
                rawToken,
                NOW.minusSeconds(60),
                NOW.plusSeconds(1800)
        );
    }

    private void saveToken(
            String rawToken,
            Instant createdAt,
            Instant expiresAt
    ) {
        EmailVerificationToken token = EmailVerificationToken.issue(
                memberId,
                tokenHasher.hash(rawToken),
                createdAt,
                expiresAt
        );

        tokenRepository.saveAndFlush(token);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        // 테스트용 시계 주입
        @Bean
        @Primary
        // 같은 타입의 빈이 여러 개 있을 때 이 빈을 우선 선택하도록 설정 -> 따라서 이 테스트의 서비스는 현재 실제 시각 대신 NOW 사용하게 될 것
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
