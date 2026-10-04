package io.github.takgeun.iyum.member.infrastructure;

import io.github.takgeun.iyum.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            select count(m) > 0
            from Member m
            where lower(m.nickname) = lower(:nickname)
            """)
    boolean existsByNicknameIgnoreCase(
            @Param("nickname") String nickname
    );
}
