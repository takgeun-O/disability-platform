package io.github.takgeun.iyum.admin.operation.infrastructure;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationSummaryCountsResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static io.github.takgeun.iyum.admin.moderation.domain.QContentModerationLog.contentModerationLog;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOperationSummaryReadRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 콘텐츠 현재 상태나 목록 조건과 무관하게 저장된 기록을 집계한다.
     * 독립 쿼리 사이에 동일한 DB 스냅샷을 보장하지 않는다.
     */
    public AdminOperationSummaryCountsResult findCounts(
            Instant fromInclusive,
            Instant toExclusive
    ) {
        BooleanExpression today = contentModerationLog.createdAt.goe(fromInclusive)
                .and(contentModerationLog.createdAt.lt(toExclusive));

        return new AdminOperationSummaryCountsResult(
                count(),
                count(today),
                count(today, contentModerationLog.action.eq(ModerationAction.HIDE)),
                count(today, contentModerationLog.action.eq(ModerationAction.RESTORE))
        );
    }

    private long count(BooleanExpression... conditions) {
        Long count = queryFactory
                .select(contentModerationLog.count())
                .from(contentModerationLog)
                .where(conditions)
                .fetchOne();
        return count == null ? 0L : count;
    }
}
