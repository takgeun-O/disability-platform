package io.github.takgeun.iyum.member.infrastructure;

import io.github.takgeun.iyum.member.domain.MemberAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberAgreementRepository extends JpaRepository<MemberAgreement, Long> {

    // 특정 회원의 동의 이력을 ID 오름차순으로 조회
    List<MemberAgreement> findAllByMemberIdOrderByIdAsc(Long memberId);
}
