package io.github.takgeun.iyum.member.infrastructure;

import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.email = :email")
    Optional<Member> findByEmailForUpdate(@Param("email") String email);

    boolean existsByEmail(String email);

    @Query("""
            select count(m) > 0
            from Member m
            where lower(m.nickname) = lower(:nickname)
            """)
    boolean existsByNicknameIgnoreCase(
            @Param("nickname") String nickname
    );

    /**
     * @Lock(LockModeType.PESSIMISTIC_WRITE) : 회원을 조회하면서 해당 DB 행에 잠금을 걸어
     * 다른 트랜잭션이 동시에 변경하지 못하도록 하는 설정 -> 이걸 비관적 잠금이라고 한다.
     *
     * 낙관적 작금인 @Version의 경우
     * 변경 시 버전을 비교해 충돌을 발견하면 실패시킨다.
     *
     * 비관적 잠금인 PESSIMISTIC_WRITE의 경우
     * 먼저 잠금을 잡아 충돌하는 작업을 대기시킨다.
     * -> 대기가 생길 수 있으므로 잠금을 가진 트랜잭션 안에서는 메일 발송처럼 오래 걸리는 작업을 피하고
     * DB처리를 짧게 끝내는 것이 좋다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select m
            from Member m
            where m.id = :memberId
            """)
    Optional<Member> findByIdForUpdate(
            @Param("memberId") Long memberId
    );
}
