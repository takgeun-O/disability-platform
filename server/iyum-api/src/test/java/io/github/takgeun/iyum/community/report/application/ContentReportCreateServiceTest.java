package io.github.takgeun.iyum.community.report.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.community.report.infrastructure.ContentReportRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentReportCreateServiceTest {

    private static final Long REPORTER_ID = 1L;
    private static final Long AUTHOR_ID = 2L;
    private static final Long POST_ID = 10L;
    private static final Long COMMENT_ID = 20L;
    private static final Long REPORT_ID = 100L;

    private static final long CREDENTIAL_VERSION = 3L;

    private static final Instant NOW =
            Instant.parse("2026-11-12T10:00:00Z");

    private static final String POST_TITLE = "신고 대상 게시글";
    private static final String POST_CONTENT =
            " \n게시글 첫 문장\n\n두  번째 문장\t ";
    private static final String COMMENT_CONTENT =
            " \n댓글 첫 문장\n두  번째 문장\t ";

    @Mock
    MemberRepository memberRepository;

    @Mock
    PostRepository postRepository;

    @Mock
    CommentRepository commentRepository;

    @Mock
    ContentReportRepository reportRepository;

    @Captor
    ArgumentCaptor<ContentReport> reportCaptor;

    private ContentReportCreateService service;

    private Member reporter;
    private Member author;
    private Post post;
    private Comment comment;

    private enum Target {
        POST,
        COMMENT
    }

    @BeforeEach
    void setUp() {
        service = new ContentReportCreateService(
                memberRepository,
                postRepository,
                commentRepository,
                reportRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        reporter = activeMember(
                REPORTER_ID,
                "reporter@example.com",
                "신고회원"
        );

        ReflectionTestUtils.setField(
                reporter,
                "credentialVersion",
                CREDENTIAL_VERSION
        );

        author = activeMember(
                AUTHOR_ID,
                "author@example.com",
                "작성회원"
        );

        post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                POST_TITLE,
                POST_CONTENT
        );
        ReflectionTestUtils.setField(post, "id", POST_ID);

        comment = Comment.create(
                post,
                author,
                COMMENT_CONTENT
        );
        ReflectionTestUtils.setField(comment, "id", COMMENT_ID);
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 신고를_접수하고_서버에서_읽은_원문을_저장한다(Target target) {
        stubReporter();
        stubVisibleTarget(target);
        stubNoExistingReport(target);
        stubSave();

        ContentReportCreateResult result = report(
                target,
                ContentReportReasonType.OTHER,
                "  검토가 필요합니다.  "
        );

        verify(reportRepository).save(reportCaptor.capture());
        ContentReport saved = reportCaptor.getValue();

        assertThat(saved.getReporterMemberId()).isEqualTo(REPORTER_ID);
        assertThat(saved.getReasonType())
                .isEqualTo(ContentReportReasonType.OTHER);
        assertThat(saved.getReasonDetail())
                .isEqualTo("검토가 필요합니다.");
        assertThat(saved.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);

        if (target == Target.POST) {
            assertThat(saved.getPostId()).isEqualTo(POST_ID);
            assertThat(saved.getCommentId()).isNull();
            assertThat(saved.getTitleSnapshot()).isEqualTo(POST_TITLE);
            assertThat(saved.getContentSnapshot()).isEqualTo(POST_CONTENT);

            verifyNoInteractions(commentRepository);
        } else {
            assertThat(saved.getPostId()).isNull();
            assertThat(saved.getCommentId()).isEqualTo(COMMENT_ID);
            assertThat(saved.getTitleSnapshot()).isNull();
            assertThat(saved.getContentSnapshot())
                    .isEqualTo(COMMENT_CONTENT);
        }

        assertThat(result.reportId()).isEqualTo(REPORT_ID);
        assertThat(result.status())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(result.createdAt()).isEqualTo(NOW);

        // 신고 접수만으로 원문이나 공개 상태를 변경하지 않는다.
        assertThat(post.getTitle()).isEqualTo(POST_TITLE);
        assertThat(post.getContent()).isEqualTo(POST_CONTENT);
        assertThat(post.getHiddenAt()).isNull();
        assertThat(post.getDeletedAt()).isNull();

        assertThat(comment.getContent()).isEqualTo(COMMENT_CONTENT);
        assertThat(comment.getHiddenAt()).isNull();
        assertThat(comment.getDeletedAt()).isNull();

        assertThat(reporter.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(reporter.getCredentialVersion())
                .isEqualTo(CREDENTIAL_VERSION);

        // 실제 DB 잠금 자체가 아니라 잠금 조회 호출 순서를 검증한다.
        InOrder order = inOrder(
                memberRepository,
                postRepository,
                commentRepository,
                reportRepository
        );

        order.verify(memberRepository).findByIdForUpdate(REPORTER_ID);
        order.verify(postRepository)
                .findVisiblePostByIdForUpdate(POST_ID);

        if (target == Target.POST) {
            order.verify(reportRepository)
                    .findByReporterMemberIdAndPostId(
                            REPORTER_ID,
                            POST_ID
                    );
        } else {
            order.verify(commentRepository)
                    .findVisibleCommentByIdForUpdate(
                            POST_ID,
                            COMMENT_ID
                    );
            order.verify(reportRepository)
                    .findByReporterMemberIdAndCommentId(
                            REPORTER_ID,
                            COMMENT_ID
                    );
        }

        order.verify(reportRepository).save(saved);
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 선택_상세_설명이_공백이면_null로_저장한다(Target target) {
        stubReporter();
        stubVisibleTarget(target);
        stubNoExistingReport(target);
        stubSave();

        report(
                target,
                ContentReportReasonType.SPAM_ADVERTISING,
                " \t\n "
        );

        verify(reportRepository).save(reportCaptor.capture());

        assertThat(reportCaptor.getValue().getReasonDetail()).isNull();
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 잘못된_인증_입력은_DB_조회_전에_거부한다(Target target) {
        Long[] invalidIds = {null, 0L, -1L};

        for (Long invalidId : invalidIds) {
            assertFailure(
                    ContentReportCreateException.Code.AUTHENTICATION_REQUIRED,
                    () -> report(
                            target,
                            invalidId,
                            CREDENTIAL_VERSION,
                            POST_ID,
                            COMMENT_ID,
                            ContentReportReasonType.SPAM_ADVERTISING,
                            null
                    )
            );
        }

        assertFailure(
                ContentReportCreateException.Code.AUTHENTICATION_REQUIRED,
                () -> report(
                        target,
                        REPORTER_ID,
                        -1L,
                        POST_ID,
                        COMMENT_ID,
                        ContentReportReasonType.SPAM_ADVERTISING,
                        null
                )
        );

        verifyNoInteractions(
                memberRepository,
                postRepository,
                commentRepository,
                reportRepository
        );
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 신고자_회원이_없으면_인증_오류로_거부한다(Target target) {
        when(memberRepository.findByIdForUpdate(REPORTER_ID))
                .thenReturn(Optional.empty());

        assertFailure(
                ContentReportCreateException.Code.AUTHENTICATION_REQUIRED,
                () -> report(target)
        );

        verifyNoInteractions(
                postRepository,
                commentRepository,
                reportRepository
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = "ACTIVE",
            mode = EnumSource.Mode.EXCLUDE
    )
    void 정상_상태가_아닌_회원은_신고할_수_없다(MemberStatus status) {
        ReflectionTestUtils.setField(reporter, "status", status);
        stubReporter();

        for (Target target : Target.values()) {
            assertFailure(
                    ContentReportCreateException.Code.AUTHENTICATION_REQUIRED,
                    () -> report(target)
            );
        }

        verifyNoInteractions(
                postRepository,
                commentRepository,
                reportRepository
        );
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 인증_버전이_다르면_대상_조회_전에_거부한다(Target target) {
        stubReporter();

        assertFailure(
                ContentReportCreateException.Code.AUTHENTICATION_REQUIRED,
                () -> report(
                        target,
                        REPORTER_ID,
                        CREDENTIAL_VERSION - 1,
                        POST_ID,
                        COMMENT_ID,
                        ContentReportReasonType.SPAM_ADVERTISING,
                        null
                )
        );

        verifyNoInteractions(
                postRepository,
                commentRepository,
                reportRepository
        );
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 잘못된_신고_사유는_대상_조회_전에_거부한다(Target target) {
        stubReporter();

        assertFailure(
                ContentReportCreateException.Code.INVALID_REASON,
                () -> report(target, null, "상세 설명")
        );

        assertFailure(
                ContentReportCreateException.Code.INVALID_REASON,
                () -> report(
                        target,
                        ContentReportReasonType.OTHER,
                        null
                )
        );

        assertFailure(
                ContentReportCreateException.Code.INVALID_REASON,
                () -> report(
                        target,
                        ContentReportReasonType.OTHER,
                        " \t\n "
                )
        );

        assertFailure(
                ContentReportCreateException.Code.INVALID_REASON,
                () -> report(
                        target,
                        ContentReportReasonType.SPAM_ADVERTISING,
                        "가".repeat(501)
                )
        );

        verifyNoInteractions(
                postRepository,
                commentRepository,
                reportRepository
        );
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 잘못된_게시글_ID는_게시글_조회_전에_거부한다(Target target) {
        stubReporter();

        Long[] invalidIds = {null, 0L, -1L};

        for (Long invalidId : invalidIds) {
            assertFailure(
                    ContentReportCreateException.Code.POST_NOT_FOUND,
                    () -> report(
                            target,
                            REPORTER_ID,
                            CREDENTIAL_VERSION,
                            invalidId,
                            COMMENT_ID,
                            ContentReportReasonType.SPAM_ADVERTISING,
                            null
                    )
            );
        }

        verifyNoInteractions(
                postRepository,
                commentRepository,
                reportRepository
        );
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 공개_게시글을_찾지_못하면_신고를_거부한다(Target target) {
        stubReporter();

        when(postRepository.findVisiblePostByIdForUpdate(POST_ID))
                .thenReturn(Optional.empty());

        assertFailure(
                ContentReportCreateException.Code.POST_NOT_FOUND,
                () -> report(target)
        );

        verifyNoInteractions(commentRepository, reportRepository);
    }

    @Test
    void 잘못된_댓글_ID는_댓글_조회_전에_거부한다() {
        stubReporter();

        when(postRepository.findVisiblePostByIdForUpdate(POST_ID))
                .thenReturn(Optional.of(post));

        Long[] invalidIds = {null, 0L, -1L};

        for (Long invalidId : invalidIds) {
            assertFailure(
                    ContentReportCreateException.Code.COMMENT_NOT_FOUND,
                    () -> report(
                            Target.COMMENT,
                            REPORTER_ID,
                            CREDENTIAL_VERSION,
                            POST_ID,
                            invalidId,
                            ContentReportReasonType.SPAM_ADVERTISING,
                            null
                    )
            );
        }

        verifyNoInteractions(commentRepository, reportRepository);
    }

    @Test
    void 해당_게시글의_공개_댓글을_찾지_못하면_거부한다() {
        stubReporter();

        when(postRepository.findVisiblePostByIdForUpdate(POST_ID))
                .thenReturn(Optional.of(post));

        when(commentRepository.findVisibleCommentByIdForUpdate(
                POST_ID,
                COMMENT_ID
        )).thenReturn(Optional.empty());

        assertFailure(
                ContentReportCreateException.Code.COMMENT_NOT_FOUND,
                () -> report(Target.COMMENT)
        );

        // 댓글 ID만 조회하지 않고 부모 게시글 ID도 함께 전달한다.
        verify(commentRepository).findVisibleCommentByIdForUpdate(
                POST_ID,
                COMMENT_ID
        );

        verifyNoInteractions(reportRepository);
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 자신이_작성한_콘텐츠는_신고할_수_없다(Target target) {
        if (target == Target.POST) {
            ReflectionTestUtils.setField(post, "author", reporter);
        } else {
            ReflectionTestUtils.setField(comment, "author", reporter);
        }

        stubReporter();
        stubVisibleTarget(target);

        assertFailure(
                ContentReportCreateException.Code.SELF_REPORT_NOT_ALLOWED,
                () -> report(target)
        );

        // 자기 콘텐츠이면 중복 조회나 저장까지 진행하지 않는다.
        verifyNoInteractions(reportRepository);
    }

    @Test
    void 자기_게시글에_다른_회원이_작성한_댓글은_신고할_수_있다() {
        ReflectionTestUtils.setField(post, "author", reporter);

        stubReporter();
        stubVisibleTarget(Target.COMMENT);
        stubNoExistingReport(Target.COMMENT);
        stubSave();

        ContentReportCreateResult result = report(Target.COMMENT);

        assertThat(result.reportId()).isEqualTo(REPORT_ID);

        verify(reportRepository).save(reportCaptor.capture());

        assertThat(reportCaptor.getValue().getCommentId())
                .isEqualTo(COMMENT_ID);
        assertThat(reportCaptor.getValue().getPostId()).isNull();
    }

    @Test
    void 정상_상태의_관리자도_다른_회원의_콘텐츠를_신고할_수_있다() {
        ReflectionTestUtils.setField(
                reporter,
                "role",
                MemberRole.ADMIN
        );

        stubReporter();
        stubVisibleTarget(Target.POST);
        stubNoExistingReport(Target.POST);
        stubSave();

        ContentReportCreateResult result = report(Target.POST);

        assertThat(result.reportId()).isEqualTo(REPORT_ID);
        assertThat(reporter.getRole()).isEqualTo(MemberRole.ADMIN);
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 기존_신고는_처리_상태와_무관하게_중복으로_거부한다(
            Target target
    ) {
        stubReporter();
        stubVisibleTarget(target);

        ContentReport existing = existingReport(target);

        if (target == Target.POST) {
            when(reportRepository.findByReporterMemberIdAndPostId(
                    REPORTER_ID,
                    POST_ID
            )).thenReturn(Optional.of(existing));
        } else {
            when(reportRepository.findByReporterMemberIdAndCommentId(
                    REPORTER_ID,
                    COMMENT_ID
            )).thenReturn(Optional.of(existing));
        }

        for (ContentReportStatus status : ContentReportStatus.values()) {
            // 관리자 상태 전환 구현과 무관하게,
            // 조회 결과의 모든 상태에 중복 정책이 적용되는지 검증한다.
            ReflectionTestUtils.setField(existing, "status", status);

            assertFailure(
                    ContentReportCreateException.Code.REPORT_ALREADY_EXISTS,
                    () -> report(
                            target,
                            ContentReportReasonType.PERSONAL_INFORMATION,
                            "새로 입력한 사유"
                    )
            );

            assertThat(existing.getStatus()).isEqualTo(status);
        }

        verify(reportRepository, never()).save(any(ContentReport.class));

        // 재신고 요청으로 기존 신고 내용을 덮어쓰지 않는다.
        assertThat(existing.getReasonType())
                .isEqualTo(ContentReportReasonType.OTHER);
        assertThat(existing.getReasonDetail()).isEqualTo("기존 신고 사유");
        assertThat(existing.getContentSnapshot()).isEqualTo("기존 원문");
        assertThat(existing.getCreatedAt())
                .isEqualTo(NOW.minusSeconds(60));
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 저장_오류를_중복_신고_오류로_일괄_변환하지_않는다(
            Target target
    ) {
        stubReporter();
        stubVisibleTarget(target);
        stubNoExistingReport(target);

        DataIntegrityViolationException failure =
                new DataIntegrityViolationException(
                        "중복 이외의 DB 무결성 오류"
                );

        when(reportRepository.save(any(ContentReport.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> report(target))
                .isSameAs(failure);
    }

    private void stubReporter() {
        when(memberRepository.findByIdForUpdate(REPORTER_ID))
                .thenReturn(Optional.of(reporter));
    }

    private void stubVisibleTarget(Target target) {
        when(postRepository.findVisiblePostByIdForUpdate(POST_ID))
                .thenReturn(Optional.of(post));

        if (target == Target.COMMENT) {
            when(commentRepository.findVisibleCommentByIdForUpdate(
                    POST_ID,
                    COMMENT_ID
            )).thenReturn(Optional.of(comment));
        }
    }

    private void stubNoExistingReport(Target target) {
        if (target == Target.POST) {
            when(reportRepository.findByReporterMemberIdAndPostId(
                    REPORTER_ID,
                    POST_ID
            )).thenReturn(Optional.empty());
        } else {
            when(reportRepository.findByReporterMemberIdAndCommentId(
                    REPORTER_ID,
                    COMMENT_ID
            )).thenReturn(Optional.empty());
        }
    }

    private void stubSave() {
        when(reportRepository.save(any(ContentReport.class)))
                .thenAnswer(invocation -> {
                    ContentReport saved = invocation.getArgument(0);

                    // 실제 DB의 ID 생성을 단위 테스트에서 흉내 낸다.
                    ReflectionTestUtils.setField(
                            saved,
                            "id",
                            REPORT_ID
                    );

                    return saved;
                });
    }

    private ContentReportCreateResult report(Target target) {
        return report(
                target,
                ContentReportReasonType.SPAM_ADVERTISING,
                null
        );
    }

    private ContentReportCreateResult report(
            Target target,
            ContentReportReasonType reasonType,
            String reasonDetail
    ) {
        return report(
                target,
                REPORTER_ID,
                CREDENTIAL_VERSION,
                POST_ID,
                COMMENT_ID,
                reasonType,
                reasonDetail
        );
    }

    private ContentReportCreateResult report(
            Target target,
            Long reporterId,
            long credentialVersion,
            Long postId,
            Long commentId,
            ContentReportReasonType reasonType,
            String reasonDetail
    ) {
        if (target == Target.POST) {
            return service.reportPost(
                    reporterId,
                    credentialVersion,
                    postId,
                    reasonType,
                    reasonDetail
            );
        }

        return service.reportComment(
                reporterId,
                credentialVersion,
                postId,
                commentId,
                reasonType,
                reasonDetail
        );
    }

    private ContentReport existingReport(Target target) {
        if (target == Target.POST) {
            return ContentReport.forPost(
                    REPORTER_ID,
                    POST_ID,
                    ContentReportReasonType.OTHER,
                    "기존 신고 사유",
                    "기존 제목",
                    "기존 원문",
                    NOW.minusSeconds(60)
            );
        }

        return ContentReport.forComment(
                REPORTER_ID,
                COMMENT_ID,
                ContentReportReasonType.OTHER,
                "기존 신고 사유",
                "기존 원문",
                NOW.minusSeconds(60)
        );
    }

    private Member activeMember(
            Long id,
            String email,
            String nickname
    ) {
        Member member = Member.createPending(
                email,
                "test-only-password-hash",
                nickname
        );

        member.activeAfterEmailVerification();
        ReflectionTestUtils.setField(member, "id", id);

        return member;
    }

    private void assertFailure(
            ContentReportCreateException.Code expectedCode,
            ThrowingCallable action
    ) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        ContentReportCreateException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
                );
    }
}