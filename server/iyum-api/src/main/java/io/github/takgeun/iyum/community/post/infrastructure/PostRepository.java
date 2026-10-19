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
import java.util.List;
import java.util.Optional;

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
    @Query(
            value = """
                    select p
                    from Post p
                    join fetch p.author
                    where p.deletedAt is null
                      and (:type is null or p.type = :type)
                      and (:topic is null or p.topic = :topic)
                    order by p.createdAt desc, p.id desc
                    """,
            // Page에는 게시글 목록 뿐만 아니라 페이지 정보도 들어있음.
            // 전체 개수를 알아야 하므로 필요한 경우 countQuery를 실행한다.
            // 다만 Spring Data는 결과만으로 전체 개수를 알 수 있으면 개수 조회를 생략하기도 함.
            // 그리고 중요한 것은 목록 쿼리와 개수 쿼리에 동일한 필터를 적용한 것
            // 그래야 목록에서 삭제된 글을 제외했는데 개수에는 포함되면 화면에 잘못된 전체 개수와 페이지 수가 나오는 것을 방지할 수 있음.
            // 그리고 개수만 세는 쿼리에는 작성자 fetch join과 정렬이 필요하지 않음. (당연히...)
            countQuery = """
                    select count(p)
                    from Post p
                    where p.deletedAt is null
                      and (:type is null or p.type = :type)
                      and (:topic is null or p.topic = :topic)
                    """
    )
    Page<Post> findVisiblePosts(
            @Param("type") @Nullable PostType type,
            @Param("topic") @Nullable PostTopic topic,
            Pageable pageable
    );

    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.id = :postId
              and p.deletedAt is null
            """)
    Optional<Post> findVisiblePostById(
            @Param("postId") Long postId
    );

    // 조회한 게시글 행을 현재 트랜잭션에서 수행하는 동안
    // 다른 트랜잭션의 충돌하는 변경이 기다리도록 비관적 쓰기 잠금 사용
    // 잠금 순서는 회원 -> 게시글 (삭제도 이 순서로 통일)
    // 참고로 이 잠금은 DB에서 실행되는 변경을 조정하는 장치임.
    // 그러니까 두 편집 화면에서 순서대로 저장했을 때 충돌을 알리는 기능까지 제공하는 건 아님
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Post p
            where p.id = :postId
                and p.deletedAt is null
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
            """)
    long countVisiblePostsByAuthorId(
            @Param("memberId") Long memberId
    );
}