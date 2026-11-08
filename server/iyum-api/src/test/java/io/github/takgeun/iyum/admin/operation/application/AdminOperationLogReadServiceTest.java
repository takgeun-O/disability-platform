package io.github.takgeun.iyum.admin.operation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.admin.operation.infrastructure.AdminOperationLogReadRepository;
import io.github.takgeun.iyum.admin.operation.infrastructure.AdminOperationLogTargetReadRepository;
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
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOperationLogReadServiceTest {

    private static final Long ADMIN_ID = 7L;
    private static final long CREDENTIAL_VERSION = 3L;

    private static final Instant NOW =
            Instant.parse("2026-11-04T03:00:00Z");

    @Mock
    MemberRepository memberRepository;

    @Mock
    AdminOperationLogReadRepository operationLogReadRepository;

    @Mock
    AdminOperationLogTargetReadRepository targetReadRepository;

    @InjectMocks
    AdminOperationLogReadService service;

    @Test
    void 관리자는_기록을_조회하고_항목과_페이지_정보를_유지한다() {
        givenActor(MemberStatus.ACTIVE, MemberRole.ADMIN);

        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        null, null, null, null, 1, 2
                );

        // 요청자 7, 과거 조치 관리자 8/9, 현재 콘텐츠 작성자 42/43을 구분한다.
        var postSummary = new AdminOperationLogCurrentTargetResult(
                new AdminOperationLogTargetAuthorResult(42L, "게시글작성자", false),
                "현재 게시글 제목", "현재 게시글 미리보기"
        );
        var commentSummary = new AdminOperationLogCurrentTargetResult(
                new AdminOperationLogTargetAuthorResult(43L, "댓글작성자", false),
                null, "현재 댓글 미리보기"
        );
        when(targetReadRepository.findPostSummaries(Set.of(123L)))
                .thenReturn(Map.of(123L, postSummary));
        when(targetReadRepository.findCommentSummaries(Set.of(456L)))
                .thenReturn(Map.of(456L, commentSummary));
        ContentModerationLog postLog = ContentModerationLog.forPost(
                8L,
                123L,
                ModerationAction.HIDE,
                "운영 정책 위반",
                NOW
        );
        ReflectionTestUtils.setField(postLog, "id", 101L);

        ContentModerationLog commentLog = ContentModerationLog.forComment(
                9L,
                456L,
                ModerationAction.RESTORE,
                "검토 결과 복원",
                NOW.minusSeconds(60)
        );
        ReflectionTestUtils.setField(commentLog, "id", 100L);

        PageRequest pageable = PageRequest.of(
                1,
                2,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        when(operationLogReadRepository.findLogs(query))
                .thenReturn(new PageImpl<>(
                        List.of(postLog, commentLog),
                        pageable,
                        5
                ));

        Page<AdminOperationLogItemResult> result = service.getLogs(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                query
        );

        assertThat(result.getContent()).containsExactly(
                new AdminOperationLogItemResult(
                        101L,
                        8L,
                        AdminOperationLogTargetType.POST,
                        123L,
                        ModerationAction.HIDE,
                        ModerationReasonType.LEGACY,
                        null,
                        "운영 정책 위반",
                        NOW,
                        postSummary
                ),
                new AdminOperationLogItemResult(
                        100L,
                        9L,
                        AdminOperationLogTargetType.COMMENT,
                        456L,
                        ModerationAction.RESTORE,
                        ModerationReasonType.LEGACY,
                        null,
                        "검토 결과 복원",
                        NOW.minusSeconds(60),
                        commentSummary
                )
        );

        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getSort()).isEqualTo(pageable.getSort());
        assertThat(result.hasNext()).isTrue();

        // 관리자 검증 → 기록 페이지 → 현재 대상 요약 조회 순서를 검증한다.
        var order = inOrder(
                memberRepository,
                operationLogReadRepository,
                targetReadRepository
        );
        order.verify(memberRepository).findById(ADMIN_ID);
        order.verify(operationLogReadRepository).findLogs(query);
        order.verify(targetReadRepository).findPostSummaries(Set.of(123L));
        order.verify(targetReadRepository).findCommentSummaries(Set.of(456L));

        verifyNoMoreInteractions(
                memberRepository,
                operationLogReadRepository,
                targetReadRepository
        );
    }

    @Test
    void 기록이_없으면_빈_페이지를_반환한다() {
        givenActor(MemberStatus.ACTIVE, MemberRole.ADMIN);

        AdminOperationLogReadQuery query = defaultQuery();

        when(operationLogReadRepository.findLogs(query))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        Page<AdminOperationLogItemResult> result = service.getLogs(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                query
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalPages()).isZero();
        assertThat(result.getSort()).isEqualTo(Sort.unsorted());
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
        verify(targetReadRepository).findPostSummaries(Set.of());
        verify(targetReadRepository).findCommentSummaries(Set.of());
        verifyNoMoreInteractions(targetReadRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 회원_ID가_유효하지_않으면_조회하지_않는다(Long memberId) {
        assertRejected(
                memberId,
                CREDENTIAL_VERSION,
                AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 인증_버전이_음수이면_조회하지_않는다() {
        assertRejected(
                ADMIN_ID,
                -1L,
                AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 존재하지_않는_회원은_인증_실패다() {
        when(memberRepository.findById(ADMIN_ID))
                .thenReturn(Optional.empty());

        assertRejected(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = {"PENDING", "DELETED"}
    )
    void 활성_회원이_아니면_인증_실패다(MemberStatus status) {
        givenActor(status, MemberRole.ADMIN);

        assertRejected(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {2L, 4L})
    void 인증_버전이_다르면_인증_실패다(long requestedVersion) {
        givenActor(MemberStatus.ACTIVE, MemberRole.ADMIN);

        assertRejected(
                ADMIN_ID,
                requestedVersion,
                AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
        );
    }

    @Test
    void 활성_일반_회원은_관리자_권한_부족이다() {
        givenActor(MemberStatus.ACTIVE, MemberRole.USER);

        assertRejected(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                AdminOperationLogReadException.Code.ADMIN_REQUIRED
        );
    }

    @Test
    void 일반_회원이라도_인증_버전이_다르면_인증_실패가_우선이다() {
        givenActor(MemberStatus.ACTIVE, MemberRole.USER);

        assertRejected(
                ADMIN_ID,
                CREDENTIAL_VERSION - 1,
                AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
        );
    }

    @Test
    void 같은_게시글의_여러_기록은_유지하고_대상_ID는_중복_제거한다() {
        givenActor(MemberStatus.ACTIVE, MemberRole.ADMIN);
        var query = defaultQuery();
        var hide = postLog(103L, 123L, ModerationAction.HIDE, "첫 숨김 사유");
        var restore = postLog(102L, 123L, ModerationAction.RESTORE, "재검토 복원 사유");
        var other = postLog(101L, 124L, ModerationAction.HIDE, "다른 게시글 사유");
        var current = summary(42L, "변경된 현재 제목", "현재 본문");
        var otherCurrent = summary(43L, "다른 현재 제목", "다른 본문");
        when(operationLogReadRepository.findLogs(query)).thenReturn(
                new PageImpl<>(List.of(hide, restore, other), PageRequest.of(0, 20), 3));
        when(targetReadRepository.findPostSummaries(Set.of(123L, 124L)))
                .thenReturn(Map.of(123L, current, 124L, otherCurrent));

        var result = service.getLogs(ADMIN_ID, CREDENTIAL_VERSION, query);

        assertThat(result.getContent()).extracting(AdminOperationLogItemResult::logId)
                .containsExactly(103L, 102L, 101L);
        assertThat(result.getContent()).extracting(AdminOperationLogItemResult::action)
                .containsExactly(ModerationAction.HIDE, ModerationAction.RESTORE, ModerationAction.HIDE);
        assertThat(result.getContent()).extracting(AdminOperationLogItemResult::reason)
                .containsExactly("첫 숨김 사유", "재검토 복원 사유", "다른 게시글 사유");
        assertThat(result.getContent()).extracting(AdminOperationLogItemResult::currentTarget)
                .containsExactly(current, current, otherCurrent);
        assertThat(result.getTotalElements()).isEqualTo(3);
        verify(targetReadRepository).findPostSummaries(Set.of(123L, 124L));
        verify(targetReadRepository).findCommentSummaries(Set.of());
        verifyNoMoreInteractions(targetReadRepository);
    }

    @Test
    void 게시글과_댓글의_숫자_ID가_같아도_종류별_요약을_연결한다() {
        givenActor(MemberStatus.ACTIVE, MemberRole.ADMIN);
        var query = defaultQuery();
        var post = postLog(102L, 123L, ModerationAction.HIDE, "게시글 사유");
        var comment = commentLog(101L, 123L);
        var postSummary = summary(42L, "게시글 제목", "게시글 미리보기");
        var commentSummary = summary(43L, null, "댓글 미리보기");
        when(operationLogReadRepository.findLogs(query)).thenReturn(
                new PageImpl<>(List.of(post, comment), PageRequest.of(0, 20), 2));
        when(targetReadRepository.findPostSummaries(Set.of(123L)))
                .thenReturn(Map.of(123L, postSummary));
        when(targetReadRepository.findCommentSummaries(Set.of(123L)))
                .thenReturn(Map.of(123L, commentSummary));

        var result = service.getLogs(ADMIN_ID, CREDENTIAL_VERSION, query);

        assertThat(result.getContent()).extracting(AdminOperationLogItemResult::targetType)
                .containsExactly(AdminOperationLogTargetType.POST, AdminOperationLogTargetType.COMMENT);
        assertThat(result.getContent()).extracting(AdminOperationLogItemResult::targetId)
                .containsExactly(123L, 123L);
        assertThat(result.getContent()).extracting(AdminOperationLogItemResult::currentTarget)
                .containsExactly(postSummary, commentSummary);
        verify(targetReadRepository).findPostSummaries(Set.of(123L));
        verify(targetReadRepository).findCommentSummaries(Set.of(123L));
        verifyNoMoreInteractions(targetReadRepository);
    }

    @Test
    void 요약_Map에_없는_대상도_과거_기록과_전체_개수를_유지한다() {
        givenActor(MemberStatus.ACTIVE, MemberRole.ADMIN);
        var query = defaultQuery();
        var present = summary(42L, "찾은 게시글", "현재 미리보기");
        when(operationLogReadRepository.findLogs(query)).thenReturn(new PageImpl<>(List.of(
                postLog(103L, 123L, ModerationAction.HIDE, "유지할 게시글 사유"),
                commentLog(102L, 456L),
                postLog(101L, 124L, ModerationAction.RESTORE, "찾은 기록 사유")
        ), PageRequest.of(0, 20), 43));
        when(targetReadRepository.findPostSummaries(Set.of(123L, 124L)))
                .thenReturn(Map.of(124L, present));
        when(targetReadRepository.findCommentSummaries(Set.of(456L))).thenReturn(Map.of());

        var result = service.getLogs(ADMIN_ID, CREDENTIAL_VERSION, query);

        assertThat(result.getContent()).containsExactly(
                new AdminOperationLogItemResult(103L, 8L, AdminOperationLogTargetType.POST, 123L,
                        ModerationAction.HIDE, ModerationReasonType.LEGACY, null, "유지할 게시글 사유", NOW, null),
                new AdminOperationLogItemResult(102L, 9L, AdminOperationLogTargetType.COMMENT, 456L,
                        ModerationAction.RESTORE, ModerationReasonType.LEGACY, null, "기존 댓글 복원 사유", NOW, null),
                new AdminOperationLogItemResult(101L, 8L, AdminOperationLogTargetType.POST, 124L,
                        ModerationAction.RESTORE, ModerationReasonType.LEGACY, null, "찾은 기록 사유", NOW, present)
        );
        assertThat(result.getTotalElements()).isEqualTo(43);
        assertThat(result.getTotalPages()).isEqualTo(3);
        verify(targetReadRepository).findPostSummaries(Set.of(123L, 124L));
        verify(targetReadRepository).findCommentSummaries(Set.of(456L));
        verifyNoMoreInteractions(targetReadRepository);
    }

    @ParameterizedTest
    @EnumSource(value = AdminOperationLogTargetType.class, names = {"POST", "COMMENT"})
    void 한_종류의_대상만_있는_페이지도_요약을_연결한다(AdminOperationLogTargetType type) {
        givenActor(MemberStatus.ACTIVE, MemberRole.ADMIN);
        var query = defaultQuery();
        var current = summary(42L, type == AdminOperationLogTargetType.POST ? "게시글 제목" : null, "현재 본문");
        var log = type == AdminOperationLogTargetType.POST
                ? postLog(101L, 123L, ModerationAction.HIDE, "게시글 사유") : commentLog(101L, 123L);
        when(operationLogReadRepository.findLogs(query)).thenReturn(
                new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));
        if (type == AdminOperationLogTargetType.POST) {
            when(targetReadRepository.findPostSummaries(Set.of(123L))).thenReturn(Map.of(123L, current));
        } else {
            when(targetReadRepository.findCommentSummaries(Set.of(123L))).thenReturn(Map.of(123L, current));
        }

        var result = service.getLogs(ADMIN_ID, CREDENTIAL_VERSION, query);

        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.logId()).isEqualTo(101L);
            assertThat(item.targetId()).isEqualTo(123L);
            assertThat(item.targetType()).isEqualTo(type);
            assertThat(item.currentTarget()).isEqualTo(current);
        });
        verify(targetReadRepository).findPostSummaries(type == AdminOperationLogTargetType.POST ? Set.of(123L) : Set.of());
        verify(targetReadRepository).findCommentSummaries(type == AdminOperationLogTargetType.COMMENT ? Set.of(123L) : Set.of());
        verifyNoMoreInteractions(targetReadRepository);
    }

    @Test
    void 범위_밖_빈_페이지도_번호와_전체_개수와_정렬을_유지한다() {
        givenActor(MemberStatus.ACTIVE, MemberRole.ADMIN);
        var query = new AdminOperationLogReadQuery(null, null, null, null, 2, 10, AdminOperationLogSort.OLDEST);
        var pageable = PageRequest.of(2, 10, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        when(operationLogReadRepository.findLogs(query)).thenReturn(new PageImpl<>(List.of(), pageable, 15));

        var result = service.getLogs(ADMIN_ID, CREDENTIAL_VERSION, query);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotalElements()).isEqualTo(15);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getSort()).isEqualTo(pageable.getSort());
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isTrue();
        verify(operationLogReadRepository).findLogs(query);
        verify(targetReadRepository).findPostSummaries(Set.of());
        verify(targetReadRepository).findCommentSummaries(Set.of());
        verifyNoMoreInteractions(operationLogReadRepository, targetReadRepository);
    }

    private AdminOperationLogCurrentTargetResult summary(Long authorId, String title, String preview) {
        return new AdminOperationLogCurrentTargetResult(
                new AdminOperationLogTargetAuthorResult(authorId, "콘텐츠 작성자", false), title, preview);
    }

    private ContentModerationLog postLog(Long logId, Long postId, ModerationAction action, String reason) {
        var log = ContentModerationLog.forPost(8L, postId, action, reason, NOW);
        ReflectionTestUtils.setField(log, "id", logId);
        return log;
    }

    private ContentModerationLog commentLog(Long logId, Long commentId) {
        var log = ContentModerationLog.forComment(9L, commentId, ModerationAction.RESTORE, "기존 댓글 복원 사유", NOW);
        ReflectionTestUtils.setField(log, "id", logId);
        return log;
    }

    private void givenActor(MemberStatus status, MemberRole role) {
        Member actor = Member.createPending(
                "operation-reader@example.com",
                "test-only-password-hash",
                "기록조회회원"
        );

        if (status != MemberStatus.PENDING) {
            actor.activeAfterEmailVerification();
        }

        if (status == MemberStatus.DELETED) {
            actor.withdraw(NOW);
        }

        ReflectionTestUtils.setField(actor, "id", ADMIN_ID);
        ReflectionTestUtils.setField(actor, "role", role);
        ReflectionTestUtils.setField(
                actor,
                "credentialVersion",
                CREDENTIAL_VERSION
        );

        when(memberRepository.findById(ADMIN_ID))
                .thenReturn(Optional.of(actor));
    }

    private void assertRejected(
            Long memberId,
            long credentialVersion,
            AdminOperationLogReadException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                service.getLogs(
                        memberId,
                        credentialVersion,
                        defaultQuery()
                )
        ).isInstanceOfSatisfying(
                AdminOperationLogReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );

        // 인증 또는 권한 검사에 실패하면 기록을 조회하면 안 된다.
        verifyNoInteractions(operationLogReadRepository, targetReadRepository);
    }

    private AdminOperationLogReadQuery defaultQuery() {
        return new AdminOperationLogReadQuery(
                null, null, null, null, 0, 20
        );
    }
}