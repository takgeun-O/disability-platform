package io.github.takgeun.iyum.community.comment.infrastructure;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CommentRepository
        extends JpaRepository<Comment, Long> {

    // 기본 findById()는 논리 삭제된 댓글도 조회함.
    // 즉, 논리 삭제된 댓글은 조회하지 않도록 별도 구현 필요

    // delete()는 물리 삭제이므로 서비스에서 논리 삭제할 때는
    // comment.delete(시각)을 사용할 것

    // 첫 조회에는 커서가 없으니 findFirstVisibleComments()
    // 이후에는 findVisibleCommentsAfter() 사용

    // exists는 지금 조회 중인 원 댓글 c에 공개 답글이 하나라도 있는지 확인
    // 공개 답글이 두 개든 열 개든 원댓글은 목록에서 한 자리만 차지
    @Query("""
            select c
            from Comment c
            join c.post p
            join fetch c.author
            where p.id = :postId
                and c.parent is null
                and p.deletedAt is null
                and p.hiddenAt is null
                and (
                     c.deletedAt is null
                     or exists (
                         select 1
                         from Comment reply
                         where reply.parent = c
                            and reply.deletedAt is null
                            and reply.hiddenAt is null
                     )
                )
                and c.hiddenAt is null
            order by c.createdAt asc, c.id asc
            """)
    List<Comment> findFirstVisibleComments(
            @Param("postId") Long postId,
            Pageable pageable
    );

    // 무한 스크롤에서는 전체 페이지 수 대신 다음 댓글이 더 있는지가 필요하기 떄문에
    // countQuery는 필요 없음.
    @Query("""
            select c
            from Comment c
            join c.post p
            join fetch c.author
            where p.id = :postId
                and c.parent is null
                and p.deletedAt is null
                and p.hiddenAt is null
                and (
                     c.deletedAt is null
                     or exists (
                        select 1
                        from Comment reply
                        where reply.parent = c
                            and reply.deletedAt is null
                            and reply.hiddenAt is null
                     )
                )
                and c.hiddenAt is null
                and (
                    c.createdAt > :afterCreatedAt
                    or (
                        c.createdAt = :afterCreatedAt
                        and c.id > :afterId
                    )
                )
            order by c.createdAt asc, c.id asc
            """)
    List<Comment> findVisibleCommentsAfter(
            @Param("postId") Long postId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    // @Lock이 있으므로 이 메서드는 트랜잭션 안에서 호출해야 한다.

    /**
     * 공개 게시글에 속한 공개 댓글을 잠금 조회한다.
     * <p>
     * 댓글 수정·좋아요 처리에 사용한다.
     * 호출 전에 부모 게시글 잠금을 먼저 획득해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from Comment c
            join c.post p
            where c.id = :commentId
                and p.id = :postId
                and p.deletedAt is null
                and p.hiddenAt is null
                and c.deletedAt is null
                and c.hiddenAt is null
                and not exists (
                    select 1 from Comment root
                    where root = c.parent and root.hiddenAt is not null
                )
            """)
    Optional<Comment> findVisibleCommentByIdForUpdate(
            @Param("postId") Long postId,
            @Param("commentId") Long commentId
    );

    /**
     * 전달 받은 게시글들의 공개 댓글 수를 한번에 집계한다.
     * 댓글이 없는 게시글은 결과에 포함되지 않는다.
     * <p>
     * p.id in :postIds -> 현재 목록에 표시할 게시글들만 대상으로 조회
     * group by p.id -> 게시글별로 묶어서 댓글 수 계산
     */
    @Query("""
            select p.id as postId,
                count(c.id) as commentCount
            from Comment c
            join c.post p
            where p.id in :postIds
                and p.deletedAt is null
                and p.hiddenAt is null
                and c.deletedAt is null
                and c.hiddenAt is null
                and not exists (
                    select 1 from Comment root
                    where root = c.parent and root.hiddenAt is not null
                )
            group by p.id
            """)
    List<PostCommentCount> countVisibleCommentsByPostIds(
            @Param("postIds") List<Long> postIds
    );

    @Query("""
        select root.id as parentId,
            count(reply.id) as replyCount
        from Comment reply
        join reply.parent root
        join root.post p
        where p.id = :postId
            and root.id in :parentIds
            and root.parent is null
            and p.deletedAt is null
            and p.hiddenAt is null
            and root.hiddenAt is null
            and reply.deletedAt is null
            and reply.hiddenAt is null
        group by root.id
        """)
    List<CommentReplyCount> countVisibleRepliesByParentIds(
            @Param("postId") Long postId,
            @Param("parentIds") List<Long> parentIds
    );

    // 내 댓글 수 조회
    @Query("""
            select count(c)
            from Comment c
            join c.post p
            where c.author.id = :memberId
                and c.deletedAt is null
                and c.hiddenAt is null
                and p.deletedAt is null
                and p.hiddenAt is null
                and not exists (
                    select 1 from Comment root
                    where root = c.parent and root.hiddenAt is not null
                )
            """)
    long countVisibleCommentsByAuthorId(
            @Param("memberId") Long memberId
    );

    // 내 댓글 첫 조회
    // 삭제되지 않은 게시글에 작성한 내 공개 댓글을 최신순으로 조회한다.
    @Query("""
            select c
            from Comment c
            join fetch c.post p
            where c.author.id = :memberId
                and c.deletedAt is null
                and c.hiddenAt is null
                and p.deletedAt is null
                and p.hiddenAt is null
                and not exists (
                    select 1 from Comment root
                    where root = c.parent and root.hiddenAt is not null
                )
            order by c.createdAt desc, c.id desc
            """)
    List<Comment> findFirstVisibleCommentsByAuthorId(
            @Param("memberId") Long memberId,
            Pageable pageable
    );

    // 내 댓글 후속 조회
    // 이전에 반환한 마지막 댓글보다 정렬 순서상 뒤에 있는 댓글을 조회한다.
    @Query("""
            select c
            from Comment c
            join fetch c.post p
            where c.author.id = :memberId
                and c.deletedAt is null
                and c.hiddenAt is null
                and p.deletedAt is null
                and p.hiddenAt is null
                and not exists (
                    select 1 from Comment root
                    where root = c.parent and root.hiddenAt is not null
                )
                and (
                    c.createdAt < :afterCreatedAt
                    or (
                        c.createdAt = :afterCreatedAt
                        and c.id < :afterId
                    )
                )
            order by c.createdAt desc, c.id desc
            """)
    List<Comment> findVisibleCommentsByAuthorIdAfter(
            @Param("memberId") Long memberId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    /**
     * 최신순 첫 조회
     * 게시글의 공개 댓글을 최신순으로 처음 조회한다.
     * post는 조회 조건에 사용하고(p의 ID나 상태 등을 조회 조건에 사용),
     * 댓글 작성자는 댓글과 함께 화면에 보여주기 위해 미리 가져온다.
     */
    @Query("""
            select c
            from Comment c
            join c.post p
            join fetch c.author
            where p.id = :postId
                and c.parent is null
                and (
                    c.deletedAt is null
                    or exists (
                        select 1
                        from Comment reply
                        where reply.parent = c
                            and reply.deletedAt is null
                            and reply.hiddenAt is null
                    )
                )
                and p.hiddenAt is null
                and p.deletedAt is null
                and c.hiddenAt is null
            order by c.createdAt desc, c.id desc
            """)
    List<Comment> findFirstVisibleCommentsLatest(
            @Param("postId") Long postId,
            Pageable pageable
    );

    /**
     * 최신순 후속 조회
     * 최신순에서 이전 페이지의 마지막 댓글보다
     * 정렬 순서상 뒤에 있는 댓글을 조회한다.
     */
    @Query("""
            select c
            from Comment c
            join c.post p
            join fetch c.author
            where p.id = :postId
                and c.parent is null
                and p.deletedAt is null
                and p.hiddenAt is null
                and (
                    c.deletedAt is null
                    or exists (
                        select 1
                        from Comment reply
                        where reply.parent = c
                            and reply.deletedAt is null
                            and reply.hiddenAt is null
                    )
                )
                and c.hiddenAt is null
                and (
                    c.createdAt < :afterCreatedAt
                    or (
                        c.createdAt = :afterCreatedAt
                        and c.id < :afterId
                    )
                )
            order by c.createdAt desc, c.id desc
            """)
    List<Comment> findVisibleCommentsLatestAfter(
            @Param("postId") Long postId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    /**
     * 댓글 인기순 첫 조회
     * 좋아요 수 내림차순,
     * 작성 시각 오름차순,
     * 댓글 ID 오름차순으로 처음 조회한다.
     * <p>
     * left join: 좋아요가 없는 댓글도 조회 대상에 포함한다.
     */
    @Query("""
            select
                c.id as commentId,
                c.createdAt as createdAt,
                count(cl.id) as likeCount
            from Comment c
            join c.post p
            left join CommentLike cl
                on cl.comment = c
                and c.deletedAt is null
            where p.id = :postId
                and c.parent is null
                and p.deletedAt is null
                and p.hiddenAt is null
                and (
                    c.deletedAt is null
                    or exists (
                        select 1
                        from Comment reply
                        where reply.parent = c
                            and reply.deletedAt is null
                            and reply.hiddenAt is null
                    )
                )
                and c.hiddenAt is null
            group by c.id, c.createdAt
            order by count(cl.id) desc, c.createdAt asc, c.id asc
            """)
    List<CommentPopularityRow> findFirstVisibleCommentsPopular(
            @Param("postId") Long postId,
            Pageable pageable
    );

    /**
     * 댓글 인기순 후속조회
     * 인기순의 다음 페이지는 이전 마지막 댓글보다 다음 조건에 해당하는 댓글을 가져와야 한다.
     * 1. 좋아요 수가 더 적거나
     * 2. 좋아요 수가 같고 작성 시각이 더 늦거나
     * 3. 좋아요 수와 작성 시각이 모두 같고 ID가 더 크거나
     */
    @Query("""
            select
                c.id as commentId,
                c.createdAt as createdAt,
                count(cl.id) as likeCount
            from Comment c
            join c.post p
            left join CommentLike cl
                on cl.comment = c
                and c.deletedAt is null
            where p.id = :postId
                and c.parent is null
                and p.deletedAt is null
                and p.hiddenAt is null
                and (
                    c.deletedAt is null
                    or exists (
                        select 1
                        from Comment reply
                        where reply.parent = c
                            and reply.deletedAt is null
                            and reply.hiddenAt is null
                    )
                )
                and c.hiddenAt is null
            group by c.id, c.createdAt
            having
                count(cl.id) < :afterLikeCount
                or (
                    count(cl.id) = :afterLikeCount
                    and (
                        c.createdAt > :afterCreatedAt
                        or (
                            c.createdAt = :afterCreatedAt
                            and c.id > :afterId
                        )
                    )
                )
            order by count(cl.id) desc, c.createdAt asc, c.id asc
            """)
    List<CommentPopularityRow> findVisibleCommentsPopularAfter(
            @Param("postId") Long postId,
            @Param("afterLikeCount") long afterLikeCount,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    /**
     * 댓글 일괄 조회
     * <p>
     * 인기순 조회 등에서 선택한 댓글들의 본문과 작성자를
     * 한번에 조회한다.
     * 인기순 쿼리는 ID·시각·좋아요 수만 반환하므로
     * 실제 응답에 필요한 본문과 작성자를 한번에 가져오는 조회가 필요하다.
     * <p>
     * IN에 ID를 넣는 순서대로 결과가 반환되는 것은 아님. (반환 순서는 보장하지 않는다.)
     * 서비스에서 이 결과를 ID별로 묶어서 인기순 결과의 순서대로 응답 조립할 것.
     * <p>
     * 또한 조회할 ID가 비어 있으면 이 메서드를 호출하지 않도록 서비스에서 처리.
     */
    @Query("""
            select c
            from Comment c
            join c.post p
            join fetch c.author
            where p.id = :postId
                and p.deletedAt is null
                and p.hiddenAt is null
                and c.deletedAt is null
                and c.hiddenAt is null
                and c.id in :commentIds
            """)
    List<Comment> findVisibleCommentsByIds(
            @Param("postId") Long postId,
            @Param("commentIds") List<Long> commentIds
    );

    @Query("""
        select c
        from Comment c
        join c.post p
        join fetch c.author
        where p.id = :postId
            and p.deletedAt is null
            and p.hiddenAt is null
            and c.id in :commentIds
            and c.parent is null
            and c.hiddenAt is null
            and (
                c.deletedAt is null
                or exists (
                    select 1
                    from Comment reply
                    where reply.parent = c
                        and reply.deletedAt is null
                        and reply.hiddenAt is null
                )
            )
        """)
    List<Comment> findPopularListCommentsByIds(
            @Param("postId") Long postId,
            @Param("commentIds") List<Long> commentIds
    );

    /**
     * 관리자 상태 변경을 위해 게시글에 속한 댓글을 잠금 조회한다.
     * <p>
     * 댓글과 부모 게시글의 숨김·삭제 여부는 서비스에서 판단.
     * 호출 전에 부모 게시글 잠금을 먼저 획득해야 한다.
     * <p>
     * 조회 조건에 postId도 넣어서 다른 게시글에 속한 댓글을
     * 잘못 처리하지 않도록 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from Comment c
            where c.id = :commentId
                and c.post.id = :postId
            """)
    Optional<Comment> findCommentByIdForModerationForUpdate(
            @Param("postId") Long postId,
            @Param("commentId") Long commentId
    );

    /**
     * 삭제되지 않은 게시글에 속한 삭제되지 않은 댓글을 잠금 조회한다.
     * <p>
     * 부모 게시글과 댓글의 숨김 상태는 제외하지 않는다.
     * 호출 전에 부모 게시글을 잠그고,
     * 조회 후에는 댓글 작성자 권한을 검증해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from Comment c
            join c.post p
            where c.id = :commentId
                and p.id = :postId
                and p.deletedAt is null
                and c.deletedAt is null
            """)
    Optional<Comment> findNotDeletedCommentByIdForUpdate(
            @Param("postId") Long postId,
            @Param("commentId") Long commentId
    );

    /**
     * 마이페이지용 내 댓글 개수
     * <p>
     * 댓글과 부모 게시글이 모두 삭제되지 않은 경우만 포함한다.
     * 댓글 자체의 숨김과 부모 게시글의 숨김은 제외 조건이 아니다.
     */
    @Query("""
            select count(c)
            from Comment c
            join c.post p
            where c.author.id = :memberId
                and c.deletedAt is null
                and p.deletedAt is null
            """)
    long countNotDeletedCommentsByAuthorId(
            @Param("memberId") Long memberId
    );

    /**
     * 마이페이지용 내 댓글 첫 조회
     * 부모 게시글의 상태와 응답용 정보를 함께 가져온다.
     */
    @Query("""
            select c
            from Comment c
            join fetch c.post p
            where c.author.id = :memberId
                and c.deletedAt is null
                and p.deletedAt is null
            order by c.createdAt desc, c.id desc
            """)
    List<Comment> findFirstNotDeletedCommentsByAuthorId(
            @Param("memberId") Long memberId,
            Pageable pageable
    );

    /**
     * 마이페이지용 내 댓글 후속 조회
     * 숨김 항목도 커서 기준에 포함한다.
     */
    @Query("""
            select c
            from Comment c
            join fetch c.post p
            where c.author.id = :memberId
                and c.deletedAt is null
                and p.deletedAt is null
                and (
                    c.createdAt < :afterCreatedAt
                    or (
                        c.createdAt = :afterCreatedAt
                        and c.id < :afterId
                    )
                )
            order by c.createdAt desc, c.id desc
            """)
    List<Comment> findNotDeletedCommentsByAuthorIdAfter(
            @Param("memberId") Long memberId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    /**
     * 관리자 조치를 위해 댓글의 부모 게시글 ID만 조회한다.
     * <p>
     * 숨김·삭제 상태로 제외하지 않는다.
     * 상태 검증은 부모 게시글과 댓글을 순서대로 잠근 뒤 수행한다.
     * <p>
     * 여기서는 @Lock으로 댓글부터 잠그는 일 없도록 한다.
     * 댓글부터 잠그면 기존의 부모 게시글 -> 댓글 잠금 순서와 달라지기 때문
     * <p>
     * 부모 ID를 얻은 뒤에는 findCommentByIdForModerationForUpdate(postId, commentId)로
     * 소속 관계까지 다시 확인한다.
     */
    @Query("""
            select c.post.id
            from Comment c
            where c.id = :commentId
            """)
    Optional<Long> findPostIdByCommentIdForModeration(
            @Param("commentId") Long commentId
    );

    /**
     * 첫 페이지 대댓글 조회 메서드 추가
     */
    @Query("""
            select c
            from Comment c
            join fetch c.author
            join c.post p
            join c.parent root
            where p.id = :postId
                and root.id = :parentId
                and root.post.id = :postId
                and root.parent is null
                and p.deletedAt is null
                and p.hiddenAt is null
                and root.hiddenAt is null
                and c.deletedAt is null
                and c.hiddenAt is null
            order by c.createdAt asc, c.id asc
            """)
    List<Comment> findFirstVisibleReplies(
            @Param("postId") Long postId,
            @Param("parentId") Long parentId,
            Pageable pageable
    );

    @Query("""
        select c
        from Comment c
        join fetch c.author
        join c.post p
        join c.parent root
        where p.id = :postId
          and root.id = :parentId
          and root.post.id = :postId
          and root.parent is null
          and p.deletedAt is null
          and p.hiddenAt is null
          and root.hiddenAt is null
          and c.deletedAt is null
          and c.hiddenAt is null
          and (
                c.createdAt > :afterCreatedAt
                or (c.createdAt = :afterCreatedAt and c.id > :afterId)
          )
        order by c.createdAt asc, c.id asc
        """)
    List<Comment> findVisibleRepliesAfter(
            @Param("postId") Long postId,
            @Param("parentId") Long parentId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") Long afterId,
            Pageable pageable
    );
}
