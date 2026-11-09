package io.github.takgeun.iyum.admin.operation.infrastructure;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogReadQuery;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogSort;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogTargetType;
import jakarta.annotation.Nullable;
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
                pageSort(query.sort())
        );

        // 목록과 전체 개수에 같은 검색 조건을 적용한다.
        // null인 조건은 QueryDSL의 where에서 제외된다.
        BooleanExpression[] conditions = {
                targetTypeCondition(query.targetType()),
                actionCondition(query.action()),
                reasonTypeCondition(query.reasonType()),
                fromCondition(query.fromInclusive()),
                toCondition(query.toExclusive())
        };

        Long count = queryFactory
                .select(contentModerationLog.count())
                .from(contentModerationLog)
                .where(conditions)
                .fetchOne();

        long total = count == null ? 0L : count;

        if (pageable.getOffset() >= total) {
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
                // 전체 검색 결과를 정렬한 다음 페이지 범위를 적용한다.
                .orderBy(orderSpecifiers(query.sort()))
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

    /**
     * 반환하는 Page에 담길 정렬 정보.
     * 이 설정만으로 QueryDSL 쿼리가 정렬되지는 않는다.
     * <p>
     * 페이지가 설명하는 정렬 정보와 실제 조회 순서를 일치시키지 위해서 필요
     * pageSort() + orderSpecifiers()
     */
    private Sort pageSort(AdminOperationLogSort sort) {
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

    /**
     * 실제 DB 조회에 적용할 정렬 조건.
     * 처리 시각이 같으면 ID로 순서를 확정한다.
     * 페이지가 설명하는 정렬 정보와 실제 조회 순서를 일치시키지 위해서 필요
     * pageSort() + orderSpecifiers()
     */
    private OrderSpecifier<?>[] orderSpecifiers(
            AdminOperationLogSort sort
    ) {
        return switch (sort) {
            case LATEST -> new OrderSpecifier<?>[]{
                    contentModerationLog.createdAt.desc(),
                    contentModerationLog.id.desc()
            };
            case OLDEST -> new OrderSpecifier<?>[]{
                    contentModerationLog.createdAt.asc(),
                    contentModerationLog.id.asc()
            };
        };
    }

    /**
     * 저장된 사유 유형 코드로 검색한다.
     * <p>
     * null이면 사유 유형 조건을 추가하지 않는다.
     * LEGACY도 기존 운영 기록을 조회하기 위한 정상적인 검색값이다.
     */
    @Nullable
    private BooleanExpression reasonTypeCondition(
            @Nullable ModerationReasonType reasonType
    ) {
        return reasonType == null
                ? null
                : contentModerationLog.reasonType.eq(reasonType);
    }
}
