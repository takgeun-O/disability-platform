package io.github.takgeun.iyum.auth.passwordreset.application;

import io.github.takgeun.iyum.auth.passwordreset.domain.PasswordResetToken;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenGenerator;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenHasher;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true",
        "iyum.auth.password-reset.mail.enabled=false",
        "iyum.auth.email-verification.mail.enabled=false"
})
@Testcontainers
@org.springframework.test.context.event.RecordApplicationEvents
class PasswordResetServiceIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-10-21T05:00:00Z");

    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "NewPassword456!";

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    PasswordResetService service;

    @Autowired
    PasswordResetTokenValidationService validation;

    @Autowired
    org.springframework.test.context.event.ApplicationEvents applicationEvents;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordResetTokenRepository tokenRepository;

    @Autowired
    PasswordResetTokenGenerator tokenGenerator;

    @Autowired
    PasswordResetTokenHasher tokenHasher;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void 사전_확인을_반복해도_DB와_이벤트는_변하지_않고_이후_변경할_수_있다() {
        Fixture fixture = createFixture();
        var before = databaseSnapshot(fixture.memberId());
        validation.validate(fixture.rawToken()); validation.validate(fixture.rawToken());
        assertThat(databaseSnapshot(fixture.memberId())).isEqualTo(before);
        assertThat(applicationEvents.stream(PasswordResetCompleted.class).count()).isZero();
        assertThat(applicationEvents.stream(PasswordResetMailRequested.class).count()).isZero();
        service.reset(fixture.rawToken(), NEW_PASSWORD);
        assertThat(findVersion(fixture.memberId())).isEqualTo(1L);
        assertThatThrownBy(() -> validation.validate(fixture.rawToken())).isInstanceOf(PasswordResetException.class);
        // 계정의 과거 재설정 여부가 아니라 새 토큰의 현재 사용 가능 여부를 확인한다.
        var raw = tokenGenerator.generate();
        tokenRepository.saveAndFlush(PasswordResetToken.issue(fixture.memberId(), tokenHasher.hash(raw), NOW, NOW.plusSeconds(60)));
        validation.validate(raw);
        service.reset(raw, "AnotherPassword789!");
        assertThat(findVersion(fixture.memberId())).isEqualTo(2L);
    }

    private long findVersion(Long memberId) { return memberRepository.findById(memberId).orElseThrow().getCredentialVersion(); }

    @Test
    void 사전_확인_후_만료되면_완료_API의_재검증이_거절한다() {
        Fixture fixture = createFixture();
        validation.validate(fixture.rawToken());
        var before = databaseSnapshot(fixture.memberId());
        when(clock.instant()).thenReturn(NOW.plusSeconds(1800));
        assertThatThrownBy(() -> service.reset(fixture.rawToken(), NEW_PASSWORD))
                .isInstanceOfSatisfying(PasswordResetException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PasswordResetException.Code.PASSWORD_RESET_TOKEN_INVALID));
        assertThat(databaseSnapshot(fixture.memberId())).isEqualTo(before);
        assertThat(applicationEvents.stream(PasswordResetCompleted.class).count()).isZero();
    }

    @Test
    void 사전_확인_후_다른_요청이_사용하면_완료_API가_다시_거절한다() {
        Fixture fixture = createFixture();
        validation.validate(fixture.rawToken());
        service.reset(fixture.rawToken(), NEW_PASSWORD);
        var before = databaseSnapshot(fixture.memberId());
        assertThatThrownBy(() -> service.reset(fixture.rawToken(), "AnotherPassword789!"))
                .isInstanceOfSatisfying(PasswordResetException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PasswordResetException.Code.PASSWORD_RESET_TOKEN_INVALID));
        assertThat(databaseSnapshot(fixture.memberId())).isEqualTo(before);
    }

    @Test
    void 비밀번호와_인증_버전을_변경하고_토큰_상태를_저장한다() {
        Fixture fixture = createFixture();

        Long otherTokenId = createAdditionalToken(fixture.memberId());

        service.reset(fixture.rawToken(), NEW_PASSWORD);

        Member member = memberRepository.findById(fixture.memberId())
                .orElseThrow();

        assertThat(
                passwordEncoder.matches(
                        NEW_PASSWORD,
                        member.getPasswordHash()
                )
        ).isTrue();

        assertThat(
                passwordEncoder.matches(
                        OLD_PASSWORD,
                        member.getPasswordHash()
                )
        ).isFalse();

        assertThat(member.getCredentialVersion()).isEqualTo(1L);

        PasswordResetToken usedToken =
                tokenRepository.findById(fixture.tokenId())
                        .orElseThrow();

        assertThat(usedToken.getUsedAt()).isEqualTo(NOW);
        assertThat(usedToken.getRevokedAt()).isNull();
        assertThat(usedToken.isUsable(NOW)).isFalse();

        PasswordResetToken otherToken =
                tokenRepository.findById(otherTokenId)
                        .orElseThrow();

        assertThat(otherToken.getUsedAt()).isNull();
        assertThat(otherToken.getRevokedAt()).isEqualTo(NOW);
        assertThat(otherToken.isUsable(NOW)).isFalse();
    }

    @Test
    void 사용한_토큰으로_다시_변경할_수_없다() {
        Fixture fixture = createFixture();

        service.reset(fixture.rawToken(), NEW_PASSWORD);

        var before = databaseSnapshot(fixture.memberId());

        assertThatThrownBy(() ->
                service.reset(
                        fixture.rawToken(),
                        "AnotherPassword789!"
                )
        )
                .isInstanceOfSatisfying(
                        PasswordResetException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        PasswordResetException.Code
                                                .PASSWORD_RESET_TOKEN_INVALID
                                )
                );

        assertThat(databaseSnapshot(fixture.memberId()))
                .isEqualTo(before);
    }

    @Test
    void 현재와_같은_비밀번호이면_DB를_변경하거나_토큰을_소비하지_않는다() {
        Fixture fixture = createFixture();

        var before = databaseSnapshot(fixture.memberId());

        assertThatThrownBy(() ->
                service.reset(fixture.rawToken(), OLD_PASSWORD)
        )
                .isInstanceOfSatisfying(
                        PasswordResetException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        PasswordResetException.Code
                                                .PASSWORD_RESET_PASSWORD_UNCHANGED
                                )
                );

        assertThat(databaseSnapshot(fixture.memberId()))
                .isEqualTo(before);

        PasswordResetToken token =
                tokenRepository.findById(fixture.tokenId())
                        .orElseThrow();

        assertThat(token.isUsable(NOW)).isTrue();
    }

    @Test
    void DB_반영_후_실패하면_회원과_모든_토큰_변경이_롤백된다() {
        Fixture fixture = createFixture();

        Long otherTokenId = createAdditionalToken(fixture.memberId());

        var before = databaseSnapshot(fixture.memberId());

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                transaction.executeWithoutResult(status -> {
                    service.reset(fixture.rawToken(), NEW_PASSWORD);

                    // 서비스의 flush()로 실제 SQL이 실행되었는지
                    // 같은 트랜잭션의 JDBC 조회로 확인한다.
                    String changedHash = jdbcTemplate.queryForObject(
                            """
                            SELECT password_hash
                            FROM members
                            WHERE id = ?
                            """,
                            String.class,
                            fixture.memberId()
                    );

                    assertThat(
                            passwordEncoder.matches(
                                    NEW_PASSWORD,
                                    changedHash
                            )
                    ).isTrue();

                    Long credentialVersion = jdbcTemplate.queryForObject(
                            """
                            SELECT credential_version
                            FROM members
                            WHERE id = ?
                            """,
                            Long.class,
                            fixture.memberId()
                    );

                    assertThat(credentialVersion).isEqualTo(1L);

                    Boolean used = jdbcTemplate.queryForObject(
                            """
                            SELECT used_at IS NOT NULL
                            FROM password_reset_tokens
                            WHERE id = ?
                            """,
                            Boolean.class,
                            fixture.tokenId()
                    );

                    Boolean revoked = jdbcTemplate.queryForObject(
                            """
                            SELECT revoked_at IS NOT NULL
                            FROM password_reset_tokens
                            WHERE id = ?
                            """,
                            Boolean.class,
                            otherTokenId
                    );

                    assertThat(used).isTrue();
                    assertThat(revoked).isTrue();

                    // SQL 실행 후, 커밋 전에 실패시킨다.
                    throw new IllegalStateException("테스트용 저장 실패");
                })
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("테스트용 저장 실패");

        // 외부 트랜잭션이 롤백된 뒤 다시 조회한다.
        assertThat(databaseSnapshot(fixture.memberId()))
                .isEqualTo(before);
    }

    @Test
    void 같은_토큰의_동시_요청은_하나만_성공한다() throws Exception {
        Fixture fixture = createFixture();

        List<ResetAttempt> results = resetConcurrently(
                fixture.rawToken(),
                List.of(
                        "ConcurrentPassword123!",
                        "ConcurrentPassword456!"
                )
        );

        List<ResetAttempt> successes = results.stream()
                .filter(result -> result.failureCode() == null)
                .toList();

        List<ResetAttempt> failures = results.stream()
                .filter(result -> result.failureCode() != null)
                .toList();

        assertThat(successes).hasSize(1);
        assertThat(failures).hasSize(1);

        assertThat(failures.getFirst().failureCode())
                .isEqualTo(
                        PasswordResetException.Code
                                .PASSWORD_RESET_TOKEN_INVALID
                );

        Member member = memberRepository.findById(fixture.memberId())
                .orElseThrow();

        // 성공한 요청의 비밀번호만 저장되어야 한다.
        assertThat(
                passwordEncoder.matches(
                        successes.getFirst().password(),
                        member.getPasswordHash()
                )
        ).isTrue();

        assertThat(
                passwordEncoder.matches(
                        failures.getFirst().password(),
                        member.getPasswordHash()
                )
        ).isFalse();

        // 두 요청이 모두 변경했다면 2가 될 수 있으므로 확인한다.
        assertThat(member.getCredentialVersion()).isEqualTo(1L);

        PasswordResetToken token =
                tokenRepository.findById(fixture.tokenId())
                        .orElseThrow();

        assertThat(token.getUsedAt()).isEqualTo(NOW);
        assertThat(token.getRevokedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = {"PENDING", "RESTRICTED", "SUSPENDED"}
    )
    void 활성_상태가_아니면_재설정할_수_없다(MemberStatus status) {
        Fixture fixture = createFixture();

        // 테스트 조건을 만들기 위해 DB의 회원 상태를 직접 변경한다.
        jdbcTemplate.update(
                """
                UPDATE members
                SET status = ?
                WHERE id = ?
                """,
                status.name(),
                fixture.memberId()
        );

        var before = databaseSnapshot(fixture.memberId());
        assertThatThrownBy(() -> validation.validate(fixture.rawToken())).isInstanceOf(PasswordResetException.class);

        assertThatThrownBy(() ->
                service.reset(fixture.rawToken(), NEW_PASSWORD)
        )
                .isInstanceOfSatisfying(
                        PasswordResetException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        PasswordResetException.Code
                                                .PASSWORD_RESET_TOKEN_INVALID
                                )
                );

        assertThat(databaseSnapshot(fixture.memberId()))
                .isEqualTo(before);
    }

    @Test
    void 탈퇴_전_토큰으로_같은_이메일의_재가입_계정을_변경할_수_없다() {
        Fixture fixture = createFixture();

        Member oldMember = memberRepository.findById(fixture.memberId())
                .orElseThrow();

        String originalEmail = oldMember.getEmail();

        oldMember.withdraw(NOW);
        memberRepository.saveAndFlush(oldMember);
        assertThatThrownBy(() -> validation.validate(fixture.rawToken())).isInstanceOf(PasswordResetException.class);

        String signupPassword = "RejoinedPassword123!";
        String nicknameSuffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        Member newMember = Member.createPending(
                originalEmail,
                passwordEncoder.encode(signupPassword),
                "재가입" + nicknameSuffix
        );

        newMember.activeAfterEmailVerification();

        Member savedNewMember =
                memberRepository.saveAndFlush(newMember);

        assertThat(savedNewMember.getId())
                .isNotEqualTo(fixture.memberId());

        var oldBefore = databaseSnapshot(fixture.memberId());
        var newBefore = databaseSnapshot(savedNewMember.getId());

        assertThatThrownBy(() ->
                service.reset(fixture.rawToken(), NEW_PASSWORD)
        )
                .isInstanceOfSatisfying(
                        PasswordResetException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        PasswordResetException.Code
                                                .PASSWORD_RESET_TOKEN_INVALID
                                )
                );

        assertThat(databaseSnapshot(fixture.memberId()))
                .isEqualTo(oldBefore);

        assertThat(databaseSnapshot(savedNewMember.getId()))
                .isEqualTo(newBefore);

        Member reloadedNewMember =
                memberRepository.findById(savedNewMember.getId())
                        .orElseThrow();

        assertThat(
                passwordEncoder.matches(
                        signupPassword,
                        reloadedNewMember.getPasswordHash()
                )
        ).isTrue();

        assertThat(reloadedNewMember.getCredentialVersion()).isZero();
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "");

        Member member = Member.createPending(
                "reset-" + suffix + "@example.com",
                passwordEncoder.encode(OLD_PASSWORD),
                "회원" + suffix.substring(0, 16)
        );

        member.activeAfterEmailVerification();

        Member savedMember = memberRepository.saveAndFlush(member);

        String rawToken = tokenGenerator.generate();

        PasswordResetToken token = PasswordResetToken.issue(
                savedMember.getId(),
                tokenHasher.hash(rawToken),
                NOW.minusSeconds(60),
                NOW.plusSeconds(1800)
        );

        PasswordResetToken savedToken =
                tokenRepository.saveAndFlush(token);

        return new Fixture(
                savedMember.getId(),
                rawToken,
                savedToken.getId()
        );
    }

    private Long createAdditionalToken(Long memberId) {
        PasswordResetToken token = PasswordResetToken.issue(
                memberId,
                tokenHasher.hash(tokenGenerator.generate()),
                NOW.minusSeconds(120),
                NOW.plusSeconds(1800)
        );

        return tokenRepository.saveAndFlush(token).getId();
    }

    private Map<String, Object> databaseSnapshot(Long memberId) {
        var member = jdbcTemplate.queryForMap(
                """
                SELECT id, email, nickname, password_hash,
                       status, role, credential_version,
                       created_at, updated_at, withdrawn_at
                FROM members
                WHERE id = ?
                """,
                memberId
        );

        var tokens = jdbcTemplate.queryForList(
                """
                SELECT id, member_id, token_hash,
                       created_at, expires_at,
                       used_at, revoked_at, version
                FROM password_reset_tokens
                WHERE member_id = ?
                ORDER BY id
                """,
                memberId
        );

        return Map.of(
                "member", member,
                "tokens", tokens
        );
    }

    private record Fixture(
            Long memberId,
            String rawToken,
            Long tokenId
    ) {
        @Override
        public String toString() {
            return "Fixture[REDACTED]";
        }
    }

    private List<ResetAttempt> resetConcurrently(
            String rawToken,
            List<String> passwords
    ) throws Exception {
        ExecutorService workers =
                Executors.newFixedThreadPool(passwords.size());

        CountDownLatch ready = new CountDownLatch(passwords.size());
        CountDownLatch start = new CountDownLatch(1);

        List<Future<ResetAttempt>> futures = new ArrayList<>();

        try {
            for (String password : passwords) {
                futures.add(workers.submit(() -> {
                    ready.countDown();

                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "동시 요청 시작 대기 시간 초과"
                        );
                    }

                    try {
                        service.reset(rawToken, password);

                        return new ResetAttempt(password, null);
                    } catch (PasswordResetException exception) {
                        return new ResetAttempt(
                                password,
                                exception.getCode()
                        );
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("모든 요청 작업이 시작 준비를 마쳐야 한다.")
                    .isTrue();

            start.countDown();

            List<ResetAttempt> results = new ArrayList<>();

            for (Future<ResetAttempt> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }

            return results;
        } finally {
            // 준비 과정에서 실패했더라도 대기 작업을 해제한다.
            start.countDown();

            workers.shutdownNow();

            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "동시 요청 테스트 작업이 종료되지 않았습니다."
                );
            }
        }
    }

    private record ResetAttempt(
            String password,
            PasswordResetException.Code failureCode
    ) {
        @Override
        public String toString() {
            return "ResetAttempt[failureCode=" + failureCode + "]";
        }
    }
}