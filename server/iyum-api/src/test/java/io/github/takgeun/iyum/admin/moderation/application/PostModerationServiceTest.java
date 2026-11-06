package io.github.takgeun.iyum.admin.moderation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.admin.moderation.infrastructure.ContentModerationLogRepository;
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
class PostModerationServiceTest {

    private static final Long ADMIN_ID = 7L;
    private static final Long POST_ID = 123L;
    private static final long CREDENTIAL_VERSION = 3L;

    private static final Instant ORIGINAL_HIDDEN_AT =
            Instant.parse("2026-10-29T10:00:00Z");

    private static final Instant DELETED_AT =
            Instant.parse("2026-10-29T11:00:00Z");

    @Mock
    MemberRepository memberRepository;

    @Mock
    PostRepository postRepository;

    @Mock
    ContentModerationLogRepository logRepository;

    @InjectMocks
    PostModerationService service;

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 상태를_변경하고_정규화한_사유로_이력을_저장한다(
            ModerationAction action
    ) {
        givenAdmin();
        Post post = givenPost();

        if (action == ModerationAction.RESTORE) {
            post.hide(ORIGINAL_HIDDEN_AT);
        }

        PostModerationResult result = execute(
                action,
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                "  운영 정책 검토  "
        );

        boolean expectedHidden = action == ModerationAction.HIDE;

        assertThat(result.postId()).isEqualTo(POST_ID);
        assertThat(result.changed()).isTrue();
        assertThat(result.hidden()).isEqualTo(expectedHidden);
        assertThat(result.hiddenAt()).isEqualTo(post.getHiddenAt());

        if (expectedHidden) {
            assertThat(post.getHiddenAt()).isNotNull();
        } else {
            assertThat(post.getHiddenAt()).isNull();
        }

        assertThat(post.getDeletedAt()).isNull();
        assertThat(post.getTitle()).isEqualTo("테스트 제목");
        assertThat(post.getContent()).isEqualTo("테스트 내용");

        ArgumentCaptor<ContentModerationLog> captor =
                ArgumentCaptor.forClass(ContentModerationLog.class);

        var order = inOrder(
                memberRepository,
                postRepository,
                logRepository
        );

        order.verify(memberRepository).findByIdForUpdate(ADMIN_ID);
        order.verify(postRepository)
                .findPostByIdForModerationForUpdate(POST_ID);
        order.verify(logRepository).save(captor.capture());

        ContentModerationLog log = captor.getValue();

        assertThat(log.getActorMemberId()).isEqualTo(ADMIN_ID);
        assertThat(log.getPostId()).isEqualTo(POST_ID);
        assertThat(log.getCommentId()).isNull();
        assertThat(log.getAction()).isEqualTo(action);
        assertThat(log.getReason()).isEqualTo("운영 정책 검토");
        assertThat(log.getCreatedAt()).isNotNull();

        if (expectedHidden) {
            assertThat(log.getCreatedAt())
                    .isEqualTo(post.getHiddenAt());
        }

        verifyNoMoreInteractions(
                memberRepository,
                postRepository,
                logRepository
        );
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 이미_요청한_상태이면_변경하거나_이력을_추가하지_않는다(
            ModerationAction action
    ) {
        givenAdmin();
        Post post = givenPost();

        if (action == ModerationAction.HIDE) {
            post.hide(ORIGINAL_HIDDEN_AT);
        }

        Instant previousHiddenAt = post.getHiddenAt();

        PostModerationResult result = execute(
                action,
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                "중복 요청 사유"
        );

        assertThat(result.postId()).isEqualTo(POST_ID);
        assertThat(result.changed()).isFalse();
        assertThat(result.hidden())
                .isEqualTo(action == ModerationAction.HIDE);
        assertThat(result.hiddenAt()).isEqualTo(previousHiddenAt);
        assertThat(post.getHiddenAt()).isEqualTo(previousHiddenAt);

        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 유효하지_않은_관리자_ID는_조회_전에_거절한다(
            Long actorId
    ) {
        for (ModerationAction action : ModerationAction.values()) {
            assertFailure(
                    action,
                    actorId,
                    CREDENTIAL_VERSION,
                    POST_ID,
                    "처리 사유",
                    ContentModerationException.Code.AUTHENTICATION_REQUIRED
            );
        }

        verifyNoInteractions(
                memberRepository,
                postRepository,
                logRepository
        );
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 음수_인증_버전은_조회_전에_거절한다(
            ModerationAction action
    ) {
        assertFailure(
                action,
                ADMIN_ID,
                -1L,
                POST_ID,
                "처리 사유",
                ContentModerationException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(
                memberRepository,
                postRepository,
                logRepository
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
                action,
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                "처리 사유",
                ContentModerationException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(postRepository, logRepository);
    }

    @ParameterizedTest
    @CsvSource({
            "HIDE, PENDING",
            "RESTORE, PENDING",
            "HIDE, DELETED",
            "RESTORE, DELETED"
    })
    void 활성_상태가_아닌_관리자는_거절한다(
            ModerationAction action,
            MemberStatus status
    ) {
        Member admin = givenAdmin();
        ReflectionTestUtils.setField(admin, "status", status);

        assertFailure(
                action,
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                "처리 사유",
                ContentModerationException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(postRepository, logRepository);
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 인증_버전이_달라진_관리자는_거절한다(
            ModerationAction action
    ) {
        Member admin = givenAdmin();

        ReflectionTestUtils.setField(
                admin,
                "credentialVersion",
                CREDENTIAL_VERSION + 1
        );

        assertFailure(
                action,
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                "처리 사유",
                ContentModerationException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(postRepository, logRepository);
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 일반_회원은_관리자_처리를_할_수_없다(
            ModerationAction action
    ) {
        Member admin = givenAdmin();
        ReflectionTestUtils.setField(admin, "role", MemberRole.USER);

        assertFailure(
                action,
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                "처리 사유",
                ContentModerationException.Code.ADMIN_REQUIRED
        );

        verifyNoInteractions(postRepository, logRepository);
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
    void 잘못된_사유는_게시글_조회_전에_거절한다(
            String reason
    ) {
        givenAdmin();

        for (ModerationAction action : ModerationAction.values()) {
            assertFailure(
                    action,
                    ADMIN_ID,
                    CREDENTIAL_VERSION,
                    POST_ID,
                    reason,
                    ContentModerationException.Code.INVALID_REASON
            );
        }

        verifyNoInteractions(postRepository, logRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 유효하지_않은_게시글_ID는_게시글_조회_전에_거절한다(
            Long postId
    ) {
        givenAdmin();

        for (ModerationAction action : ModerationAction.values()) {
            assertFailure(
                    action,
                    ADMIN_ID,
                    CREDENTIAL_VERSION,
                    postId,
                    "처리 사유",
                    ContentModerationException.Code.POST_NOT_FOUND
            );
        }

        verifyNoInteractions(postRepository, logRepository);
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 존재하지_않는_게시글은_거절한다(
            ModerationAction action
    ) {
        givenAdmin();

        when(postRepository.findPostByIdForModerationForUpdate(POST_ID))
                .thenReturn(Optional.empty());

        assertFailure(
                action,
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                "처리 사유",
                ContentModerationException.Code.POST_NOT_FOUND
        );

        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @CsvSource({
            "HIDE, false",
            "HIDE, true",
            "RESTORE, false",
            "RESTORE, true"
    })
    void 삭제된_게시글은_숨김_여부와_관계없이_거절한다(
            ModerationAction action,
            boolean hidden
    ) {
        givenAdmin();
        Post post = givenPost();

        if (hidden) {
            post.hide(ORIGINAL_HIDDEN_AT);
        }
        post.delete(DELETED_AT);

        assertFailure(
                action,
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                "처리 사유",
                ContentModerationException.Code.POST_DELETED
        );

        assertThat(post.getDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(post.getHiddenAt())
                .isEqualTo(hidden ? ORIGINAL_HIDDEN_AT : null);

        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 이력_저장_실패를_성공으로_처리하지_않는다(
            ModerationAction action
    ) {
        givenAdmin();
        Post post = givenPost();

        if (action == ModerationAction.RESTORE) {
            post.hide(ORIGINAL_HIDDEN_AT);
        }

        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("테스트 저장 실패");

        when(logRepository.save(any(ContentModerationLog.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> execute(
                action,
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                "처리 사유"
        )).isSameAs(failure);
    }

    @ParameterizedTest
    @CsvSource({
            "HIDE, SPAM_ADVERTISING, , 광고·홍보·스팸",
            "RESTORE, NO_VIOLATION, , 재검토 결과 위반 없음",
            "HIDE, OTHER, 개별 숨김 사유, 개별 숨김 사유",
            "RESTORE, OTHER, 개별 복원 사유, 개별 복원 사유"
    })
    void 유형과_상세_사유로_상태를_변경하고_이력을_저장한다(
            ModerationAction action,
            ModerationReasonType reasonType,
            String detail,
            String expectedReason
    ) {
        givenAdmin();
        Post post = givenPost();

        if (action == ModerationAction.RESTORE) {
            post.hide(ORIGINAL_HIDDEN_AT);
        }

        String inputDetail = detail == null
                ? null
                : "  " + detail + "  ";

        PostModerationResult result = executeTyped(
                action,
                reasonType,
                inputDetail
        );

        assertThat(result.changed()).isTrue();
        assertThat(result.hidden())
                .isEqualTo(action == ModerationAction.HIDE);
        assertThat(result.hiddenAt()).isEqualTo(post.getHiddenAt());

        ArgumentCaptor<ContentModerationLog> captor =
                ArgumentCaptor.forClass(ContentModerationLog.class);

        var order = inOrder(
                memberRepository,
                postRepository,
                logRepository
        );

        order.verify(memberRepository).findByIdForUpdate(ADMIN_ID);
        order.verify(postRepository)
                .findPostByIdForModerationForUpdate(POST_ID);
        order.verify(logRepository).save(captor.capture());

        ContentModerationLog log = captor.getValue();

        assertThat(log.getActorMemberId()).isEqualTo(ADMIN_ID);
        assertThat(log.getPostId()).isEqualTo(POST_ID);
        assertThat(log.getCommentId()).isNull();
        assertThat(log.getAction()).isEqualTo(action);
        assertThat(log.getReasonType()).isEqualTo(reasonType);
        assertThat(log.getReasonDetail()).isEqualTo(detail);
        assertThat(log.getReason()).isEqualTo(expectedReason);
        assertThat(log.getCreatedAt()).isNotNull();

        if (action == ModerationAction.HIDE) {
            assertThat(log.getCreatedAt()).isEqualTo(post.getHiddenAt());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "HIDE, , 상세 설명",
            "RESTORE, , 상세 설명",
            "HIDE, LEGACY, 상세 설명",
            "RESTORE, LEGACY, 상세 설명",
            "HIDE, NO_VIOLATION, 상세 설명",
            "RESTORE, SPAM_ADVERTISING, 상세 설명",
            "HIDE, OTHER, ",
            "RESTORE, OTHER, "
    })
    void 잘못된_사유는_게시글_조회와_이력_저장_전에_거부한다(
            ModerationAction action,
            ModerationReasonType reasonType,
            String detail
    ) {
        givenAdmin();

        assertThatThrownBy(() ->
                executeTyped(action, reasonType, detail)
        ).isInstanceOfSatisfying(
                ContentModerationException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                ContentModerationException.Code.INVALID_REASON
                        )
        );

        verify(memberRepository).findByIdForUpdate(ADMIN_ID);
        verifyNoInteractions(postRepository, logRepository);
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 유형을_사용한_중복_요청은_이력을_추가하지_않는다(
            ModerationAction action
    ) {
        givenAdmin();
        Post post = givenPost();

        if (action == ModerationAction.HIDE) {
            post.hide(ORIGINAL_HIDDEN_AT);
        }

        Instant previousHiddenAt = post.getHiddenAt();

        PostModerationResult result = executeTyped(
                action,
                ModerationReasonType.OTHER,
                "중복 요청 확인"
        );

        assertThat(result.changed()).isFalse();
        assertThat(post.getHiddenAt()).isEqualTo(previousHiddenAt);
        assertThat(result.hiddenAt()).isEqualTo(previousHiddenAt);

        verifyNoInteractions(logRepository);
    }

    private Member givenAdmin() {
        Member admin = Member.createPending(
                "moderation-admin@example.com",
                "test-only-password-hash",
                "테스트회원"
        );
        admin.activeAfterEmailVerification();

        ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
        ReflectionTestUtils.setField(admin, "role", MemberRole.ADMIN);
        ReflectionTestUtils.setField(
                admin,
                "credentialVersion",
                CREDENTIAL_VERSION
        );

        when(memberRepository.findByIdForUpdate(ADMIN_ID))
                .thenReturn(Optional.of(admin));

        return admin;
    }

    private Post givenPost() {
        // 관리자와 다른 작성자의 게시글로 구성한다.
        Member author = Member.createPending(
                "post-author@example.com",
                "test-only-password-hash",
                "게시글작성회원"
        );
        author.activeAfterEmailVerification();
        ReflectionTestUtils.setField(author, "id", 99L);

        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "테스트 제목",
                "테스트 내용"
        );

        ReflectionTestUtils.setField(post, "id", POST_ID);

        when(postRepository.findPostByIdForModerationForUpdate(POST_ID))
                .thenReturn(Optional.of(post));

        return post;
    }

    private PostModerationResult execute(
            ModerationAction action,
            Long actorId,
            long credentialVersion,
            Long postId,
            String reason
    ) {
        return switch (action) {
            case HIDE -> service.hide(
                    actorId,
                    credentialVersion,
                    postId,
                    reason
            );
            case RESTORE -> service.restore(
                    actorId,
                    credentialVersion,
                    postId,
                    reason
            );
        };
    }

    private void assertFailure(
            ModerationAction action,
            Long actorId,
            long credentialVersion,
            Long postId,
            String reason,
            ContentModerationException.Code expectedCode
    ) {
        assertThatThrownBy(() -> execute(
                action,
                actorId,
                credentialVersion,
                postId,
                reason
        ))
                .isInstanceOfSatisfying(
                        ContentModerationException.class,
                        exception ->
                                assertThat(exception.getCode())
                                        .isEqualTo(expectedCode)
                );
    }

    private PostModerationResult executeTyped(
            ModerationAction action,
            ModerationReasonType reasonType,
            String detail
    ) {
        return switch (action) {
            case HIDE -> service.hide(
                    ADMIN_ID,
                    CREDENTIAL_VERSION,
                    POST_ID,
                    reasonType,
                    detail
            );
            case RESTORE -> service.restore(
                    ADMIN_ID,
                    CREDENTIAL_VERSION,
                    POST_ID,
                    reasonType,
                    detail
            );
        };
    }
}