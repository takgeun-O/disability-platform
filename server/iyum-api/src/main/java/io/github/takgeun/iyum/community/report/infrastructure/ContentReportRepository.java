package io.github.takgeun.iyum.community.report.infrastructure;

import io.github.takgeun.iyum.community.report.domain.ContentReport;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
