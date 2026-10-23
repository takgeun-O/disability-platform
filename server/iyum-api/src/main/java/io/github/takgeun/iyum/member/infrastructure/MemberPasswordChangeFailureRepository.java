package io.github.takgeun.iyum.member.infrastructure;

import io.github.takgeun.iyum.member.domain.MemberPasswordChangeFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * 비밀번호 변경 서비스에서는 회원 행 잠금을 획득한 뒤 사용한다.
 * <p>
 * 실패 기록이 아직 없는 회원도 있으므로,
 * 실패 기록 행만 잠그는 것으로 동시 요청을 제어하면 안된다.
 */
public interface MemberPasswordChangeFailureRepository
        extends JpaRepository<MemberPasswordChangeFailure, Long> {

    // 최근 15분 집계 범위 = 15분 전 < 실패 시각 <= 현재 (기존 재설정 토큰의 발급 횟수 조회도 같은 방식으로 바운드 처리 중)
    @Query("""
            select count(f)
            from MemberPasswordChangeFailure f
            where f.memberId = :memberId
                and f.failedAt > :windowStart
                and f.failedAt <= :now
            """)
    long countFailuresInWindow(
            @Param("memberId") Long memberId,
            @Param("windowStart") Instant windowStart,
            @Param("now") Instant now
    );

    @Query("""
            select max(f.blockedUntil)
            from MemberPasswordChangeFailure f
            where f.memberId = :memberId
            """)
    Optional<Instant> findBlockedUntil(
            @Param("memberId") Long memberId
    );

    // 집계 구간 밖의 기록을 정리하되,
    // 아직 유효한 차단 종료 시각이 있는 기록은 보존한다.

    // @Modifying: @Query에 작성한 쿼리가 조회가 아니라 데이터를 변경하는 쿼리임을
    // Spring Data JPA에 알려주는 애노테이션. (여기서는 delete 쿼리니까 붙여준 것.)
    // flushAutomatically = true : 삭제 쿼리 실행 전에 변경사항을 DB에 반영
    // 그동안 영속성 컨텍스트에 쌓인 변경사항을 DB에 반영부터 해야
    // 삭제 쿼리 실행 시 DB에 반영된 최신 데이터를 기준으로 돌아갈 수 있음.
    // 주의) clearAutomatically = true 사용 X (영속성 컨텍스트를 비움)
    // 사용 시 같은 트랜잭션에서 조회한 Member까지 관리 대상에서 빠져나가게 됨.
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from MemberPasswordChangeFailure f
            where f.memberId = :memberId
                and f.failedAt <= :windowStart
                and (
                    f.blockedUntil is null
                    or f.blockedUntil <= :now
                )
            """)
    int deleteExpiredByMemberId(
            // 반환값 int는 삭제된 엔티티 수
            @Param("memberId") Long memberId,
            @Param("windowStart") Instant windowStart,
            @Param("now") Instant now
    );

    // 비밀번호 변경 성공 시 해당 회원의 실패 기록을 초기화한다.
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from MemberPasswordChangeFailure f
            where f.memberId = :memberId
            """)
    int deleteAllByMemberId(
            @Param("memberId") Long memberId
    );
}
