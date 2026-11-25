package io.github.takgeun.iyum.community.comment.infrastructure;

import io.github.takgeun.iyum.community.comment.domain.CommentLike;
import jakarta.annotation.Nullable;
import org.springframework.data.domain.Pageable;
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
                and c.hiddenAt is null
                and p.deletedAt is null
                and p.hiddenAt is null
            group by c.id
            """)
    List<CommentLikeSummary> findSummariesByCommentIds(
            @Param("commentIds") List<Long> commentIds,
            @Param("viewerId") @Nullable Long viewerId  // 비로그인일 때 viewerId=null
    );

    /**
     * 베스트 댓글 조회 메서드
     * 같은 묶음의 원댓글과 대댓글이 함께 경쟁한다.
     * 원댓글별 대표를 먼저 고르고,
     * 그 다음 전체 상위 3개를 고르는 순서
     * 예를 들어 질문에 달린 대댓글이 각각 좋아요 15개, 14개, 13개이고
     * 다른 질문의 대댓글이 12개라면
     * 베스트에는 좋아요 15개 답글과 12개 답글이 올라와야 한다.
     * 먼저 3개를 자르고 나중에 중복 제거하면 좋아요가 12개인 답글을 놓친다.
     */
    @Query(
            value = """
                    with candidates as (
                        select
                            c.id as comment_id,
                            /* coalesce(a, b): 왼쪽부터 확인해서 처음으로 NULL이 아닌 값 반환
                                예를 들어 원댓글의 경우 부모가 없으니 자기 ID를 사용하고
                                대댓글은 부모가 있으므로 부모 ID를 사용한다.
                                이런 식으로 원댓글과 대댓글과 같은 묶음 번호를 부여하도록 하는 것!
                            */
                            coalesce(c.parent_id, c.id) as root_id,
                            c.created_at,
                            count(*) as like_count
                        from community_comments c
                        join community_posts p
                            on p.id = c.post_id
                        /*
                            댓글과 좋아요를 연결한다.
                            한 댓글에 좋아요가 12개라면 조인 결과에서
                            그 댓글에 해당하는 행이 12개 생긴다.
                        */
                        join community_comment_likes cl
                            on cl.comment_id = c.id
                        /*
                            community_comments를 한 번 더 조인하는 이유
                            현재 댓글의 부모가 숨김 상태인지 확인하기 위해서임.
                            LEFT JOIN을 쓰는 이유는 부모가 없는 원댓글도
                            베스트 후보에 포함해야 하기 때문. 즉, 원댓글과 대댓글을 모두 후보로 보고
                            부모의 숨김 여부까지 검사해야 하기 때문에 쓴 것.

                            예를 들어 원댓글 100과 그 대댓글 101이 있다면
                            원댓글 100을 검사할 때는 연결된 부모 행이 없으니 parent.hidden_at은 NULL임.
                            따라서 parent.hidden_at is null 조건 통과하고
                            원댓글 자신의 숨김 여부는 c.hidden_at is null 조건에서 검사한다.
                            대댓글 101을 검사할 때는 parent.hidden_at이 실제 원댓글 100의 값임.
                            관리자가 100을 숨겼다면, 101 자체가 공개 상태이고 좋아요가 10개 이상이어도
                            베스트 후보에서 제외된다.
                        */
                        left join community_comments parent
                            on parent.id = c.parent_id
                        where p.id = :postId
                            and p.deleted_at is null
                            and p.hidden_at is null
                            and c.deleted_at is null
                            and c.hidden_at is null     -- 후보 댓글 자체가 공개 상태인가
                            and parent.hidden_at is null-- 부모가 있다면 그 부모도 숨김 상태가 아닌가
                        group by c.id, c.parent_id, c.created_at
                        having count(*) >= 10
                    ),
                    
                    /*
                        ranked
                        같은 root_id 안에서 좋아요가 가장 많은 댓글에 1을 부여한다.
                        좋아요가 같으면 기존 베스트 정책대로 먼저 작성한 댓글순,
                        작성 시각도 같으면 ID가 작은 댓글이 앞선다.
                    */
                    ranked as (
                        select
                            comment_id,
                            created_at,
                            like_count,
                            row_number() over (
                                -- 원댓글 묶음이 바뀔 때마다 순위를 1부터 다시 매긴다.
                                -- 같은 root_id를 가진 후보끼리만 순위를 매긴다.
                                partition by root_id
                                order by like_count desc, created_at asc, comment_id asc
                            ) as position_in_thread
                        from candidates
                    )
                    select comment_id
                    from ranked
                    -- 묶음별 1위만 남긴다.
                    where position_in_thread = 1
                    -- 살아남은 댓글들을 전체 베스트 순서로 정렬한다. (대표들을 화면에 보여줄 순서를 정하는 용도)
                    order by like_count desc, created_at asc, comment_id asc
                    """,
            nativeQuery = true
    )
    List<Long> findBestCommentIds(
            @Param("postId") Long postId,
            Pageable pageable
    );

    /**
     * 지정한 댓글에서 parentIds에 담긴 원댓글마다
     * 좋아요 10개 이상 받은 공개 대댓글 중 1개를 골라
     * (원댓글 ID, 대댓글 ID)로 반환한다.
     * 원댓글 자체의 좋아요 수는 선정에 영향을 주지 않는다.
     *
     * 같은 원댓글에 달린 대댓글끼리만 경쟁시키기 위해
     * from community_comments reply로 시작하여
     * reply.parent_id in (:parentIds) 적용
     */
    @Query(
            value = """
                -- DISTINCT ON으로 원댓글마다 첫 번째 후보만 남긴다.
                -- 같은 parent_id를 가진 행 중 ORDER BY 기준으로
                -- 첫 번째 행 하나를 남긴다.
                select distinct on (reply.parent_id)
                    reply.parent_id as "parentId",
                    reply.id as "replyId"
                from community_comments reply
                -- 대댓글이 어느 원댓글에 달렸는지 연결
                -- 하단에서 parent.hidden_at is null로 숨긴 원댓글 제외
                join community_comments parent
                    on parent.id = reply.parent_id
                -- :postId에 해당하는 게시글인지, 그 글이 삭제되거나 숨겨지지 않았는지 확인
                join community_posts p
                    on p.id = reply.post_id
                -- cl.comment_id = reply.id로 대댓글이 받은 좋아요를 연결
                -- 하단에서 대댓글 자체도 삭제 또는 숨김 상태라면 제외
                join community_comment_likes cl
                    on cl.comment_id = reply.id
                where p.id = :postId
                    and reply.parent_id in (:parentIds)
                    and p.deleted_at is null
                    and p.hidden_at is null
                    and parent.hidden_at is null
                    and reply.deleted_at is null
                    and reply.hidden_at is null
                group by reply.parent_id, reply.id, reply.created_at
                having count(*) >= 10
                order by
                    reply.parent_id,
                    count(*) desc,
                    reply.created_at asc,
                    reply.id asc
                """,
            nativeQuery = true
    )
    List<BestReplyIds> findBestRepliesByParentIds(
            @Param("postId") Long postId,
            @Param("parentIds") List<Long> parentIds
    );
}
