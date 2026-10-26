package io.github.takgeun.iyum.community.comment.domain;

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
        name = "community_comment_likes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_community_comment_likes_comment_member",
                        columnNames = {"comment_id", "member_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CommentLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 좋아요 대상 댓글
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "comment_id",
            nullable = false,
            updatable = false
    )
    private Comment comment;

    // 좋아요를 누른 회원
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

    private CommentLike(Comment comment, Member member) {
        if(comment == null) {
            throw new IllegalArgumentException("댓글은 필수입니다.");
        }

        if(member == null) {
            throw new IllegalArgumentException("회원은 필수입니다.");
        }

        this.comment = comment;
        this.member = member;
    }

    public static CommentLike create(
            Comment comment,
            Member member
    ) {
        return new CommentLike(comment, member);
    }
}
