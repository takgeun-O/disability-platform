package io.github.takgeun.iyum.community.comment.infrastructure;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class CommentRepositoryTest {

    private static final Instant CREATED_TIME =
            Instant.parse("2026-10-17T03:00:00Z");

    private static final Instant UPDATED_TIME =
            Instant.parse("2026-10-17T04:00:00Z");

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    EntityManager entityManager;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    @BeforeEach
    void setUpTime() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_TIME));
    }

    @Test
    void 댓글을_저장하고_연관관계와_생성시각을_조회한다() {
        String content = "  첫 번째 줄\n\n두 번째 줄  ";
        Comment saved = saveComment(content);

        Long commentId = saved.getId();
        Long postId = saved.getPost().getId();
        Long authorId = saved.getAuthor().getId();

        // flush() 다음 clear() 호출
        entityManager.clear();

        Comment found = commentRepository
                .findById(commentId)
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(commentId);
        assertThat(found.getPost().getId()).isEqualTo(postId);
        assertThat(found.getPost().getTitle())
                .isEqualTo("보청기 질문");
        assertThat(found.getAuthor().getId()).isEqualTo(authorId);
        assertThat(found.getAuthor().getNickname())
                .isEqualTo("댓글회원");
        assertThat(found.getContent()).isEqualTo(content);
        assertThat(found.getCreatedAt()).isEqualTo(CREATED_TIME);
        assertThat(found.getUpdatedAt()).isEqualTo(CREATED_TIME);
        assertThat(found.getDeletedAt()).isNull();
    }

    @Test
    void 이모지_이천_개를_저장하고_다시_조회한다() {
        String content = "😀".repeat(2_000);
        Comment saved = saveComment(content);
        Long commentId = saved.getId();

        entityManager.clear();

        Comment found = commentRepository
                .findById(commentId)
                .orElseThrow();

        assertThat(found.getContent()).isEqualTo(content);
    }

    @Test
    void 변경_감지로_내용과_수정시각을_갱신한다() {
        Comment saved = saveComment("기존 내용");
        Long commentId = saved.getId();
        Long postId = saved.getPost().getId();
        Long authorId = saved.getAuthor().getId();

        entityManager.clear();

        Comment comment = commentRepository
                .findById(commentId)
                .orElseThrow();

        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(UPDATED_TIME));

        String updatedContent = "  수정한 내용\n다음 줄  ";
        comment.update(updatedContent);

        // 별도의 save() 없이 변경 감지로 UPDATE 실행
        entityManager.flush();
        entityManager.clear();

        Comment found = commentRepository
                .findById(commentId)
                .orElseThrow();

        assertThat(found.getContent()).isEqualTo(updatedContent);
        assertThat(found.getCreatedAt()).isEqualTo(CREATED_TIME);
        assertThat(found.getUpdatedAt()).isEqualTo(UPDATED_TIME);
        assertThat(found.getPost().getId()).isEqualTo(postId);
        assertThat(found.getAuthor().getId()).isEqualTo(authorId);
        assertThat(found.getDeletedAt()).isNull();
    }

    @Test
    void 논리_삭제하면_행은_남고_삭제시각이_저장된다() {
        Comment saved = saveComment("기존 내용");
        Long commentId = saved.getId();

        entityManager.clear();

        Comment comment = commentRepository
                .findById(commentId)
                .orElseThrow();

        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(UPDATED_TIME));

        comment.delete(UPDATED_TIME);

        entityManager.flush();
        entityManager.clear();

        // 기본 findById는 논리 삭제된 행도 조회한다.
        Comment found = commentRepository
                .findById(commentId)
                .orElseThrow();

        assertThat(found.getDeletedAt()).isEqualTo(UPDATED_TIME);
        assertThat(found.getContent()).isEqualTo("기존 내용");
        assertThat(found.getCreatedAt()).isEqualTo(CREATED_TIME);
        assertThat(found.getUpdatedAt()).isEqualTo(UPDATED_TIME);
    }

    @Test
    void 첫_조회는_작성시각과_ID_오름차순으로_개수를_제한한다() {
        Member author = saveActiveMember(
                "cursor-first@example.com",
                "첫조회회원"
        );
        Post post = savePost(author, "조회할 게시글");

        saveCommentAt(
                post, author, "늦은 댓글",
                CREATED_TIME.plusSeconds(60)
        );

        Comment first = saveCommentAt(
                post, author, "먼저 나온 댓글", CREATED_TIME
        );
        Comment second = saveCommentAt(
                post, author, "같은 시각 다음 댓글", CREATED_TIME
        );

        Long postId = post.getId();
        Long firstId = first.getId();
        Long secondId = second.getId();

        entityManager.clear();

        List<Comment> result =
                commentRepository.findFirstVisibleComments(
                        postId,
                        PageRequest.of(0, 2)
                );

        assertThat(result)
                .extracting(Comment::getId)
                .containsExactly(firstId, secondId);

        assertThat(
                entityManager.getEntityManagerFactory()
                        .getPersistenceUnitUtil()
                        .isLoaded(result.getFirst(), "author")
        ).isTrue();

        assertThat(result.getFirst().getAuthor().getNickname())
                .isEqualTo("첫조회회원");
    }

    @Test
    void 커서_이후의_댓글을_조회하고_삭제된_댓글과_다른_게시글은_제외한다() {
        Member author = saveActiveMember(
                "cursor-next@example.com",
                "다음조회회원"
        );
        Post post = savePost(author, "대상 게시글");
        Post otherPost = savePost(author, "다른 게시글");

        // 더 늦은 시각의 댓글이 더 작은 ID를 가진 경우도 검증한다.
        Comment later = saveCommentAt(
                post, author, "늦은 시각 댓글",
                CREATED_TIME.plusSeconds(60)
        );

        Comment cursor = saveCommentAt(
                post, author, "커서 댓글", CREATED_TIME
        );

        Comment sameTimeNext = saveCommentAt(
                post, author, "같은 시각 다음 댓글", CREATED_TIME
        );

        Comment deleted = saveCommentAt(
                post, author, "삭제할 댓글", CREATED_TIME
        );

        saveCommentAt(
                otherPost, author, "다른 글의 댓글", CREATED_TIME
        );

        deleted.delete(UPDATED_TIME);
        entityManager.flush();

        Long postId = post.getId();
        Instant cursorTime = cursor.getCreatedAt();
        Long cursorId = cursor.getId();
        Long nextId = sameTimeNext.getId();
        Long laterId = later.getId();

        entityManager.clear();

        List<Comment> result =
                commentRepository.findVisibleCommentsAfter(
                        postId,
                        cursorTime,
                        cursorId,
                        PageRequest.of(0, 20)
                );

        assertThat(result)
                .extracting(Comment::getId)
                .containsExactly(nextId, laterId);
    }

    @Test
    void 커서에_해당하는_댓글이_삭제돼도_다음_댓글을_조회한다() {
        Member author = saveActiveMember(
                "cursor-deleted@example.com",
                "커서삭제회원"
        );
        Post post = savePost(author, "대상 게시글");

        Comment cursor = saveCommentAt(
                post, author, "마지막으로 읽은 댓글", CREATED_TIME
        );
        Comment next = saveCommentAt(
                post, author, "다음 댓글", CREATED_TIME
        );

        Long postId = post.getId();
        Instant cursorTime = cursor.getCreatedAt();
        Long cursorId = cursor.getId();
        Long nextId = next.getId();

        cursor.delete(UPDATED_TIME);
        entityManager.flush();
        entityManager.clear();

        List<Comment> result =
                commentRepository.findVisibleCommentsAfter(
                        postId,
                        cursorTime,
                        cursorId,
                        PageRequest.of(0, 20)
                );

        assertThat(result)
                .extracting(Comment::getId)
                .containsExactly(nextId);
    }

    @Test
    void 삭제된_게시글의_댓글은_첫_조회와_후속_조회에서_제외한다() {
        Member author = saveActiveMember(
                "cursor-post@example.com",
                "게시글삭제회원"
        );
        Post post = savePost(author, "삭제할 게시글");

        Comment cursor = saveCommentAt(
                post, author, "커서 댓글", CREATED_TIME
        );
        saveCommentAt(
                post, author, "다음 댓글", CREATED_TIME
        );

        Long postId = post.getId();
        Instant cursorTime = cursor.getCreatedAt();
        Long cursorId = cursor.getId();

        post.delete(UPDATED_TIME);
        entityManager.flush();
        entityManager.clear();

        assertThat(
                commentRepository.findFirstVisibleComments(
                        postId,
                        PageRequest.of(0, 20)
                )
        ).isEmpty();

        assertThat(
                commentRepository.findVisibleCommentsAfter(
                        postId,
                        cursorTime,
                        cursorId,
                        PageRequest.of(0, 20)
                )
        ).isEmpty();

        // 댓글 자체는 논리 삭제되지 않았다.
        assertThat(
                commentRepository.findById(cursorId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isNull();
    }

    @Test
    void 댓글이_없거나_마지막_댓글_이후이면_빈_목록을_반환한다() {
        Member author = saveActiveMember(
                "cursor-empty@example.com",
                "빈조회회원"
        );
        Post post = savePost(author, "댓글 없는 게시글");
        Long postId = post.getId();

        entityManager.clear();

        assertThat(
                commentRepository.findFirstVisibleComments(
                        postId,
                        PageRequest.of(0, 20)
                )
        ).isEmpty();

        Comment last = saveCommentAt(
                post, author, "마지막 댓글", CREATED_TIME
        );

        Instant cursorTime = last.getCreatedAt();
        Long cursorId = last.getId();

        entityManager.clear();

        assertThat(
                commentRepository.findVisibleCommentsAfter(
                        postId,
                        cursorTime,
                        cursorId,
                        PageRequest.of(0, 20)
                )
        ).isEmpty();
    }

    @Test
    void 수정할_댓글을_게시글_ID와_댓글_ID로_잠금_조회한다() {
        Comment saved = saveComment("수정할 댓글");

        Long commentId = saved.getId();
        Long postId = saved.getPost().getId();
        Long authorId = saved.getAuthor().getId();

        entityManager.clear();

        // 여기서 JPA는 DB에 잠금 조회를 요청하고, 조회된 found는 영속성 컨텍스트에서 관리된다.
        Comment found = commentRepository
                .findVisibleCommentByIdForUpdate(postId, commentId)
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(commentId);
        assertThat(found.getPost().getId()).isEqualTo(postId);
        assertThat(found.getAuthor().getId()).isEqualTo(authorId);
        assertThat(found.getContent()).isEqualTo("수정할 댓글");

        // 관리 중인 엔티티에 적용된 잠금 모드를 확인한다.
        assertThat(entityManager.getLockMode(found))
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void 다른_게시글의_댓글은_수정용_조회에서_제외한다() {
        Comment saved = saveComment("원래 게시글의 댓글");

        Post otherPost = savePost(
                saved.getPost().getAuthor(),
                "다른 게시글"
        );

        Long commentId = saved.getId();
        Long otherPostId = otherPost.getId();

        entityManager.clear();

        assertThat(
                commentRepository.findVisibleCommentByIdForUpdate(
                        otherPostId,
                        commentId
                )
        ).isEmpty();
    }

    @Test
    void 삭제된_댓글은_수정용_조회에서_제외한다() {
        Comment saved = saveComment("삭제할 댓글");

        Long commentId = saved.getId();
        Long postId = saved.getPost().getId();

        saved.delete(UPDATED_TIME);
        entityManager.flush();
        entityManager.clear();

        assertThat(
                commentRepository.findVisibleCommentByIdForUpdate(
                        postId,
                        commentId
                )
        ).isEmpty();

        // 물리 삭제된 것이 아니라 조회 대상에서 제외된 것이다.
        assertThat(
                commentRepository.findById(commentId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isEqualTo(UPDATED_TIME);
    }

    @Test
    void 게시글이_삭제되면_댓글도_수정용_조회에서_제외한다() {
        Comment saved = saveComment("남아 있는 댓글");

        Long commentId = saved.getId();
        Long postId = saved.getPost().getId();

        saved.getPost().delete(UPDATED_TIME);
        entityManager.flush();
        entityManager.clear();

        assertThat(
                commentRepository.findVisibleCommentByIdForUpdate(
                        postId,
                        commentId
                )
        ).isEmpty();

        // 댓글 자체는 삭제되지 않았어도 부모 게시글이 삭제되면 제외한다.
        assertThat(
                commentRepository.findById(commentId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isNull();
    }

    @Test
    void 게시글이나_댓글이_없으면_수정용_조회는_빈_결과를_반환한다() {
        Comment saved = saveComment("기존 댓글");

        Long commentId = saved.getId();
        Long postId = saved.getPost().getId();

        entityManager.clear();

        assertThat(
                commentRepository.findVisibleCommentByIdForUpdate(
                        postId,
                        Long.MAX_VALUE
                )
        ).isEmpty();

        assertThat(
                commentRepository.findVisibleCommentByIdForUpdate(
                        Long.MAX_VALUE,
                        commentId
                )
        ).isEmpty();
    }

    @Test
    void 요청한_게시글들의_댓글_개수를_게시글별로_집계한다() {
        Member author = saveActiveMember(
                "count-group@example.com",
                "집계회원"
        );

        Post firstPost = savePost(author, "첫 번째 게시글");
        Post secondPost = savePost(author, "두 번째 게시글");
        Post otherPost = savePost(author, "요청하지 않은 게시글");

        saveCommentAt(
                firstPost, author, "첫 번째 글의 댓글 1", CREATED_TIME
        );
        saveCommentAt(
                firstPost, author, "첫 번째 글의 댓글 2", CREATED_TIME
        );
        saveCommentAt(
                secondPost, author, "두 번째 글의 댓글", CREATED_TIME
        );
        saveCommentAt(
                otherPost, author, "집계 대상이 아닌 댓글", CREATED_TIME
        );

        Long firstPostId = firstPost.getId();
        Long secondPostId = secondPost.getId();

        entityManager.flush();
        entityManager.clear();

        List<PostCommentCount> result = commentRepository.countVisibleCommentsByPostIds(
                List.of(firstPostId, secondPostId)
        );

        assertThat(result)
                .extracting(
                        PostCommentCount::getPostId,
                        PostCommentCount::getCommentCount
                )
                .containsExactlyInAnyOrder(
                        // tuple(): 게시글 ID와 댓글 수를 한 쌍으로 비교하기 위해 사용
                        tuple(firstPostId, 2L),
                        tuple(secondPostId, 1L)
                );
    }

    @Test
    void 삭제된_댓글과_삭제된_게시글의_댓글은_개수에서_제외한다() {
        Member author = saveActiveMember(
                "count-deleted@example.com",
                "삭제집계회원"
        );

        Post visiblePost = savePost(author, "공개 게시글");
        Post deletedPost = savePost(author, "삭제할 게시글");

        saveCommentAt(
                visiblePost, author, "남아 있는 댓글", CREATED_TIME
        );

        Comment deletedComment = saveCommentAt(
                visiblePost, author, "삭제할 댓글", CREATED_TIME
        );

        Comment commentOnDeletedPost = saveCommentAt(
                deletedPost, author, "삭제된 글에 남을 댓글", CREATED_TIME
        );

        Long visiblePostId = visiblePost.getId();
        Long deletedPostId = deletedPost.getId();
        Long deletedCommentId = deletedComment.getId();
        Long commentOnDeletedPostId = commentOnDeletedPost.getId();

        // 댓글, 게시글 소프트 삭제
        deletedComment.delete(UPDATED_TIME);
        deletedPost.delete(UPDATED_TIME);

        entityManager.flush();
        entityManager.clear();

        List<PostCommentCount> result = commentRepository.countVisibleCommentsByPostIds(
                List.of(visiblePostId, deletedPostId)
        );

        assertThat(result)
                .extracting(
                        PostCommentCount::getPostId,
                        PostCommentCount::getCommentCount
                )
                .containsExactly(
                        tuple(visiblePostId, 1L)
                );

        // 삭제된 댓글은 DB에 남아있지만 집계에서는 제외
        assertThat(
                commentRepository.findById(deletedCommentId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isEqualTo(UPDATED_TIME);

        // 댓글 자체가 삭제되지 않았더라도 부모 게시글이 삭제되면 제외된다.
        assertThat(
                commentRepository.findById(commentOnDeletedPostId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isNull();
    }

    @Test
    void 댓글이_없거나_모두_삭제된_게시글은_집계_결과에_포함되지_않는다() {
        Member author = saveActiveMember(
                "count-empty@example.com",
                "빈집계회원"
        );

        Post emptyPost = savePost(author, "댓글 없는 게시글");
        Post allDeletedPost = savePost(author, "댓글이 모두 삭제된 게시글");

        Comment deletedComment = saveCommentAt(
                allDeletedPost, author, "삭제할 댓글", CREATED_TIME
        );

        Long emptyPostId = emptyPost.getId();
        Long allDeletedPostId = allDeletedPost.getId();

        deletedComment.delete(UPDATED_TIME);

        entityManager.flush();
        entityManager.clear();

        List<PostCommentCount> result =
                commentRepository.countVisibleCommentsByPostIds(
                        List.of(emptyPostId, allDeletedPostId)
                );

        assertThat(result).isEmpty();
    }

    private Comment saveComment(String content) {
        Member postAuthor = saveActiveMember(
                "post-writer@example.com",
                "게시글회원"
        );

        // saveAndFlush() : 엔티티를 저장하고 SQL을 DB에 반영
        Post post = postRepository.saveAndFlush(
                Post.create(
                        postAuthor,
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        "보청기 질문",
                        "게시글 본문"
                )
        );

        Member commentAuthor = saveActiveMember(
                "comment-writer@example.com",
                "댓글회원"
        );

        return commentRepository.saveAndFlush(
                Comment.create(post, commentAuthor, content)
        );
    }

    private Member saveActiveMember(
            String email,
            String nickname
    ) {
        Member member = Member.createPending(
                email,
                "test-only-password-hash",
                nickname
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private Post savePost(Member author, String title) {
        return postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        title,
                        "게시글 본문"
                )
        );
    }

    private Comment saveCommentAt(
            Post post,
            Member author,
            String content,
            Instant createdAt
    ) {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(createdAt));

        return commentRepository.saveAndFlush(
                Comment.create(post, author, content)
        );
    }
}