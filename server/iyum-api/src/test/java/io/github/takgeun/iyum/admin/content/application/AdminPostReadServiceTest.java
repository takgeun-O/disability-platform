package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.admin.content.infrastructure.AdminPostReadRepository;
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
import org.junit.jupiter.params.provider.EnumSource;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPostReadServiceTest {

    private static final Long ADMIN_ID = 7L;
    private static final long CREDENTIAL_VERSION = 3L;
    private static final Long POST_ID = 123L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-30T10:00:00Z");

    private static final Instant UPDATED_AT =
            Instant.parse("2026-10-30T10:30:00Z");

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-30T11:00:00Z");

    private static final Instant DELETED_AT =
            Instant.parse("2026-10-30T12:00:00Z");

    @Mock
    MemberRepository memberRepository;

    @Mock
    AdminPostReadRepository postReadRepository;

    @InjectMocks
    AdminPostReadService service;

    @Test
    void 관리자는_목록을_조회하고_페이지_정보를_유지한다() {
        givenAdmin();

        AdminPostReadQuery query = new AdminPostReadQuery(
                AdminPostStatus.HIDDEN,
                "보청기",
                1,
                2
        );

        Member author = createMember(
                8L,
                MemberStatus.ACTIVE,
                MemberRole.USER,
                0L
        );

        Post post = createPost(
                author,
                "보청기 후기",
                "첫 번째 줄\n\n두 번째 줄"
        );
        post.hide(HIDDEN_AT);

        PageRequest pageable = PageRequest.of(
                1,
                2,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        when(postReadRepository.findPosts(query))
                .thenReturn(new PageImpl<>(
                        List.of(post),
                        pageable,
                        3
                ));

        Page<AdminPostItemResult> result = service.getPosts(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                query
        );

        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getSort()).isEqualTo(pageable.getSort());
        assertThat(result.hasNext()).isFalse();
        assertThat(result.getContent()).hasSize(1);

        AdminPostItemResult item = result.getContent().getFirst();

        assertThat(item.postId()).isEqualTo(POST_ID);
        assertThat(item.type()).isEqualTo(PostType.GENERAL);
        assertThat(item.topic()).isEqualTo(PostTopic.DAILY);
        assertThat(item.title()).isEqualTo("보청기 후기");
        assertThat(item.contentPreview())
                .isEqualTo("첫 번째 줄 두 번째 줄");

        assertThat(item.status()).isEqualTo(AdminPostStatus.HIDDEN);
        assertThat(item.createdAt()).isEqualTo(CREATED_AT);
        assertThat(item.updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(item.hiddenAt()).isEqualTo(HIDDEN_AT);
        assertThat(item.deletedAt()).isNull();

        assertThat(item.author().memberId()).isEqualTo(8L);
        assertThat(item.author().nickname()).isEqualTo("조회검증회원");
        assertThat(item.author().withdrawn()).isFalse();

        // 게시글 조회 전에 관리자 검증이 이루어졌는지도 확인한다.
        var order = inOrder(memberRepository, postReadRepository);
        order.verify(memberRepository).findById(ADMIN_ID);
        order.verify(postReadRepository).findPosts(query);

        verifyNoMoreInteractions(memberRepository, postReadRepository);
    }

    @Test
    void 목록의_본문_미리보기는_코드포인트_200자로_제한한다() {
        givenAdmin();

        AdminPostReadQuery query = defaultQuery();
        Post post = createPost(
                activeAuthor(),
                "긴 본문",
                "😀".repeat(201)
        );

        when(postReadRepository.findPosts(query))
                .thenReturn(new PageImpl<>(
                        List.of(post),
                        PageRequest.of(0, 20),
                        1
                ));

        Page<AdminPostItemResult> result = service.getPosts(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                query
        );

        String preview = result.getContent()
                .getFirst()
                .contentPreview();

        assertThat(preview).isEqualTo("😀".repeat(200));
        assertThat(preview.codePointCount(0, preview.length()))
                .isEqualTo(200);
    }

    @Test
    void 조회_결과가_없으면_빈_페이지를_반환한다() {
        givenAdmin();

        AdminPostReadQuery query = defaultQuery();

        when(postReadRepository.findPosts(query))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        Page<AdminPostItemResult> result = service.getPosts(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                query
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(20);
    }

    enum ContentState {
        VISIBLE,
        HIDDEN,
        DELETED,
        HIDDEN_AND_DELETED
    }

    @ParameterizedTest
    @EnumSource(ContentState.class)
    void 상세는_원문을_유지하고_삭제_상태를_우선한다(
            ContentState state
    ) {
        givenAdmin();

        String content = "첫 번째 줄\n\n"
                + "😀".repeat(201)
                + "\n마지막 줄";

        Post post = createPost(
                activeAuthor(),
                "상세 확인 제목",
                content
        );

        boolean hidden = state == ContentState.HIDDEN
                || state == ContentState.HIDDEN_AND_DELETED;

        boolean deleted = state == ContentState.DELETED
                || state == ContentState.HIDDEN_AND_DELETED;

        if (hidden) {
            post.hide(HIDDEN_AT);
        }

        if (deleted) {
            post.delete(DELETED_AT);
        }

        when(postReadRepository.findPostById(POST_ID))
                .thenReturn(Optional.of(post));

        AdminPostDetailResult result = service.getPost(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID
        );

        AdminPostStatus expectedStatus = switch (state) {
            case VISIBLE -> AdminPostStatus.VISIBLE;
            case HIDDEN -> AdminPostStatus.HIDDEN;
            case DELETED, HIDDEN_AND_DELETED ->
                    AdminPostStatus.DELETED;
        };

        assertThat(result.postId()).isEqualTo(POST_ID);
        assertThat(result.type()).isEqualTo(PostType.GENERAL);
        assertThat(result.topic()).isEqualTo(PostTopic.DAILY);
        assertThat(result.title()).isEqualTo("상세 확인 제목");
        assertThat(result.content()).isEqualTo(content);
        assertThat(result.status()).isEqualTo(expectedStatus);
        assertThat(result.createdAt()).isEqualTo(CREATED_AT);
        assertThat(result.updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(result.hiddenAt())
                .isEqualTo(hidden ? HIDDEN_AT : null);
        assertThat(result.deletedAt())
                .isEqualTo(deleted ? DELETED_AT : null);

        assertThat(result.author().memberId()).isEqualTo(8L);
        assertThat(result.author().nickname())
                .isEqualTo("조회검증회원");
        assertThat(result.author().withdrawn()).isFalse();

        var order = inOrder(memberRepository, postReadRepository);
        order.verify(memberRepository).findById(ADMIN_ID);
        order.verify(postReadRepository).findPostById(POST_ID);
    }

    @Test
    void 탈퇴한_작성자는_목록과_상세에서_탈퇴한_회원으로_표시한다() {
        givenAdmin();

        Member author = activeAuthor();
        Post post = createPost(author, "작성자 탈퇴", "본문");

        author.withdraw(DELETED_AT);

        AdminPostReadQuery query = defaultQuery();

        when(postReadRepository.findPosts(query))
                .thenReturn(new PageImpl<>(
                        List.of(post),
                        PageRequest.of(0, 20),
                        1
                ));

        when(postReadRepository.findPostById(POST_ID))
                .thenReturn(Optional.of(post));

        AdminPostAuthorResult listAuthor = service.getPosts(
                        ADMIN_ID,
                        CREDENTIAL_VERSION,
                        query
                )
                .getContent()
                .getFirst()
                .author();

        AdminPostAuthorResult detailAuthor = service.getPost(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                POST_ID
        ).author();

        AdminPostAuthorResult expected =
                new AdminPostAuthorResult(
                        8L,
                        "탈퇴한 회원",
                        true
                );

        assertThat(listAuthor).isEqualTo(expected);
        assertThat(detailAuthor).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 회원_ID가_유효하지_않으면_조회하지_않는다(
            Long memberId
    ) {
        assertBothRejected(
                memberId,
                CREDENTIAL_VERSION,
                AdminContentReadException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 인증_버전이_음수이면_조회하지_않는다() {
        assertBothRejected(
                ADMIN_ID,
                -1L,
                AdminContentReadException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 회원이_존재하지_않으면_인증_실패다() {
        when(memberRepository.findById(ADMIN_ID))
                .thenReturn(Optional.empty());

        assertBothRejected(
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

        assertBothRejected(
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

        assertBothRejected(
                ADMIN_ID,
                requestedVersion,
                AdminContentReadException.Code.AUTHENTICATION_REQUIRED
        );
    }

    @Test
    void 활성_일반_회원은_관리자_권한_부족이다() {
        Member actor = createMember(
                ADMIN_ID,
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREDENTIAL_VERSION
        );

        when(memberRepository.findById(ADMIN_ID))
                .thenReturn(Optional.of(actor));

        assertBothRejected(
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

        assertBothRejected(
                ADMIN_ID,
                CREDENTIAL_VERSION - 1,
                AdminContentReadException.Code.AUTHENTICATION_REQUIRED
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 게시글_ID가_유효하지_않으면_찾을_수_없다(
            Long postId
    ) {
        givenAdmin();

        assertThatThrownBy(() ->
                service.getPost(
                        ADMIN_ID,
                        CREDENTIAL_VERSION,
                        postId
                )
        ).isInstanceOfSatisfying(
                AdminContentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminContentReadException.Code.POST_NOT_FOUND
                        )
        );

        verify(memberRepository).findById(ADMIN_ID);
        verifyNoInteractions(postReadRepository);
    }

    @Test
    void 상세_게시글이_존재하지_않으면_찾을_수_없다() {
        givenAdmin();

        when(postReadRepository.findPostById(POST_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.getPost(
                        ADMIN_ID,
                        CREDENTIAL_VERSION,
                        POST_ID
                )
        ).isInstanceOfSatisfying(
                AdminContentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminContentReadException.Code.POST_NOT_FOUND
                        )
        );
    }

    private void assertBothRejected(
            Long memberId,
            long credentialVersion,
            AdminContentReadException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                service.getPosts(
                        memberId,
                        credentialVersion,
                        defaultQuery()
                )
        ).isInstanceOfSatisfying(
                AdminContentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );

        assertThatThrownBy(() ->
                service.getPost(
                        memberId,
                        credentialVersion,
                        POST_ID
                )
        ).isInstanceOfSatisfying(
                AdminContentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );

        // 인증·권한 실패 시 콘텐츠 조회에 도달하면 안 된다.
        verifyNoInteractions(postReadRepository);
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
                "조회검증회원"
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

    private Post createPost(
            Member author,
            String title,
            String content
    ) {
        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                title,
                content
        );

        ReflectionTestUtils.setField(post, "id", POST_ID);
        ReflectionTestUtils.setField(
                post,
                "createdAt",
                CREATED_AT
        );
        ReflectionTestUtils.setField(
                post,
                "updatedAt",
                UPDATED_AT
        );

        return post;
    }

    private AdminPostReadQuery defaultQuery() {
        return new AdminPostReadQuery(
                AdminPostStatus.ALL,
                null,
                0,
                20
        );
    }
}