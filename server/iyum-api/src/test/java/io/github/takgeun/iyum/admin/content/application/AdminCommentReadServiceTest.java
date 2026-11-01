package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.admin.content.infrastructure.AdminCommentReadRepository;
import io.github.takgeun.iyum.admin.content.infrastructure.AdminPostReadRepository;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCommentReadServiceTest {

    private static final Long ADMIN_ID = 7L;
    private static final long CREDENTIAL_VERSION = 3L;
    private static final Long POST_ID = 123L;
    private static final Long COMMENT_ID = 456L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-30T10:00:00Z");

    private static final Instant UPDATED_AT =
            Instant.parse("2026-10-30T10:30:00Z");

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-30T11:00:00Z");

    private static final Instant DELETED_AT =
            Instant.parse("2026-10-30T12:00:00Z");

    private static final String ORIGINAL_CONTENT =
            "첫 번째 줄\n\n두 번째 줄\n" + "😀".repeat(201);

    @Mock
    MemberRepository memberRepository;

    @Mock
    AdminPostReadRepository postReadRepository;

    @Mock
    AdminCommentReadRepository commentReadRepository;

    @InjectMocks
    AdminCommentReadService service;

    enum ContentState {
        VISIBLE,
        HIDDEN,
        DELETED,
        HIDDEN_AND_DELETED;

        boolean hidden() {
            return this == HIDDEN || this == HIDDEN_AND_DELETED;
        }

        boolean deleted() {
            return this == DELETED || this == HIDDEN_AND_DELETED;
        }
    }

    static Stream<Arguments> contentStates() {
        return Arrays.stream(ContentState.values())
                .flatMap(postState ->
                        Arrays.stream(ContentState.values())
                                .map(commentState ->
                                        Arguments.of(postState, commentState)
                                )
                );
    }

    @ParameterizedTest
    @MethodSource("contentStates")
    void 부모와_댓글의_상태를_구분하고_원문과_페이지_정보를_유지한다(
            ContentState postState,
            ContentState commentState
    ) {
        givenAdmin();

        Member author = activeAuthor();
        Post post = createPost(author);
        Comment comment = createComment(post, author);

        applyState(post, postState);
        applyState(comment, commentState);

        AdminCommentReadQuery query = new AdminCommentReadQuery(
                AdminCommentStatus.ALL,
                "두 번째",
                1,
                2
        );

        PageRequest pageable = PageRequest.of(
                1,
                2,
                Sort.by(
                        Sort.Order.asc("createdAt"),
                        Sort.Order.asc("id")
                )
        );

        when(postReadRepository.findPostById(POST_ID))
                .thenReturn(Optional.of(post));

        when(commentReadRepository.findComments(POST_ID, query))
                .thenReturn(new PageImpl<>(
                        List.of(comment),
                        pageable,
                        3
                ));

        AdminCommentListResult result = service.getComments(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                query
        );

        AdminPostStatus expectedPostStatus = switch (postState) {
            case VISIBLE -> AdminPostStatus.VISIBLE;
            case HIDDEN -> AdminPostStatus.HIDDEN;
            case DELETED, HIDDEN_AND_DELETED -> AdminPostStatus.DELETED;
        };

        AdminCommentStatus expectedCommentStatus =
                switch (commentState) {
                    case VISIBLE -> AdminCommentStatus.VISIBLE;
                    case HIDDEN -> AdminCommentStatus.HIDDEN;
                    case DELETED, HIDDEN_AND_DELETED ->
                            AdminCommentStatus.DELETED;
                };

        assertThat(result.postId()).isEqualTo(POST_ID);
        assertThat(result.postStatus()).isEqualTo(expectedPostStatus);

        Page<AdminCommentItemResult> comments = result.comments();

        assertThat(comments.getNumber()).isEqualTo(1);
        assertThat(comments.getSize()).isEqualTo(2);
        assertThat(comments.getTotalElements()).isEqualTo(3);
        assertThat(comments.getTotalPages()).isEqualTo(2);
        assertThat(comments.getSort()).isEqualTo(pageable.getSort());
        assertThat(comments.hasNext()).isFalse();
        assertThat(comments.hasPrevious()).isTrue();
        assertThat(comments.getContent()).hasSize(1);

        AdminCommentItemResult item = comments.getContent().getFirst();

        assertThat(item.commentId()).isEqualTo(COMMENT_ID);
        assertThat(item.content()).isEqualTo(ORIGINAL_CONTENT);
        assertThat(item.status()).isEqualTo(expectedCommentStatus);
        assertThat(item.createdAt()).isEqualTo(CREATED_AT);
        assertThat(item.updatedAt()).isEqualTo(UPDATED_AT);

        assertThat(item.hiddenAt())
                .isEqualTo(commentState.hidden() ? HIDDEN_AT : null);
        assertThat(item.deletedAt())
                .isEqualTo(commentState.deleted() ? DELETED_AT : null);

        assertThat(item.author()).isEqualTo(
                new AdminCommentAuthorResult(
                        8L,
                        "댓글조회검증회원",
                        false
                )
        );

        // 관리자 검증 → 부모 확인 → 댓글 조회 순서여야 한다.
        var order = inOrder(
                memberRepository,
                postReadRepository,
                commentReadRepository
        );

        order.verify(memberRepository).findById(ADMIN_ID);
        order.verify(postReadRepository).findPostById(POST_ID);
        order.verify(commentReadRepository).findComments(POST_ID, query);

        verifyNoMoreInteractions(
                memberRepository,
                postReadRepository,
                commentReadRepository
        );
    }

    @Test
    void 댓글이_없어도_부모_게시글_상태를_반환한다() {
        givenAdmin();

        Post post = createPost(activeAuthor());
        post.hide(HIDDEN_AT);
        post.delete(DELETED_AT);

        AdminCommentReadQuery query = defaultQuery();

        when(postReadRepository.findPostById(POST_ID))
                .thenReturn(Optional.of(post));

        when(commentReadRepository.findComments(POST_ID, query))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        AdminCommentListResult result = service.getComments(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                query
        );

        assertThat(result.postId()).isEqualTo(POST_ID);
        assertThat(result.postStatus()).isEqualTo(AdminPostStatus.DELETED);
        assertThat(result.comments().getContent()).isEmpty();
        assertThat(result.comments().getTotalElements()).isZero();
        assertThat(result.comments().getSize()).isEqualTo(20);
    }

    @Test
    void 탈퇴한_댓글_작성자는_탈퇴한_회원으로_표시한다() {
        givenAdmin();

        Member author = activeAuthor();
        Post post = createPost(author);
        Comment comment = createComment(post, author);

        author.withdraw(DELETED_AT);

        AdminCommentReadQuery query = defaultQuery();

        when(postReadRepository.findPostById(POST_ID))
                .thenReturn(Optional.of(post));

        when(commentReadRepository.findComments(POST_ID, query))
                .thenReturn(new PageImpl<>(
                        List.of(comment),
                        PageRequest.of(0, 20),
                        1
                ));

        AdminCommentListResult result = service.getComments(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                query
        );

        AdminCommentItemResult item =
                result.comments().getContent().getFirst();

        assertThat(item.content()).isEqualTo(ORIGINAL_CONTENT);
        assertThat(item.author()).isEqualTo(
                new AdminCommentAuthorResult(
                        8L,
                        "탈퇴한 회원",
                        true
                )
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 회원_ID가_유효하지_않으면_조회하지_않는다(
            Long memberId
    ) {
        assertAccessRejected(
                memberId,
                CREDENTIAL_VERSION,
                AdminContentReadException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 인증_버전이_음수이면_조회하지_않는다() {
        assertAccessRejected(
                ADMIN_ID,
                -1L,
                AdminContentReadException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 회원이_없으면_인증_실패다() {
        when(memberRepository.findById(ADMIN_ID))
                .thenReturn(Optional.empty());

        assertAccessRejected(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                AdminContentReadException.Code.AUTHENTICATION_REQUIRED
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = {"PENDING", "DELETED"}
    )
    void 활성_회원이_아니면_인증_실패다(
            MemberStatus status
    ) {
        Member actor = createMember(
                ADMIN_ID,
                status,
                MemberRole.ADMIN,
                CREDENTIAL_VERSION
        );

        when(memberRepository.findById(ADMIN_ID))
                .thenReturn(Optional.of(actor));

        assertAccessRejected(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                AdminContentReadException.Code.AUTHENTICATION_REQUIRED
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {2L, 4L})
    void 인증_버전이_다르면_인증_실패다(
            long requestedVersion
    ) {
        givenAdmin();

        assertAccessRejected(
                ADMIN_ID,
                requestedVersion,
                AdminContentReadException.Code.AUTHENTICATION_REQUIRED
        );
    }

    @Test
    void 유효한_일반_회원은_관리자_권한_부족이다() {
        Member actor = createMember(
                ADMIN_ID,
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREDENTIAL_VERSION
        );

        when(memberRepository.findById(ADMIN_ID))
                .thenReturn(Optional.of(actor));

        assertAccessRejected(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                AdminContentReadException.Code.ADMIN_REQUIRED
        );
    }

    @Test
    void 일반_회원이라도_인증_버전이_다르면_인증_실패를_우선한다() {
        Member actor = createMember(
                ADMIN_ID,
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREDENTIAL_VERSION
        );

        when(memberRepository.findById(ADMIN_ID))
                .thenReturn(Optional.of(actor));

        assertAccessRejected(
                ADMIN_ID,
                CREDENTIAL_VERSION - 1,
                AdminContentReadException.Code.AUTHENTICATION_REQUIRED
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 부모_ID가_유효하지_않으면_콘텐츠_저장소를_호출하지_않는다(
            Long postId
    ) {
        givenAdmin();

        assertThatThrownBy(() ->
                service.getComments(
                        ADMIN_ID,
                        CREDENTIAL_VERSION,
                        postId,
                        defaultQuery()
                )
        ).isInstanceOfSatisfying(
                AdminContentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminContentReadException.Code.POST_NOT_FOUND
                        )
        );

        verify(memberRepository).findById(ADMIN_ID);
        verifyNoInteractions(postReadRepository, commentReadRepository);
    }

    @Test
    void 부모가_없으면_댓글_저장소를_호출하지_않는다() {
        givenAdmin();

        when(postReadRepository.findPostById(POST_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.getComments(
                        ADMIN_ID,
                        CREDENTIAL_VERSION,
                        POST_ID,
                        defaultQuery()
                )
        ).isInstanceOfSatisfying(
                AdminContentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminContentReadException.Code.POST_NOT_FOUND
                        )
        );

        var order = inOrder(memberRepository, postReadRepository);
        order.verify(memberRepository).findById(ADMIN_ID);
        order.verify(postReadRepository).findPostById(POST_ID);

        verifyNoInteractions(commentReadRepository);
    }

    private void assertAccessRejected(
            Long memberId,
            long credentialVersion,
            AdminContentReadException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                service.getComments(
                        memberId,
                        credentialVersion,
                        POST_ID,
                        defaultQuery()
                )
        ).isInstanceOfSatisfying(
                AdminContentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );

        verifyNoInteractions(postReadRepository, commentReadRepository);
    }

    private void givenAdmin() {
        Member admin = createMember(
                ADMIN_ID,
                MemberStatus.ACTIVE,
                MemberRole.ADMIN,
                CREDENTIAL_VERSION
        );

        when(memberRepository.findById(ADMIN_ID))
                .thenReturn(Optional.of(admin));
    }

    private Member activeAuthor() {
        return createMember(
                8L,
                MemberStatus.ACTIVE,
                MemberRole.USER,
                0L
        );
    }

    private Member createMember(
            Long id,
            MemberStatus status,
            MemberRole role,
            long credentialVersion
    ) {
        Member member = Member.createPending(
                "member" + id + "@example.com",
                "test-only-password-hash",
                "댓글조회검증회원"
        );

        if (status != MemberStatus.PENDING) {
            member.activeAfterEmailVerification();
        }

        if (status == MemberStatus.DELETED) {
            member.withdraw(DELETED_AT);
        }

        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "role", role);
        ReflectionTestUtils.setField(
                member,
                "credentialVersion",
                credentialVersion
        );

        return member;
    }

    private Post createPost(Member author) {
        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "부모 게시글",
                "부모 게시글 내용"
        );

        ReflectionTestUtils.setField(post, "id", POST_ID);

        return post;
    }

    private Comment createComment(
            Post post,
            Member author
    ) {
        Comment comment = Comment.create(
                post,
                author,
                ORIGINAL_CONTENT
        );

        ReflectionTestUtils.setField(comment, "id", COMMENT_ID);
        ReflectionTestUtils.setField(
                comment,
                "createdAt",
                CREATED_AT
        );
        ReflectionTestUtils.setField(
                comment,
                "updatedAt",
                UPDATED_AT
        );

        return comment;
    }

    private void applyState(Post post, ContentState state) {
        if (state.hidden()) {
            post.hide(HIDDEN_AT);
        }

        if (state.deleted()) {
            post.delete(DELETED_AT);
        }
    }

    private void applyState(Comment comment, ContentState state) {
        if (state.hidden()) {
            comment.hide(HIDDEN_AT);
        }

        if (state.deleted()) {
            comment.delete(DELETED_AT);
        }
    }

    private AdminCommentReadQuery defaultQuery() {
        return new AdminCommentReadQuery(
                AdminCommentStatus.ALL,
                null,
                0,
                20
        );
    }
}