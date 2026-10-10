package io.github.takgeun.iyum.auth.infrastructure;

import io.github.takgeun.iyum.auth.domain.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.time.Instant;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, Long> {

    // 사용·폐기·만료 여부와 무관하게 최초 가입을 포함한 모든 발급 이력을 센다.
    @Query(
            "select max(t.createdAt) " +
                    "from EmailVerificationToken t " +
                    "where t.memberId = :memberId")
    Optional<Instant> findLatestIssuedAt(@Param("memberId") Long memberId);

    long countByMemberIdAndCreatedAtGreaterThan(Long memberId, Instant windowStart);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from EmailVerificationToken t
            where t.memberId = :memberId and t.usedAt is null and t.revokedAt is null
            order by t.id
            """)
    List<EmailVerificationToken> findUnconsumedForUpdate(@Param("memberId") Long memberId);

    /**
     * 사용자가 원본 토큰을 제출하면 서버가 그 값을 해시하고,
     * findByTokenHash()로 해당 기록을 찾는다.(일반적인 조회)
     */
    Optional<EmailVerificationToken> findByTokenHash(
            String tokenHash
    );

    /**
     * 토큰이 어느 회원의 것인지 ID만 조회
     */
    @Query("""
            select t.memberId
            from EmailVerificationToken  t
            where t.tokenHash = :tokenHash
            """)
    Optional<Long> findMemberIdByTokenHash(
            @Param("tokenHash") String tokenHash
    );

    /**
     * 토큰 행을 잠그면서 조회
     * 인증 서비스에서는 아래 순서대로 사용될 것.
     *  1. 토큰 해시로 회원 ID만 알아낸다.
     *  2. 해당 회원을 잠그면서 조회한다.
     *  3. 토큰을 잠그면서 조회한다.
     *  4. 상태를 검사하고 변경한다.
     * 처음에 ID만 읽는 이유는 회원 잠금을 얻기 전에 토큰 엔티티의 과거 상태를
     * 영속성 컨텍스트에 먼저 담지 않기 위해서
     *
     * 또한 앞으로 재발급 기능에서도 회원 -> 토큰 순서로 잠금을 얻도록 맞출 것.
     * 여러 행을 잠글 때 이렇게 순서를 통일하면 교착 상태를 예방하는 데에 도움이 된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from EmailVerificationToken  t
            where t.tokenHash = :tokenHash
            """)
    Optional<EmailVerificationToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );
}
