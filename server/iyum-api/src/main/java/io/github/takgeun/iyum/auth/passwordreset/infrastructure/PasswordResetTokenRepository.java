package io.github.takgeun.iyum.auth.passwordreset.infrastructure;

import io.github.takgeun.iyum.auth.passwordreset.domain.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // 토큰 엔티티를 먼저 읽지 않고, 잠글 회원의 ID만 알아낸다.
    @Query("""
            select t.memberId
            from PasswordResetToken t
            where t.tokenHash = :tokenHash
            """)
    Optional<Long> findMemberIdByTokenHash(
            @Param("tokenHash") String tokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from PasswordResetToken t
            where t.tokenHash = :tokenHash
            """)
    Optional<PasswordResetToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    // 만료 여부와 무관하게 아직 사용·폐기되지 않은 토큰을 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from PasswordResetToken t
            where t.memberId = :memberId
                and t.usedAt is null
                and t.revokedAt is null
            order by t.id
            """)
    List<PasswordResetToken> findUnconsumedForUpdate(
            @Param("memberId") Long memberId
    );

    // 사용·폐기 여부와 관계없이 가장 최근 발급 시각을 조회한다.
    @Query("""
            select max(t.createdAt)
            from PasswordResetToken t
            where t.memberId = :memberId
            """)
    Optional<Instant> findLatestIssuedAt(
            @Param("memberId") Long memberId
    );

    // 발급 이력의 이동 구간: (windowStart, windowEnd)
    // 용도: 최근 1시간 발급 횟수 검사
    @Query("""
            select count(t)
            from PasswordResetToken t
            where t.memberId = :memberId
                and t.createdAt > :windowStart
                and t.createdAt <= :windowEnd
            """)
    long countIssuedInWindow(
            @Param("memberId") Long memberId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    // 회원 탈퇴 시 재설정 토큰을 함께 정리할 때 사용한다.
    @Modifying
    @Query("""
            delete from PasswordResetToken t
            where t.memberId = :memberId
            """)
    int deleteAllByMemberId(
            @Param("memberId") Long memberId
    );
}
