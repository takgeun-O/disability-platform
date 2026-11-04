package io.github.takgeun.iyum.admin.operation.infrastructure;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogReadQuery;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogTargetType;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Null;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static io.github.takgeun.iyum.admin.moderation.domain.QContentModerationLog.contentModerationLog;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOperationLogReadRepository {

    private final JPAQueryFactory queryFactory;

    public Page<ContentModerationLog> findLogs(
            AdminOperationLogReadQuery query
    ) {
        Pageable pageable = PageRequest.of(
                query.page(),
                query.size(),
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        // 목록과 전체 개수에 같은 검색 조건을 적용한다.
        // null인 조건은 QueryDSL의 where에서 제외된다.
        BooleanExpression[] conditions = {
                targetTypeCondition(query.targetType()),
                actionCondition(query.action()),
                fromCondition(query.fromInclusive()),
                toCondition(query.toExclusive())
        };

        Long count = queryFactory
                .select(contentModerationLog.count())
                .from(contentModerationLog)
                .where(conditions)
                .fetchOne();

        long total = count == null ? 0L : count;

        if(pageable.getOffset() >= total) {
            return new PageImpl<>(
                    List.of(),
                    pageable,
                    total
            );
        }

        List<ContentModerationLog> content = queryFactory
                .selectFrom(contentModerationLog)
                .where(conditions)
                // Pageable의 Sort는 QueryDSL에 자동 적용되지 않는다.
                // 그래서 여기서도 따로 정렬 조건 지정해줌
                .orderBy(
                        contentModerationLog.createdAt.desc(),
                        contentModerationLog.id.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(
                content,
                pageable,
                total
        );
    }

    @Nullable
    private BooleanExpression targetTypeCondition(
            AdminOperationLogTargetType targetType
    ) {
        // 기존 이력은 postId와 commentId 중 하나만 갖는다.
        return switch (targetType) {
            case ALL -> null;
            case POST -> contentModerationLog.postId.isNotNull();
            case COMMENT -> contentModerationLog.commentId.isNotNull();
        };
    }

    @Nullable
    private BooleanExpression actionCondition(
            @Nullable ModerationAction action
    ) {
        return action == null
                ? null
                : contentModerationLog.action.eq(action);
    }

    @Nullable
    private BooleanExpression fromCondition(
            @Nullable Instant fromInclusive
    ) {
        // 시작 시각은 포함한다.
        return fromInclusive == null
                ? null
                : contentModerationLog.createdAt.goe(fromInclusive);
    }

    @Nullable
    private BooleanExpression toCondition(
            @Nullable Instant toExclusive
    ) {
        // 종료 날짜 다음 날 00:00은 포함하지 않는다.
        return toExclusive == null
                ? null
                : contentModerationLog.createdAt.lt(toExclusive);
    }
}
