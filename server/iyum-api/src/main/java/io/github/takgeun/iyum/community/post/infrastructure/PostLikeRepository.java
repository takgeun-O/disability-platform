package io.github.takgeun.iyum.community.post.infrastructure;

import io.github.takgeun.iyum.community.post.domain.PostLike;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostLikeRepository
        extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPost_IdAndMember_Id(
            Long postId,
            Long memberId
    );

    // 해당 게시글의 좋아요 개수 조회
    long countByPost_Id(Long postId);

    //    | postId | likeCount | viewerLikeCount |
//            |---|---:|---:|
//            | 100 | 8 | 1 |
//            | 101 | 3 | 0 |

    // p.id in :postIds -> 현재 화면에 반환할 게시글 ID만 조회
    // 게시글별로 좋아요 행을 묶어서 개수 계산
//    게시글 100의 좋아요 회원: 7, 12, 20
//    현재 조회 회원: 7
//
//    전체 좋아요 수: 3
//    조회 회원의 좋아요 수: 1 + 0 + 0 = 1
    @Query("""
            select
                p.id as postId,
                count(pl) as likeCount,
                sum(
                    case
                        when pl.member.id = :viewerId then 1
                        else 0
                    end
                ) as viewerLikeCount
            from PostLike pl
            join pl.post p
            where p.deletedAt is null
                and p.hiddenAt is null
                and p.id in :postIds
            group by p.id
            """)
    List<PostLikeSummary> findSummariesByPostIds(
            @Param("postIds") List<Long> postIds,
            // viewerId가 null인 비로그인 조회에서는 일치 조건이 성립하지 않으므로 모두 0으로 계산
            @Param("viewerId") @Nullable Long viewerId
    );
}
