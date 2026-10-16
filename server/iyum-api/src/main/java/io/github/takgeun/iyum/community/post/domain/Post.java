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

        if (topic == null) {
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

    // 객체가 아직 없는 상태에서 create()를 호출하므로 static 선언
    public static Post create(
            Member author,
            PostType type,
            PostTopic topic,
            String title,
            String content
    ) {
        return new Post(author, type, topic, title, content);
    }

    // update()는 수정할 객체가 이미 있는 상태에서 호출하므로 static 선언 X
    public void update(
            PostType type,
            PostTopic topic,
            String title,
            String content
    ) {
        if (type == null) {
            throw new IllegalArgumentException(
                    "글 유형은 필수입니다."
            );
        }

        if (topic == null) {
            throw new IllegalArgumentException(
                    "주제는 필수입니다."
            );
        }

        // 모든 검증을 먼저 마친다.
        String normalizedTitle =
                PostInputPolicy.normalizeTitle(title);

        String validatedContent =
                PostInputPolicy.validateContent(content);

        // 검증에 모두 성공했을 때만 기존 값 변경
        // this.title = PostInputPolicy.normalizeTitle(title);
        // this.content = PostInputPolicy.validateContent(content);
        // 위처럼 변경하면 제목은 정상이라 변경됐는데 content 검증에서 예외가 발생할 경우 메모리에 있는 객체는 제목만 바뀐 상태가 되어버린다.
        // 아래 방식은 객체 내부의 변경 순서를 안전하게 만드는 것일 뿐 DB 트랜잭션 처리는 서비스에서 별도로 구현해야 한다.

        // 또한 updatedAt은 여기서 직접 대입하지 않고, @LastModifiedDate와 JPA Auditing을 활용할 것.
        // JPA가 변경 사항을 DB에 반영하는 flush 과정에서 updatedAt이 갱신될 것. (전제 : JPA Auditing 활성화되어 있고 트랜잭션 안에서 JPA가 관리하는 엔티티를 수정하는 경우)
        this.type = type;
        this.topic = topic;
        this.title = normalizedTitle;
        this.content = validatedContent;
    }

    // 실제 요청을 처리할 때는 서버의 서비스가 삭제 시각을 전달할 것 (사용자가 요청 본문으로 보내는 값은 아님)
    public void delete(Instant deletedAt) {
        if (deletedAt == null) {
            throw new IllegalArgumentException(
                    "삭제 시각은 필수입니다."
            );
        }

        // 중복 삭제 요청 시 최초 삭제 시각 유지
        if (this.deletedAt != null) {
            return;
        }

        this.deletedAt = deletedAt;
    }
}
