package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.auth.domain.EmailVerificationToken;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenHasher;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "iyum.auth.email-verification.token-ttl=15m"
})
@Testcontainers
@Import(
        EmailVerificationTokenIssueServiceIntegrationTest
                .FixedClockConfig.class
)
public class EmailVerificationTokenIssueServiceIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-10-06T00:00:00Z");

    // @Container : 이 컨테이너의 시작과 종료를 Testcontainers가 관리하도록 표시
    /**
     * 1. 테스트 클래스의 테스트 실행 전에 PostgreSQL 컨테이너 시작
     * 2. 여러 @Test 메서드가 같은 컨테이너 사용 (static 썼으니까)
     * 3. 마지막 테스트가 끝나면 컨테이너 종료
     */
    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

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
    EmailVerificationTokenIssueService issueService;

    @Autowired
    EmailVerificationTokenRepository tokenRepository;

    @Autowired
    EmailVerificationTokenHasher tokenHasher;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        // deleteAllInBatch() : 해당 Repository가 관리하는 테이블의 모든 행을 한 번의 DELETE 쿼리로 삭제하는 메소드
        // 여기서 토큰을 먼저 삭제하는 이유는 외래 키 관계를 생각하면 된다. (토큰의 member_id가 회원을 참조하고 있으므로)
        tokenRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void 원본에_대응하는_해시와_설정된_만료_시각을_저장한다() {
        Long memberId = createPendingMember();

        IssuedEmailVerificationToken result = issueService.issue(memberId);

        String expectedHash = tokenHasher.hash(result.rawToken());

        EmailVerificationToken saved = tokenRepository
                .findByTokenHash(expectedHash)
                .orElseThrow();

        assertThat(tokenRepository.count()).isEqualTo(1);
        assertThat(result.memberId()).isEqualTo(memberId);
        assertThat(result.rawToken())
                .matches("[A-Za-z0-9_-]{43}");

        assertThat(saved.getMemberId()).isEqualTo(memberId);
        assertThat(saved.getTokenHash())
                .isEqualTo(expectedHash)
                .isNotEqualTo(result.rawToken());

        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getExpiresAt())
                .isEqualTo(NOW.plusSeconds(15 * 60));
        assertThat(result.expiresAt())
                .isEqualTo(saved.getExpiresAt());

        assertThat(saved.getUsedAt()).isNull();
        assertThat(saved.getRevokedAt()).isNull();
        assertThat(saved.isUsable(NOW)).isTrue();

        Member member = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(member.getStatus())
                .isEqualTo(MemberStatus.PENDING);

        assertThat(result.toString())
                .doesNotContain(result.rawToken());
    }

    @Test
    void 존재하지_않는_회원에게는_발급하지_않는다() {
        assertThatThrownBy(() ->
                issueService.issue(Long.MAX_VALUE)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 회원입니다.");

        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void 이미_활성화된_회원에게는_발급하지_않는다() {
        Member member = newPendingMember();
        member.activeAfterEmailVerification();

        Long memberId = memberRepository
                .saveAndFlush(member)
                .getId();

        assertThatThrownBy(() ->
                issueService.issue(memberId)
        ).isInstanceOf(IllegalStateException.class);

        assertThat(tokenRepository.count()).isZero();

        Member reloaded = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(reloaded.getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void 외부_트랜잭션이_실패하면_토큰_저장도_롤백된다() {
        Long memberId = createPendingMember();

        // 외부 트랜잭션 만들기
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    issueService.issue(memberId);

                    throw new IllegalStateException(
                            "후속 처리 실패 테스트"
                    );
                })
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("후속 처리 실패 테스트");

        assertThat(tokenRepository.count()).isZero();
        assertThat(memberRepository.existsById(memberId)).isTrue();
    }

    private Long createPendingMember() {
        return memberRepository
                .saveAndFlush(newPendingMember())
                .getId();
    }

    private Member newPendingMember() {
        return Member.createPending(
                "issue_test@example.com",
                "test-only-password-hash",
                "IssueUser"
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        // 테스트용 시계 주입
        @Bean
        @Primary    // 같은 타입의 빈이 여러 개 있을 때 이 빈을 우선 선택하도록 설정 -> 따라서 이 테스트의 서비스는 현재 실제 시각 대신 NOW 사용하게 될 것
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
