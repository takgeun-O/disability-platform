package io.github.takgeun.iyum.admin.member.infrastructure;

import io.github.takgeun.iyum.admin.member.domain.MemberModerationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberModerationLogRepository
        extends JpaRepository<MemberModerationLog, Long> {

    Page<MemberModerationLog> findByTargetMemberId(
            Long targetMemberId,
            Pageable pageable
            // 정렬 조건은 조회 서비스에서 고정해서 전달
    );
}
