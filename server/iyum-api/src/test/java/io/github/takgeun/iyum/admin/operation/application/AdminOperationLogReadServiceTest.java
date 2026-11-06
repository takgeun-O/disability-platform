package io.github.takgeun.iyum.admin.operation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.admin.operation.infrastructure.AdminOperationLogReadRepository;
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
class AdminOperationLogReadServiceTest {

    private static final Long ADMIN_ID = 7L;
    private static final long CREDENTIAL_VERSION = 3L;

    private static final Instant NOW =
            Instant.parse("2026-11-04T03:00:00Z");

    @Mock
    MemberRepository memberRepository;

    @Mock
    AdminOperationLogReadRepository operationLogReadRepository;

    @InjectMocks
    AdminOperationLogReadService service;

    @Test
    void 관리자는_기록을_조회하고_항목과_페이지_정보를_유지한다() {
        givenActor(MemberStatus.ACTIVE, MemberRole.ADMIN);

        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        null, null, null, null, 1, 2
                );

        // 조회 요청자 7번과 실제 조치 관리자 8번을 구분한다.
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
                        NOW
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
                        NOW.minusSeconds(60)
                )
        );

        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getSort()).isEqualTo(pageable.getSort());
        assertThat(result.hasNext()).isTrue();

        // 관리자 검증이 기록 조회보다 먼저 실행되어야 한다.
        var order = inOrder(
                memberRepository,
                operationLogReadRepository
        );
        order.verify(memberRepository).findById(ADMIN_ID);
        order.verify(operationLogReadRepository).findLogs(query);

        verifyNoMoreInteractions(
                memberRepository,
                operationLogReadRepository
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
        verifyNoInteractions(operationLogReadRepository);
    }

    private AdminOperationLogReadQuery defaultQuery() {
        return new AdminOperationLogReadQuery(
                null, null, null, null, 0, 20
        );
    }
}