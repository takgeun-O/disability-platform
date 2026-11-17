package io.github.takgeun.iyum.admin.report.infrastructure;

import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.takgeun.iyum.admin.report.application.AdminReportSummaryCountsResult;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static io.github.takgeun.iyum.community.report.domain.QContentReport.contentReport;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportSummaryReadRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 목록 필터와 무관한 전체 신고 요약 수치를 조회한다.
     * <p>
     * fromInclusive는 한국 날짜 기준 오늘 시작 시각,
     * toExclusive는 다음날 시작 시각
     *
     * 게시글·댓글 테이블과 조인하지 않는다. (ContentReport 엔티티에 이미 정보 있음)
     * 신고 자체를 세므로 현재 콘텐츠의 숨김·삭제 여부나
     * 동일 콘텐츠에 대한 다른 신고의 존재가 집계를 방해하지 않는다.
     *
     * 이 메서드는 집계 쿼리 3번 실행한다.
     *
     * READ COMMITTED에서는 쿼리 사이에 다른 트랜잭션의 변경이
     * 커밋되면 각 집계가 서로 다른 DB 상태를 반영할 수 있다.
     * 예) 세 번의 집계 쿼리가 실행되는 사이에 다른 관리자가 신고를 처리하면
     * 각 숫자가 서로 다른 시점의 DB 상태를 반영할 수 있다.
     * 현재 운영 요약 카드에서는 이 정도의 차이는 허용한다.
     * 정확한 한 시점의 정산 자료가 아니라 운영 현황을 보여주는 수치이기 때문에
     * 지금 당장 격리 수준을 높일 필요는 없다.
     * (필요 시 단일 쿼리로 묶거나 REPEATABLE READ를 적용하면
     * 동일한 스냅샷을 기준으로 집계하도록 바꿀 수 있다.)
     */
    public AdminReportSummaryCountsResult findCounts(
            Instant fromInclusive,
            Instant toExclusive
    ) {
        // 처리 대기
        // 날짜 조건 없음. 어제 접수됐더라도 현재 RECEIVED라면 포함
        Long pendingReportCount = queryFactory
                .select(contentReport.count())
                .from(contentReport)
                .where(
                        contentReport.status.eq(
                                ContentReportStatus.RECEIVED
                        )
                )
                .fetchOne();

        // 오늘 접수
        // 상태 조건 없음. 오늘 접수 후 종결됐어도 접수 건수는 유지
        Long todayReportCount = queryFactory
                .select(contentReport.count())
                .from(contentReport)
                .where(
                        contentReport.createdAt.goe(fromInclusive),
                        contentReport.createdAt.lt(toExclusive)
                )
                .fetchOne();

        // 오늘 종결은 handledAt으로 집계
        // RESOLVED와 DISMISSED를 포함하며, 접수 날짜는 확인하지 않음
        Long todayHandledReportCount = queryFactory
                .select(contentReport.count())
                .from(contentReport)
                .where(
                        contentReport.status.in(
                                ContentReportStatus.RESOLVED,
                                ContentReportStatus.DISMISSED
                        ),
                        contentReport.handledAt.goe(fromInclusive),
                        contentReport.handledAt.lt(toExclusive)
                )
                .fetchOne();

        return new AdminReportSummaryCountsResult(
                pendingReportCount == null
                        ? 0L
                        : pendingReportCount,
                todayReportCount == null
                        ? 0L
                        : todayReportCount,
                todayHandledReportCount == null
                        ? 0L
                        : todayHandledReportCount
        );
    }
}
