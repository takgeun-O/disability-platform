package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostLikeRepository;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class PostLikeServiceIntegrationTest {

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
    PostLikeService postLikeService;

    @Autowired
    PostDeleteService postDeleteService;

    @Autowired
    PostLikeRepository postLikeRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    MemberWithdrawalService memberWithdrawalService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long authorId;
    private Long otherMemberId;
    private Long postId;

    @BeforeEach
    void setUp() {
        // 이 클래스의 Testcontainers 전용 DB만 정리한다.
        // 외래 키를 참조하는 좋아요부터 삭제한다.
        postLikeRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member author = saveActiveMember(
                "like-author@example.com",
                "좋아요작성자"
        );

        Member otherMember = saveActiveMember(
                "like-other@example.com",
                "다른회원"
        );

        Post post = postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        "좋아요 테스트 게시글",
                        "테스트 본문"
                )
        );

        authorId = author.getId();
        otherMemberId = otherMember.getId();
        postId = post.getId();
    }

    @Test
    void 최초_등록과_중복_등록은_같은_좋아요를_유지한다() {
        PostLikeResult first =
                postLikeService.like(authorId, postId);

        Long firstLikeId = findLikeId(authorId);

        PostLikeResult repeated =
                postLikeService.like(authorId, postId);

        assertThat(first)
                .isEqualTo(new PostLikeResult(postId, true, 1L));

        assertThat(repeated).isEqualTo(first);

        // 개수만 같은 것이 아니라 기존 행을 그대로 유지한다.
        assertThat(findLikeId(authorId)).isEqualTo(firstLikeId);
        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(1L);
    }

    @Test
    void 취소와_중복_취소는_좋아요가_없는_상태를_유지한다() {
        postLikeService.like(authorId, postId);

        PostLikeResult first =
                postLikeService.unlike(authorId, postId);

        PostLikeResult repeated =
                postLikeService.unlike(authorId, postId);

        assertThat(first)
                .isEqualTo(new PostLikeResult(postId, false, 0L));

        assertThat(repeated).isEqualTo(first);

        assertThat(
                postLikeRepository.findByPost_IdAndMember_Id(
                        postId,
                        authorId
                )
        ).isEmpty();

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isZero();

        // 좋아요 취소가 게시글이나 회원을 삭제하지 않는다.
        assertThat(postRepository.existsById(postId)).isTrue();
        assertThat(memberRepository.existsById(authorId)).isTrue();
    }

    @Test
    void 한_번도_등록하지_않은_좋아요도_취소할_수_있다() {
        PostLikeResult result =
                postLikeService.unlike(authorId, postId);

        assertThat(result)
                .isEqualTo(new PostLikeResult(postId, false, 0L));

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isZero();
    }

    @Test
    void 내_좋아요를_취소해도_다른_회원의_좋아요는_유지된다() {
        postLikeService.like(authorId, postId);
        postLikeService.like(otherMemberId, postId);

        PostLikeResult result =
                postLikeService.unlike(authorId, postId);

        assertThat(result)
                .isEqualTo(new PostLikeResult(postId, false, 1L));

        assertThat(
                postLikeRepository.findByPost_IdAndMember_Id(
                        postId,
                        authorId
                )
        ).isEmpty();

        assertThat(
                postLikeRepository.findByPost_IdAndMember_Id(
                        postId,
                        otherMemberId
                )
        ).isPresent();

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(1L);
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
        postLikeService.like(authorId, postId);

        // V7의 회원 상태 관련 DB 제약을 지키면서 상태를 변경한다.
        MemberStatusFixture.setStatus(
                jdbcTemplate,
                authorId,
                status
        );

        assertBothRejected(
                authorId,
                postId,
                PostLikeException.Code.MEMBER_NOT_ACTIVE
        );

        // 거절된 요청은 기존 좋아요를 변경하지 않는다.
        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(1L);

        assertThat(
                postLikeRepository.findByPost_IdAndMember_Id(
                        postId,
                        authorId
                )
        ).isPresent();
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
                PostLikeException.Code.MEMBER_NOT_FOUND
        );

        assertThat(postLikeRepository.count()).isZero();
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
                PostLikeException.Code.POST_NOT_FOUND
        );

        assertThat(postLikeRepository.count()).isZero();
    }

    @Test
    void 삭제된_게시글은_좋아요_유무와_관계없이_거절한다() {
        postLikeService.like(authorId, postId);

        // 실제 게시글 삭제 서비스를 호출해 논리 삭제한다.
        postDeleteService.delete(authorId, postId);

        // 기존 좋아요가 있는 회원
        assertBothRejected(
                authorId,
                postId,
                PostLikeException.Code.POST_NOT_FOUND
        );

        // 기존 좋아요가 없는 회원
        assertBothRejected(
                otherMemberId,
                postId,
                PostLikeException.Code.POST_NOT_FOUND
        );

        assertThat(
                postRepository.findById(postId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isNotNull();

        // 게시글 논리 삭제는 좋아요 행을 물리 삭제하지 않는다.
        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(1L);
    }

    @Test
    void 같은_회원이_동시에_등록해도_좋아요는_하나만_저장된다()
            throws Exception {
        List<PostLikeResult> results = runTogether(
                () -> postLikeService.like(authorId, postId),
                () -> postLikeService.like(authorId, postId)
        );

        assertThat(results).containsExactly(
                new PostLikeResult(postId, true, 1L),
                new PostLikeResult(postId, true, 1L)
        );

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(1L);
    }

    @Test
    void 다른_회원이_동시에_등록하면_좋아요가_각각_저장된다()
            throws Exception {
        List<PostLikeResult> results = runTogether(
                () -> postLikeService.like(authorId, postId),
                () -> postLikeService.like(otherMemberId, postId)
        );

        assertThat(results).allSatisfy(result -> {
            assertThat(result.postId()).isEqualTo(postId);
            assertThat(result.liked()).isTrue();
        });

        // 누가 먼저 잠금을 얻는지는 정하지 않는다.
        // 먼저 처리된 요청은 1, 다음 요청은 2를 반환한다.
        assertThat(results)
                .extracting(PostLikeResult::likeCount)
                .containsExactlyInAnyOrder(1L, 2L);

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(2L);

        assertThat(
                postLikeRepository.findByPost_IdAndMember_Id(
                        postId,
                        authorId
                )
        ).isPresent();

        assertThat(
                postLikeRepository.findByPost_IdAndMember_Id(
                        postId,
                        otherMemberId
                )
        ).isPresent();
    }

    @Test
    void 같은_회원이_동시에_취소해도_다른_회원의_좋아요는_유지된다()
            throws Exception {
        postLikeService.like(authorId, postId);
        postLikeService.like(otherMemberId, postId);

        List<PostLikeResult> results = runTogether(
                () -> postLikeService.unlike(authorId, postId),
                () -> postLikeService.unlike(authorId, postId)
        );

        assertThat(results).containsExactly(
                new PostLikeResult(postId, false, 1L),
                new PostLikeResult(postId, false, 1L)
        );

        assertThat(
                postLikeRepository.findByPost_IdAndMember_Id(
                        postId,
                        authorId
                )
        ).isEmpty();

        assertThat(
                postLikeRepository.findByPost_IdAndMember_Id(
                        postId,
                        otherMemberId
                )
        ).isPresent();

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 게시글_삭제가_먼저이면_대기하던_좋아요_요청은_거절된다(
            boolean register
    ) throws Exception {
        RuntimeException failure = runWithBlockedRequest(
                () -> postDeleteService.delete(authorId, postId),
                () -> {
                    if (register) {
                        postLikeService.like(otherMemberId, postId);
                    } else {
                        postLikeService.unlike(otherMemberId, postId);
                    }
                }
        );

        assertThat(failure)
                .isInstanceOfSatisfying(
                        PostLikeException.class,
                        exception ->
                                assertThat(exception.getCode())
                                        .isEqualTo(
                                                PostLikeException.Code.POST_NOT_FOUND
                                        )
                );

        assertThat(
                postRepository.findById(postId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isNotNull();

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 회원_탈퇴가_먼저이면_대기하던_좋아요_요청은_거절된다(
            boolean register
    ) throws Exception {
        String rawPassword = "Test-password-123!";

        // 실제 탈퇴 서비스를 호출하므로 검증 가능한 해시를 저장한다.
        Member member = Member.createPending(
                "withdraw-race@example.com",
                passwordEncoder.encode(rawPassword),
                "탈퇴경합회원"
        );
        member.activeAfterEmailVerification();

        Long memberId = memberRepository.saveAndFlush(member).getId();

        RuntimeException failure = runWithBlockedRequest(
                () -> memberWithdrawalService.withdraw(
                        memberId,
                        rawPassword
                ),
                () -> {
                    if (register) {
                        postLikeService.like(memberId, postId);
                    } else {
                        postLikeService.unlike(memberId, postId);
                    }
                }
        );

        assertThat(failure)
                .isInstanceOfSatisfying(
                        PostLikeException.class,
                        exception ->
                                assertThat(exception.getCode())
                                        .isEqualTo(
                                                PostLikeException.Code.MEMBER_NOT_ACTIVE
                                        )
                );

        Member withdrawn = memberRepository.findById(memberId)
                .orElseThrow();

        // 현재 탈퇴 구현은 개인정보를 정리하고 DELETED로 변경한다.
        assertThat(withdrawn.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(withdrawn.getPasswordHash()).isNull();

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isZero();
    }

    @Test
    void 좋아요_등록이_먼저이면_등록_커밋_후_게시글이_삭제된다()
            throws Exception {
        RuntimeException failure = runWithBlockedRequest(
                () -> {
                    PostLikeResult result =
                            postLikeService.like(otherMemberId, postId);

                    assertThat(result)
                            .isEqualTo(
                                    new PostLikeResult(postId, true, 1L)
                            );
                },
                () -> postDeleteService.delete(authorId, postId)
        );

        assertThat(failure).isNull();

        assertThat(
                postRepository.findById(postId)
                        .orElseThrow()
                        .getDeletedAt()
        ).isNotNull();

        assertThat(
                postLikeRepository.findByPost_IdAndMember_Id(
                        postId,
                        otherMemberId
                )
        ).isPresent();

        assertThat(postLikeRepository.countByPost_Id(postId))
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

    private Long findLikeId(Long memberId) {
        return postLikeRepository
                .findByPost_IdAndMember_Id(postId, memberId)
                .orElseThrow()
                .getId();
    }

    private void assertBothRejected(
            Long memberId,
            Long targetPostId,
            PostLikeException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                postLikeService.like(memberId, targetPostId)
        )
                .isInstanceOfSatisfying(
                        PostLikeException.class,
                        exception ->
                                assertThat(exception.getCode())
                                        .isEqualTo(expectedCode)
                );

        assertThatThrownBy(() ->
                postLikeService.unlike(memberId, targetPostId)
        )
                .isInstanceOfSatisfying(
                        PostLikeException.class,
                        exception ->
                                assertThat(exception.getCode())
                                        .isEqualTo(expectedCode)
                );
    }

    private List<PostLikeResult> runTogether(
            Callable<PostLikeResult> firstAction,
            Callable<PostLikeResult> secondAction
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // CountDownLatch: 두 작업이 준비된 뒤 함께 출발하도록 만드는 장치
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<PostLikeResult> first = executor.submit(() -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "첫 번째 작업의 시작 대기 시간을 초과했습니다."
                    );
                }

                return firstAction.call();
            });

            Future<PostLikeResult> second = executor.submit(() -> {
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
}