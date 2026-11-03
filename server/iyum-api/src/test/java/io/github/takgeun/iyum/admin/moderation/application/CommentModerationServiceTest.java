package io.github.takgeun.iyum.admin.moderation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.infrastructure.ContentModerationLogRepository;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentModerationServiceTest {

    private static final Long ADMIN_ID = 7L;
    private static final Long POST_ID = 123L;
    private static final Long COMMENT_ID = 456L;
    private static final long CREDENTIAL_VERSION = 3L;

    private static final Instant POST_HIDDEN_AT =
            Instant.parse("2026-10-29T09:00:00Z");

    private static final Instant COMMENT_HIDDEN_AT =
            Instant.parse("2026-10-29T10:00:00Z");

    private static final Instant DELETED_AT =
            Instant.parse("2026-10-29T11:00:00Z");

    @Mock
    MemberRepository memberRepository;

    @Mock
    PostRepository postRepository;

    @Mock
    CommentRepository commentRepository;

    @Mock
    ContentModerationLogRepository logRepository;

    @InjectMocks
    CommentModerationService service;

    @ParameterizedTest
    @CsvSource({
            "HIDE, false",
            "HIDE, true",
            "RESTORE, false",
            "RESTORE, true"
    })
    void 부모의_숨김_여부와_관계없이_댓글을_처리한다(
            ModerationAction action,
            boolean parentHidden
    ) {
        givenAdmin();
        Post post = givenPost(parentHidden);
        Comment comment = givenComment(post);

        if (action == ModerationAction.RESTORE) {
            comment.hide(COMMENT_HIDDEN_AT);
        }

        CommentModerationResult result = execute(
                action, ADMIN_ID, CREDENTIAL_VERSION,
                POST_ID, COMMENT_ID, "  댓글 운영 정책 검토  "
        );

        boolean expectedHidden = action == ModerationAction.HIDE;

        assertThat(result.postId()).isEqualTo(POST_ID);
        assertThat(result.commentId()).isEqualTo(COMMENT_ID);
        assertThat(result.changed()).isTrue();
        assertThat(result.hidden()).isEqualTo(expectedHidden);
        assertThat(result.hiddenAt()).isEqualTo(comment.getHiddenAt());

        if (expectedHidden) {
            assertThat(comment.getHiddenAt()).isNotNull();
        } else {
            assertThat(comment.getHiddenAt()).isNull();
        }

        assertThat(comment.getDeletedAt()).isNull();
        assertThat(comment.getContent()).isEqualTo("테스트 댓글");

        // 댓글을 처리해도 부모 게시글 상태는 유지한다.
        assertThat(post.getHiddenAt())
                .isEqualTo(parentHidden ? POST_HIDDEN_AT : null);
        assertThat(post.getDeletedAt()).isNull();
        assertThat(post.getContent()).isEqualTo("테스트 본문");

        ArgumentCaptor<ContentModerationLog> captor =
                ArgumentCaptor.forClass(ContentModerationLog.class);

        var order = inOrder(
                memberRepository,
                postRepository,
                commentRepository,
                logRepository
        );

        order.verify(memberRepository).findByIdForUpdate(ADMIN_ID);
        order.verify(postRepository)
                .findPostByIdForModerationForUpdate(POST_ID);
        order.verify(commentRepository)
                .findCommentByIdForModerationForUpdate(POST_ID, COMMENT_ID);
        order.verify(logRepository).save(captor.capture());

        ContentModerationLog log = captor.getValue();

        assertThat(log.getActorMemberId()).isEqualTo(ADMIN_ID);
        assertThat(log.getPostId()).isNull();
        assertThat(log.getCommentId()).isEqualTo(COMMENT_ID);
        assertThat(log.getAction()).isEqualTo(action);
        assertThat(log.getReason()).isEqualTo("댓글 운영 정책 검토");
        assertThat(log.getCreatedAt()).isNotNull();

        if (expectedHidden) {
            assertThat(log.getCreatedAt())
                    .isEqualTo(comment.getHiddenAt());
        }

        verifyNoMoreInteractions(
                memberRepository,
                postRepository,
                commentRepository,
                logRepository
        );
    }

    @ParameterizedTest
    @CsvSource({
            "HIDE, false",
            "HIDE, true",
            "RESTORE, false",
            "RESTORE, true"
    })
    void 중복_요청은_상태와_이력을_변경하지_않는다(
            ModerationAction action,
            boolean parentHidden
    ) {
        givenAdmin();
        Post post = givenPost(parentHidden);
        Comment comment = givenComment(post);

        if (action == ModerationAction.HIDE) {
            comment.hide(COMMENT_HIDDEN_AT);
        }

        Instant previousHiddenAt = comment.getHiddenAt();

        CommentModerationResult result = execute(
                action, ADMIN_ID, CREDENTIAL_VERSION,
                POST_ID, COMMENT_ID, "중복 요청 사유"
        );

        assertThat(result.changed()).isFalse();
        assertThat(result.hidden())
                .isEqualTo(action == ModerationAction.HIDE);
        assertThat(result.hiddenAt()).isEqualTo(previousHiddenAt);
        assertThat(comment.getHiddenAt()).isEqualTo(previousHiddenAt);
        assertThat(post.getHiddenAt())
                .isEqualTo(parentHidden ? POST_HIDDEN_AT : null);

        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 유효하지_않은_관리자_ID는_조회_전에_거절한다(
            Long actorId
    ) {
        assertBothFail(
                actorId, CREDENTIAL_VERSION,
                POST_ID, COMMENT_ID, "처리 사유",
                ContentModerationException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(
                memberRepository, postRepository,
                commentRepository, logRepository
        );
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 음수_인증_버전은_조회_전에_거절한다(
            ModerationAction action
    ) {
        assertFailure(
                action, ADMIN_ID, -1L,
                POST_ID, COMMENT_ID, "처리 사유",
                ContentModerationException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(
                memberRepository, postRepository,
                commentRepository, logRepository
        );
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 존재하지_않는_회원은_거절한다(
            ModerationAction action
    ) {
        when(memberRepository.findByIdForUpdate(ADMIN_ID))
                .thenReturn(Optional.empty());

        assertFailure(
                action, ADMIN_ID, CREDENTIAL_VERSION,
                POST_ID, COMMENT_ID, "처리 사유",
                ContentModerationException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(
                postRepository, commentRepository, logRepository
        );
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, ADMIN, 3, AUTHENTICATION_REQUIRED",
            "DELETED, ADMIN, 3, AUTHENTICATION_REQUIRED",
            "ACTIVE, ADMIN, 4, AUTHENTICATION_REQUIRED",
            "ACTIVE, USER, 3, ADMIN_REQUIRED"
    })
    void 관리자_자격이_유효하지_않으면_대상_조회_전에_거절한다(
            MemberStatus status,
            MemberRole role,
            long databaseVersion,
            ContentModerationException.Code expectedCode
    ) {
        Member admin = givenAdmin();

        ReflectionTestUtils.setField(admin, "status", status);
        ReflectionTestUtils.setField(admin, "role", role);
        ReflectionTestUtils.setField(
                admin, "credentialVersion", databaseVersion
        );

        assertBothFail(
                ADMIN_ID, CREDENTIAL_VERSION,
                POST_ID, COMMENT_ID, "처리 사유",
                expectedCode
        );

        verifyNoInteractions(
                postRepository, commentRepository, logRepository
        );
    }

    static Stream<String> invalidReasons() {
        return Stream.of(
                (String) null,
                "",
                "   ",
                "\t\n",
                "가".repeat(501)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidReasons")
    void 사유가_잘못되면_대상_조회_전에_거절한다(String reason) {
        givenAdmin();

        assertBothFail(
                ADMIN_ID, CREDENTIAL_VERSION,
                POST_ID, COMMENT_ID, reason,
                ContentModerationException.Code.INVALID_REASON
        );

        verifyNoInteractions(
                postRepository, commentRepository, logRepository
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 게시글_ID가_잘못되면_게시글_조회_전에_거절한다(
            Long postId
    ) {
        givenAdmin();

        assertBothFail(
                ADMIN_ID, CREDENTIAL_VERSION,
                postId, COMMENT_ID, "처리 사유",
                ContentModerationException.Code.POST_NOT_FOUND
        );

        verifyNoInteractions(
                postRepository, commentRepository, logRepository
        );
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 부모_게시글이_없으면_댓글_조회_전에_거절한다(
            ModerationAction action
    ) {
        givenAdmin();

        when(postRepository.findPostByIdForModerationForUpdate(POST_ID))
                .thenReturn(Optional.empty());

        assertFailure(
                action, ADMIN_ID, CREDENTIAL_VERSION,
                POST_ID, COMMENT_ID, "처리 사유",
                ContentModerationException.Code.POST_NOT_FOUND
        );

        verifyNoInteractions(commentRepository, logRepository);
    }

    @ParameterizedTest
    @CsvSource({
            "HIDE, false",
            "HIDE, true",
            "RESTORE, false",
            "RESTORE, true"
    })
    void 부모_게시글이_삭제되었으면_댓글_조회_전에_거절한다(
            ModerationAction action,
            boolean parentHidden
    ) {
        givenAdmin();
        Post post = givenPost(parentHidden);
        post.delete(DELETED_AT);

        assertFailure(
                action, ADMIN_ID, CREDENTIAL_VERSION,
                POST_ID, COMMENT_ID, "처리 사유",
                ContentModerationException.Code.POST_DELETED
        );

        assertThat(post.getDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(post.getHiddenAt())
                .isEqualTo(parentHidden ? POST_HIDDEN_AT : null);

        verifyNoInteractions(commentRepository, logRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 댓글_ID가_잘못되면_댓글_조회_전에_거절한다(
            Long commentId
    ) {
        givenAdmin();
        givenPost(false);

        assertBothFail(
                ADMIN_ID, CREDENTIAL_VERSION,
                POST_ID, commentId, "처리 사유",
                ContentModerationException.Code.COMMENT_NOT_FOUND
        );

        verifyNoInteractions(commentRepository, logRepository);
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 해당_게시글에서_댓글을_찾지_못하면_거절한다(
            ModerationAction action
    ) {
        givenAdmin();
        givenPost(false);

        when(commentRepository.findCommentByIdForModerationForUpdate(
                POST_ID, COMMENT_ID
        )).thenReturn(Optional.empty());

        assertFailure(
                action, ADMIN_ID, CREDENTIAL_VERSION,
                POST_ID, COMMENT_ID, "처리 사유",
                ContentModerationException.Code.COMMENT_NOT_FOUND
        );

        verify(commentRepository)
                .findCommentByIdForModerationForUpdate(POST_ID, COMMENT_ID);

        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @CsvSource({
            "HIDE, false",
            "HIDE, true",
            "RESTORE, false",
            "RESTORE, true"
    })
    void 삭제된_댓글은_숨김_여부와_관계없이_거절한다(
            ModerationAction action,
            boolean commentHidden
    ) {
        givenAdmin();
        Post post = givenPost(false);
        Comment comment = givenComment(post);

        if (commentHidden) {
            comment.hide(COMMENT_HIDDEN_AT);
        }
        comment.delete(DELETED_AT);

        assertFailure(
                action, ADMIN_ID, CREDENTIAL_VERSION,
                POST_ID, COMMENT_ID, "처리 사유",
                ContentModerationException.Code.COMMENT_DELETED
        );

        assertThat(comment.getDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(comment.getHiddenAt())
                .isEqualTo(commentHidden ? COMMENT_HIDDEN_AT : null);
        assertThat(post.getHiddenAt()).isNull();

        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 이력_저장_실패를_성공으로_처리하지_않는다(
            ModerationAction action
    ) {
        givenAdmin();
        Post post = givenPost(false);
        Comment comment = givenComment(post);

        if (action == ModerationAction.RESTORE) {
            comment.hide(COMMENT_HIDDEN_AT);
        }

        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("테스트 저장 실패");

        when(logRepository.save(any(ContentModerationLog.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> execute(
                action, ADMIN_ID, CREDENTIAL_VERSION,
                POST_ID, COMMENT_ID, "처리 사유"
        )).isSameAs(failure);
    }

    private Member givenAdmin() {
        Member admin = createMember(ADMIN_ID, "관리테스트회원");

        ReflectionTestUtils.setField(admin, "role", MemberRole.ADMIN);
        ReflectionTestUtils.setField(
                admin, "credentialVersion", CREDENTIAL_VERSION
        );

        when(memberRepository.findByIdForUpdate(ADMIN_ID))
                .thenReturn(Optional.of(admin));

        return admin;
    }

    private Post givenPost(boolean hidden) {
        Member author = createMember(99L, "게시글작성회원");

        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "테스트 제목",
                "테스트 본문"
        );
        ReflectionTestUtils.setField(post, "id", POST_ID);

        if (hidden) {
            post.hide(POST_HIDDEN_AT);
        }

        when(postRepository.findPostByIdForModerationForUpdate(POST_ID))
                .thenReturn(Optional.of(post));

        return post;
    }

    private Comment givenComment(Post post) {
        Member author = createMember(100L, "댓글작성회원");

        Comment comment = Comment.create(
                post,
                author,
                "테스트 댓글"
        );
        ReflectionTestUtils.setField(comment, "id", COMMENT_ID);

        when(commentRepository.findCommentByIdForModerationForUpdate(
                POST_ID, COMMENT_ID
        )).thenReturn(Optional.of(comment));

        return comment;
    }

    private Member createMember(Long id, String nickname) {
        Member member = Member.createPending(
                "member-" + id + "@example.com",
                "test-only-password-hash",
                nickname
        );
        member.activeAfterEmailVerification();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private CommentModerationResult execute(
            ModerationAction action,
            Long actorId,
            long credentialVersion,
            Long postId,
            Long commentId,
            String reason
    ) {
        return switch (action) {
            case HIDE -> service.hide(
                    actorId, credentialVersion, postId, commentId, reason
            );
            case RESTORE -> service.restore(
                    actorId, credentialVersion, postId, commentId, reason
            );
        };
    }

    private void assertBothFail(
            Long actorId,
            long credentialVersion,
            Long postId,
            Long commentId,
            String reason,
            ContentModerationException.Code expectedCode
    ) {
        for (ModerationAction action : ModerationAction.values()) {
            assertFailure(
                    action, actorId, credentialVersion,
                    postId, commentId, reason, expectedCode
            );
        }
    }

    private void assertFailure(
            ModerationAction action,
            Long actorId,
            long credentialVersion,
            Long postId,
            Long commentId,
            String reason,
            ContentModerationException.Code expectedCode
    ) {
        assertThatThrownBy(() -> execute(
                action, actorId, credentialVersion,
                postId, commentId, reason
        ))
                .isInstanceOfSatisfying(
                        ContentModerationException.class,
                        exception ->
                                assertThat(exception.getCode())
                                        .isEqualTo(expectedCode)
                );
    }
}