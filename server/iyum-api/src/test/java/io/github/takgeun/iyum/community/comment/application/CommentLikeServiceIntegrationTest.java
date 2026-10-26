package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentLikeRepository;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.application.PostDeleteService;
import io.github.takgeun.iyum.community.post.application.PostLikeResult;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.application.MemberWithdrawalService;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import io.github.takgeun.iyum.support.MemberStatusFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true",
        "spring.datasource.hikari.connection-init-sql=SET lock_timeout TO '15s'"
})
@Testcontainers
class CommentLikeServiceIntegrationTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    CommentLikeService commentLikeService;

    @Autowired
    CommentDeleteService commentDeleteService;

    @Autowired
    PostDeleteService postDeleteService;

    @Autowired
    CommentLikeRepository commentLikeRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    MemberWithdrawalService memberWithdrawalService;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Long authorId;
    private Long otherMemberId;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        // 이 클래스의 Testcontainers 전용 DB를 정리한다.
        // 외래 키를 참조하는 자식 데이터부터 삭제한다.
        commentLikeRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member author = saveActiveMember(
                "comment-like-author@example.com",
                "댓글작성자"
        );

        Member otherMember = saveActiveMember(
                "comment-like-other@example.com",
                "다른회원"
        );

        Post post = savePost(author);

        Comment comment = commentRepository.saveAndFlush(
                Comment.create(post, author, "좋아요 테스트 댓글")
        );

        authorId = author.getId();
        otherMemberId = otherMember.getId();
        postId = post.getId();
        commentId = comment.getId();
    }

    @Test
    void 자기_댓글에_등록할_수_있고_중복_등록은_기존_행을_유지한다() {
        CommentLikeResult first =
                commentLikeService.like(authorId, postId, commentId);

        Long firstLikeId = findLikeId(authorId, commentId);

        CommentLikeResult repeated =
                commentLikeService.like(authorId, postId, commentId);

        assertThat(first).isEqualTo(
                new CommentLikeResult(postId, commentId, true, 1L)
        );

        assertThat(repeated).isEqualTo(first);

        // 개수뿐 아니라 기존 좋아요 행 자체가 유지되는지 확인한다.
        assertThat(findLikeId(authorId, commentId))
                .isEqualTo(firstLikeId);

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);
    }

    @Test
    void 취소와_중복_취소는_좋아요가_없는_상태를_유지한다() {
        commentLikeService.like(authorId, postId, commentId);

        CommentLikeResult first =
                commentLikeService.unlike(authorId, postId, commentId);

        CommentLikeResult repeated =
                commentLikeService.unlike(authorId, postId, commentId);

        assertThat(first).isEqualTo(
                new CommentLikeResult(postId, commentId, false, 0L)
        );

        assertThat(repeated).isEqualTo(first);

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, authorId
                )
        ).isEmpty();

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isZero();

        // 좋아요 취소가 댓글·게시글·회원을 삭제하면 안 된다.
        assertThat(commentRepository.existsById(commentId)).isTrue();
        assertThat(postRepository.existsById(postId)).isTrue();
        assertThat(memberRepository.existsById(authorId)).isTrue();
    }

    @Test
    void 한_번도_등록하지_않은_좋아요도_취소할_수_있다() {
        CommentLikeResult result =
                commentLikeService.unlike(authorId, postId, commentId);

        assertThat(result).isEqualTo(
                new CommentLikeResult(postId, commentId, false, 0L)
        );

        assertThat(commentLikeRepository.count()).isZero();
    }

    @Test
    void 취소한_뒤_다시_등록할_수_있다() {
        commentLikeService.like(authorId, postId, commentId);
        Long previousLikeId = findLikeId(authorId, commentId);

        commentLikeService.unlike(authorId, postId, commentId);

        CommentLikeResult result =
                commentLikeService.like(authorId, postId, commentId);

        assertThat(result).isEqualTo(
                new CommentLikeResult(postId, commentId, true, 1L)
        );

        assertThat(findLikeId(authorId, commentId))
                .isNotEqualTo(previousLikeId);

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);
    }

    @Test
    void 내_좋아요를_취소해도_다른_회원의_좋아요는_유지된다() {
        commentLikeService.like(authorId, postId, commentId);

        CommentLikeResult second =
                commentLikeService.like(otherMemberId, postId, commentId);

        assertThat(second).isEqualTo(
                new CommentLikeResult(postId, commentId, true, 2L)
        );

        CommentLikeResult result =
                commentLikeService.unlike(authorId, postId, commentId);

        assertThat(result).isEqualTo(
                new CommentLikeResult(postId, commentId, false, 1L)
        );

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, authorId
                )
        ).isEmpty();

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, otherMemberId
                )
        ).isPresent();

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);
    }

    @Test
    void 같은_게시글의_다른_댓글_좋아요는_독립적으로_처리한다() {
        Post post = postRepository.findById(postId).orElseThrow();
        Member author = memberRepository.findById(authorId).orElseThrow();

        Long secondCommentId = commentRepository.saveAndFlush(
                Comment.create(post, author, "두 번째 댓글")
        ).getId();

        commentLikeService.like(authorId, postId, commentId);
        commentLikeService.like(authorId, postId, secondCommentId);

        commentLikeService.unlike(authorId, postId, commentId);

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isZero();

        assertThat(commentLikeRepository.countByComment_Id(secondCommentId))
                .isEqualTo(1L);

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        secondCommentId, authorId
                )
        ).isPresent();
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = "ACTIVE",
            mode = EnumSource.Mode.EXCLUDE
    )
    void ACTIVE가_아니면_등록과_취소를_모두_거절한다(
            MemberStatus status
    ) {
        commentLikeService.like(otherMemberId, postId, commentId);

        // 회원 상태별 DB 제약을 지키면서 테스트 상태를 준비한다.
        MemberStatusFixture.setStatus(
                jdbcTemplate, otherMemberId, status
        );

        assertBothRejected(
                otherMemberId,
                postId,
                commentId,
                CommentLikeException.Code.MEMBER_NOT_ACTIVE
        );

        // 거절된 취소 요청이 기존 좋아요를 삭제하면 안 된다.
        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, otherMemberId
                )
        ).isPresent();

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1, 0, Long.MAX_VALUE})
    void 회원_ID가_잘못되었거나_회원이_없으면_거절한다(
            Long invalidMemberId
    ) {
        assertBothRejected(
                invalidMemberId,
                postId,
                commentId,
                CommentLikeException.Code.MEMBER_NOT_FOUND
        );

        assertThat(commentLikeRepository.count()).isZero();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1, 0, Long.MAX_VALUE})
    void 게시글_ID가_잘못되었거나_게시글이_없으면_거절한다(
            Long invalidPostId
    ) {
        assertBothRejected(
                authorId,
                invalidPostId,
                commentId,
                CommentLikeException.Code.POST_NOT_FOUND
        );

        assertThat(commentLikeRepository.count()).isZero();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1, 0, Long.MAX_VALUE})
    void 댓글_ID가_잘못되었거나_댓글이_없으면_거절한다(
            Long invalidCommentId
    ) {
        assertBothRejected(
                authorId,
                postId,
                invalidCommentId,
                CommentLikeException.Code.COMMENT_NOT_FOUND
        );

        assertThat(commentLikeRepository.count()).isZero();
    }

    @Test
    void 다른_게시글에_속한_댓글이면_등록과_취소를_거절한다() {
        commentLikeService.like(authorId, postId, commentId);

        Member author = memberRepository.findById(authorId).orElseThrow();
        Long otherPostId = savePost(author).getId();

        // commentId는 원래 postId 소속이다.
        // 존재하는 다른 게시글 ID와 조합해 요청한다.
        assertBothRejected(
                authorId,
                otherPostId,
                commentId,
                CommentLikeException.Code.COMMENT_NOT_FOUND
        );

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, authorId
                )
        ).isPresent();

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);
    }

    @Test
    void 삭제된_댓글은_좋아요_유무와_관계없이_거절한다() {
        commentLikeService.like(otherMemberId, postId, commentId);

        commentDeleteService.delete(authorId, postId, commentId);

        // 기존 좋아요가 있는 회원
        assertBothRejected(
                otherMemberId,
                postId,
                commentId,
                CommentLikeException.Code.COMMENT_NOT_FOUND
        );

        // 기존 좋아요가 없는 회원
        assertBothRejected(
                authorId,
                postId,
                commentId,
                CommentLikeException.Code.COMMENT_NOT_FOUND
        );

        assertThat(
                commentRepository.findById(commentId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isNotNull();

        // 댓글 논리 삭제는 기존 좋아요 행을 물리 삭제하지 않는다.
        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);
    }

    @Test
    void 부모_게시글이_삭제되면_등록과_취소를_거절한다() {
        commentLikeService.like(otherMemberId, postId, commentId);

        postDeleteService.delete(authorId, postId);

        assertBothRejected(
                otherMemberId,
                postId,
                commentId,
                CommentLikeException.Code.POST_NOT_FOUND
        );

        assertBothRejected(
                authorId,
                postId,
                commentId,
                CommentLikeException.Code.POST_NOT_FOUND
        );

        assertThat(
                postRepository.findById(postId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isNotNull();

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);
    }

    @Test
    void 같은_회원이_동시에_등록해도_좋아요는_하나만_저장된다()
            throws Exception {
        List<CommentLikeResult> results = runTogether(
                () -> commentLikeService.like(
                        authorId, postId, commentId
                ),
                () -> commentLikeService.like(
                        authorId, postId, commentId
                )
        );

        assertThat(results).containsExactly(
                new CommentLikeResult(postId, commentId, true, 1L),
                new CommentLikeResult(postId, commentId, true, 1L)
        );

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, authorId
                )
        ).isPresent();
    }

    @Test
    void 다른_회원이_동시에_등록하면_좋아요가_각각_저장된다()
            throws Exception {
        List<CommentLikeResult> results = runTogether(
                () -> commentLikeService.like(
                        authorId, postId, commentId
                ),
                () -> commentLikeService.like(
                        otherMemberId, postId, commentId
                )
        );

        assertThat(results).allSatisfy(result -> {
            assertThat(result.postId()).isEqualTo(postId);
            assertThat(result.commentId()).isEqualTo(commentId);
            assertThat(result.liked()).isTrue();
        });

        // 먼저 처리된 요청은 1, 다음 요청은 2를 반환한다.
        // 어느 회원이 먼저 처리될지는 정하지 않는다.
        assertThat(results)
                .extracting(CommentLikeResult::likeCount)
                .containsExactlyInAnyOrder(1L, 2L);

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(2L);

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, authorId
                )
        ).isPresent();

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, otherMemberId
                )
        ).isPresent();
    }

    @Test
    void 같은_회원이_동시에_취소해도_다른_회원의_좋아요는_유지된다()
            throws Exception {
        commentLikeService.like(authorId, postId, commentId);
        commentLikeService.like(otherMemberId, postId, commentId);

        List<CommentLikeResult> results = runTogether(
                () -> commentLikeService.unlike(
                        authorId, postId, commentId
                ),
                () -> commentLikeService.unlike(
                        authorId, postId, commentId
                )
        );

        assertThat(results).containsExactly(
                new CommentLikeResult(postId, commentId, false, 1L),
                new CommentLikeResult(postId, commentId, false, 1L)
        );

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, authorId
                )
        ).isEmpty();

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, otherMemberId
                )
        ).isPresent();

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 댓글_삭제가_먼저이면_대기하던_좋아요_요청은_거절된다(
            boolean register
    ) throws Exception {
        // 취소 요청을 검증할 때는 기존 좋아요를 준비한다.
        if (!register) {
            commentLikeService.like(otherMemberId, postId, commentId);
        }

        RuntimeException failure = runWithBlockedRequest(
                () -> commentDeleteService.delete(
                        authorId, postId, commentId
                ),
                () -> {
                    if (register) {
                        commentLikeService.like(
                                otherMemberId, postId, commentId
                        );
                    } else {
                        commentLikeService.unlike(
                                otherMemberId, postId, commentId
                        );
                    }
                }
        );

        assertThat(failure).isInstanceOfSatisfying(
                CommentLikeException.class,
                exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(
                                        CommentLikeException.Code.COMMENT_NOT_FOUND
                                )
        );

        assertThat(
                commentRepository.findById(commentId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isNotNull();

        // 거절된 등록은 행을 만들지 않고,
        // 거절된 취소는 기존 행을 지우지 않는다.
        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(register ? 0L : 1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 게시글_삭제가_먼저이면_대기하던_댓글_좋아요_요청은_거절된다(
            boolean register
    ) throws Exception {
        if (!register) {
            commentLikeService.like(otherMemberId, postId, commentId);
        }

        RuntimeException failure = runWithBlockedRequest(
                () -> postDeleteService.delete(authorId, postId),
                () -> {
                    if (register) {
                        commentLikeService.like(
                                otherMemberId, postId, commentId
                        );
                    } else {
                        commentLikeService.unlike(
                                otherMemberId, postId, commentId
                        );
                    }
                }
        );

        assertThat(failure).isInstanceOfSatisfying(
                CommentLikeException.class,
                exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(
                                        CommentLikeException.Code.POST_NOT_FOUND
                                )
        );

        assertThat(
                postRepository.findById(postId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isNotNull();

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(register ? 0L : 1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 회원_탈퇴가_먼저이면_대기하던_댓글_좋아요_요청은_거절된다(
            boolean register
    ) throws Exception {
        String rawPassword = "Test-password-123!";

        Member member = Member.createPending(
                "comment-like-withdraw@example.com",
                passwordEncoder.encode(rawPassword),
                "탈퇴경합회원"
        );

        member.activeAfterEmailVerification();

        Long memberId = memberRepository.saveAndFlush(member).getId();

        if (!register) {
            commentLikeService.like(memberId, postId, commentId);
        }

        RuntimeException failure = runWithBlockedRequest(
                () -> memberWithdrawalService.withdraw(
                        memberId, rawPassword
                ),
                () -> {
                    if (register) {
                        commentLikeService.like(
                                memberId, postId, commentId
                        );
                    } else {
                        commentLikeService.unlike(
                                memberId, postId, commentId
                        );
                    }
                }
        );

        assertThat(failure).isInstanceOfSatisfying(
                CommentLikeException.class,
                exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(
                                        CommentLikeException.Code.MEMBER_NOT_ACTIVE
                                )
        );

        Member withdrawn = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(withdrawn.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(withdrawn.getPasswordHash()).isNull();

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(register ? 0L : 1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 좋아요_등록이_먼저이면_등록_커밋_후_삭제된다(
            boolean deleteComment
    ) throws Exception {
        RuntimeException failure = runWithBlockedRequest(
                () -> {
                    CommentLikeResult result =
                            commentLikeService.like(
                                    otherMemberId, postId, commentId
                            );

                    assertThat(result).isEqualTo(
                            new CommentLikeResult(
                                    postId, commentId, true, 1L
                            )
                    );
                },
                () -> {
                    if (deleteComment) {
                        commentDeleteService.delete(
                                authorId, postId, commentId
                        );
                    } else {
                        postDeleteService.delete(authorId, postId);
                    }
                }
        );

        assertThat(failure).isNull();

        if (deleteComment) {
            assertThat(
                    commentRepository.findById(commentId)
                            .orElseThrow()
                            .getDeletedAt()
            ).isNotNull();
        } else {
            assertThat(
                    postRepository.findById(postId)
                            .orElseThrow()
                            .getDeletedAt()
            ).isNotNull();
        }

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, otherMemberId
                )
        ).isPresent();

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);
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

    private Post savePost(Member author) {
        return postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        "댓글 좋아요 테스트 게시글",
                        "테스트 본문"
                )
        );
    }

    private Long findLikeId(
            Long memberId,
            Long targetCommentId
    ) {
        return commentLikeRepository
                .findByComment_IdAndMember_Id(
                        targetCommentId, memberId
                )
                .orElseThrow()
                .getId();
    }

    private void assertBothRejected(
            Long memberId,
            Long targetPostId,
            Long targetCommentId,
            CommentLikeException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                commentLikeService.like(
                        memberId, targetPostId, targetCommentId
                )
        ).isInstanceOfSatisfying(
                CommentLikeException.class,
                exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
        );

        assertThatThrownBy(() ->
                commentLikeService.unlike(
                        memberId, targetPostId, targetCommentId
                )
        ).isInstanceOfSatisfying(
                CommentLikeException.class,
                exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
        );
    }

    private RuntimeException runWithBlockedRequest(
            Runnable firstAction,
            Runnable secondAction
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch firstReady = new CountDownLatch(1);
        CountDownLatch secondReady = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);

        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();

        try {
            Future<?> first = executor.submit(() ->
                    new TransactionTemplate(transactionManager)
                            .executeWithoutResult(status -> {
                                firstPid.set(currentBackendPid());

                                // 실제 서비스를 호출한다.
                                // 서비스는 이 외부 트랜잭션에 참여한다.
                                firstAction.run();

                                firstReady.countDown();

                                // 서비스 처리가 끝나도 외부 트랜잭션이
                                // 끝나지 않았으므로 아직 커밋되지 않는다.
                                awaitLatch(
                                        allowFirstCommit,
                                        "첫 번째 작업의 커밋 허용"
                                );
                            })
            );

            if (!firstReady.await(10, TimeUnit.SECONDS)) {
                // 작업이 예외로 종료됐다면 원래 예외를 확인한다.
                if (first.isDone()) {
                    first.get();
                }

                throw new IllegalStateException(
                        "첫 번째 작업이 준비되지 않았습니다."
                );
            }

            Future<RuntimeException> second = executor.submit(() -> {
                try {
                    new TransactionTemplate(transactionManager)
                            .executeWithoutResult(status -> {
                                secondPid.set(currentBackendPid());
                                secondReady.countDown();

                                secondAction.run();
                            });

                    return null;
                } catch (RuntimeException exception) {
                    // 트랜잭션이 롤백된 뒤 예외를 결과로 전달한다.
                    return exception;
                }
            });

            assertThat(secondReady.await(10, TimeUnit.SECONDS))
                    .as("두 번째 작업이 DB 연결을 확보해야 한다.")
                    .isTrue();

            // 단순히 시간이 지났다고 추정하지 않고,
            // 실제로 첫 번째 작업의 잠금을 기다리는지 확인한다.
            assertBlockedBy(
                    secondPid.get(),
                    firstPid.get()
            );

            allowFirstCommit.countDown();

            // 첫 번째 작업의 커밋까지 완료됐는지 확인한다.
            first.get(30, TimeUnit.SECONDS);

            // 잠금이 풀린 뒤 두 번째 작업의 결과를 확인한다.
            return second.get(30, TimeUnit.SECONDS);
        } finally {
            allowFirstCommit.countDown();
            executor.shutdownNow();

            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS))
                    .as("경합 테스트의 작업 스레드가 종료되어야 한다.")
                    .isTrue();
        }
    }

    private int currentBackendPid() {
        return jdbcTemplate.queryForObject(
                "SELECT pg_backend_pid()",
                Integer.class
        );
    }

    private void assertBlockedBy(
            int waitingPid,
            int blockingPid
    ) throws InterruptedException {
        long deadline =
                System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (System.nanoTime() < deadline) {
            Boolean blocked = jdbcTemplate.queryForObject(
                    "SELECT ? = ANY(pg_blocking_pids(?))",
                    Boolean.class,
                    blockingPid,
                    waitingPid
            );

            if (Boolean.TRUE.equals(blocked)) {
                return;
            }

            Thread.sleep(25);
        }

        throw new AssertionError(
                "두 번째 작업이 첫 번째 작업의 DB 잠금을 "
                        + "기다리는 상태를 확인하지 못했습니다."
        );
    }

    private void awaitLatch(
            CountDownLatch latch,
            String description
    ) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        description + " 대기 시간을 초과했습니다."
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    description + " 대기 중 인터럽트가 발생했습니다.",
                    exception
            );
        }
    }

    private List<CommentLikeResult> runTogether(
            Callable<CommentLikeResult> firstAction,
            Callable<CommentLikeResult> secondAction
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // CountDownLatch: 두 작업이 준비된 뒤 함께 출발하도록 만드는 장치
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<CommentLikeResult> first = executor.submit(() -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "첫 번째 작업의 시작 대기 시간을 초과했습니다."
                    );
                }

                return firstAction.call();
            });

            Future<CommentLikeResult> second = executor.submit(() -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "두 번째 작업의 시작 대기 시간을 초과했습니다."
                    );
                }

                return secondAction.call();
            });

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("두 작업이 모두 실행 준비를 마쳐야 한다.")
                    .isTrue();

            start.countDown();

            return List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
        } finally {
            start.countDown();
            executor.shutdownNow();

            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                    .as("테스트 작업 스레드가 종료되어야 한다.")
                    .isTrue();
        }
    }
}