package io.github.takgeun.iyum.admin.dashboard.infrastructure;

import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardCountsResult;
import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardPendingReportResult;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static io.github.takgeun.iyum.community.post.domain.QPost.post;
import static io.github.takgeun.iyum.community.report.domain.QContentReport.contentReport;
import static io.github.takgeun.iyum.member.domain.QMember.member;

/**
 * 이 저장소에서 실행하는 조회는 집계 4번 + 신고 목록 1번임.
 * 목록의 각 신고마다 별도 조회를 실행하는 구조는 아님.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardReadRepository {

    private static final int PENDING_REPORT_LIMIT = 5;

    private final JPAQueryFactory queryFactory;

    /**
     * 대시보드 요약 수치를 조회한다.
     *
     * fromInclusive와 toExclusive는 서비스가 계산한
     * 한국 날짜의 시작 시각과 다음 날짜의 시작 시각이다.
     */
    public AdminDashboardCountsResult findCounts(
            Instant fromInclusive,
            Instant toExclusive
    ) {

        // 날짜 조건을 넣지 않음.
        // 어제 접수됐든, 한 달 전에 접수됐든 현재 접수 상태인 신고 전체가
        // 처리 대기 대상이기 때문
        Long pendingReportCount = queryFactory
                .select(contentReport.count())
                .from(contentReport)
                .where(
                        contentReport.status.eq(
                                ContentReportStatus.RECEIVED
                        )
                )
                .fetchOne();

        // 신고 상태 조건을 넣지 않음.
        // 오늘 접수된 뒤 처리 완료·기각됐어도
        // 오늘 접수 건수에는 포함되어야 하기 때문
        Long todayReportCount = queryFactory
                .select(contentReport.count())
                .from(contentReport)
                .where(
                        contentReport.createdAt.goe(fromInclusive),
                        contentReport.createdAt.lt(toExclusive)
                )
                .fetchOne();

        Long todayPostCount = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        post.createdAt.goe(fromInclusive),
                        post.createdAt.lt(toExclusive)
                )
                .fetchOne();

        Long todayMemberCount = queryFactory
                .select(member.count())
                .from(member)
                .where(
                        member.createdAt.goe(fromInclusive),
                        member.createdAt.lt(toExclusive)
                )
                .fetchOne();

        return new AdminDashboardCountsResult(
                pendingReportCount == null
                        ? 0L
                        : pendingReportCount,
                todayReportCount == null
                        ? 0L
                        : todayReportCount,
                todayPostCount == null
                        ? 0L
                        : todayPostCount,
                todayMemberCount == null
                        ? 0L
                        : todayMemberCount
        );
    }

    /**
     * 처리 대기 신고를 오래된 순으로 최대 5건 조회한다.
     *
     * 접수 시각이 같으면 신고 ID 오름차순으로 정렬한다.
     */
    public List<AdminDashboardPendingReportResult> findPendingReports() {
        return queryFactory
                .selectFrom(contentReport)
                // 게시글·댓글·회원과 조인할 필요 없다.
                // ContentReport에 모두 있으므로
                // 현재 콘텐츠가 삭제되거나 조회되지 않아도
                // 접수 신고는 목록에 남는다.
                .where(
                        contentReport.status.eq(
                                ContentReportStatus.RECEIVED
                        )
                )
                .orderBy(
                        contentReport.createdAt.asc(),
                        contentReport.id.asc()
                )
                .limit(PENDING_REPORT_LIMIT)
                .fetch()
                .stream()
                .map(AdminDashboardPendingReportResult::from)
                .toList();
    }
}
