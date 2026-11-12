package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.admin.report.infrastructure.AdminReportReadRepository;
import io.github.takgeun.iyum.admin.report.infrastructure.AdminReportTargetReadRepository;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 관리자 신고 조회 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportReadService {

    private final MemberRepository memberRepository;
    private final AdminReportReadRepository reportReadRepository;
    private final AdminReportTargetReadRepository targetReadRepository;

    /**
     * actorMemberId와 credentialVersion은
     * 로긘한 MemberPrincipal에서 전달받는다.
     */
    public Page<AdminReportItemResult> getReports(
            @Nullable Long actorMemberId,
            long credentialVersion,
            AdminReportReadQuery query
    ) {
        validateAdmin(actorMemberId, credentialVersion);

        // 신고 페이지를 먼저 확정한다.
        Page<ContentReport> reports =
                reportReadRepository.findReports(query);

        // 현재 페이지에 있는 대상 ID만 중복 없이 수집한다.
        Set<Long> postIds = reports.getContent()
                .stream()
                .map(ContentReport::getPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> commentIds = reports.getContent()
                .stream()
                .map(ContentReport::getCommentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, AdminReportCurrentTargetResult> posts =
                postIds.isEmpty()
                        ? Map.of()
                        : targetReadRepository.findPosts(postIds);

        Map<Long, AdminReportCurrentTargetResult> comments =
                commentIds.isEmpty()
                        ? Map.of()
                        : targetReadRepository.findComments(commentIds);

        // Page.map으로 페이지 정보와 서버 정렬 순서를 유지한다.
        return reports.map(report -> {
            AdminReportCurrentTargetResult currentTarget =
                    report.getPostId() != null
                            ? posts.get(report.getPostId())
                            : comments.get(report.getCommentId());

            return AdminReportItemResult.from(report, currentTarget);
        });
    }

    public AdminReportDetailResult getReport(
            @Nullable Long actorMemberId,
            long credentialVersion,
            @Nullable Long reportId
    ) {
        // 신고 존재 여부보다 요청자의 자격을 먼저 확인
        validateAdmin(actorMemberId, credentialVersion);

        if (reportId == null || reportId <= 0) {
            throw new AdminReportReadException(
                    AdminReportReadException.Code.REPORT_NOT_FOUND
            );
        }

        ContentReport report = reportReadRepository.findById(reportId)
                .orElseThrow(() ->
                        new AdminReportReadException(
                                AdminReportReadException.Code.REPORT_NOT_FOUND
                        )
                );

        AdminReportCurrentTargetResult currentTarget;

        if (report.getPostId() != null) {
            Long postId = report.getPostId();

            currentTarget = targetReadRepository
                    .findPosts(Set.of(postId))
                    .get(postId);
        } else {
            Long commentId = report.getCommentId();

            currentTarget = targetReadRepository
                    .findComments(Set.of(commentId))
                    .get(commentId);
        }

        // 현재 대상이 없어도 신고 당시 스냅샷은 반환한다.
        return AdminReportDetailResult.from(report, currentTarget);
    }

    private void validateAdmin(
            @Nullable Long actorMemberId,
            long credentialVersion
    ) {
        if (actorMemberId == null
                || actorMemberId <= 0
                || credentialVersion < 0) {
            throw new AdminReportReadException(
                    AdminReportReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        Member actor = memberRepository.findById(actorMemberId)
                .orElseThrow(() ->
                        new AdminReportReadException(
                                AdminReportReadException.Code.AUTHENTICATION_REQUIRED
                        )
                );

        if (actor.getStatus() != MemberStatus.ACTIVE
                || actor.getCredentialVersion() != credentialVersion) {
            throw new AdminReportReadException(
                    AdminReportReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        if (actor.getRole() != MemberRole.ADMIN) {
            throw new AdminReportReadException(
                    AdminReportReadException.Code.ADMIN_REQUIRED
            );
        }
    }
}
