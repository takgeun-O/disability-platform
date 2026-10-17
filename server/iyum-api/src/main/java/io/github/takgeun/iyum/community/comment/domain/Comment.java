package io.github.takgeun.iyum.community.comment.domain;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "community_comments")
@Getter
// JPA는 DB에서 데이터를 읽어 엔티티 객체를 만들 때 사용할 public 또는 protected 기본 생성자가 필요함
// 동시에 개발자가 다른 패키지에서 new 하는 걸 막고 싶음 --> 접근레벨 PROTECTED 설정
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 엔티티가 저장되거나 수정될 때 생성 시각·수정 시각 등을 자동으로 기록해주는 리스너를 연결하는 기능
// 이게 있어야 @CreatedDate, @LastModifiedDate 사용 가능
@EntityListeners(AuditingEntityListener.class)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 댓글에서 게시글을 참조하는 단방향 관계 -> Post에 댓글 목록 필드를 추가할 필요 없음
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "post_id",
            nullable = false,
            updatable = false   // 해당 외래키를 JPA의 수정 SQL에서 제외
    )
    private Post post;

    // 댓글에서 댓글의 작성자를 참조하는 단방향 관계 -> Member에 댓글 목록 필드를 추가할 필요 없음
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "author_id",
            nullable = false,
            updatable = false   // 해당 외래키를 JPA의 수정 SQL에서 제외
    )
    private Member author;

    @Column(
            name = "content",
            nullable = false,
            columnDefinition = "text"
    )
    private String content;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Comment(Post post, Member author, String content) {

        if (post == null) {
            throw new IllegalArgumentException(
                    "게시글은 필수입니다."
            );
        }

        if (author == null) {
            throw new IllegalArgumentException(
                    "작성자는 필수입니다."
            );
        }

        this.post = post;
        this.author = author;
        this.content = CommentInputPolicy.validateContent(content);
    }

    // 생성 시각은 기존 JPA Auditing이 처리
    public static Comment create(
            Post post,
            Member author,
            String content
    ) {
        return new Comment(post, author, content);
    }

    // 수정 시각은 기존 JPA Auditing이 처리
    public void update(String content) {
        // 우변의 검증이 성공해야 대입되므로, 검증 실패 시 기존 내용 유지
        this.content = CommentInputPolicy.validateContent(content);
    }

    // 삭제 시각은 서비스에서 전달할 파라미터
    public void delete(Instant deletedAt) {
        if (deletedAt == null) {
            throw new IllegalArgumentException(
                    "삭제 시각은 필수입니다."
            );
        }

        if (this.deletedAt != null) {
            return;
        }

        this.deletedAt = deletedAt;
    }
}
