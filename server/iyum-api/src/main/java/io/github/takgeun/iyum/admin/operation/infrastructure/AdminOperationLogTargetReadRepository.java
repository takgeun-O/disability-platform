package io.github.takgeun.iyum.admin.operation.infrastructure;

import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogContentPreview;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogCurrentTargetResult;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogTargetAuthorResult;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
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
 * 운영 기록 목록에 표시할 현재 대상 정보를 일괄 조회한다.
 *
 * 관리자 인증·권한 검증은 호출하는 서비스에서 먼저 수행
 * 숨김·소프트 삭제 여부와 관계없이 현재 저장된 내용을 조회한다.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOperationLogTargetReadRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 게시글 ID를 키로 하는 현재 대상 요약을 반환한다.
     * 존재하지 않는 게시글 ID는 결과에 포함되지 않는다.
     *
     * 결과는 ID로 찾을 수 있도록 Map으로 반환한다.
     */
    public Map<Long, AdminOperationLogCurrentTargetResult> findPostSummaries(
            // 같은 댓글에 숨김·해제 기록이 여러 개 있어도 댓글 정보는 한 번만 조회하면 된다.
            // 서비스가 운영 기록 페이지의 ID를 Set으로 모아 전달하게 된다.
            Set<Long> postIds
    ) {
        if(postIds.isEmpty()) {
            return Map.of();
        }

        List<Post> posts = queryFactory
                .selectFrom(post)
                .join(post.author).fetchJoin()
                .where(post.id.in(postIds))
                .fetch();

        return posts.stream()
                .collect(Collectors.toMap(
                        Post::getId,
                        item -> new AdminOperationLogCurrentTargetResult(
                                AdminOperationLogTargetAuthorResult.from(
                                        item.getAuthor()
                                ),
                                item.getTitle(),
                                AdminOperationLogContentPreview.from(
                                        item.getContent()
                                )
                        )
                ));
    }

    /**
     * 댓글 ID를 키로 하는 현재 대상 요약을 반환한다.
     * 존재하지 않는 댓글 ID는 결과에 포함되지 않는다.
     *
     * 부모 게시글의 상태는 조회 조건으로 사용하지 않는다.
     */
    public Map<Long, AdminOperationLogCurrentTargetResult> findCommentSummaries(
            Set<Long> commentIds
    ) {
        if(commentIds.isEmpty()) {
            return Map.of();
        }

        List<Comment> comments = queryFactory
                .selectFrom(comment)
                .join(comment.author).fetchJoin()
                .where(comment.id.in(commentIds))
                .fetch();

        return comments.stream()
                .collect(Collectors.toMap(
                        Comment::getId,
                        item -> new AdminOperationLogCurrentTargetResult(
                                AdminOperationLogTargetAuthorResult.from(
                                        item.getAuthor()
                                ),
                                null,
                                AdminOperationLogContentPreview.from(
                                        item.getContent()
                                )
                        )
                ));
    }
}
