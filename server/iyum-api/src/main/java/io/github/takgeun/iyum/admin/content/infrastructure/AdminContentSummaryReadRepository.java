package io.github.takgeun.iyum.admin.content.infrastructure;

import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.takgeun.iyum.admin.content.application.AdminContentSummaryCountsResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static io.github.takgeun.iyum.community.comment.domain.QComment.comment;
import static io.github.takgeun.iyum.community.post.domain.QPost.post;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminContentSummaryReadRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 목록 필터와 무관한 전체 게시글·댓글 요약 수치를 조회한다.
     *
     * fromInclusive는 한국 날짜 기준 오늘 시작 시각,
     * toExclusive는 다음날 시작 시각이다.
     *
     * 오늘 작성 수는 현재 숨김·삭제 여부와 무관하게 집계한다.
     * 현재 숨김 수는 기간 조건 없이 집계하며 삭제된 대상은 제외한다.
     * 댓글 집계에는 부모 게시글의 상태를 반영하지 않는다.
     *
     * 집계 쿼리 4개를 실행하며,
     * 모든 조회가 동일한 DB 스냅샷임을 보장하지 않는다.
     * 예) 네 번의 집계 쿼리가 실행되는 사이에 콘텐츠가 작성·숨김·복원·삭제되면
     * 각 숫자가 서로 다른 시점의 DB 상태를 반영할 수 있다.
     * 현재 운영 요약 카드에서는 이 정도의 차이는 허용한다.
     * 정확한 한 시점의 정산 자료가 아니라 운영 현황을 보여주는 수치이기 때문에
     * 지금 당장 격리 수준을 높일 필요는 없다.
     * (필요 시 단일 쿼리로 묶거나 REPEATABLE READ를 적용하면
     * 동일한 스냅샷을 기준으로 집계하도록 바꿀 수 있다.)
     */
    public AdminContentSummaryCountsResult findCounts(
            Instant fromInclusive,
            Instant toExclusive
    ) {
        // 숨김·삭제 조건 없음. (오늘 작성한 뒤 삭제해도 오늘 작성 수에 포함)
        Long todayPostCount = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        post.createdAt.goe(fromInclusive),
                        post.createdAt.lt(toExclusive)
                )
                .fetchOne();

        // 숨김·삭제 조건 없음. (오늘 작성한 뒤 삭제해도 오늘 작성 수에 포함)
        // 댓글 쿼리는 댓글만 조회한다. (부모 게시글만 숨겨졌다는 이유로 숨김 댓글 수가 증가하지 않음)
        Long todayCommentCount = queryFactory
                .select(comment.count())
                .from(comment)
                .where(
                        comment.createdAt.goe(fromInclusive),
                        comment.createdAt.lt(toExclusive)
                )
                .fetchOne();

        // 현재 숨김 쿼리에는 날짜 조건이 없음.
        // 어제 작성·숨김 처리한 콘텐츠도 아직 숨겨져 있다면 포함
        Long hiddenPostCount = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        post.hiddenAt.isNotNull(),
                        post.deletedAt.isNull()
                )
                .fetchOne();

        // 현재 숨김 쿼리에는 날짜 조건이 없음.
        // 어제 작성·숨김 처리한 콘텐츠도 아직 숨겨져 있다면 포함
        // 댓글 쿼리는 댓글만 조회한다. (부모 게시글만 숨겨졌다는 이유로 숨김 댓글 수가 증가하지 않음)
        Long hiddenCommentCount = queryFactory
                .select(comment.count())
                .from(comment)
                .where(
                        comment.hiddenAt.isNotNull(),
                        comment.deletedAt.isNull()
                )
                .fetchOne();

        return new AdminContentSummaryCountsResult(
                todayPostCount == null
                        ? 0L
                        : todayPostCount,
                todayCommentCount == null
                        ? 0L
                        : todayCommentCount,
                hiddenPostCount == null
                        ? 0L
                        : hiddenPostCount,
                hiddenCommentCount == null
                        ? 0L
                        : hiddenCommentCount
        );
    }
}
