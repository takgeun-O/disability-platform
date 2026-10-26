package io.github.takgeun.iyum.community.comment.infrastructure;

import io.github.takgeun.iyum.community.comment.domain.CommentLike;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentLikeRepository
        extends JpaRepository<CommentLike, Long> {

    // 특정 회원이 특정 댓글에 누른 좋아요 조회
    // Comment_id: CommentLike의 comment 필드 -> Comment의 id 필드를 따라간다는 의미
    Optional<CommentLike> findByComment_IdAndMember_Id(
            Long commentId,
            Long memberId
    );

    // 해당 댓글에 저장된 좋아요 개수 조회
    long countByComment_Id(Long commentId);

    @Query("""
            select
                c.id as commentId,
                count(cl) as likeCount,
                sum(
                    case
                        when cl.member.id = :viewerId then 1
                        else 0
                    end 
                ) as viewerLikeCount
            from CommentLike cl
            join cl.comment c
            join c.post p
            where c.id in :commentIds
                and c.deletedAt is null
                and p.deletedAt is null
            group by c.id
            """)
    List<CommentLikeSummary> findSummariesByCommentIds(
            @Param("commentIds") List<Long> commentIds,
            @Param("viewerId") @Nullable Long viewerId  // 비로그인일 때 viewerId=null
    );
}
