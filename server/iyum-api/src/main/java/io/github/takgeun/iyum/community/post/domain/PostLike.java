package io.github.takgeun.iyum.community.post.domain;

import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(
        name = "community_post_likes",
        uniqueConstraints = {
                // 엔티티에 DB 구조의 의도 표현
                @UniqueConstraint(
                        name = "uk_community_likes_post_member",
                        columnNames = {"post_id", "member_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // PostLike -> Post 방향으로만 단방향 연결
    // 좋아요를 통해서 게시글과 회원을 찾을 수 있음. (Post 엔티티에 좋아요 컬렉션 추가할 필요 없음)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "post_id",
            nullable = false,
            updatable = false
    )
    private Post post;

    // post와 동일
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            updatable = false
    )
    private Member member;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private PostLike(Post post, Member member) {
        if(post == null) {
            throw new IllegalArgumentException(
                    "게시글은 필수입니다."
            );
        }

        if(member == null) {
            throw new IllegalArgumentException(
                    "회원은 필수입니다."
            );
        }

        this.post = post;
        this.member = member;
    }

    public static PostLike create(Post post, Member member) {
        return new PostLike(post, member);
    }
}
