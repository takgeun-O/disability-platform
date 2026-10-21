package io.github.takgeun.iyum.auth.passwordreset.infrastructure;

import io.github.takgeun.iyum.auth.passwordreset.domain.PasswordResetToken;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class PasswordResetTokenRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-10-21T03:00:00Z");

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
    PasswordResetTokenRepository tokenRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void 토큰을_저장하고_해시로_조회한_뒤_사용_시각을_저장한다() {
        Long memberId = createMember("저장회원");
        String tokenHash = hash(1);

        PasswordResetToken saved = saveToken(
                memberId,
                tokenHash,
                NOW.minusSeconds(60)
        );

        Long tokenId = saved.getId();
        entityManager.clear();

        PasswordResetToken found = tokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(tokenId);
        assertThat(found.getMemberId()).isEqualTo(memberId);
        assertThat(found.getTokenHash()).isEqualTo(tokenHash);
        assertThat(found.getCreatedAt())
                .isEqualTo(NOW.minusSeconds(60));
        assertThat(found.getExpiresAt())
                .isEqualTo(NOW.plusSeconds(1740));
        assertThat(found.getUsedAt()).isNull();
        assertThat(found.getRevokedAt()).isNull();

        long previousVersion = found.getVersion();

        found.use(NOW);
        tokenRepository.flush();
        entityManager.clear();

        PasswordResetToken reloaded = tokenRepository
                .findById(tokenId)
                .orElseThrow();

        assertThat(reloaded.getUsedAt()).isEqualTo(NOW);
        assertThat(reloaded.getRevokedAt()).isNull();
        assertThat(reloaded.isUsable(NOW)).isFalse();
        assertThat(reloaded.getVersion())
                .isGreaterThan(previousVersion);
    }

    @Test
    void 회원_ID와_잠금_대상_토큰을_해시로_조회한다() {
        Long memberId = createMember("조회회원");
        String tokenHash = hash(2);

        saveToken(memberId, tokenHash, NOW);
        entityManager.clear();

        assertThat(
                tokenRepository.findMemberIdByTokenHash(tokenHash)
        ).contains(memberId);

        // 실제 서비스에서도 회원을 먼저 잠근다.
        memberRepository.findByIdForUpdate(memberId).orElseThrow();

        PasswordResetToken found = tokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow();

        assertThat(found.getMemberId()).isEqualTo(memberId);
        assertThat(found.getTokenHash()).isEqualTo(tokenHash);

        assertThat(
                tokenRepository.findMemberIdByTokenHash(hash(999))
        ).isEmpty();

        assertThat(
                tokenRepository.findByTokenHashForUpdate(hash(999))
        ).isEmpty();
    }

    @Test
    void 사용과_폐기가_없는_토큰만_조회하며_만료된_토큰도_포함한다() {
        Long memberId = createMember("폐기회원");
        Long otherMemberId = createMember("다른회원");

        PasswordResetToken expired = saveToken(
                memberId, hash(10), NOW.minusSeconds(3600)
        );

        PasswordResetToken usable = saveToken(
                memberId, hash(11), NOW.minusSeconds(60)
        );

        PasswordResetToken used = saveToken(
                memberId, hash(12), NOW.minusSeconds(60)
        );
        used.use(NOW);

        PasswordResetToken revoked = saveToken(
                memberId, hash(13), NOW.minusSeconds(60)
        );
        revoked.revoke(NOW);

        saveToken(otherMemberId, hash(14), NOW.minusSeconds(60));

        tokenRepository.flush();

        Long expiredId = expired.getId();
        Long usableId = usable.getId();
        entityManager.clear();

        memberRepository.findByIdForUpdate(memberId).orElseThrow();

        var targets = tokenRepository.findUnconsumedForUpdate(memberId);

        assertThat(targets)
                .extracting(PasswordResetToken::getId)
                .containsExactly(expiredId, usableId);

        targets.forEach(token -> token.revoke(NOW));

        tokenRepository.flush();
        entityManager.clear();

        assertThat(
                tokenRepository.findById(expiredId)
                        .orElseThrow()
                        .getRevokedAt()
        ).isEqualTo(NOW);

        assertThat(
                tokenRepository.findById(usableId)
                        .orElseThrow()
                        .getRevokedAt()
        ).isEqualTo(NOW);

        assertThat(
                tokenRepository.findUnconsumedForUpdate(memberId)
        ).isEmpty();

        assertThat(
                tokenRepository.findByTokenHash(hash(14))
                        .orElseThrow()
                        .getRevokedAt()
        ).isNull();
    }

    @Test
    void 발급_횟수는_시작_경계를_제외하고_끝_경계를_포함한다() {
        Long memberId = createMember("횟수회원");
        Long otherMemberId = createMember("비교회원");

        Instant windowStart = NOW.minusSeconds(3600);

        // 정확히 한 시간 전: 제외
        saveToken(memberId, hash(20), windowStart);

        // 구간 내부이며 현재는 만료됨: 포함
        saveToken(
                memberId, hash(21), windowStart.plusSeconds(1)
        );

        // 사용된 토큰도 발급 이력에 포함
        PasswordResetToken used = saveToken(
                memberId, hash(22), NOW.minusSeconds(120)
        );
        used.use(NOW.minusSeconds(60));

        // 폐기된 토큰도 발급 이력에 포함
        PasswordResetToken revoked = saveToken(
                memberId, hash(23), NOW.minusSeconds(90)
        );
        revoked.revoke(NOW.minusSeconds(30));

        // 현재 시각과 동일: 포함
        saveToken(memberId, hash(24), NOW);

        // 미래 시각: 제외
        saveToken(memberId, hash(25), NOW.plusSeconds(1));

        // 다른 회원: 제외
        saveToken(otherMemberId, hash(26), NOW);

        tokenRepository.flush();
        entityManager.clear();

        assertThat(
                tokenRepository.countIssuedInWindow(
                        memberId,
                        windowStart,
                        NOW
                )
        ).isEqualTo(4);
    }

    @Test
    void 최근_발급_시각은_사용과_폐기_여부에_관계없이_조회한다() {
        Long memberId = createMember("최근회원");

        assertThat(
                tokenRepository.findLatestIssuedAt(memberId)
        ).isEmpty();

        assertThat(
                tokenRepository.countIssuedInWindow(
                        memberId,
                        NOW.minusSeconds(3600),
                        NOW
                )
        ).isZero();

        saveToken(memberId, hash(30), NOW.minusSeconds(120));

        PasswordResetToken used = saveToken(
                memberId, hash(31), NOW.minusSeconds(60)
        );
        used.use(NOW);

        PasswordResetToken latest = saveToken(
                memberId, hash(32), NOW.minusSeconds(30)
        );
        latest.revoke(NOW);

        tokenRepository.flush();
        entityManager.clear();

        assertThat(
                tokenRepository.findLatestIssuedAt(memberId)
        ).contains(NOW.minusSeconds(30));
    }

    @Test
    void 같은_해시는_다른_회원이라도_중복_저장할_수_없다() {
        Long memberId = createMember("중복회원");
        Long otherMemberId = createMember("중복비교");

        saveToken(memberId, hash(40), NOW);

        assertThatThrownBy(() ->
                saveToken(otherMemberId, hash(40), NOW)
        ).isInstanceOf(DataIntegrityViolationException.class);

        // 제약 위반 이후 이 트랜잭션에서 추가 DB 작업을 하지 않는다.
    }

    @Test
    void 존재하지_않는_회원의_토큰은_저장할_수_없다() {
        assertThatThrownBy(() ->
                saveToken(Long.MAX_VALUE, hash(50), NOW)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 지정한_회원의_토큰만_모두_삭제한다() {
        Long memberId = createMember("삭제회원");
        Long otherMemberId = createMember("유지회원");

        saveToken(memberId, hash(60), NOW);

        PasswordResetToken used = saveToken(
                memberId, hash(61), NOW.minusSeconds(60)
        );
        used.use(NOW);

        PasswordResetToken revoked = saveToken(
                memberId, hash(62), NOW.minusSeconds(60)
        );
        revoked.revoke(NOW);

        saveToken(otherMemberId, hash(63), NOW);

        tokenRepository.flush();
        entityManager.clear();

        memberRepository.findByIdForUpdate(memberId).orElseThrow();

        int deleted = tokenRepository.deleteAllByMemberId(memberId);

        // 벌크 삭제 결과와 영속성 컨텍스트를 맞추기 위해 비운다.
        entityManager.clear();

        assertThat(deleted).isEqualTo(3);
        assertThat(tokenRepository.findByTokenHash(hash(60))).isEmpty();
        assertThat(tokenRepository.findByTokenHash(hash(61))).isEmpty();
        assertThat(tokenRepository.findByTokenHash(hash(62))).isEmpty();

        assertThat(tokenRepository.findByTokenHash(hash(63)))
                .isPresent();

        assertThat(memberRepository.findById(memberId))
                .isPresent();
    }

    private Long createMember(String nickname) {
        Member member = Member.createPending(
                nickname + "@example.com",
                "test-only-password-hash",
                nickname
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member).getId();
    }

    private PasswordResetToken saveToken(
            Long memberId,
            String tokenHash,
            Instant createdAt
    ) {
        return tokenRepository.saveAndFlush(
                PasswordResetToken.issue(
                        memberId,
                        tokenHash,
                        createdAt,
                        createdAt.plusSeconds(1800)
                )
        );
    }

    private static String hash(int value) {
        return "%064x".formatted(value);
    }
}