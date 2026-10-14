package io.github.takgeun.iyum.community.post.domain;

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
@Table(name = "community_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)  // 엔티티가 생성되거나 수정될 때, 생성 시각·수정 시각 등을 자동으로 기록하는 리스너 연결
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // optional = false : 연관관계가 필수이므로 연결된 엔티티가 반드시 있어야 한다.
    // LAZY : 작성자 정보가 필요할 때 조회하도록 지정
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "author_id",
            nullable = false,
            updatable = false
    )
    private Member author;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 20)
    private PostType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "topic", nullable = false, length = 32)
    private PostTopic topic;

    @Column(
            name = "title",
            nullable = false,
            length = PostInputPolicy.TITLE_MAX_LENGTH
    )
    private String title;

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

    private Post(
            Member author,
            PostType type,
            PostTopic topic,
            String title,
            String content
    ) {
        if (author == null) {
            throw new IllegalArgumentException(
                    "작성자는 필수입니다."
            );
        }

        if (type == null) {
            throw new IllegalArgumentException(
                    "글 유형은 필수입니다."
            );
        }

        if(topic == null) {
            throw new IllegalArgumentException(
                    "주제는 필수입니다."
            );
        }

        this.author = author;
        this.type = type;
        this.topic = topic;
        this.title = PostInputPolicy.normalizeTitle(title);
        this.content = PostInputPolicy.validateContent(content);
    }

    public static Post create(
            Member author,
            PostType type,
            PostTopic topic,
            String title,
            String content
    ) {
        return new Post(author, type, topic, title, content);
    }
}
