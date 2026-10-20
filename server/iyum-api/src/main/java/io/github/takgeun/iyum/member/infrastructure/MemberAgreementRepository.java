package io.github.takgeun.iyum.member.infrastructure;

import io.github.takgeun.iyum.member.domain.MemberAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberAgreementRepository extends JpaRepository<MemberAgreement, Long> {

    // 특정 회원의 동의 이력을 ID 오름차순으로 조회
    List<MemberAgreement> findAllByMemberIdOrderByIdAsc(Long memberId);

    /**
     * 약관 동의 저장소에 삭제 메서드 추가
     */
    @Modifying
    @Query("""
            delete from MemberAgreement a
            where a.memberId = :memberId
            """)
    int deleteAllByMemberId(
            @Param("memberId") Long memberId
    );
}
