package io.github.takgeun.iyum.admin.content.infrastructure;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.takgeun.iyum.admin.content.application.AdminCommentReadQuery;
import io.github.takgeun.iyum.admin.content.application.AdminCommentStatus;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static io.github.takgeun.iyum.community.comment.domain.QComment.comment;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCommentReadRepository {

    private final JPAQueryFactory queryFactory;

    public Page<Comment> findComments(
            Long postId,
            AdminCommentReadQuery query
    ) {
        PageRequest pageable = PageRequest.of(
                query.page(),
                query.size(),
                Sort.by(
                        Sort.Order.asc("createdAt"),
                        Sort.Order.asc("id")
                )
        );

        // 다른 게시글의 댓글이 섞이지 않도록 항상 적용
        // comment: 엔티티를 바탕으로 생성한 쿼리 작성용 객체
        // 즉, ALL이고 검색어가 없어도 해당 게시글의 댓글만 조회할 것
        BooleanExpression postCondition =
                comment.post.id.eq(postId);

        BooleanExpression statusCondition =
                statusCondition(query.statuses());

        BooleanExpression keywordCondition =
                contentContains(query.keyword());

        Long count = queryFactory
                .select(comment.count())
                .from(comment)
                .where(
                        postCondition,  // 선택한 게시글의 댓글만 조회
                        statusCondition,// 선택한 댓글 상태 중 하나에 해당
                        keywordCondition// 댓글 본문 검색
                )
                .fetchOne();

        long total = count == null ? 0L : count;

        // 요청한 페이지에 데이터가 없다면 일찍 반환
        if(pageable.getOffset() >= total) {
            return new PageImpl<>(
                    List.of(),
                    pageable,
                    total
            );
        }

        List<Comment> content = queryFactory
                .selectFrom(comment)
                .join(comment.author).fetchJoin()
                .where(
                        postCondition,  // 선택한 게시글의 댓글만 조회
                        statusCondition,// 선택한 댓글 상태 중 하나에 해당
                        keywordCondition// 댓글 본문 검색
                )
                .orderBy(
                        comment.createdAt.asc(),
                        comment.id.asc()
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

    /**
     * 관리자용 댓글 단건 조회
     *
     * 댓글과 부모 게시글의 숨김·삭제 여부에 관계없이 조회한다.
     * 관리자 인증·권한 검증은 이 메서드를 호출하는 서비스에서 수행한다.
     *
     * 상세 응답에 필요한 작성자와 부모 게시글을 함께 조회한다.
     * 반환하는 본문은 현재 저장된 내용이며, 운영 조치 당시의 본문은 아니다.
     */
    public Optional<Comment> findCommentById(Long commentId) {
        Comment result = queryFactory
                .selectFrom(comment)
                .join(comment.author).fetchJoin()
                // 현재 post가 지연 로딩으로 설정되어 있고 fetch join이 없다면
                // 댓글을 가져온 시점에는 부모 게시글이 아직 로딩되지 않을 수 있음.
                // 그러면 부모 게시글을 초기화하기 위한 추가 조회가 발생할 수 있다.
                // 따라서 댓글을 가져올 때 작성자는 물론, 부모 게시글까지 한 번의 쿼리로 함께 가져오게 하여
                // 응답을 만들면서 작성자 닉네임, 게시글 정보를 읽어도 해당 연관관계를 위한
                // 추가 조회가 필요하지 않게 된다.
                .join(comment.post).fetchJoin()
                // 의도적으로 상태 조건을 추가하지 않는다.
                // 관리자는 기본적으로 상태에 상관없이 모두 조회할 수 있어야 하므로
                .where(comment.id.eq(commentId))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    private BooleanExpression statusCondition(
            Set<AdminCommentStatus> statuses
    ) {
        BooleanExpression condition = Expressions.FALSE;

        if (statuses.contains(AdminCommentStatus.VISIBLE)) {
            condition = condition.or(
                    comment.deletedAt.isNull()
                            .and(comment.hiddenAt.isNull())
            );
        }

        if (statuses.contains(AdminCommentStatus.HIDDEN)) {
            condition = condition.or(
                    comment.deletedAt.isNull()
                            .and(comment.hiddenAt.isNotNull())
            );
        }

        if (statuses.contains(AdminCommentStatus.DELETED)) {
            condition = condition.or(
                    comment.deletedAt.isNotNull()
            );
        }

        return condition;
    }

    @Nullable
    private BooleanExpression contentContains(
            @Nullable String keyword
    ) {
        if (keyword == null) {
            return null;
        }

        String escaped = keyword
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

        return comment.content
                .lower()
                .like("%" + escaped + "%", '!');
    }
}
