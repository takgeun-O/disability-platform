package io.github.takgeun.iyum.community.post.infrastructure;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import jakarta.persistence.LockModeType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 여기서 Post는 테이블 이름이 아니라 엔티티 클래스 이름
    //
    // where p.deletedAt is null
    //  삭제 시 행을 지우는 대신 deletedAt에 삭제 시각을 넣는 방식으로 설계했음.
    //  따라서 공개 조회에서는 이 조건을 항상 적용해야 한다.
    //
    // and (:type is null or p.type = :type) : 전달한 type에 따라 의미가 달라진다.
    //  type == null : 유형으로 제한하지 않음
    //  type == QUESTION : 질문 글만 조회
    //
    // join fetch p.author : 게시글 가져올 때 작성자 엔티티도 함께 가져오기 (목록을 받은 뒤 작성자 닉네임을 읽을 때 추가 조회가 이어지는 상황 방지)
    default Page<Post> findVisiblePosts(
            @Param("type") @Nullable PostType type,
            @Param("topic") @Nullable PostTopic topic,
            Pageable pageable
    ) {
        return findVisiblePosts(
                type,
                topic,
                null,
                pageable
        );
    }

    default Page<Post> findVisiblePosts(
            @Param("type") @Nullable PostType type,
            @Param("topic") @Nullable PostTopic topic,
            @Param("keyword") @Nullable String keyword,
            Pageable pageable
    ) {
        return findVisiblePostSearchRows(
                type,
                topic,
                keyword,
                pageable
        ).map(PostSearchRow::getPost);
    }

    // bodyMatchPosition: 본문에서 검색어가 처음 등장하는 위치
    // 검색어가 없으면 bodyMatchPosition = 0
    // 검색어가 있으면 bodyMatchPosition = 본문에서 검색어의 첫 위치
    @Query(
            value = """
                select
                    p as post,
                    case
                        when coalesce(:keyword, '') = '' then 0
                        else locate(lower(:keyword), lower(p.content))
                    end as bodyMatchPosition
                from Post p
                join fetch p.author
                where p.deletedAt is null
                    and p.hiddenAt is null
                    and (:type is null or p.type = :type)
                    and (:topic is null or p.topic = :topic)
                    and (
                        coalesce(:keyword, '') = ''
                        or locate(lower(:keyword), lower(p.title)) > 0
                        or locate(lower(:keyword), lower(p.content)) > 0
                    )
                order by p.createdAt desc, p.id desc
                """,
            countQuery = """
                select count(p)
                from Post p
                where p.deletedAt is null
                    and p.hiddenAt is null
                    and (:type is null or p.type = :type)
                    and (:topic is null or p.topic = :topic)
                    and (
                        coalesce(:keyword, '') = ''
                        or locate(lower(:keyword), lower(p.title)) > 0
                        or locate(lower(:keyword), lower(p.content)) > 0
                    )
                """
    )
    Page<PostSearchRow> findVisiblePostSearchRows(
            @Param("type") @Nullable PostType type,
            @Param("topic") @Nullable PostTopic topic,
            @Param("keyword") @Nullable String keyword,
            Pageable pageable
    );

    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.id = :postId
              and p.deletedAt is null
              and p.hiddenAt is null
            """)
    Optional<Post> findVisiblePostById(
            @Param("postId") Long postId
    );

    // 조회한 게시글 행을 현재 트랜잭션에서 수행하는 동안
    // 다른 트랜잭션의 충돌하는 변경이 기다리도록 비관적 쓰기 잠금 사용
    // 잠금 순서는 회원 -> 게시글 (삭제도 이 순서로 통일)
    // 참고로 이 잠금은 DB에서 실행되는 변경을 조정하는 장치임.
    // 그러니까 두 편집 화면에서 순서대로 저장했을 때 충돌을 알리는 기능까지 제공하는 건 아님

    /**
     * 일반 사용자의 수정·댓글 등록·좋아요 처리를 위해
     * 공개 상태인 게시글을 잠금 조회한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Post p
            where p.id = :postId
                and p.deletedAt is null
                and p.hiddenAt is null
            """)
    Optional<Post> findVisiblePostByIdForUpdate(
            @Param("postId") Long postId
    );

    // 첫 조회: 커서 없이 최신 게시글부터 가져온다.
    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.deletedAt is null
                and p.hiddenAt is null
                and (:type is null or p.type = :type)
                and (:topic is null or p.topic = :topic)
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findFirstVisiblePosts(
            @Param("type") @Nullable PostType type,
            @Param("topic") @Nullable PostTopic topic,
            // 게시글을 최대 몇 개 가져올지 전달하는 용도
            // 페이지 번호, 페이지 크기, 정렬 조건 정보가 들어있음.
            // 다만 커서 조회 방식에서는 조회 개수를 제한하는 용도로 사용할 것
            Pageable pageable
    );

    // 후속 조회: 직전에 반환한 마지막 게시글보다
    // 정렬 순서상 뒤에 있는 게시글을 가져온다.
    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.deletedAt is null
                and p.hiddenAt is null
                and (:type is null or p.type = :type)
                and (:topic is null or p.topic = :topic)
                and (
                    p.createdAt < :afterCreatedAt
                    or (
                        p.createdAt = :afterCreatedAt
                        and p.id < :afterId
                    )
                )
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findVisiblePostsAfter(
            @Param("type") @Nullable PostType type,
            @Param("topic") @Nullable PostTopic topic,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    // 내 게시글 수 조회
    // 전체 게시글을 가져와서 Java로 세는 대신 DB에서 개수만 계산해 반환
    @Query("""
            select count(p)
            from Post p
            where p.author.id = :memberId
                and p.deletedAt is null
                and p.hiddenAt is null
            """)
    long countVisiblePostsByAuthorId(
            @Param("memberId") Long memberId
    );

    // 내 게시글 첫 조회: 내가 작성한 공개 게시글을 최신순으로 조회한다.
    // p.author.id = :memberId
    // DB에서 조회할 때부터 내 게시글로 제한한다. (전체 게시글 피드 조회와의 차이점)
    // 전체 게시글을 가져와서 Java 코드로 내 게시글을 골라내는 방식으로 하면
    // 조회 개수와 커서 처리가 꼬일 수 있음.
    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.author.id = :memberId
                and p.deletedAt is null
                and p.hiddenAt is null
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findFirstVisiblePostsByAuthorId(
            @Param("memberId") Long memberId,
            Pageable pageable
    );

    // 내 게시글 후속 조회: 커서보다 정렬 순서상 뒤에 있는 게시글을 조회한다.
    // 최신순이므로 다음 목록은 마지막으로 받은 게시글보다 더 오래된 게시글부터 가져와야 한다.
    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.author.id = :memberId
                and p.deletedAt is null
                and p.hiddenAt is null
                and (
                    p.createdAt < :afterCreatedAt
                    or (
                        p.createdAt = :afterCreatedAt
                        and p.id < :afterId
                    )
                )
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findVisiblePostsByAuthorIdAfter(
            @Param("memberId") Long memberId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") Long afterId,
            // Pageable: 조회 개수를 제한하는 용도로 사용할 것
            // 위 쿼리상 조건에 만족하면 내림차순으로 모두 가져오는데
            // Pageable이 그 개수를 제한할 것
            Pageable pageable
    );

    /**
     * 관리자 상태 변경을 위해 게시글을 잠금 조회한다.
     * <p>
     * 숨김·삭제 여부로 제외하지 않는다.
     * 삭제된 게시글의 처리 허용 여부는 서비스에서 판단
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Post p
            where p.id = :postId
            """)
    Optional<Post> findPostByIdForModerationForUpdate(
            @Param("postId") Long postId
    );

    /**
     * 삭제되지 않은 게시글을 잠금 조회한다. (숨김 여부와 관계없이 삭제되지 않은 게시글 조회)
     * <p>
     * 숨김 상태는 제외하지 않으므로 작성자 삭제에 사용할 수 있다.
     * 작성자 권한은 호출한 서비스에서 검증해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Post p
            where p.id = :postId
                and p.deletedAt is null
            """)
    Optional<Post> findNotDeletedPostByIdForUpdate(
            @Param("postId") Long postId
    );

    // 마이페이지용: 본인이 작성했고 삭제되지 않은 게시글 수.
    // 관리자 숨김 상태의 게시글도 포함한다.
    @Query("""
            select count(p)
            from Post p
            where p.author.id = :memberId
                and p.deletedAt is null
            """)
    long countNotDeletedPostsByAuthorId(
            @Param("memberId") Long memberId
    );

    // 마이페이지 첫 조회: 공개·숨김 항목을 함께 최신순으로 조회한다.
    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.author.id = :memberId
                and p.deletedAt is null
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findFirstNotDeletedPostsByAuthorId(
            @Param("memberId") Long memberId,
            Pageable pageable
    );

    // 마이페이지 후속 조회: 마지막 반환 항목보다 뒤에 있는 게시글
    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.author.id = :memberId
                and p.deletedAt is null
                and (
                    p.createdAt < :afterCreatedAt
                    or (
                        p.createdAt = :afterCreatedAt
                        and p.id < :afterId
                    )
                )
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findNotDeletedPostsByAuthorIdAfter(
            @Param("memberId") Long memberId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    /**
     * 전달받은 ID에 해당하는 공개 게시글과 작성자 조회
     *
     * 주의할 점은 where p.id in :postIds에서
     * IN은 조회 대상을 지정할 뿐, 입력 목록의 순서를 보장하지 않는다.
     */
    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.id in :postIds
                and p.deletedAt is null
                and p.hiddenAt is null
            """)
    List<Post> findVisiblePostsWithAuthorByIds(
            @Param("postIds") List<Long> postIds
    );

    /**
     * 순위대로 정렬된 게시글 ID 목록을 받아
     * 그 순서 그대로 실제 게시글 객체 목록을 만들어 반환하는 메서드
     * 그 중 숨김·삭제된 게시글은 제외한다.
     *
     * 참고로 default 메서드는 인터페이스 안에서 구현까지 제공하는 메서드임.
     * 일반적으로 인터페이스에서는 선언만 하는데 default 를 사용하면
     * {...} 안에 실행할 코드를 직접 작성할 수 있음.
     */
    default List<Post> findVisiblePostsInRankOrder(
            // findWeeklyBestPostIds()가 반환한 순위순 게시글 ID 목록
            // order by count(pl) desc, p.createdAt desc, p.id desc
            List<Long> rankedIds
    ) {
        // 조회할 게시글이 없으므로 DB에 추가로 조회하지 않고
        // 빈 목록을 바로 반환하고 종료
        if (rankedIds.isEmpty()) {
            return List.of();
        }

        // 1. 순위를 계산해서 게시글 "ID만" 가져온다.
        // 실제 게시글을 조회하고 ID로 찾을 수 있게 저장
        // findVisiblePostsWithAuthorByIds 의 where절은
        // 입력 목록의 순서를 보장하지 않는다.
        // 따라서 순서에 관계 없이 꺼낼 수 있도록 Map에 저장한다.
        Map<Long, Post> postsById = new HashMap<>();
        for (Post post : findVisiblePostsWithAuthorByIds(rankedIds)) {
            postsById.put(post.getId(), post);
        }

        // 2. 해당 ID들의 게시글·작성자를 가져오고 원래 순서대로 정렬한다.
        // 원래 순위 목록을 따라 게시글을 다시 배치한다.
        // 여기서는 DB가 반환한 순서가 아니라 입력 받은 rankedIds 순서대로 반복
        List<Post> rankedPosts = new ArrayList<>(postsById.size());
        // 입력 받은 rankedIds 순서대로 반복
        for (Long id : rankedIds) {
            Post post = postsById.get(id);

            // null 체크 이유
            // 순위 ID를 조회한 뒤, 실제 게시글을 가져오기 전에
            // 해당 게시글이 숨김 처리될 수 있으므로 null인 게시글은 건너뛴다.
            if (post != null) {
                rankedPosts.add(post);
            }
        }

        // 이로써 원래 순위가 복원된다.
        return rankedPosts;
    }
}