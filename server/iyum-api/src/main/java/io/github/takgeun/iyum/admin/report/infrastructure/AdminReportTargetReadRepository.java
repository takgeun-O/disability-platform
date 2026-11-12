package io.github.takgeun.iyum.admin.report.infrastructure;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.takgeun.iyum.admin.report.application.AdminReportCurrentTargetResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetAuthorResult;
import io.github.takgeun.iyum.member.domain.QMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.takgeun.iyum.community.comment.domain.QComment.comment;
import static io.github.takgeun.iyum.community.post.domain.QPost.post;

/**
 * 신고 대상의 현재 정보를 일괄 조회한다.
 *
 * 호출 서비스에서 관리자 인증과 권한을 먼저 검증해야 한다.
 * 숨김 및 소프트 삭제 상태는 조회 제외 조건으로 사용하지 않는다.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportTargetReadRepository {

    private static final QMember AUTHOR =
            new QMember("reportTargetAuthor");

    private final JPAQueryFactory queryFactory;

    /**
     * 게시글 ID를 키로 반환한다.
     * 존재하지 않는 ID는 결과 Map에 포함되지 않는다.
     */
    public Map<Long, AdminReportCurrentTargetResult> findPosts(
            Set<Long> postIds
    ) {
        if(postIds.isEmpty()) {
            return Map.of();
        }

        List<Tuple> rows = queryFactory
                .select(
                        post.id,
                        AUTHOR.id,
                        AUTHOR.nickname,
                        AUTHOR.status,
                        post.title,
                        post.content,
                        post.hiddenAt,
                        post.deletedAt
                )
                .from(post)
                .join(post.author, AUTHOR)
                .where(post.id.in(postIds))
                .fetch();

        return rows.stream()
                .collect(Collectors.toMap(
                        row -> row.get(post.id),
                        row -> new AdminReportCurrentTargetResult(
                                row.get(post.id),
                                null,
                                authorFrom(row),
                                row.get(post.title),
                                row.get(post.content),
                                row.get(post.hiddenAt),
                                row.get(post.deletedAt)
                        )
                ));
    }

    /**
     * 댓글 ID를 키로 반환한다.
     * 부모 게시글의 숨김·삭제 여부와 관계없이 조회한다.
     */
    public Map<Long, AdminReportCurrentTargetResult> findComments(
            Set<Long> commentIds
    ) {
        if(commentIds.isEmpty()) {
            return Map.of();
        }

        List<Tuple> rows = queryFactory
                .select(
                        comment.id,
                        comment.post.id,
                        AUTHOR.id,
                        AUTHOR.nickname,
                        AUTHOR.status,
                        comment.content,
                        comment.hiddenAt,
                        comment.deletedAt
                )
                .from(comment)
                .join(comment.author, AUTHOR)
                .where(comment.id.in(commentIds))
                .fetch();

        return rows.stream()
                .collect(Collectors.toMap(
                        row -> row.get(comment.id),
                        row -> new AdminReportCurrentTargetResult(
                                row.get(comment.id),
                                row.get(comment.post.id),
                                authorFrom(row),
                                null,
                                row.get(comment.content),
                                row.get(comment.hiddenAt),
                                row.get(comment.deletedAt)
                        )
                ));
    }

    private AdminReportTargetAuthorResult authorFrom(Tuple row) {
        return AdminReportTargetAuthorResult.from(
                row.get(AUTHOR.id),
                row.get(AUTHOR.nickname),
                row.get(AUTHOR.status)
        );
    }
}
