package io.github.takgeun.iyum.community.report.infrastructure;

import io.github.takgeun.iyum.community.report.domain.ContentReport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ContentReportRepository
        extends JpaRepository<ContentReport, Long> {

    // 신고 접수 서비스에서 기존 신고 여부를 확인할 때 사용
    Optional<ContentReport> findByReporterMemberIdAndPostId(
            Long reporterMemberId,
            Long postId
    );

    // 신고 접수 서비스에서 기존 신고 여부를 확인할 때 사용
    Optional<ContentReport> findByReporterMemberIdAndCommentId(
            Long reportMemberId,
            Long commentId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from ContentReport r
            where r.id = :reportId
            """)
    Optional<ContentReport> findByIdForUpdate(
            @Param("reportId") Long reportId
    );
}
