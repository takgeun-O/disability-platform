package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.admin.moderation.application.ContentModerationException;
import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.admin.moderation.infrastructure.ContentModerationLogRepository;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportHandlingOutcome;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportHideAndResolveServiceTest {

    private static final Long ACTOR_ID = 10L;
    private static final Long AUTHOR_ID = 20L;
    private static final Long REPORTER_ID = 30L;

    private static final Long POST_ID = 100L;
    private static final Long COMMENT_ID = 200L;
    private static final Long REPORT_ID = 300L;

    private static final long CREDENTIAL_VERSION = 7L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-13T05:00:00Z");

    private static final Instant PREVIOUS_TIME =
            Instant.parse("2026-11-13T06:00:00Z");

    private static final Instant NOW =
            Instant.parse("2026-11-13T07:00:00Z");

    private static final String POST_TITLE = "신고 대상 게시글";
    private static final String POST_CONTENT = "신고 대상 게시글 본문";
    private static final String COMMENT_CONTENT = "신고 대상 댓글 본문";

    private static final String REPORT_REASON = "검토가 필요합니다.";
    private static final String HANDLING_REASON =
            "운영 정책 위반을 확인하여 숨김 처리합니다.";

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ContentReportRepository reportRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private ContentModerationLogRepository logRepository;

    private AdminReportHideAndResolveService service;

    enum Target {
        POST,
        COMMENT
    }

    @BeforeEach
    void setUp() {
        service = createService(
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 정상_콘텐츠를_숨기고_신고를_처리_완료한다(
            Target target
    ) {
        Fixture fixture = fixture(target);
        Member admin = givenAuthorizedAdmin();

        givenReport(fixture.report());
        givenTarget(target, fixture);

        AdminReportHandlingResult result = execute();

        assertThat(hiddenAt(target, fixture)).isEqualTo(NOW);

        assertResolved(
                fixture.report(),
                ContentReportHandlingOutcome.CONTENT_HIDDEN,
                HANDLING_REASON
        );
        assertResult(result, HANDLING_REASON);
        assertOriginalDataUnchanged(target, fixture);
        assertAdminUnchanged(admin);

        ContentModerationLog log = capturedLog();

        assertLog(
                log,
                target,
                HANDLING_REASON
        );

        if (target == Target.POST) {
            verifyNoInteractions(commentRepository);
        } else {
            assertThat(fixture.post().getHiddenAt()).isNull();
        }

        verifyNoMoreInteractions(logRepository);
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 이미_숨긴_콘텐츠는_기존_시각을_유지하고_운영_기록을_추가하지_않는다(
            Target target
    ) {
        Fixture fixture = fixture(target);
        hideTarget(target, fixture, PREVIOUS_TIME);

        givenAuthorizedAdmin();
        givenReport(fixture.report());
        givenTarget(target, fixture);

        AdminReportHandlingResult result = execute();

        assertThat(hiddenAt(target, fixture))
                .isEqualTo(PREVIOUS_TIME);

        assertResolved(
                fixture.report(),
                ContentReportHandlingOutcome.CONTENT_ALREADY_HIDDEN,
                HANDLING_REASON
        );
        assertResult(result, HANDLING_REASON);
        assertOriginalDataUnchanged(target, fixture);

        verifyNoInteractions(logRepository);
    }

    @Test
    void 부모_게시글이_숨겨져_있어도_정상_댓글은_개별_숨김_처리한다() {
        Fixture fixture = fixture(Target.COMMENT);
        fixture.post().hide(PREVIOUS_TIME);

        givenAuthorizedAdmin();
        givenReport(fixture.report());
        givenTarget(Target.COMMENT, fixture);

        execute();

        assertThat(fixture.post().getHiddenAt())
                .isEqualTo(PREVIOUS_TIME);

        assertThat(fixture.comment().getHiddenAt())
                .isEqualTo(NOW);

        assertResolved(
                fixture.report(),
                ContentReportHandlingOutcome.CONTENT_HIDDEN,
                HANDLING_REASON
        );

        assertLog(
                capturedLog(),
                Target.COMMENT,
                HANDLING_REASON
        );
        assertOriginalDataUnchanged(Target.COMMENT, fixture);
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 처리_사유의_앞뒤_유니코드_공백을_제거하여_양쪽_기록에_저장한다(
            Target target
    ) {
        Fixture fixture = fixture(target);

        givenAuthorizedAdmin();
        givenReport(fixture.report());
        givenTarget(target, fixture);

        String input =
                "\u00A0\u2003 \n"
                        + HANDLING_REASON
                        + "\t \u2003\u00A0";

        AdminReportHandlingResult result = service.hideAndResolve(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                REPORT_ID,
                input
        );

        assertResolved(
                fixture.report(),
                ContentReportHandlingOutcome.CONTENT_HIDDEN,
                HANDLING_REASON
        );
        assertResult(result, HANDLING_REASON);

        assertLog(
                capturedLog(),
                target,
                HANDLING_REASON
        );
    }

    @ParameterizedTest
    @MethodSource("validBoundaryReasons")
    void 처리_사유는_코드포인트_기준_500자까지_허용한다(
            String reason
    ) {
        Fixture fixture = fixture(Target.POST);

        givenAuthorizedAdmin();
        givenReport(fixture.report());
        givenTarget(Target.POST, fixture);

        service.hideAndResolve(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                REPORT_ID,
                reason
        );

        assertResolved(
                fixture.report(),
                ContentReportHandlingOutcome.CONTENT_HIDDEN,
                reason
        );

        assertLog(
                capturedLog(),
                Target.POST,
                reason
        );
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                    "NULL, 7",
                    "0, 7",
                    "-1, 7",
                    "10, -1"
            },
            nullValues = "NULL"
    )
    void 잘못된_인증_정보는_회원_조회_전에_거부한다(
            Long actorId,
            long credentialVersion
    ) {
        assertHandlingFailure(
                AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED,
                () -> service.hideAndResolve(
                        actorId,
                        credentialVersion,
                        REPORT_ID,
                        HANDLING_REASON
                )
        );

        verifyNoInteractions(
                memberRepository,
                reportRepository,
                postRepository,
                commentRepository,
                logRepository
        );
    }

    @Test
    void 요청_관리자가_없으면_인증_오류로_거부한다() {
        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.empty());

        assertHandlingFailure(
                AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED,
                this::execute
        );

        verifyNoInteractions(
                reportRepository,
                postRepository,
                commentRepository,
                logRepository
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "ACTIVE"
    )
    void 활성_상태가_아닌_관리자는_거부한다(
            MemberStatus status
    ) {
        Member admin = admin();

        // 관리자에게 suspend()를 호출하는 대신
        // 상태가 달라진 계정을 테스트 준비 과정에서 직접 구성한다.
        ReflectionTestUtils.setField(
                admin,
                "status",
                status
        );

        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.of(admin));

        assertHandlingFailure(
                AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED,
                this::execute
        );

        verifyNoInteractions(
                reportRepository,
                postRepository,
                commentRepository,
                logRepository
        );
    }

    @Test
    void 인증_버전이_다르면_거부한다() {
        Member admin = admin();

        ReflectionTestUtils.setField(
                admin,
                "credentialVersion",
                CREDENTIAL_VERSION + 1
        );

        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.of(admin));

        assertHandlingFailure(
                AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED,
                this::execute
        );

        verifyNoInteractions(
                reportRepository,
                postRepository,
                commentRepository,
                logRepository
        );
    }

    @Test
    void 일반_회원은_관리자_권한_오류로_거부한다() {
        Member member = admin();

        ReflectionTestUtils.setField(
                member,
                "role",
                MemberRole.USER
        );

        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.of(member));

        assertHandlingFailure(
                AdminReportHandlingException.Code.ADMIN_REQUIRED,
                this::execute
        );

        verifyNoInteractions(
                reportRepository,
                postRepository,
                commentRepository,
                logRepository
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 잘못된_신고_ID는_관리자_검증_후_거부한다(
            Long reportId
    ) {
        givenAuthorizedAdmin();

        assertHandlingFailure(
                AdminReportHandlingException.Code.REPORT_NOT_FOUND,
                () -> service.hideAndResolve(
                        ACTOR_ID,
                        CREDENTIAL_VERSION,
                        reportId,
                        HANDLING_REASON
                )
        );

        verify(memberRepository).findByIdForUpdate(ACTOR_ID);

        verifyNoInteractions(
                reportRepository,
                postRepository,
                commentRepository,
                logRepository
        );
    }

    @Test
    void 신고가_없으면_콘텐츠_조회_전에_거부한다() {
        givenAuthorizedAdmin();

        when(reportRepository.findByIdForUpdate(REPORT_ID))
                .thenReturn(Optional.empty());

        assertHandlingFailure(
                AdminReportHandlingException.Code.REPORT_NOT_FOUND,
                this::execute
        );

        verifyNoInteractions(
                postRepository,
                commentRepository,
                logRepository
        );
    }

    @ParameterizedTest
    @CsvSource({
            "POST, RESOLVED",
            "POST, DISMISSED",
            "COMMENT, RESOLVED",
            "COMMENT, DISMISSED"
    })
    void 이미_처리된_신고는_콘텐츠를_변경하지_않는다(
            Target target,
            ContentReportStatus status
    ) {
        Fixture fixture = fixture(target);
        ContentReport report = fixture.report();

        String previousReason = "기존 검토 결과";

        if (status == ContentReportStatus.RESOLVED) {
            report.resolve(
                    ACTOR_ID,
                    previousReason,
                    PREVIOUS_TIME
            );
        } else {
            report.dismiss(
                    ACTOR_ID,
                    previousReason,
                    PREVIOUS_TIME
            );
        }

        givenAuthorizedAdmin();
        givenReport(report);

        assertHandlingFailure(
                AdminReportHandlingException.Code.REPORT_ALREADY_HANDLED,
                this::execute
        );

        assertThat(report.getStatus()).isEqualTo(status);
        assertThat(report.getHandledByMemberId()).isEqualTo(ACTOR_ID);
        assertThat(report.getHandledAt()).isEqualTo(PREVIOUS_TIME);
        assertThat(report.getHandlingReason()).isEqualTo(previousReason);

        assertThat(report.getHandlingOutcome())
                .isEqualTo(ContentReportHandlingOutcome.NO_CONTENT_ACTION);

        assertThat(fixture.post().getHiddenAt()).isNull();
        assertThat(fixture.comment().getHiddenAt()).isNull();

        assertOriginalDataUnchanged(target, fixture);

        verifyNoInteractions(
                postRepository,
                commentRepository,
                logRepository
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            " \t\r\n ",
            "\u00A0\u2003"
    })
    void 비어_있는_처리_사유는_콘텐츠_조회_전에_거부한다(
            String reason
    ) {
        assertInvalidReason(reason);
    }

    @ParameterizedTest
    @MethodSource("tooLongReasons")
    void 코드포인트_기준_500자를_초과한_처리_사유는_거부한다(
            String reason
    ) {
        assertInvalidReason(reason);
    }

    @Test
    void 대상_게시글이_없으면_신고를_변경하지_않는다() {
        Fixture fixture = fixture(Target.POST);

        givenAuthorizedAdmin();
        givenReport(fixture.report());

        when(postRepository.findPostByIdForModerationForUpdate(POST_ID))
                .thenReturn(Optional.empty());

        assertContentFailure(
                ContentModerationException.Code.POST_NOT_FOUND,
                this::execute
        );

        assertReceived(fixture.report());
        assertThat(fixture.post().getHiddenAt()).isNull();

        verifyNoInteractions(
                commentRepository,
                logRepository
        );
    }

    @Test
    void 삭제된_게시글은_숨기거나_신고를_처리하지_않는다() {
        Fixture fixture = fixture(Target.POST);
        fixture.post().delete(PREVIOUS_TIME);

        givenAuthorizedAdmin();
        givenReport(fixture.report());
        givenTarget(Target.POST, fixture);

        assertContentFailure(
                ContentModerationException.Code.POST_DELETED,
                this::execute
        );

        assertReceived(fixture.report());

        assertThat(fixture.post().getDeletedAt())
                .isEqualTo(PREVIOUS_TIME);
        assertThat(fixture.post().getHiddenAt()).isNull();

        verifyNoInteractions(
                commentRepository,
                logRepository
        );
    }

    @Test
    void 댓글의_부모_ID를_조회할_수_없으면_댓글_없음으로_거부한다() {
        Fixture fixture = fixture(Target.COMMENT);

        givenAuthorizedAdmin();
        givenReport(fixture.report());

        when(commentRepository.findPostIdByCommentIdForModeration(COMMENT_ID))
                .thenReturn(Optional.empty());

        assertContentFailure(
                ContentModerationException.Code.COMMENT_NOT_FOUND,
                this::execute
        );

        assertReceived(fixture.report());

        verify(commentRepository)
                .findPostIdByCommentIdForModeration(COMMENT_ID);

        verifyNoMoreInteractions(commentRepository);

        verifyNoInteractions(
                postRepository,
                logRepository
        );
    }

    @Test
    void 댓글의_부모_게시글이_없으면_거부한다() {
        Fixture fixture = fixture(Target.COMMENT);

        givenAuthorizedAdmin();
        givenReport(fixture.report());

        when(commentRepository.findPostIdByCommentIdForModeration(COMMENT_ID))
                .thenReturn(Optional.of(POST_ID));

        when(postRepository.findPostByIdForModerationForUpdate(POST_ID))
                .thenReturn(Optional.empty());

        assertContentFailure(
                ContentModerationException.Code.POST_NOT_FOUND,
                this::execute
        );

        assertReceived(fixture.report());
        assertThat(fixture.comment().getHiddenAt()).isNull();

        verify(commentRepository)
                .findPostIdByCommentIdForModeration(COMMENT_ID);

        verifyNoMoreInteractions(commentRepository);
        verifyNoInteractions(logRepository);
    }

    @Test
    void 댓글의_부모_게시글이_삭제되었다면_댓글_잠금_조회_전에_거부한다() {
        Fixture fixture = fixture(Target.COMMENT);
        fixture.post().delete(PREVIOUS_TIME);

        givenAuthorizedAdmin();
        givenReport(fixture.report());

        when(commentRepository.findPostIdByCommentIdForModeration(COMMENT_ID))
                .thenReturn(Optional.of(POST_ID));

        when(postRepository.findPostByIdForModerationForUpdate(POST_ID))
                .thenReturn(Optional.of(fixture.post()));

        assertContentFailure(
                ContentModerationException.Code.POST_DELETED,
                this::execute
        );

        assertReceived(fixture.report());

        assertThat(fixture.post().getDeletedAt())
                .isEqualTo(PREVIOUS_TIME);
        assertThat(fixture.comment().getHiddenAt()).isNull();

        verify(commentRepository)
                .findPostIdByCommentIdForModeration(COMMENT_ID);

        verifyNoMoreInteractions(commentRepository);
        verifyNoInteractions(logRepository);
    }

    @Test
    void 부모_게시글_잠금_후_댓글이_없으면_거부한다() {
        Fixture fixture = fixture(Target.COMMENT);

        givenAuthorizedAdmin();
        givenReport(fixture.report());

        when(commentRepository.findPostIdByCommentIdForModeration(COMMENT_ID))
                .thenReturn(Optional.of(POST_ID));

        when(postRepository.findPostByIdForModerationForUpdate(POST_ID))
                .thenReturn(Optional.of(fixture.post()));

        when(commentRepository.findCommentByIdForModerationForUpdate(
                POST_ID,
                COMMENT_ID
        )).thenReturn(Optional.empty());

        assertContentFailure(
                ContentModerationException.Code.COMMENT_NOT_FOUND,
                this::execute
        );

        assertReceived(fixture.report());
        assertThat(fixture.post().getHiddenAt()).isNull();

        verifyNoInteractions(logRepository);
    }

    @Test
    void 삭제된_댓글은_숨기거나_신고를_처리하지_않는다() {
        Fixture fixture = fixture(Target.COMMENT);
        fixture.comment().delete(PREVIOUS_TIME);

        givenAuthorizedAdmin();
        givenReport(fixture.report());
        givenTarget(Target.COMMENT, fixture);

        assertContentFailure(
                ContentModerationException.Code.COMMENT_DELETED,
                this::execute
        );

        assertReceived(fixture.report());

        assertThat(fixture.comment().getDeletedAt())
                .isEqualTo(PREVIOUS_TIME);

        assertThat(fixture.comment().getHiddenAt()).isNull();
        assertThat(fixture.post().getHiddenAt()).isNull();

        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 관리자와_신고와_콘텐츠를_차례로_잠근_후_처리_시각을_구한다(
            Target target
    ) {
        Fixture fixture = fixture(target);
        Clock orderedClock = mock(Clock.class);

        service = createService(orderedClock);

        givenAuthorizedAdmin();
        givenReport(fixture.report());
        givenTarget(target, fixture);

        when(orderedClock.instant()).thenReturn(NOW);

        execute();

        InOrder order = inOrder(
                memberRepository,
                reportRepository,
                postRepository,
                commentRepository,
                orderedClock,
                logRepository
        );

        order.verify(memberRepository)
                .findByIdForUpdate(ACTOR_ID);

        order.verify(reportRepository)
                .findByIdForUpdate(REPORT_ID);

        if (target == Target.COMMENT) {
            order.verify(commentRepository)
                    .findPostIdByCommentIdForModeration(COMMENT_ID);
        }

        order.verify(postRepository)
                .findPostByIdForModerationForUpdate(POST_ID);

        if (target == Target.COMMENT) {
            order.verify(commentRepository)
                    .findCommentByIdForModerationForUpdate(
                            POST_ID,
                            COMMENT_ID
                    );
        }

        order.verify(orderedClock).instant();

        order.verify(logRepository)
                .save(any(ContentModerationLog.class));

        order.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 운영_기록_저장_실패를_전파하고_신고_처리_완료로_진행하지_않는다(
            Target target
    ) {
        Fixture fixture = fixture(target);

        givenAuthorizedAdmin();
        givenReport(fixture.report());
        givenTarget(target, fixture);

        RuntimeException failure =
                new IllegalStateException("운영 기록 저장 실패");

        when(logRepository.save(any(ContentModerationLog.class)))
                .thenThrow(failure);

        assertThatThrownBy(this::execute)
                .isSameAs(failure);

        assertReceived(fixture.report());

        verify(logRepository)
                .save(any(ContentModerationLog.class));

        verifyNoMoreInteractions(logRepository);

        /*
         * 이 테스트는 Spring 트랜잭션을 실행하지 않는 단위 테스트다.
         *
         * 실제 객체의 hide() 호출은 이미 실행되었을 수 있으므로
         * 메모리 객체의 hiddenAt이 되돌아갔다고 검증하지 않는다.
         *
         * 콘텐츠·운영 기록·신고의 DB 롤백은
         * 별도의 트랜잭션 통합 테스트에서 검증한다.
         */
    }

    private AdminReportHideAndResolveService createService(
            Clock clock
    ) {
        return new AdminReportHideAndResolveService(
                memberRepository,
                reportRepository,
                postRepository,
                commentRepository,
                logRepository,
                clock
        );
    }

    private AdminReportHandlingResult execute() {
        return service.hideAndResolve(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                REPORT_ID,
                HANDLING_REASON
        );
    }

    private Member givenAuthorizedAdmin() {
        Member admin = admin();

        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.of(admin));

        return admin;
    }

    private void givenReport(ContentReport report) {
        when(reportRepository.findByIdForUpdate(REPORT_ID))
                .thenReturn(Optional.of(report));
    }

    private void givenTarget(
            Target target,
            Fixture fixture
    ) {
        if (target == Target.COMMENT) {
            when(commentRepository.findPostIdByCommentIdForModeration(COMMENT_ID))
                    .thenReturn(Optional.of(POST_ID));
        }

        when(postRepository.findPostByIdForModerationForUpdate(POST_ID))
                .thenReturn(Optional.of(fixture.post()));

        if (target == Target.COMMENT) {
            when(commentRepository.findCommentByIdForModerationForUpdate(
                    POST_ID,
                    COMMENT_ID
            )).thenReturn(Optional.of(fixture.comment()));
        }
    }

    private Member admin() {
        Member admin = activeMember(
                ACTOR_ID,
                "admin@example.com",
                "처리관리자"
        );

        ReflectionTestUtils.setField(
                admin,
                "role",
                MemberRole.ADMIN
        );
        ReflectionTestUtils.setField(
                admin,
                "credentialVersion",
                CREDENTIAL_VERSION
        );

        return admin;
    }

    private Member activeMember(
            Long id,
            String email,
            String nickname
    ) {
        Member member = Member.createPending(
                email,
                "test-password-hash",
                nickname
        );

        member.activeAfterEmailVerification();

        ReflectionTestUtils.setField(
                member,
                "id",
                id
        );

        return member;
    }

    private Fixture fixture(Target target) {
        Member author = activeMember(
                AUTHOR_ID,
                "author@example.com",
                "콘텐츠작성자"
        );

        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                POST_TITLE,
                POST_CONTENT
        );

        ReflectionTestUtils.setField(
                post,
                "id",
                POST_ID
        );

        Comment comment = Comment.create(
                post,
                author,
                COMMENT_CONTENT
        );

        ReflectionTestUtils.setField(
                comment,
                "id",
                COMMENT_ID
        );

        ContentReport report;

        if (target == Target.POST) {
            report = ContentReport.forPost(
                    REPORTER_ID,
                    POST_ID,
                    ContentReportReasonType.OTHER,
                    REPORT_REASON,
                    POST_TITLE,
                    POST_CONTENT,
                    CREATED_AT
            );
        } else {
            report = ContentReport.forComment(
                    REPORTER_ID,
                    COMMENT_ID,
                    ContentReportReasonType.OTHER,
                    REPORT_REASON,
                    COMMENT_CONTENT,
                    CREATED_AT
            );
        }

        ReflectionTestUtils.setField(
                report,
                "id",
                REPORT_ID
        );

        return new Fixture(
                post,
                comment,
                report
        );
    }

    private void hideTarget(
            Target target,
            Fixture fixture,
            Instant hiddenAt
    ) {
        if (target == Target.POST) {
            fixture.post().hide(hiddenAt);
        } else {
            fixture.comment().hide(hiddenAt);
        }
    }

    private Instant hiddenAt(
            Target target,
            Fixture fixture
    ) {
        return target == Target.POST
                ? fixture.post().getHiddenAt()
                : fixture.comment().getHiddenAt();
    }

    private ContentModerationLog capturedLog() {
        ArgumentCaptor<ContentModerationLog> captor =
                ArgumentCaptor.forClass(ContentModerationLog.class);

        verify(logRepository).save(captor.capture());

        return captor.getValue();
    }

    private void assertLog(
            ContentModerationLog log,
            Target target,
            String reason
    ) {
        assertThat(log.getActorMemberId()).isEqualTo(ACTOR_ID);
        assertThat(log.getAction()).isEqualTo(ModerationAction.HIDE);
        assertThat(log.getReasonType()).isEqualTo(ModerationReasonType.OTHER);
        assertThat(log.getReasonDetail()).isEqualTo(reason);
        assertThat(log.getReason()).isEqualTo(reason);
        assertThat(log.getCreatedAt()).isEqualTo(NOW);

        if (target == Target.POST) {
            assertThat(log.getPostId()).isEqualTo(POST_ID);
            assertThat(log.getCommentId()).isNull();
        } else {
            assertThat(log.getPostId()).isNull();
            assertThat(log.getCommentId()).isEqualTo(COMMENT_ID);
        }
    }

    private void assertResolved(
            ContentReport report,
            ContentReportHandlingOutcome outcome,
            String reason
    ) {
        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RESOLVED);

        assertThat(report.getHandlingOutcome()).isEqualTo(outcome);
        assertThat(report.getHandledByMemberId()).isEqualTo(ACTOR_ID);
        assertThat(report.getHandledAt()).isEqualTo(NOW);
        assertThat(report.getHandlingReason()).isEqualTo(reason);
    }

    private void assertResult(
            AdminReportHandlingResult result,
            String reason
    ) {
        assertThat(result.reportId()).isEqualTo(REPORT_ID);

        assertThat(result.status())
                .isEqualTo(ContentReportStatus.RESOLVED);

        assertThat(result.handledByMemberId()).isEqualTo(ACTOR_ID);
        assertThat(result.handledAt()).isEqualTo(NOW);
        assertThat(result.handlingReason()).isEqualTo(reason);
    }

    private void assertReceived(ContentReport report) {
        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);

        assertThat(report.getHandledByMemberId()).isNull();
        assertThat(report.getHandledAt()).isNull();
        assertThat(report.getHandlingReason()).isNull();
        assertThat(report.getHandlingOutcome()).isNull();
    }

    private void assertOriginalDataUnchanged(
            Target target,
            Fixture fixture
    ) {
        ContentReport report = fixture.report();

        assertThat(report.getReporterMemberId()).isEqualTo(REPORTER_ID);
        assertThat(report.getReasonType()).isEqualTo(ContentReportReasonType.OTHER);
        assertThat(report.getReasonDetail()).isEqualTo(REPORT_REASON);
        assertThat(report.getCreatedAt()).isEqualTo(CREATED_AT);

        if (target == Target.POST) {
            assertThat(report.getPostId()).isEqualTo(POST_ID);
            assertThat(report.getCommentId()).isNull();
            assertThat(report.getTitleSnapshot()).isEqualTo(POST_TITLE);
            assertThat(report.getContentSnapshot()).isEqualTo(POST_CONTENT);

            // 게시글을 숨겨도 댓글 자체의 숨김 값은 변경하지 않는다.
            assertThat(fixture.comment().getHiddenAt()).isNull();
        } else {
            assertThat(report.getPostId()).isNull();
            assertThat(report.getCommentId()).isEqualTo(COMMENT_ID);
            assertThat(report.getTitleSnapshot()).isNull();
            assertThat(report.getContentSnapshot()).isEqualTo(COMMENT_CONTENT);
        }

        assertThat(fixture.post().getTitle()).isEqualTo(POST_TITLE);
        assertThat(fixture.post().getContent()).isEqualTo(POST_CONTENT);
        assertThat(fixture.post().getDeletedAt()).isNull();

        assertThat(fixture.comment().getContent()).isEqualTo(COMMENT_CONTENT);
        assertThat(fixture.comment().getContentUpdatedAt()).isNull();
        assertThat(fixture.comment().getDeletedAt()).isNull();

        Member author = fixture.post().getAuthor();

        assertThat(author.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(author.getRole()).isEqualTo(MemberRole.USER);
        assertThat(author.getCredentialVersion()).isZero();
    }

    private void assertAdminUnchanged(Member admin) {
        assertThat(admin.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(admin.getRole()).isEqualTo(MemberRole.ADMIN);

        assertThat(admin.getCredentialVersion())
                .isEqualTo(CREDENTIAL_VERSION);
    }

    private void assertInvalidReason(String reason) {
        Fixture fixture = fixture(Target.POST);

        givenAuthorizedAdmin();
        givenReport(fixture.report());

        assertHandlingFailure(
                AdminReportHandlingException.Code.INVALID_HANDLING_REASON,
                () -> service.hideAndResolve(
                        ACTOR_ID,
                        CREDENTIAL_VERSION,
                        REPORT_ID,
                        reason
                )
        );

        assertReceived(fixture.report());
        assertThat(fixture.post().getHiddenAt()).isNull();

        verifyNoInteractions(
                postRepository,
                commentRepository,
                logRepository
        );
    }

    private void assertHandlingFailure(
            AdminReportHandlingException.Code expectedCode,
            ThrowingCallable action
    ) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        AdminReportHandlingException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
                );
    }

    private void assertContentFailure(
            ContentModerationException.Code expectedCode,
            ThrowingCallable action
    ) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        ContentModerationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
                );
    }

    private static Stream<String> validBoundaryReasons() {
        return Stream.of(
                "가".repeat(500),
                "😀".repeat(500)
        );
    }

    private static Stream<String> tooLongReasons() {
        return Stream.of(
                "가".repeat(501),
                "😀".repeat(501)
        );
    }

    private record Fixture(
            Post post,
            Comment comment,
            ContentReport report
    ) {
    }
}