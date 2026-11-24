package io.github.takgeun.iyum.community.comment.infrastructure;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.domain.CommentLike;
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
    CommentLikeRepository commentLikeRepository;

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

        // 새 댓글에는 본문 수정 시각이 기록되지 않는다.
        assertThat(found.getContentUpdatedAt()).isNull();

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
        comment.update(updatedContent, UPDATED_TIME);

        // 별도의 save() 없이 변경 감지로 UPDATE 실행
        entityManager.flush();
        entityManager.clear();

        // DB에서 다시 읽음
        Comment found = commentRepository
                .findById(commentId)
                .orElseThrow();

        assertThat(found.getContent()).isEqualTo(updatedContent);
        assertThat(found.getCreatedAt()).isEqualTo(CREATED_TIME);
        assertThat(found.getUpdatedAt()).isEqualTo(UPDATED_TIME);
        assertThat(found.getPost().getId()).isEqualTo(postId);
        assertThat(found.getAuthor().getId()).isEqualTo(authorId);
        assertThat(found.getDeletedAt()).isNull();
        assertThat(found.getContentUpdatedAt())
                .isEqualTo(UPDATED_TIME);
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

    @Test
    void 최신순은_등록순의_역순이며_같은_시각에서도_페이지가_이어진다() {
        Member author = saveActiveMember(
                "latest-order@example.com",
                "최신순회원"
        );
        Post post = savePost(author, "최신순 검증 게시글");

        // 저장 순서와 작성 시각 순서를 일부러 다르게 만든다.
        Comment newest = saveCommentAt(
                post, author, "가장 최신 댓글",
                CREATED_TIME.plusSeconds(60)
        );
        Comment sameTimeFirst = saveCommentAt(
                post, author, "같은 시각 작은 ID",
                CREATED_TIME
        );
        Comment oldest = saveCommentAt(
                post, author, "가장 오래된 댓글",
                CREATED_TIME.minusSeconds(60)
        );
        Comment sameTimeSecond = saveCommentAt(
                post, author, "같은 시각 큰 ID",
                CREATED_TIME
        );

        Long postId = post.getId();
        Long newestId = newest.getId();
        Long firstId = sameTimeFirst.getId();
        Long oldestId = oldest.getId();
        Long secondId = sameTimeSecond.getId();

        entityManager.clear();

        List<Comment> ascending =
                commentRepository.findFirstVisibleComments(
                        postId,
                        PageRequest.of(0, 20)
                );

        List<Comment> descending =
                commentRepository.findFirstVisibleCommentsLatest(
                        postId,
                        PageRequest.of(0, 20)
                );

        assertThat(ascending)
                .extracting(Comment::getId)
                .containsExactly(
                        oldestId, firstId, secondId, newestId
                );

        assertThat(descending)
                .extracting(Comment::getId)
                .containsExactly(
                        newestId, secondId, firstId, oldestId
                );

        // 같은 시각의 댓글 두 개 사이에서 페이지를 나눈다.
        List<Comment> firstPage =
                commentRepository.findFirstVisibleCommentsLatest(
                        postId,
                        PageRequest.of(0, 2)
                );

        assertThat(firstPage)
                .extracting(Comment::getId)
                .containsExactly(newestId, secondId);

        Comment cursor = firstPage.getLast();

        List<Comment> secondPage =
                commentRepository.findVisibleCommentsLatestAfter(
                        postId,
                        cursor.getCreatedAt(),
                        cursor.getId(),
                        PageRequest.of(0, 2)
                );

        assertThat(secondPage)
                .extracting(Comment::getId)
                .containsExactly(firstId, oldestId);

        Comment last = secondPage.getLast();

        assertThat(
                commentRepository.findVisibleCommentsLatestAfter(
                        postId,
                        last.getCreatedAt(),
                        last.getId(),
                        PageRequest.of(0, 2)
                )
        ).isEmpty();
    }

    @Test
    void 최신순은_조회대상이_아닌_댓글을_제외하고_삭제된_커서로도_이어진다() {
        Member author = saveActiveMember(
                "latest-filter@example.com",
                "최신필터회원"
        );
        Post post = savePost(author, "대상 게시글");
        Post otherPost = savePost(author, "다른 게시글");

        Comment cursor = saveCommentAt(
                post, author, "첫 페이지 마지막 댓글",
                CREATED_TIME.plusSeconds(60)
        );
        Comment next = saveCommentAt(
                post, author, "다음 페이지 댓글",
                CREATED_TIME
        );
        Comment deleted = saveCommentAt(
                post, author, "삭제할 댓글",
                CREATED_TIME.plusSeconds(30)
        );
        saveCommentAt(
                otherPost, author, "다른 글의 댓글",
                CREATED_TIME.plusSeconds(30)
        );

        deleted.delete(UPDATED_TIME);
        entityManager.flush();

        Long postId = post.getId();
        Long cursorId = cursor.getId();
        Instant cursorTime = cursor.getCreatedAt();
        Long nextId = next.getId();

        entityManager.clear();

        assertThat(
                commentRepository.findFirstVisibleCommentsLatest(
                        postId,
                        PageRequest.of(0, 20)
                )
        )
                .extracting(Comment::getId)
                .containsExactly(cursorId, nextId);

        // 첫 페이지 조회 이후 커서 댓글이 삭제된 상황.
        commentRepository.findById(cursorId)
                .orElseThrow()
                .delete(UPDATED_TIME);

        entityManager.flush();
        entityManager.clear();

        assertThat(
                commentRepository.findVisibleCommentsLatestAfter(
                        postId,
                        cursorTime,
                        cursorId,
                        PageRequest.of(0, 20)
                )
        )
                .extracting(Comment::getId)
                .containsExactly(nextId);
    }

    @Test
    void 최신순에서도_삭제된_게시글의_댓글은_조회하지_않는다() {
        Member author = saveActiveMember(
                "latest-deleted-post@example.com",
                "최신삭제회원"
        );
        Post post = savePost(author, "삭제할 게시글");

        Comment cursor = saveCommentAt(
                post, author, "최신 댓글",
                CREATED_TIME.plusSeconds(60)
        );
        saveCommentAt(
                post, author, "오래된 댓글",
                CREATED_TIME
        );

        Long postId = post.getId();
        Long cursorId = cursor.getId();
        Instant cursorTime = cursor.getCreatedAt();

        post.delete(UPDATED_TIME);
        entityManager.flush();
        entityManager.clear();

        assertThat(
                commentRepository.findFirstVisibleCommentsLatest(
                        postId,
                        PageRequest.of(0, 20)
                )
        ).isEmpty();

        assertThat(
                commentRepository.findVisibleCommentsLatestAfter(
                        postId,
                        cursorTime,
                        cursorId,
                        PageRequest.of(0, 20)
                )
        ).isEmpty();

        // 댓글 자체는 삭제되지 않았어도 조회 대상에서 제외된다.
        assertThat(
                commentRepository.findById(cursorId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isNull();
    }

    @Test
    void 최신순으로_댓글이_없는_게시글을_조회하면_빈_목록을_반환한다() {
        Member author = saveActiveMember(
                "latest-empty@example.com",
                "최신빈목록회원"
        );
        Post post = savePost(author, "댓글 없는 게시글");
        Long postId = post.getId();

        entityManager.clear();

        assertThat(
                commentRepository.findFirstVisibleCommentsLatest(
                        postId,
                        PageRequest.of(0, 20)
                )
        ).isEmpty();
    }

    @Test
    void 인기순은_좋아요가_없는_댓글도_등록순으로_이어_조회한다() {
        Member author = saveActiveMember(
                "popular-zero@example.com",
                "인기순기본회원"
        );
        Post post = savePost(author, "인기순 기본 검증");

        Comment first = saveCommentAt(
                post, author, "먼저 작성한 댓글",
                CREATED_TIME
        );
        Comment second = saveCommentAt(
                post, author, "같은 시각 다음 댓글",
                CREATED_TIME
        );

        Long postId = post.getId();
        Long firstId = first.getId();
        Long secondId = second.getId();

        entityManager.clear();

        List<CommentPopularityRow> firstPage =
                commentRepository.findFirstVisibleCommentsPopular(
                        postId,
                        PageRequest.of(0, 1)
                );

        assertThat(firstPage)
                .extracting(CommentPopularityRow::getCommentId)
                .containsExactly(firstId);

        CommentPopularityRow cursor = firstPage.getFirst();

        assertThat(cursor.getCreatedAt()).isEqualTo(CREATED_TIME);
        assertThat(cursor.getLikeCount()).isEqualTo(0L);

        List<CommentPopularityRow> secondPage =
                commentRepository.findVisibleCommentsPopularAfter(
                        postId,
                        cursor.getLikeCount(),
                        cursor.getCreatedAt(),
                        cursor.getCommentId(),
                        PageRequest.of(0, 1)
                );

        assertThat(secondPage)
                .extracting(CommentPopularityRow::getCommentId)
                .containsExactly(secondId);

        assertThat(secondPage.getFirst().getLikeCount())
                .isEqualTo(0L);

        CommentPopularityRow last = secondPage.getFirst();

        assertThat(
                commentRepository.findVisibleCommentsPopularAfter(
                        postId,
                        last.getLikeCount(),
                        last.getCreatedAt(),
                        last.getCommentId(),
                        PageRequest.of(0, 1)
                )
        ).isEmpty();
    }

    @Test
    void 인기순은_좋아요수_작성시각_ID로_정렬하고_페이지를_이어간다() {
        Member author = saveActiveMember(
                "popular-order-author@example.com",
                "인기정렬작성자"
        );
        Member likerA = saveActiveMember(
                "popular-order-a@example.com",
                "인기회원가"
        );
        Member likerB = saveActiveMember(
                "popular-order-b@example.com",
                "인기회원나"
        );
        Member likerC = saveActiveMember(
                "popular-order-c@example.com",
                "인기회원다"
        );

        Post post = savePost(author, "인기순 정렬 검증");

        // 저장 순서와 기대 정렬 순서를 일부러 다르게 만든다.
        Comment later = saveCommentAt(
                post, author, "동점이지만 늦게 작성한 댓글",
                CREATED_TIME.plusSeconds(60)
        );
        Comment sameTimeFirst = saveCommentAt(
                post, author, "같은 시각 작은 ID",
                CREATED_TIME
        );
        Comment zero = saveCommentAt(
                post, author, "좋아요 없는 댓글",
                CREATED_TIME.minusSeconds(60)
        );
        Comment sameTimeSecond = saveCommentAt(
                post, author, "같은 시각 큰 ID",
                CREATED_TIME
        );
        Comment top = saveCommentAt(
                post, author, "좋아요가 가장 많은 댓글",
                CREATED_TIME.plusSeconds(120)
        );

        saveLikes(later, likerA, likerB);
        saveLikes(sameTimeFirst, likerA, likerB);
        saveLikes(sameTimeSecond, likerA, likerB);
        saveLikes(top, likerA, likerB, likerC);

        Long postId = post.getId();
        Long topId = top.getId();
        Long firstId = sameTimeFirst.getId();
        Long secondId = sameTimeSecond.getId();
        Long laterId = later.getId();
        Long zeroId = zero.getId();

        entityManager.clear();

        List<CommentPopularityRow> firstPage =
                commentRepository.findFirstVisibleCommentsPopular(
                        postId,
                        PageRequest.of(0, 2)
                );

        assertThat(firstPage)
                .extracting(
                        CommentPopularityRow::getCommentId,
                        CommentPopularityRow::getLikeCount
                )
                .containsExactly(
                        tuple(topId, 3L),
                        tuple(firstId, 2L)
                );

        CommentPopularityRow firstCursor = firstPage.getLast();

        List<CommentPopularityRow> secondPage =
                commentRepository.findVisibleCommentsPopularAfter(
                        postId,
                        firstCursor.getLikeCount(),
                        firstCursor.getCreatedAt(),
                        firstCursor.getCommentId(),
                        PageRequest.of(0, 2)
                );

        assertThat(secondPage)
                .extracting(
                        CommentPopularityRow::getCommentId,
                        CommentPopularityRow::getLikeCount
                )
                .containsExactly(
                        tuple(secondId, 2L),
                        tuple(laterId, 2L)
                );

        CommentPopularityRow secondCursor = secondPage.getLast();

        List<CommentPopularityRow> thirdPage =
                commentRepository.findVisibleCommentsPopularAfter(
                        postId,
                        secondCursor.getLikeCount(),
                        secondCursor.getCreatedAt(),
                        secondCursor.getCommentId(),
                        PageRequest.of(0, 2)
                );

        assertThat(thirdPage)
                .extracting(
                        CommentPopularityRow::getCommentId,
                        CommentPopularityRow::getLikeCount
                )
                .containsExactly(tuple(zeroId, 0L));

        CommentPopularityRow last = thirdPage.getLast();

        assertThat(
                commentRepository.findVisibleCommentsPopularAfter(
                        postId,
                        last.getLikeCount(),
                        last.getCreatedAt(),
                        last.getCommentId(),
                        PageRequest.of(0, 2)
                )
        ).isEmpty();
    }

    @Test
    void 인기순은_좋아요가_있어도_삭제된_댓글과_다른_게시글을_제외한다() {
        Member author = saveActiveMember(
                "popular-filter-author@example.com",
                "인기필터작성자"
        );
        Member likerA = saveActiveMember(
                "popular-filter-a@example.com",
                "필터회원가"
        );
        Member likerB = saveActiveMember(
                "popular-filter-b@example.com",
                "필터회원나"
        );

        Post post = savePost(author, "대상 게시글");
        Post otherPost = savePost(author, "다른 게시글");

        Comment first = saveCommentAt(
                post, author, "좋아요 두 개",
                CREATED_TIME
        );
        Comment next = saveCommentAt(
                post, author, "좋아요 한 개",
                CREATED_TIME.plusSeconds(60)
        );
        Comment deleted = saveCommentAt(
                post, author, "삭제할 댓글",
                CREATED_TIME
        );
        Comment other = saveCommentAt(
                otherPost, author, "다른 글의 댓글",
                CREATED_TIME
        );

        saveLikes(first, likerA, likerB);
        saveLikes(next, likerA);
        saveLikes(deleted, likerA);
        saveLikes(other, likerA);

        deleted.delete(UPDATED_TIME);
        entityManager.flush();

        Long postId = post.getId();
        Long firstId = first.getId();
        Long nextId = next.getId();
        Long deletedId = deleted.getId();

        entityManager.clear();

        List<CommentPopularityRow> result =
                commentRepository.findFirstVisibleCommentsPopular(
                        postId,
                        PageRequest.of(0, 20)
                );

        assertThat(result)
                .extracting(CommentPopularityRow::getCommentId)
                .containsExactly(firstId, nextId);

        CommentPopularityRow cursor = result.getFirst();

        assertThat(
                commentRepository.findVisibleCommentsPopularAfter(
                        postId,
                        cursor.getLikeCount(),
                        cursor.getCreatedAt(),
                        cursor.getCommentId(),
                        PageRequest.of(0, 20)
                )
        )
                .extracting(CommentPopularityRow::getCommentId)
                .containsExactly(nextId);

        // 좋아요가 사라져서 제외된 것이 아니라,
        // 댓글의 삭제 조건으로 제외됐음을 확인한다.
        assertThat(commentLikeRepository.countByComment_Id(deletedId))
                .isEqualTo(1L);
    }

    @Test
    void 인기순은_삭제된_게시글의_댓글을_조회하지_않는다() {
        Member author = saveActiveMember(
                "popular-post-author@example.com",
                "인기게시글작성자"
        );
        Member liker = saveActiveMember(
                "popular-post-liker@example.com",
                "인기게시글회원"
        );

        Post post = savePost(author, "삭제할 게시글");

        Comment first = saveCommentAt(
                post, author, "좋아요 있는 댓글",
                CREATED_TIME
        );
        saveCommentAt(
                post, author, "좋아요 없는 댓글",
                CREATED_TIME.plusSeconds(60)
        );

        saveLikes(first, liker);

        Long postId = post.getId();
        Long firstId = first.getId();
        Instant firstTime = first.getCreatedAt();

        post.delete(UPDATED_TIME);
        entityManager.flush();
        entityManager.clear();

        assertThat(
                commentRepository.findFirstVisibleCommentsPopular(
                        postId,
                        PageRequest.of(0, 20)
                )
        ).isEmpty();

        assertThat(
                commentRepository.findVisibleCommentsPopularAfter(
                        postId,
                        1L,
                        firstTime,
                        firstId,
                        PageRequest.of(0, 20)
                )
        ).isEmpty();

        assertThat(
                commentRepository.findById(firstId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isNull();
    }

    @Test
    void 원댓글과_답글을_저장한_뒤_다시_조회하면_부모_관계가_유지된다() {
        Comment parent = saveComment(
                "첫 보청기 상담 때 무엇을 준비하셨나요?"
        );
        Member replyAuthor = saveActiveMember(
                "reply-writer@example.com",
                "답글회원"
        );

        Comment reply = commentRepository.saveAndFlush(
                Comment.createReply(
                        parent,
                        replyAuthor,
                        "저는 평소 잘 안 들렸던 상황을 메모해 갔어요."
                )
        );

        Long parentId = parent.getId();
        Long replyId = reply.getId();
        Long postId = parent.getPost().getId();

        entityManager.clear();

        Comment foundParent = commentRepository.findById(parentId)
                .orElseThrow();
        Comment foundReply = commentRepository.findById(replyId)
                .orElseThrow();

        assertThat(foundParent.getParent()).isNull();
        assertThat(foundReply.getParent().getId()).isEqualTo(parentId);
        assertThat(foundReply.getPost().getId()).isEqualTo(postId);
    }

    @Test
    void 등록순_첫_페이지와_다음_페이지에는_원댓글만_나온다() {
        Comment first = saveComment("첫 번째 원댓글");
        Post post = first.getPost();
        Member author = first.getAuthor();

        commentRepository.saveAndFlush(
                Comment.createReply(first, author, "첫 번째 원댓글의 답글")
        );

        Comment second = saveCommentAt(
                post, author, "두 번째 원댓글", CREATED_TIME
        );

        commentRepository.saveAndFlush(
                Comment.createReply(second, author, "두 번째 원댓글의 답글")
        );

        Comment third = saveCommentAt(
                post, author, "세 번째 원댓글", CREATED_TIME
        );

        Long postId = post.getId();
        entityManager.clear();

        PageRequest pageSizeTwo = PageRequest.of(0, 2);

        List<Comment> firstPage = commentRepository.findFirstVisibleComments(
                postId,
                pageSizeTwo
        );

        assertThat(firstPage)
                .extracting(Comment::getId)
                .containsExactly(first.getId(), second.getId());

        Comment cursor = firstPage.getLast();

        List<Comment> nextPage = commentRepository.findVisibleCommentsAfter(
                postId,
                cursor.getCreatedAt(),
                cursor.getId(),
                pageSizeTwo
        );

        assertThat(nextPage)
                .extracting(Comment::getId)
                .containsExactly(third.getId());
    }

    @Test
    void 최신순_첫_페이지와_다음_페이지에는_원댓글만_나온다() {
        Comment first = saveComment("첫 번째 원댓글");
        Post post = first.getPost();
        Member author = first.getAuthor();

        commentRepository.saveAndFlush(
                Comment.createReply(first, author, "첫 번째 원댓글의 답글")
        );

        Comment second = saveCommentAt(
                post,
                author,
                "두 번째 원댓글",
                CREATED_TIME
        );

        commentRepository.saveAndFlush(
                Comment.createReply(second, author, "두 번째 원댓글의 답글")
        );

        Comment third = saveCommentAt(
                post,
                author,
                "세 번째 원댓글",
                CREATED_TIME
        );

        Long postId = post.getId();
        entityManager.clear();

        PageRequest pageable = PageRequest.of(0, 2);

        var firstPage = commentRepository.findFirstVisibleCommentsLatest(
                postId,
                pageable
        );

        assertThat(firstPage)
                .extracting(Comment::getId)
                .containsExactly(third.getId(), second.getId());

        Comment cursor = firstPage.getLast();

        var nextPage = commentRepository.findVisibleCommentsLatestAfter(
                postId,
                cursor.getCreatedAt(),
                cursor.getId(),
                pageable
        );

        assertThat(nextPage)
                .extracting(Comment::getId)
                .containsExactly(first.getId());
    }

    @Test
    void 인기순_첫_페이지와_다음_페이지에는_원댓글만_나온다() {
        Comment popularRoot = saveComment("좋아요 2개인 원댓글");
        Post post = popularRoot.getPost();
        Member author = popularRoot.getAuthor();

        Comment otherRoot = saveCommentAt(
                post,
                author,
                "좋아요가 없는 원댓글",
                CREATED_TIME
        );

        Comment mostLikedReply = commentRepository.saveAndFlush(
                Comment.createReply(otherRoot, author, "좋아요 3개인 답글")
        );
        Comment lessLikedReply = commentRepository.saveAndFlush(
                Comment.createReply(otherRoot, author, "좋아요 1개인 답글")
        );

        Member readerOne = saveActiveMember("reader1@example.com", "독자하나");
        Member readerTwo = saveActiveMember("reader2@example.com", "독자둘");
        Member readerThree = saveActiveMember("reader3@example.com", "독자셋");

        saveLikes(popularRoot, readerOne, readerTwo);
        saveLikes(mostLikedReply, readerOne, readerTwo, readerThree);
        saveLikes(lessLikedReply, readerOne);

        Long postId = post.getId();
        entityManager.flush();
        entityManager.clear();

        PageRequest pageable = PageRequest.of(0, 1);

        var firstPage = commentRepository.findFirstVisibleCommentsPopular(
                postId,
                pageable
        );

        assertThat(firstPage)
                .extracting(CommentPopularityRow::getCommentId)
                .containsExactly(popularRoot.getId());

        var cursor = firstPage.getFirst();
        assertThat(cursor.getLikeCount()).isEqualTo(2L);

        var nextPage = commentRepository.findVisibleCommentsPopularAfter(
                postId,
                cursor.getLikeCount(),
                cursor.getCreatedAt(),
                cursor.getCommentId(),
                pageable
        );

        assertThat(nextPage)
                .extracting(CommentPopularityRow::getCommentId)
                .containsExactly(otherRoot.getId());
        assertThat(nextPage.getFirst().getLikeCount()).isZero();
    }

    @Test
    void 답글은_같은_원댓글_안에서_등록순으로_다음_페이지까지_이어진다() {
        Comment parent = saveComment("답글을 펼칠 원댓글");
        Post post = parent.getPost();
        Member author = parent.getAuthor();

        Comment otherParent = saveCommentAt(
                post,
                author,
                "같은 게시글의 다른 원댓글",
                CREATED_TIME
        );

        Comment firstReply = commentRepository.saveAndFlush(
                Comment.createReply(parent, author, "첫 번째 답글")
        );

        commentRepository.saveAndFlush(
                Comment.createReply(otherParent, author, "다른 원댓글의 답글")
        );

        Comment secondReply = commentRepository.saveAndFlush(
                Comment.createReply(parent, author, "두 번째 답글")
        );

        commentRepository.saveAndFlush(
                Comment.createReply(otherParent, author, "커서 뒤에 있는 다른 원댓글의 답글")
        );

        Comment thirdReply = commentRepository.saveAndFlush(
                Comment.createReply(parent, author, "세 번째 답글")
        );

        Long postId = post.getId();
        Long parentId = parent.getId();
        entityManager.clear();

        PageRequest pageable = PageRequest.of(0, 2);

        var firstPage = commentRepository.findFirstVisibleReplies(
                postId,
                parentId,
                pageable
        );

        assertThat(firstPage)
                .extracting(Comment::getId)
                .containsExactly(firstReply.getId(), secondReply.getId());

        Comment cursor = firstPage.getLast();

        var nextPage = commentRepository.findVisibleRepliesAfter(
                postId,
                parentId,
                cursor.getCreatedAt(),
                cursor.getId(),
                pageable
        );

        assertThat(nextPage)
                .extracting(Comment::getId)
                .containsExactly(thirdReply.getId());
    }

    @Test
    void 삭제되거나_숨겨진_답글은_첫_페이지와_다음_페이지에서_제외된다() {
        Comment parent = saveComment("공개 원댓글");
        Member author = parent.getAuthor();

        saveReply(parent, author, "첫 페이지에서 제외할 삭제 답글")
                .delete(UPDATED_TIME);
        saveReply(parent, author, "첫 페이지에서 제외할 숨김 답글")
                .hide(UPDATED_TIME);

        Comment firstVisible = saveReply(parent, author, "첫 번째 공개 답글");

        saveReply(parent, author, "다음 페이지에서 제외할 삭제 답글")
                .delete(UPDATED_TIME);
        saveReply(parent, author, "다음 페이지에서 제외할 숨김 답글")
                .hide(UPDATED_TIME);

        Comment secondVisible = saveReply(parent, author, "두 번째 공개 답글");

        Long postId = parent.getPost().getId();
        Long parentId = parent.getId();
        entityManager.flush();
        entityManager.clear();

        PageRequest pageable = PageRequest.of(0, 1);

        var firstPage = commentRepository.findFirstVisibleReplies(
                postId,
                parentId,
                pageable
        );

        assertThat(firstPage)
                .extracting(Comment::getId)
                .containsExactly(firstVisible.getId());

        Comment cursor = firstPage.getFirst();

        var nextPage = commentRepository.findVisibleRepliesAfter(
                postId,
                parentId,
                cursor.getCreatedAt(),
                cursor.getId(),
                pageable
        );

        assertThat(nextPage)
                .extracting(Comment::getId)
                .containsExactly(secondVisible.getId());
    }

    @Test
    void 삭제된_원댓글의_공개_답글은_첫_페이지와_다음_페이지에서_조회된다() {
        Comment parent = saveComment("작성자가 삭제할 원댓글");
        Member author = parent.getAuthor();

        Comment firstReply = saveReply(parent, author, "첫 번째 공개 답글");
        Comment secondReply = saveReply(parent, author, "두 번째 공개 답글");

        Long postId = parent.getPost().getId();
        Long parentId = parent.getId();

        parent.delete(UPDATED_TIME);

        entityManager.flush();
        entityManager.clear();

        PageRequest pageable = PageRequest.of(0, 1);

        var firstPage = commentRepository.findFirstVisibleReplies(
                postId,
                parentId,
                pageable
        );

        assertThat(firstPage)
                .extracting(Comment::getId)
                .containsExactly(firstReply.getId());

        Comment cursor = firstPage.getFirst();

        var nextPage = commentRepository.findVisibleRepliesAfter(
                postId,
                parentId,
                cursor.getCreatedAt(),
                cursor.getId(),
                pageable
        );

        assertThat(nextPage)
                .extracting(Comment::getId)
                .containsExactly(secondReply.getId());
    }

    @Test
    void 원댓글이_숨겨지면_첫_페이지와_숨김_전에_받은_커서_모두_빈_목록을_반환한다() {
        Comment parent = saveComment("관리자가 숨길 원댓글");
        Member author = parent.getAuthor();

        Comment firstReply = saveReply(parent, author, "숨기기 전에 읽을 답글");
        saveReply(parent, author, "다음 페이지에 남아 있는 답글");

        Long postId = parent.getPost().getId();
        Long parentId = parent.getId();
        PageRequest pageable = PageRequest.of(0, 1);

        entityManager.flush();

        var beforeHide = commentRepository.findFirstVisibleReplies(
                postId,
                parentId,
                pageable
        );

        assertThat(beforeHide)
                .extracting(Comment::getId)
                .containsExactly(firstReply.getId());

        Comment cursor = beforeHide.getFirst();

        parent.hide(UPDATED_TIME);

        entityManager.flush();
        entityManager.clear();

        var firstPage = commentRepository.findFirstVisibleReplies(
                postId,
                parentId,
                pageable
        );

        var nextPage = commentRepository.findVisibleRepliesAfter(
                postId,
                parentId,
                cursor.getCreatedAt(),
                cursor.getId(),
                pageable
        );

        assertThat(firstPage).isEmpty();
        assertThat(nextPage).isEmpty();
        assertThat(commentRepository.findVisibleCommentByIdForUpdate(
                postId, firstReply.getId()
        )).isEmpty();
        assertThat(commentRepository.countVisibleCommentsByPostIds(
                List.of(postId)
        )).isEmpty();

        commentRepository.findById(parentId).orElseThrow().restore();
        entityManager.flush();

        assertThat(commentRepository.findVisibleCommentByIdForUpdate(
                postId, firstReply.getId()
        )).isPresent();
        assertThat(commentRepository.countVisibleCommentsByPostIds(List.of(postId)))
                .extracting(PostCommentCount::getCommentCount)
                .containsExactly(3L);
    }

    @Test
    void 등록순에서_공개_답글이_남은_삭제_원댓글만_첫_페이지와_다음_페이지에_포함된다() {
        Comment noReplies = saveComment("답글 없이 삭제할 원댓글");
        Post post = noReplies.getPost();
        Member author = noReplies.getAuthor();
        noReplies.delete(UPDATED_TIME);

        Comment firstParent = saveCommentAt(
                post, author, "공개 답글 두 개가 남을 원댓글", CREATED_TIME
        );
        saveReply(firstParent, author, "첫 번째 공개 답글");
        saveReply(firstParent, author, "두 번째 공개 답글");
        firstParent.delete(UPDATED_TIME);

        Comment noVisibleReplies = saveCommentAt(
                post, author, "공개 답글이 남지 않을 원댓글", CREATED_TIME
        );
        saveReply(noVisibleReplies, author, "삭제된 답글").delete(UPDATED_TIME);
        saveReply(noVisibleReplies, author, "숨겨진 답글").hide(UPDATED_TIME);
        noVisibleReplies.delete(UPDATED_TIME);

        Comment hiddenParent = saveCommentAt(
                post, author, "관리자가 숨길 원댓글", CREATED_TIME
        );
        saveReply(hiddenParent, author, "원댓글을 숨기기 전에 작성한 답글");
        hiddenParent.hide(UPDATED_TIME);

        Comment secondParent = saveCommentAt(
                post, author, "공개 답글 한 개가 남을 원댓글", CREATED_TIME
        );
        saveReply(secondParent, author, "남아 있는 공개 답글");
        secondParent.delete(UPDATED_TIME);

        Long postId = post.getId();
        entityManager.flush();
        entityManager.clear();

        PageRequest pageable = PageRequest.of(0, 1);

        var firstPage = commentRepository.findFirstVisibleComments(
                postId,
                pageable
        );

        assertThat(firstPage)
                .extracting(Comment::getId)
                .containsExactly(firstParent.getId());

        Comment cursor = firstPage.getFirst();

        var nextPage = commentRepository.findVisibleCommentsAfter(
                postId,
                cursor.getCreatedAt(),
                cursor.getId(),
                pageable
        );

        assertThat(nextPage)
                .extracting(Comment::getId)
                .containsExactly(secondParent.getId());
    }

    @Test
    void 최신순에서_공개_답글이_남은_삭제_원댓글은_페이지를_넘겨도_역순으로_조회된다() {
        Comment olderParent = saveComment("먼저 작성한 원댓글");
        Post post = olderParent.getPost();
        Member author = olderParent.getAuthor();

        saveReply(olderParent, author, "먼저 작성한 원댓글의 공개 답글");
        olderParent.delete(UPDATED_TIME);

        Comment noVisibleReplies = saveCommentAt(
                post, author, "공개 답글이 남지 않은 원댓글", CREATED_TIME
        );
        saveReply(noVisibleReplies, author, "삭제된 답글").delete(UPDATED_TIME);
        saveReply(noVisibleReplies, author, "숨겨진 답글").hide(UPDATED_TIME);
        noVisibleReplies.delete(UPDATED_TIME);

        Comment newerParent = saveCommentAt(
                post, author, "나중에 작성한 원댓글", CREATED_TIME
        );
        saveReply(newerParent, author, "나중에 작성한 원댓글의 공개 답글");
        newerParent.delete(UPDATED_TIME);

        Comment hiddenParent = saveCommentAt(
                post, author, "관리자가 숨긴 원댓글", CREATED_TIME
        );
        saveReply(hiddenParent, author, "원댓글을 숨기기 전에 작성한 답글");
        hiddenParent.hide(UPDATED_TIME);

        Long postId = post.getId();
        entityManager.flush();
        entityManager.clear();

        PageRequest pageable = PageRequest.of(0, 1);

        var firstPage = commentRepository.findFirstVisibleCommentsLatest(
                postId,
                pageable
        );

        assertThat(firstPage)
                .extracting(Comment::getId)
                .containsExactly(newerParent.getId());

        Comment cursor = firstPage.getFirst();

        var nextPage = commentRepository.findVisibleCommentsLatestAfter(
                postId,
                cursor.getCreatedAt(),
                cursor.getId(),
                pageable
        );

        assertThat(nextPage)
                .extracting(Comment::getId)
                .containsExactly(olderParent.getId());
    }

    @Test
    void 인기순에서_공개_답글이_남은_삭제_원댓글은_좋아요_0개로_페이지가_이어진다() {
        Comment visible = saveComment("좋아요 1개인 공개 원댓글");
        Post post = visible.getPost();
        Member author = visible.getAuthor();

        Member readerOne = saveActiveMember("popular1@example.com", "인기독자하나");
        Member readerTwo = saveActiveMember("popular2@example.com", "인기독자둘");

        saveLikes(visible, readerOne);

        Comment firstDeleted = saveCommentAt(
                post, author, "먼저 작성한 삭제 원댓글", CREATED_TIME
        );
        saveReply(firstDeleted, author, "첫 번째 원댓글에 남은 공개 답글");
        saveLikes(firstDeleted, readerOne, readerTwo);
        firstDeleted.delete(UPDATED_TIME);

        Comment noVisibleReplies = saveCommentAt(
                post, author, "숨긴 답글만 남은 삭제 원댓글", CREATED_TIME
        );
        saveReply(noVisibleReplies, author, "관리자가 숨긴 답글")
                .hide(UPDATED_TIME);
        noVisibleReplies.delete(UPDATED_TIME);

        Comment secondDeleted = saveCommentAt(
                post, author, "나중에 작성한 삭제 원댓글", CREATED_TIME
        );
        saveReply(secondDeleted, author, "두 번째 원댓글에 남은 공개 답글");
        saveLikes(secondDeleted, readerOne, readerTwo);
        secondDeleted.delete(UPDATED_TIME);

        Long postId = post.getId();
        entityManager.flush();
        entityManager.clear();

        PageRequest pageable = PageRequest.of(0, 2);

        var firstPage = commentRepository.findFirstVisibleCommentsPopular(
                postId,
                pageable
        );

        assertThat(firstPage)
                .extracting(CommentPopularityRow::getCommentId)
                .containsExactly(visible.getId(), firstDeleted.getId());
        assertThat(firstPage)
                .extracting(CommentPopularityRow::getLikeCount)
                .containsExactly(1L, 0L);

        var cursor = firstPage.getLast();

        var nextPage = commentRepository.findVisibleCommentsPopularAfter(
                postId,
                cursor.getLikeCount(),
                cursor.getCreatedAt(),
                cursor.getCommentId(),
                pageable
        );

        assertThat(nextPage)
                .extracting(CommentPopularityRow::getCommentId)
                .containsExactly(secondDeleted.getId());
        assertThat(nextPage.getFirst().getLikeCount()).isZero();
    }

    @Test
    void 원댓글별로_공개_답글만_집계하고_삭제된_원댓글의_공개_답글도_포함한다() {
        Comment visibleParent = saveComment("공개 원댓글");
        Post post = visibleParent.getPost();
        Member author = visibleParent.getAuthor();

        saveReply(visibleParent, author, "공개 답글 하나");
        saveReply(visibleParent, author, "공개 답글 둘");
        saveReply(visibleParent, author, "삭제한 답글").delete(UPDATED_TIME);
        saveReply(visibleParent, author, "숨긴 답글").hide(UPDATED_TIME);

        Comment deletedParent = saveCommentAt(
                post, author, "작성자가 삭제할 원댓글", CREATED_TIME
        );
        saveReply(deletedParent, author, "삭제 후에도 남는 공개 답글");
        deletedParent.delete(UPDATED_TIME);

        Comment hiddenParent = saveCommentAt(
                post, author, "관리자가 숨길 원댓글", CREATED_TIME
        );
        saveReply(hiddenParent, author, "원댓글이 숨겨지기 전에 작성한 답글");
        hiddenParent.hide(UPDATED_TIME);

        Comment emptyParent = saveCommentAt(
                post, author, "답글이 없는 원댓글", CREATED_TIME
        );

        Long postId = post.getId();
        List<Long> parentIds = List.of(
                visibleParent.getId(),
                deletedParent.getId(),
                hiddenParent.getId(),
                emptyParent.getId()
        );

        entityManager.flush();
        entityManager.clear();

        var result = commentRepository.countVisibleRepliesByParentIds(
                postId,
                parentIds
        );

        assertThat(result)
                .extracting(
                        CommentReplyCount::getParentId,
                        CommentReplyCount::getReplyCount
                )
                .containsExactlyInAnyOrder(
                        tuple(visibleParent.getId(), 2L),
                        tuple(deletedParent.getId(), 1L)
                );
    }

    @Test
    void 작성자별_공개_조회는_원댓글_숨김과_복원을_반영하고_삭제_후에도_공개_답글을_포함한다() {
        Comment parent = saveComment("다른 회원의 원댓글");
        Member author = saveActiveMember("reply-reader@example.com", "답글작성자");
        Comment reply = saveReply(parent, author, "작성자별 목록에 표시할 답글");
        Comment cursor = saveCommentAt(
                parent.getPost(), author, "더 최근에 작성한 원댓글",
                CREATED_TIME.plusSeconds(10)
        );

        assertPublicAuthorPages(author, cursor, reply.getId());

        parent.hide(UPDATED_TIME);
        assertPublicAuthorPages(author, cursor);

        parent.restore();
        assertPublicAuthorPages(author, cursor, reply.getId());

        parent.delete(UPDATED_TIME);
        assertPublicAuthorPages(author, cursor, reply.getId());
    }

    private void assertPublicAuthorPages(
            Member author,
            Comment cursor,
            Long... expectedAfterCursor
    ) {
        entityManager.flush();
        PageRequest limit = PageRequest.of(0, 2);
        assertThat(commentRepository.countVisibleCommentsByAuthorId(author.getId()))
                .isEqualTo(1L + expectedAfterCursor.length);

        List<Comment> firstPage = commentRepository.findFirstVisibleCommentsByAuthorId(
                author.getId(), limit
        );
        assertThat(firstPage).hasSize(1 + expectedAfterCursor.length);
        assertThat(firstPage.getFirst().getId()).isEqualTo(cursor.getId());
        assertThat(firstPage.subList(1, firstPage.size()))
                .extracting(Comment::getId)
                .containsExactly(expectedAfterCursor);

        assertThat(commentRepository.findVisibleCommentsByAuthorIdAfter(
                author.getId(), cursor.getCreatedAt(), cursor.getId(), limit
        )).extracting(Comment::getId).containsExactly(expectedAfterCursor);
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

    private void saveLikes(Comment comment, Member... members) {
        for (Member member : members) {
            commentLikeRepository.saveAndFlush(
                    CommentLike.create(comment, member)
            );
        }
    }

    private Comment saveReply(Comment parent, Member author, String content) {
        return commentRepository.save(
                Comment.createReply(parent, author, content)
        );
    }
}