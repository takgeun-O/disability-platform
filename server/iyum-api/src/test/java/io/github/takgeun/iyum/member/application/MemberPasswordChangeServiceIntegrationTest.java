package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetException;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetService;
import io.github.takgeun.iyum.auth.passwordreset.domain.PasswordResetToken;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenHasher;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberPasswordChangeFailure;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberPasswordChangeFailureRepository;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.takgeun.iyum.member.application.MemberPasswordChangeResult.Reason.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true",
        "spring.datasource.hikari.maximum-pool-size=4", // 테스트가 잠금 상태를 관찰할 연결도 필요해서 테스트의 연결 풀 크기 명시
        "iyum.auth.password-reset.mail.enabled=false",
        "iyum.auth.email-verification.mail.enabled=false"
})
@Testcontainers
@RecordApplicationEvents
class MemberPasswordChangeServiceIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-10-22T06:00:00Z");

    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "NewPassword456!";
    private static final String WRONG_PASSWORD = "WrongPassword789!";
    private static final String RESET_PASSWORD = "ResetPassword987!";

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
    MemberPasswordChangeService service;

    @Autowired
    MemberPasswordChangeFailureRepository failureRepository;

    @Autowired
    PasswordResetTokenRepository tokenRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApplicationEvents applicationEvents;

    @Autowired
    PasswordResetService passwordResetService;

    @Autowired
    PasswordResetTokenHasher passwordResetTokenHasher;

    @Autowired
    MemberWithdrawalService memberWithdrawalService;

    @MockitoSpyBean
    MemberRepository memberRepository;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void 비밀번호_불일치_기록은_호출자에게_돌아오기_전에_커밋된다() {
        Fixture fixture = createFixture();

        assertThatThrownBy(() -> {
            var result = service.change(
                    fixture.memberId(), 0L,
                    WRONG_PASSWORD, NEW_PASSWORD
            );

            assertRejected(result, CURRENT_PASSWORD_MISMATCH);

            // 서비스가 반환된 뒤 호출자가 오류로 변환하는 상황을 재현한다.
            throw new IllegalStateException("호출자에서 발생한 오류");
        }).isInstanceOf(IllegalStateException.class)
                .hasMessage("호출자에서 발생한 오류");

        assertThat(failureCount(fixture.memberId())).isEqualTo(1);

        Member member = findMember(fixture.memberId());
        assertThat(member.getCredentialVersion()).isZero();
        assertThat(passwordEncoder.matches(
                OLD_PASSWORD, member.getPasswordHash()
        )).isTrue();
    }

    @Test
    void 다섯번_실패하면_차단하고_추가_요청으로_기간을_연장하지_않는다() {
        Fixture fixture = createFixture();

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertRejected(service.change(
                    fixture.memberId(), 0L,
                    WRONG_PASSWORD, NEW_PASSWORD
            ), CURRENT_PASSWORD_MISMATCH);
        }

        assertRejected(service.change(
                fixture.memberId(), 0L,
                WRONG_PASSWORD, NEW_PASSWORD
        ), RATE_LIMITED);

        assertThat(failureCount(fixture.memberId())).isEqualTo(5);
        assertThat(failureRepository.findBlockedUntil(fixture.memberId()))
                .contains(NOW.plusSeconds(900));

        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        // 차단 중에는 올바른 현재 비밀번호를 입력해도 변경하지 않는다.
        assertRejected(service.change(
                fixture.memberId(), 0L,
                OLD_PASSWORD, NEW_PASSWORD
        ), RATE_LIMITED);

        assertThat(failureCount(fixture.memberId())).isEqualTo(5);
        assertThat(failureRepository.findBlockedUntil(fixture.memberId()))
                .contains(NOW.plusSeconds(900));
        assertThat(findMember(fixture.memberId()).getCredentialVersion())
                .isZero();

        // 정확히 차단 종료 시각이 되면 다시 시도할 수 있다.
        when(clock.instant()).thenReturn(NOW.plusSeconds(900));

        assertThat(service.change(
                fixture.memberId(), 0L,
                OLD_PASSWORD, NEW_PASSWORD
        )).isInstanceOf(MemberPasswordChangeResult.Changed.class);

        assertThat(failureCount(fixture.memberId())).isZero();
    }

    @Test
    void 변경_성공시_버전_증가와_토큰_폐기와_실패_초기화를_커밋한다() {
        Fixture fixture = createFixture();
        saveFailure(fixture.memberId());

        var result = service.change(
                fixture.memberId(), 0L,
                OLD_PASSWORD, NEW_PASSWORD
        );

        assertThat(result).isInstanceOfSatisfying(
                MemberPasswordChangeResult.Changed.class,
                changed -> {
                    assertThat(changed.memberId())
                            .isEqualTo(fixture.memberId());
                    assertThat(changed.previousCredentialVersion()).isZero();
                    assertThat(changed.credentialVersion()).isEqualTo(1L);
                }
        );

        Member member = findMember(fixture.memberId());

        assertThat(member.getCredentialVersion()).isEqualTo(1L);
        assertThat(passwordEncoder.matches(
                NEW_PASSWORD, member.getPasswordHash()
        )).isTrue();
        assertThat(passwordEncoder.matches(
                OLD_PASSWORD, member.getPasswordHash()
        )).isFalse();

        PasswordResetToken token = tokenRepository
                .findById(fixture.tokenId()).orElseThrow();

        assertThat(token.getUsedAt()).isNull();
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
        assertThat(failureCount(fixture.memberId())).isZero();

        // 이벤트 발행 여부를 검사한다. 메일 발송 검사는 아니다.
        assertThat(applicationEvents.stream(MemberPasswordChanged.class))
                .hasSize(1);
    }

    @Test
    void 입력_오류와_동일_비밀번호는_실패_횟수에_포함하지_않는다() {
        Fixture fixture = createFixture();

        assertRejected(service.change(
                fixture.memberId(), 0L,
                WRONG_PASSWORD, "short"
        ), INVALID_NEW_PASSWORD);

        assertRejected(service.change(
                fixture.memberId(), 0L,
                "", NEW_PASSWORD
        ), INVALID_CURRENT_PASSWORD);

        assertRejected(service.change(
                fixture.memberId(), 0L,
                OLD_PASSWORD, OLD_PASSWORD
        ), PASSWORD_UNCHANGED);

        assertThat(failureCount(fixture.memberId())).isZero();
        assertThat(findMember(fixture.memberId()).getCredentialVersion())
                .isZero();

        assertThat(tokenRepository.findById(fixture.tokenId())
                .orElseThrow().getRevokedAt()).isNull();

        assertThat(applicationEvents.stream(MemberPasswordChanged.class))
                .isEmpty();
    }

    @Test
    void 이전_인증_버전과_탈퇴_상태에서는_변경하지_않는다() {
        Fixture fixture = createFixture();

        Member member = findMember(fixture.memberId());
        member.resetPassword(passwordEncoder.encode(OLD_PASSWORD));
        memberRepository.saveAndFlush(member);

        // 비밀번호는 맞지만 요청 인증 버전 0은 이미 오래된 버전이다.
        assertRejected(service.change(
                fixture.memberId(), 0L,
                OLD_PASSWORD, NEW_PASSWORD
        ), AUTHENTICATION_REQUIRED);

        member = findMember(fixture.memberId());
        member.withdraw(NOW);
        memberRepository.saveAndFlush(member);

        // 인증 버전이 같아도 ACTIVE 상태가 아니면 거절한다.
        assertRejected(service.change(
                fixture.memberId(), 1L,
                OLD_PASSWORD, NEW_PASSWORD
        ), AUTHENTICATION_REQUIRED);

        assertThat(failureCount(fixture.memberId())).isZero();
        assertThat(applicationEvents.stream(MemberPasswordChanged.class))
                .isEmpty();
    }

    @Test
    void DB_변경_중_실패하면_비밀번호와_토큰과_실패_기록을_복구한다() {
        Fixture fixture = createFixture();
        saveFailure(fixture.memberId());

        String originalHash = findMember(fixture.memberId()).getPasswordHash();

        doAnswer(invocation -> {
            // 실패 기록 삭제의 flushAutomatically에 의해
            // 회원 변경 SQL이 이미 실행된 상태인지 확인한다.
            Long version = jdbcTemplate.queryForObject(
                    "select credential_version from members where id = ?",
                    Long.class,
                    fixture.memberId()
            );
            assertThat(version).isEqualTo(1L);

            throw new IllegalStateException("테스트용 저장 실패");
        }).when(memberRepository).flush();

        assertThatThrownBy(() -> service.change(
                fixture.memberId(), 0L,
                OLD_PASSWORD, NEW_PASSWORD
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("테스트용 저장 실패");

        // 서비스 트랜잭션 종료 후 별도 조회로 롤백 결과를 확인한다.
        Member member = findMember(fixture.memberId());

        assertThat(member.getPasswordHash()).isEqualTo(originalHash);
        assertThat(member.getCredentialVersion()).isZero();
        assertThat(failureCount(fixture.memberId())).isEqualTo(1);

        assertThat(tokenRepository.findById(fixture.tokenId())
                .orElseThrow().getRevokedAt()).isNull();

        assertThat(applicationEvents.stream(MemberPasswordChanged.class))
                .isEmpty();
    }

    @Test
    void 동시_변경에서는_먼저_잠근_요청만_성공한다() throws Exception {
        Fixture fixture = createFixture();
        String secondNewPassword = "AnotherPassword789!";

        var results = runWithMemberLockContention(
                fixture.memberId(),
                () -> service.change(
                        fixture.memberId(), 0L,
                        OLD_PASSWORD, NEW_PASSWORD
                ),
                () -> service.change(
                        fixture.memberId(), 0L,
                        OLD_PASSWORD, secondNewPassword
                )
        );

        assertThat(results.first()).isInstanceOfSatisfying(
                MemberPasswordChangeResult.Changed.class,
                changed -> {
                    assertThat(changed.memberId())
                            .isEqualTo(fixture.memberId());
                    assertThat(changed.previousCredentialVersion()).isZero();
                    assertThat(changed.credentialVersion()).isEqualTo(1L);
                }
        );

        // 두 번째 요청도 시작 당시 버전은 0이었다.
        // 하지만 잠금 획득 후에는 첫 번째 요청이 커밋한 버전 1을
        // 확인해야 하므로, 비밀번호 비교 전에 인증 오류로 거절한다.
        assertRejected(
                results.second(),
                AUTHENTICATION_REQUIRED
        );

        Member member = findMember(fixture.memberId());

        assertThat(member.getCredentialVersion()).isEqualTo(1L);
        assertThat(passwordEncoder.matches(
                NEW_PASSWORD, member.getPasswordHash()
        )).isTrue();
        assertThat(passwordEncoder.matches(
                secondNewPassword, member.getPasswordHash()
        )).isFalse();

        // 오래된 인증 버전의 요청을 비밀번호 불일치로 집계하면 안 된다.
        assertThat(failureCount(fixture.memberId())).isZero();

        PasswordResetToken token = tokenRepository
                .findById(fixture.tokenId()).orElseThrow();

        assertThat(token.getUsedAt()).isNull();
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void 동시_비밀번호_불일치도_실패_기록이_누락되지_않는다()
            throws Exception {
        Fixture fixture = createFixture();

        var results = runWithMemberLockContention(
                fixture.memberId(),
                () -> service.change(
                        fixture.memberId(), 0L,
                        WRONG_PASSWORD, NEW_PASSWORD
                ),
                () -> service.change(
                        fixture.memberId(), 0L,
                        WRONG_PASSWORD, NEW_PASSWORD
                )
        );

        assertRejected(
                results.first(),
                CURRENT_PASSWORD_MISMATCH
        );
        assertRejected(
                results.second(),
                CURRENT_PASSWORD_MISMATCH
        );

        // 첫 번째 실패가 커밋된 후 두 번째 요청이 집계해야 한다.
        assertThat(failureCount(fixture.memberId())).isEqualTo(2);
        assertThat(failureRepository.findBlockedUntil(fixture.memberId()))
                .isEmpty();

        Member member = findMember(fixture.memberId());

        assertThat(member.getCredentialVersion()).isZero();
        assertThat(passwordEncoder.matches(
                OLD_PASSWORD, member.getPasswordHash()
        )).isTrue();

        assertThat(tokenRepository.findById(fixture.tokenId())
                .orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    void 네번_실패한_뒤_동시_요청이_와도_다섯번째까지만_기록한다()
            throws Exception {
        // 1. 테스트 회원과 재설정 토큰 준비
        // 재설정 토큰은 마지막에 실패한 비밀번호 변경이 토큰을 잘못 폐기하지 않았는지 검사하기 위함.
        Fixture fixture = createFixture();

        // 2. 기존 실패 기록 4개 미리 저장
        // previousFailures는 이번 실패가 발생하기 전에 이미 몇 번 실패했는지 횟수를 의미함.
        for (int previousFailures = 0; previousFailures < 4; previousFailures++) {
            failureRepository.saveAndFlush(
                    MemberPasswordChangeFailure.record(
                            fixture.memberId(),
                            NOW.minusSeconds(30),   // 현재 기준 30초 전에 발생한 것으로 설정 (따라서 모두 최근 15분 집계에 포함됨)
                            previousFailures
                    )
            );
        }


        // 3. 잘못된 비밀번호 요청 2개를 경쟁시킨다.
        // 두 람다식은 각각 별도 작업 스레드에서 실행할 요청임.
        // 두 요청 모두 같은 회원과 인증 버전 0을 사용하고 현재 비밀번호는 잘못 입력
        // runWithMemberLockContention()이 실행 순서를 제어
        // 첫 번쨰 요청이 회원 행 잠금을 먼저 획득하고, 두 번째 요청은 그 잠금을 기다린다.
        /**
         * 첫 번째 요청의 서비스 처리 과정
         * 1. 회원이 ACTIVE이고 인증 버전이 0인지 확인
         * 2. 아직 차단되지 않았으므로 현재 비밀번호 검사
         * 3. WRONG_PASSWORD가 일치하지 않으므로 기존 실패 횟수 조회
         * 4. 기존 실패가 4개이므로 다섯 번쨰 실패 기록 생성
         * 5. 차단 종료 시각을 NOW + 15분으로 설정
         * 6. 실패 기록을 커밋하고 RATE_LIMITED 결과 반환
         * --> 비밀번호 변경은 거절됐지만, 실패 기록과 차단 정보는 커밋되어야 한다.
         */
        var results = runWithMemberLockContention(
                fixture.memberId(),
                () -> service.change(
                        fixture.memberId(), 0L,
                        WRONG_PASSWORD, NEW_PASSWORD
                ),
                () -> service.change(
                        fixture.memberId(), 0L,
                        WRONG_PASSWORD, NEW_PASSWORD
                )
        );

        // 참고로 Rejected는 Java의 record라서 isEqualTo(expected)로 거절 사유와 차단 종료 시각이 모두 같은지 확인
        var expected = new MemberPasswordChangeResult.Rejected(
                RATE_LIMITED,
                NOW.plusSeconds(900)
        );

        // 첫 번째 요청은 다섯 번째 실패를 기록하고 차단을 시작한다.
        // --> RATE_LIMITED 반환
        assertThat(results.first()).isEqualTo(expected);

        // 두 번째 요청은 잠금 획득 후 차단 상태를 확인한다.
        // --> RATE_LIMITED 반환
        // 비밀번호 비교나 여섯 번째 실패 기록을 수행하면 안 된다.
        assertThat(results.second()).isEqualTo(expected);

        // DB에 남은 최종 상태 검사 (추가 요청이 두 개였어도 실패 기록은 4개에서 5개로만 증가했는지 확인
        assertThat(failureCount(fixture.memberId())).isEqualTo(5);
        assertThat(failureRepository.findBlockedUntil(fixture.memberId()))
                .contains(NOW.plusSeconds(900));

        Member member = findMember(fixture.memberId());

        // 비밀번호 변경이 성공하지 않았으므로 인증 버전은 0
        assertThat(member.getCredentialVersion()).isZero();

        // 기존 비밀번호로 여전히 해시 검증이 성공하는지 확인
        assertThat(passwordEncoder.matches(
                OLD_PASSWORD, member.getPasswordHash()
        )).isTrue();

        // 비밀번호 변경이 실패했으므로 기존 이메일 재설정 토큰도 폐기되지 않아야 한다.
        assertThat(tokenRepository.findById(fixture.tokenId())
                .orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    void 이메일_재설정이_먼저_완료되면_대기하던_변경은_이전_버전으로_거절한다()
            throws Exception {
        Fixture fixture = createFixture();
        ResetTokenFixture resetToken =
                createUsableResetToken(fixture.memberId());

        var results = runWithMemberLockContention(
                fixture.memberId(),
                () -> {
                    passwordResetService.reset(
                            resetToken.rawToken(),
                            RESET_PASSWORD
                    );

                    // reset()은 void 메서드다.
                    // Callable 형식에 맞추기 위한 반환값이며 서비스 결과는 아니다.
                    return null;
                },
                () -> service.change(
                        fixture.memberId(),
                        0L,
                        OLD_PASSWORD,
                        NEW_PASSWORD
                )
        );

        // 첫 번째 작업이 예외 없이 끝났고 Future.get()까지 완료됐다.
        assertThat(results.first()).isNull();

        // 변경 요청은 버전 0으로 시작했지만,
        // 잠금 획득 후 재설정이 커밋한 버전 1을 확인해야 한다.
        assertRejected(
                results.second(),
                AUTHENTICATION_REQUIRED
        );

        Member member = findMember(fixture.memberId());

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getCredentialVersion()).isEqualTo(1L);

        assertThat(passwordEncoder.matches(
                RESET_PASSWORD, member.getPasswordHash()
        )).isTrue();

        assertThat(passwordEncoder.matches(
                NEW_PASSWORD, member.getPasswordHash()
        )).isFalse();

        assertThat(passwordEncoder.matches(
                OLD_PASSWORD, member.getPasswordHash()
        )).isFalse();

        // 오래된 인증 버전의 요청을 비밀번호 불일치로 집계하면 안 된다.
        assertThat(failureCount(fixture.memberId())).isZero();

        PasswordResetToken usedToken = tokenRepository
                .findById(resetToken.tokenId()).orElseThrow();

        assertThat(usedToken.getUsedAt()).isEqualTo(NOW);
        assertThat(usedToken.getRevokedAt()).isNull();

        // 재설정에 사용하지 않은 다른 토큰은 폐기된다.
        PasswordResetToken otherToken = tokenRepository
                .findById(fixture.tokenId()).orElseThrow();

        assertThat(otherToken.getUsedAt()).isNull();
        assertThat(otherToken.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void 마이페이지_변경이_먼저_완료되면_대기하던_재설정은_폐기된_토큰으로_거절한다()
            throws Exception {
        Fixture fixture = createFixture();
        ResetTokenFixture resetToken =
                createUsableResetToken(fixture.memberId());

        var results = runWithMemberLockContention(
                fixture.memberId(),
                () -> service.change(
                        fixture.memberId(),
                        0L,
                        OLD_PASSWORD,
                        NEW_PASSWORD
                ),
                () -> {
                    try {
                        passwordResetService.reset(
                                resetToken.rawToken(),
                                RESET_PASSWORD
                        );

                        // 예외 없이 성공하면 아래의 오류 코드 검증이 실패한다.
                        return null;
                    } catch (PasswordResetException e) {
                        // 서비스 프록시 밖에서 예외를 받는다.
                        // 재설정 트랜잭션의 롤백이 끝난 뒤 오류 코드를 반환한다.
                        return e.getCode();
                    }
                }
        );

        assertThat(results.first()).isInstanceOfSatisfying(
                MemberPasswordChangeResult.Changed.class,
                changed -> {
                    assertThat(changed.previousCredentialVersion()).isZero();
                    assertThat(changed.credentialVersion()).isEqualTo(1L);
                }
        );

        assertThat(results.second()).isEqualTo(
                PasswordResetException.Code.PASSWORD_RESET_TOKEN_INVALID
        );

        Member member = findMember(fixture.memberId());

        // 재설정이 추가로 성공했다면 버전이 2가 되므로,
        // 버전 1과 마이페이지에서 지정한 비밀번호를 함께 검사한다.
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getCredentialVersion()).isEqualTo(1L);

        assertThat(passwordEncoder.matches(
                NEW_PASSWORD, member.getPasswordHash()
        )).isTrue();

        assertThat(passwordEncoder.matches(
                RESET_PASSWORD, member.getPasswordHash()
        )).isFalse();

        assertThat(passwordEncoder.matches(
                OLD_PASSWORD, member.getPasswordHash()
        )).isFalse();

        assertThat(failureCount(fixture.memberId())).isZero();

        PasswordResetToken rejectedToken = tokenRepository
                .findById(resetToken.tokenId()).orElseThrow();

        assertThat(rejectedToken.getUsedAt()).isNull();
        assertThat(rejectedToken.getRevokedAt()).isEqualTo(NOW);

        PasswordResetToken otherToken = tokenRepository
                .findById(fixture.tokenId()).orElseThrow();

        assertThat(otherToken.getUsedAt()).isNull();
        assertThat(otherToken.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void 탈퇴가_먼저_완료되면_대기하던_비밀번호_변경은_거절한다()
            throws Exception {
        Fixture fixture = createFixture();

        var results = runWithMemberLockContention(
                fixture.memberId(),
                () -> {
                    memberWithdrawalService.withdraw(
                            fixture.memberId(),
                            OLD_PASSWORD
                    );

                    // withdraw()의 정상 종료를 Callable 형태로 표현한다.
                    return null;
                },
                () -> service.change(
                        fixture.memberId(),
                        0L,
                        OLD_PASSWORD,
                        NEW_PASSWORD
                )
        );

        assertThat(results.first()).isNull();

        // 요청 시작 당시에는 ACTIVE였더라도,
        // 잠금 획득 후에는 탈퇴가 반영된 상태를 확인해야 한다.
        assertRejected(
                results.second(),
                AUTHENTICATION_REQUIRED
        );

        Member member = findMember(fixture.memberId());

        assertThat(member.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(member.getWithdrawnAt()).isEqualTo(NOW);

        // 대기하던 변경 요청이 삭제된 개인정보나 비밀번호를
        // 다시 저장하지 않았는지 확인한다.
        assertThat(member.getEmail()).isNull();
        assertThat(member.getNickname()).isNull();
        assertThat(member.getPasswordHash()).isNull();

        // 현재 탈퇴 구현은 인증 버전을 증가시키지 않는다.
        // 변경 요청도 거절됐으므로 기존 버전 0이 유지돼야 한다.
        assertThat(member.getCredentialVersion()).isZero();

        assertThat(failureCount(fixture.memberId())).isZero();
    }

    /**
     * 첫 번째 요청이 회원 잠금을 보유한 상태에서
     * 두 번째 요청이 실제 DB 잠금 대기에 들어가도록 제어한다.
     * <p>
     * Repository의 반환값을 가짜로 만들지 않고 실제 조회를 실행한다.
     */
    // A는 첫 번째 작업의 반환형, B는 두 번째 작업의 반환형 (도우미의 반환형을 일반화한다.)
    // 왜냐하면 비밀번호 변경의 경우 MemberPasswordChangeResult를 반환하는데
    // 재설정과 탈퇴는 반환값이 없는 void 메서드임.
    private <A, B> ConcurrentResults<A, B> runWithMemberLockContention(
            Long memberId,
            Callable<A> firstAction,
            Callable<B> secondAction
    ) throws Exception {
        // CountDownLatch(1) : 신호를 한 번 기다리는 장치
        // 15초 안에 신호를 받으면 true, 신호를 받지 못하고 시간이 지나면 false
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttempted = new CountDownLatch(1);

        AtomicInteger invocationOrder = new AtomicInteger();
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();

        // Spring이 설정한 원래 저장소 호출 방식을 보관한다.
        // 원래 Repository 프록시로 위임하는 동작을 그대로 사용한다.
        Answer<?> repositoryDefaultAnswer = mockingDetails(memberRepository)
                .getMockCreationSettings()
                .getDefaultAnswer();

        doAnswer(invocation -> {
            int order = invocationOrder.incrementAndGet();

            // 서비스 트랜잭션과 같은 DataSource의 연결을 사용한다.
            // 따라서 이 PID는 현재 요청이 사용하는 DB 세션의 PID다.
            int pid = jdbcTemplate.queryForObject(
                    "select pg_backend_pid()",
                    Integer.class
            );

            // 테스트 실패 시 DB 잠금 대기가 무한히 이어지지 않도록 한다.
            // true는 이 설정을 현재 트랜잭션에만 적용한다는 의미다.
            jdbcTemplate.queryForObject(
                    "select set_config('lock_timeout', '15s', true)",
                    String.class
            );

            if (order == 1) {
                firstPid.set(pid);

                // 실제 SELECT ... FOR UPDATE를 실행한다.
                // 실제 저장소 조회를 실행하고 성공하면 잠금 획득 신호를 보내자는 의도
                Object member = repositoryDefaultAnswer.answer(invocation);

                // 실제 잠금을 얻은 뒤에만 첫 번째 요청을 멈춘다.
                firstLocked.countDown();

                assertThat(releaseFirst.await(30, TimeUnit.SECONDS))
                        .as("첫 번째 요청의 진행 재개 신호")
                        .isTrue();

                return member;
            }

            if (order == 2) {
                secondPid.set(pid);
                secondAttempted.countDown();
            }

            // 첫 번째 트랜잭션이 끝나기 전에는 여기서 잠금을 기다린다.
            return repositoryDefaultAnswer.answer(invocation);
        }).when(memberRepository).findByIdForUpdate(memberId);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 테스트 스레드는 먼저 첫 번째 요청을 별도 스레드에서 실행한다.
        try {
            // 첫 번째 작업 스레드는 서비스에서 findByIdForUpdate()를 호출하고
            // 설정한 doAnswer()로 들어간다.
            Future<A> first =
                    executor.submit(firstAction);

            assertThat(firstLocked.await(15, TimeUnit.SECONDS))
                    .as("첫 번째 요청의 회원 잠금 획득")
                    .isTrue();

//            boolean locked = firstLocked.await(15, TimeUnit.SECONDS);
//
//            if (!locked) {
//                // 신호를 기다리는 동안 작업이 이미 실패했는지 확인한다.
//                if (first.isDone()) {
//                    try {
//                        first.get();
//                    } catch (ExecutionException e) {
//                        // 대기 실패에 가려졌던 작업 스레드의 원래 예외를 보여준다.
//                        throw new AssertionError(
//                                "첫 번째 요청이 잠금 획득 신호를 보내기 전에 실패했습니다.",
//                                e.getCause()
//                        );
//                    }
//
//                    throw new AssertionError(
//                            "첫 번째 요청이 잠금 획득 신호 없이 종료됐습니다."
//                    );
//                }
//
//                throw new AssertionError(
//                        "첫 번째 요청이 아직 실행 중이지만 "
//                                + "15초 안에 잠금 획득 신호를 보내지 못했습니다."
//                );
//            }

            Future<B> second =
                    executor.submit(secondAction);

            assertThat(secondAttempted.await(15, TimeUnit.SECONDS))
                    .as("두 번째 요청의 회원 잠금 시도")
                    .isTrue();

            // 스레드 시작 여부만 확인하지 않고,
            // PostgreSQL에서 실제 잠금 대기가 발생했는지 확인한다.
            awaitDatabaseLockWait(firstPid.get(), secondPid.get());

            releaseFirst.countDown();

            return new ConcurrentResults<>(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );
        } finally {
            // 검증 도중 실패해도 첫 번째 요청이 계속 멈춰 있지 않게 한다.
            releaseFirst.countDown();
            executor.shutdownNow();

            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS))
                    .as("동시 요청 작업 스레드 종료")
                    .isTrue();
        }
    }

    private void awaitDatabaseLockWait(
            int blockingPid,
            int waitingPid
    ) throws InterruptedException {
        assertThat(blockingPid).isPositive();
        assertThat(waitingPid).isPositive().isNotEqualTo(blockingPid);

        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbcTemplate.queryForObject(
                    "select ? = any(pg_blocking_pids(?))",
                    Boolean.class,
                    blockingPid,
                    waitingPid
            );

            if (Boolean.TRUE.equals(waiting)) {
                return;
            }

            // 고정 시간 후 성공으로 간주하지 않는다.
            // 짧은 간격으로 실제 잠금 상태를 다시 확인한다.
            Thread.sleep(50);
        }

        throw new AssertionError(
                "두 번째 요청의 DB 잠금 대기가 확인되지 않았습니다."
        );
    }

    private record ConcurrentResults<A, B>(
            A first,
            B second
    ) {
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");

        Member member = Member.createPending(
                "change-" + suffix + "@example.com",
                passwordEncoder.encode(OLD_PASSWORD),
                "회원" + suffix.substring(0, 12)
        );
        member.activeAfterEmailVerification();

        Long memberId = memberRepository.saveAndFlush(member).getId();

        // 테스트 전용 64자리 해시 값이다. 실제 원본 토큰은 만들지 않는다.
        PasswordResetToken token = tokenRepository.saveAndFlush(
                PasswordResetToken.issue(
                        memberId,
                        suffix.repeat(2),
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(1800)
                )
        );

        return new Fixture(memberId, token.getId());
    }

    private void saveFailure(Long memberId) {
        failureRepository.saveAndFlush(
                MemberPasswordChangeFailure.record(
                        memberId,
                        NOW.minusSeconds(30),
                        0
                )
        );
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow();
    }

    private long failureCount(Long memberId) {
        Instant now = clock.instant();

        return failureRepository.countFailuresInWindow(
                memberId,
                now.minusSeconds(900),
                now
        );
    }

    private void assertRejected(
            MemberPasswordChangeResult result,
            MemberPasswordChangeResult.Reason reason
    ) {
        assertThat(result).isInstanceOfSatisfying(
                MemberPasswordChangeResult.Rejected.class,
                rejected -> assertThat(rejected.reason()).isEqualTo(reason)
        );
    }

    // 테스트 데이터를 DB에 준비하는 용도 (이메일 발급 API나 발급 횟수 제한을 검증하는 건 아님)
    // 원본 토큰 없이 테스트용 해시만 저장하는 createFixture()와 달리
    // 실제 PasswordResetService.reset()에 전달할 원본 토큰을 만들기 위함
    private ResetTokenFixture createUsableResetToken(Long memberId) {
        // 실제 발급 형식과 동일하게 32바이트 난수를
        // 패딩 없는 Base64URL 문자열로 변환한다.
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        PasswordResetToken token = tokenRepository.saveAndFlush(
                PasswordResetToken.issue(
                        memberId,
                        passwordResetTokenHasher.hash(rawToken),
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(1800)
                )
        );

        return new ResetTokenFixture(token.getId(), rawToken);
    }

    private record ResetTokenFixture(
            Long tokenId,
            String rawToken
    ) {
        @Override
        public String toString() {
            return "ResetTokenFixture[REDACTED]";
        }
    }

    private record Fixture(Long memberId, Long tokenId) {
    }
}