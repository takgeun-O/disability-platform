package io.github.takgeun.iyum.admin.content.infrastructure;

import com.querydsl.core.types.dsl.BooleanExpression;
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
                statusCondition(query.status());

        BooleanExpression keywordCondition =
                contentContains(query.keyword());

        Long count = queryFactory
                .select(comment.count())
                .from(comment)
                .where(
                        postCondition,
                        statusCondition,
                        keywordCondition
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
                        postCondition,
                        statusCondition,
                        keywordCondition
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

    @Nullable
    private BooleanExpression statusCondition(
            AdminCommentStatus status
    ) {
        return switch (status) {
            // 관리자는 숨김·삭제된 게시글에 달린 댓글도 확인해야 하므로
            // post.hiddenAt.isNull() 같은거 사용 X
            case ALL -> null;

            case VISIBLE -> comment.deletedAt.isNull()
                    .and(comment.hiddenAt.isNull());

            case HIDDEN -> comment.deletedAt.isNull()
                    .and(comment.hiddenAt.isNotNull());

            case DELETED -> comment.deletedAt.isNotNull();
        };
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
