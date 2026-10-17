package io.github.takgeun.iyum.community.comment.infrastructure;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CommentRepository
        extends JpaRepository<Comment, Long> {

    // 기본 findById()는 논리 삭제된 댓글도 조회함.
    // 즉, 논리 삭제된 댓글은 조회하지 않도록 별도 구현 필요

    // delete()는 물리 삭제이므로 서비스에서 논리 삭제할 때는
    // comment.delete(시각)을 사용할 것

    // 첫 조회에는 커서가 없으니 findFirstVisibleComments()
    // 이후에는 findVisibleCommentsAfter() 사용
    @Query("""
            select c
            from Comment c
            join c.post p
            join fetch c.author
            where p.id = :postId
                and p.deletedAt is null
                and c.deletedAt is null
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
                and p.deletedAt is null
                and c.deletedAt is null
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
}
