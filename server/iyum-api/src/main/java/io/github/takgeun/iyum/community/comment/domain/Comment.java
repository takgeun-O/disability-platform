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

    // 댓글 엔티티의 마지막 변경 시각
    // JPA Auditing 관리 방식
    // updatedAt의 null값은 본문 수정 시각이 별도로 기록되지 않았다는 의미
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 마지막으로 보문이 실제 변경된 시각.
     *
     * 신규 댓글 및 과거 본문 수정 시각이 별도로 기록되지 않은 댓글은 null.
     * 숨김·복원·삭제 등 본문 외 변경으로는 갱신하지 않음.
     */
    @Column(name = "content_updated_at")
    private Instant contentUpdatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // 관리자가 숨긴 시각. null이면 개별 숨김 상태가 아님
    @Column(name = "hidden_at")
    private Instant hiddenAt;

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

    /**
     * 본문이 실제로 변경된 경우에만 본문 수정 시각을 기록한다.
     *
     * updatedAt은 JPA Auditing이 관리하는 엔티티 변경 시각
     * contentUpdatedAt은 본문 변경만을 나타내는 시각
     */
    public void update(String content, Instant changedAt) {
        // 우변의 검증이 성공해야 대입되므로, 검증 실패 시 기존 내용 유지
        // 검증에 실패하면 기존 본문과 본문 수정 시각을 보존
        String validatedContent =
                CommentInputPolicy.validateContent(content);

        if(changedAt == null) {
            throw new IllegalArgumentException(
                    "본문 수정 시각은 필수입니다."
            );
        }

        // 같은 본문으로 요청하면 수정 이력을 새로 만들지 않음.
        if(this.content.equals(validatedContent)) {
            return;
        }

        this.content = validatedContent;
        this.contentUpdatedAt = changedAt;
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

    /**
     * 관리자가 컨텐츠를 숨긴다.
     *
     * @return 실제로 숨김 상태로 변경되었으면 true
     */
    public boolean hide(Instant hiddenAt) {
        if(hiddenAt == null) {
            throw new IllegalArgumentException(
                    "숨김 시각은 필수입니다."
            );
        }

        validateNotDeletedForModeration();

        // 이미 숨겨져 있다면 최초 숨김 시각을 유지한다.
        if(this.hiddenAt != null) {
            return false;
        }

        this.hiddenAt = hiddenAt;
        return true;
    }

    /**
     * 관리자가 콘텐츠의 개별 숨김 상태를 해제한다.
     *
     * @return 실제로 숨김 상태가 해제되었다면 true
     */
    public boolean restore() {
        validateNotDeletedForModeration();

        if(this.hiddenAt == null) {
            return false;
        }

        this.hiddenAt = null;
        return true;
    }

    private void validateNotDeletedForModeration() {
        if(this.deletedAt != null) {
            throw new IllegalStateException(
                    "삭제된 콘텐츠는 숨김 또는 복원할 수 없습니다."
            );
        }
    }
}
