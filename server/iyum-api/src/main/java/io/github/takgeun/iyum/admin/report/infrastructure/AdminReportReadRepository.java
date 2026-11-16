package io.github.takgeun.iyum.admin.report.infrastructure;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.takgeun.iyum.admin.report.application.AdminReportReadQuery;
import io.github.takgeun.iyum.admin.report.application.AdminReportSort;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetType;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static io.github.takgeun.iyum.community.report.domain.QContentReport.contentReport;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportReadRepository {

    private final JPAQueryFactory queryFactory;

    public Page<ContentReport> findReports(AdminReportReadQuery query) {
        PageRequest pageable = PageRequest.of(
                query.page(),
                query.size(),
                pageSort(query.sort())
        );

        BooleanExpression[] conditions = {
                targetTypeCondition(query.targetType()),
                statusCondition(query.status()),
                reasonTypeCondition(query.reasonType()),
                fromCondition(query.fromInclusive()),
                toCondition(query.toExclusive())
        };

        Long count = queryFactory
                .select(contentReport.count())
                .from(contentReport)
                .where(conditions)
                .fetchOne();

        long total = count == null ? 0L : count;

        if(pageable.getOffset() >= total) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        List<ContentReport> reports = queryFactory
                .selectFrom(contentReport)
                .where(conditions)
                .orderBy(orderSpecifiers(query.sort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(reports, pageable, total);
    }

    public Optional<ContentReport> findById(Long reportId) {
        return Optional.ofNullable(
                queryFactory
                        .selectFrom(contentReport)
                        .where(contentReport.id.eq(reportId))
                        .fetchOne()
        );
    }

    @Nullable
    private BooleanExpression targetTypeCondition(
            AdminReportTargetType targetType
    ) {
        return switch (targetType) {
            case ALL -> null;
            case POST -> contentReport.postId.isNotNull();
            case COMMENT -> contentReport.commentId.isNotNull();
        };
    }

    @Nullable
    private BooleanExpression statusCondition(
            @Nullable ContentReportStatus status
    ) {
        return status == null
                ? null
                : contentReport.status.eq(status);
    }

    @Nullable
    private BooleanExpression reasonTypeCondition(
            @Nullable ContentReportReasonType reasonType
    ) {
        return reasonType == null
                ? null
                : contentReport.reasonType.eq(reasonType);
    }

    @Nullable
    private BooleanExpression fromCondition(
            @Nullable Instant fromInclusive
    ) {
        return fromInclusive == null
                ? null
                : contentReport.createdAt.goe(fromInclusive);
    }

    @Nullable
    private BooleanExpression toCondition(
            @Nullable Instant toExclusive
    ) {
        return toExclusive == null
                ? null
                : contentReport.createdAt.lt(toExclusive);
    }

    private Sort pageSort(AdminReportSort sort) {
        return switch (sort) {
            case LATEST -> Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );
            case OLDEST -> Sort.by(
                    Sort.Order.asc("createdAt"),
                    Sort.Order.asc("id")
            );
        };
    }

    private OrderSpecifier<?>[] orderSpecifiers(AdminReportSort sort) {
        return switch (sort) {
            case LATEST -> new OrderSpecifier<?>[]{
                    contentReport.createdAt.desc(),
                    contentReport.id.desc()
            };
            case OLDEST -> new OrderSpecifier<?>[]{
                    contentReport.createdAt.asc(),
                    contentReport.id.asc()
            };
        };
    }
}
