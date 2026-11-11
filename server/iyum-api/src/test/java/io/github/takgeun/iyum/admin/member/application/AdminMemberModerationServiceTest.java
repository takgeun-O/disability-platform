package io.github.takgeun.iyum.admin.member.application;

import io.github.takgeun.iyum.admin.member.domain.MemberModerationAction;
import io.github.takgeun.iyum.admin.member.domain.MemberModerationLog;
import io.github.takgeun.iyum.admin.member.infrastructure.MemberModerationLogRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

import static io.github.takgeun.iyum.admin.member.application.AdminMemberModerationException.Code.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMemberModerationServiceTest {

    private static final Long ACTOR_ID = 40L;
    private static final Long TARGET_ID = 20L;
    private static final long ACTOR_VERSION = 3L;

    private static final Instant NOW =
            Instant.parse("2026-11-11T10:00:00Z");

    @Mock
    MemberRepository memberRepository;

    @Mock
    MemberModerationLogRepository logRepository;

    private AdminMemberModerationService service;

    @BeforeEach
    void setUp() {
        service = new AdminMemberModerationService(
                memberRepository,
                logRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @ParameterizedTest
    @EnumSource(MemberModerationAction.class)
    void 실제_상태를_변경하고_정규화한_사유로_이력을_저장한다(
            MemberModerationAction action
    ) {
        Member actor = admin();
        Member target = member(
                TARGET_ID,
                action.beforeStatus(),
                MemberRole.USER,
                5L
        );
        stubMembers(actor, target);

        AdminMemberModerationResult result =
                invoke(action, "  운영 정책 검토 결과  ");

        assertThat(result.memberId()).isEqualTo(TARGET_ID);
        assertThat(result.status()).isEqualTo(action.afterStatus());
        assertThat(result.changed()).isTrue();

        assertThat(target.getStatus()).isEqualTo(action.afterStatus());
        assertThat(target.getCredentialVersion()).isEqualTo(6L);
        assertThat(actor.getCredentialVersion()).isEqualTo(ACTOR_VERSION);

        ArgumentCaptor<MemberModerationLog> captor =
                ArgumentCaptor.forClass(MemberModerationLog.class);

        verify(logRepository).save(captor.capture());

        MemberModerationLog log = captor.getValue();

        assertThat(log.getActorMemberId()).isEqualTo(ACTOR_ID);
        assertThat(log.getTargetMemberId()).isEqualTo(TARGET_ID);
        assertThat(log.getAction()).isEqualTo(action);
        assertThat(log.getBeforeStatus()).isEqualTo(action.beforeStatus());
        assertThat(log.getAfterStatus()).isEqualTo(action.afterStatus());
        assertThat(log.getReason()).isEqualTo("운영 정책 검토 결과");
        assertThat(log.getCreatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @EnumSource(MemberModerationAction.class)
    void 이미_요청한_상태이면_인증_버전과_이력을_변경하지_않는다(
            MemberModerationAction action
    ) {
        Member target = member(
                TARGET_ID,
                action.afterStatus(),
                MemberRole.USER,
                5L
        );
        stubMembers(admin(), target);

        AdminMemberModerationResult result = invoke(action, "재확인");

        assertThat(result.memberId()).isEqualTo(TARGET_ID);
        assertThat(result.status()).isEqualTo(action.afterStatus());
        assertThat(result.changed()).isFalse();
        assertThat(target.getCredentialVersion()).isEqualTo(5L);

        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @ValueSource(longs = {10L, 50L})
    void 관리자와_대상은_항상_회원_ID_오름차순으로_잠근다(
            long targetId
    ) {
        Member target = member(
                targetId,
                MemberStatus.ACTIVE,
                MemberRole.USER,
                0L
        );
        stubMembers(admin(), target);

        service.suspend(
                ACTOR_ID,
                ACTOR_VERSION,
                targetId,
                "운영 정책 위반"
        );

        InOrder order = inOrder(memberRepository);

        order.verify(memberRepository)
                .findByIdForUpdate(Math.min(ACTOR_ID, targetId));

        order.verify(memberRepository)
                .findByIdForUpdate(Math.max(ACTOR_ID, targetId));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 잘못된_요청자_ID이면_저장소를_호출하지_않는다(Long actorId) {
        assertCode(AUTHENTICATION_REQUIRED, () ->
                service.suspend(
                        actorId,
                        ACTOR_VERSION,
                        TARGET_ID,
                        "운영 정책 위반"
                )
        );

        verifyNoInteractions(memberRepository, logRepository);
    }

    @Test
    void 음수_인증_버전이면_저장소를_호출하지_않는다() {
        assertCode(AUTHENTICATION_REQUIRED, () ->
                service.suspend(
                        ACTOR_ID,
                        -1L,
                        TARGET_ID,
                        "운영 정책 위반"
                )
        );

        verifyNoInteractions(memberRepository, logRepository);
    }

    @Test
    void 요청자와_대상이_모두_없으면_인증_오류를_먼저_반환한다() {
        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.empty());
        when(memberRepository.findByIdForUpdate(TARGET_ID))
                .thenReturn(Optional.empty());

        assertCode(AUTHENTICATION_REQUIRED, () ->
                invoke(MemberModerationAction.SUSPEND, "운영 정책 위반")
        );

        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = "ACTIVE",
            mode = EnumSource.Mode.EXCLUDE
    )
    void 요청자가_활성_상태가_아니면_거부한다(MemberStatus status) {
        Member actor = admin();
        ReflectionTestUtils.setField(actor, "status", status);

        Member target = activeTarget();
        stubMembers(actor, target);

        assertCode(AUTHENTICATION_REQUIRED, () ->
                invoke(MemberModerationAction.SUSPEND, "운영 정책 위반")
        );

        assertThat(target.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(target.getCredentialVersion()).isZero();
        verifyNoInteractions(logRepository);
    }

    @Test
    void 요청자의_인증_버전이_다르면_거부한다() {
        stubMembers(admin(), activeTarget());

        assertCode(AUTHENTICATION_REQUIRED, () ->
                service.suspend(
                        ACTOR_ID,
                        ACTOR_VERSION - 1L,
                        TARGET_ID,
                        "운영 정책 위반"
                )
        );

        verifyNoInteractions(logRepository);
    }

    @Test
    void 일반_회원의_요청은_거부한다() {
        Member actor = member(
                ACTOR_ID,
                MemberStatus.ACTIVE,
                MemberRole.USER,
                ACTOR_VERSION
        );
        stubMembers(actor, activeTarget());

        assertCode(ADMIN_REQUIRED, () ->
                invoke(MemberModerationAction.SUSPEND, "운영 정책 위반")
        );

        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void 사유가_비어_있으면_상태를_변경하지_않는다(String reason) {
        Member target = activeTarget();
        stubMembers(admin(), target);

        assertCode(INVALID_REASON, () ->
                invoke(MemberModerationAction.SUSPEND, reason)
        );

        assertThat(target.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(target.getCredentialVersion()).isZero();
        verifyNoInteractions(logRepository);
    }

    @Test
    void 사유가_500_코드포인트를_초과하면_거부한다() {
        Member target = activeTarget();
        stubMembers(admin(), target);

        assertCode(INVALID_REASON, () ->
                invoke(
                        MemberModerationAction.SUSPEND,
                        "가".repeat(501)
                )
        );

        assertThat(target.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(target.getCredentialVersion()).isZero();
        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @EnumSource(MemberModerationAction.class)
    void 이미_요청한_상태여도_잘못된_사유는_거부한다(
            MemberModerationAction action
    ) {
        Member target = member(
                TARGET_ID,
                action.afterStatus(),
                MemberRole.USER,
                5L
        );
        stubMembers(admin(), target);

        assertCode(INVALID_REASON, () -> invoke(action, " "));

        assertThat(target.getStatus()).isEqualTo(action.afterStatus());
        assertThat(target.getCredentialVersion()).isEqualTo(5L);
        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 잘못된_대상_ID는_회원_없음으로_처리한다(Long targetId) {
        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.of(admin()));

        assertCode(MEMBER_NOT_FOUND, () ->
                service.suspend(
                        ACTOR_ID,
                        ACTOR_VERSION,
                        targetId,
                        "운영 정책 위반"
                )
        );

        verify(memberRepository).findByIdForUpdate(ACTOR_ID);
        verifyNoMoreInteractions(memberRepository);
        verifyNoInteractions(logRepository);
    }

    @Test
    void 대상_회원이_없으면_거부한다() {
        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.of(admin()));
        when(memberRepository.findByIdForUpdate(TARGET_ID))
                .thenReturn(Optional.empty());

        assertCode(MEMBER_NOT_FOUND, () ->
                invoke(MemberModerationAction.SUSPEND, "운영 정책 위반")
        );

        verifyNoInteractions(logRepository);
    }

    @Test
    void 자기_자신에게_조치할_수_없다() {
        Member actor = admin();

        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.of(actor));

        assertCode(SELF_MODERATION_NOT_ALLOWED, () ->
                service.suspend(
                        ACTOR_ID,
                        ACTOR_VERSION,
                        ACTOR_ID,
                        "운영 정책 위반"
                )
        );

        assertThat(actor.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(actor.getCredentialVersion()).isEqualTo(ACTOR_VERSION);

        verify(memberRepository).findByIdForUpdate(ACTOR_ID);
        verifyNoMoreInteractions(memberRepository);
        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @EnumSource(MemberModerationAction.class)
    void 다른_관리자_계정도_조치할_수_없다(
            MemberModerationAction action
    ) {
        Member target = member(
                TARGET_ID,
                action.beforeStatus(),
                MemberRole.ADMIN,
                5L
        );
        stubMembers(admin(), target);

        assertCode(ADMIN_TARGET_NOT_ALLOWED, () ->
                invoke(action, "운영 정책 위반")
        );

        assertThat(target.getStatus()).isEqualTo(action.beforeStatus());
        assertThat(target.getCredentialVersion()).isEqualTo(5L);
        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = {"ACTIVE", "SUSPENDED"},
            mode = EnumSource.Mode.EXCLUDE
    )
    void 허용하지_않은_상태에서는_정지와_해제를_거부한다(
            MemberStatus status
    ) {
        Member target = member(
                TARGET_ID,
                status,
                MemberRole.USER,
                5L
        );
        stubMembers(admin(), target);

        assertCode(MEMBER_STATUS_NOT_ALLOWED, () ->
                invoke(MemberModerationAction.SUSPEND, "운영 정책 위반")
        );

        assertCode(MEMBER_STATUS_NOT_ALLOWED, () ->
                invoke(
                        MemberModerationAction.LIFT_SUSPENSION,
                        "재검토 결과 해제"
                )
        );

        assertThat(target.getStatus()).isEqualTo(status);
        assertThat(target.getCredentialVersion()).isEqualTo(5L);
        verifyNoInteractions(logRepository);
    }

    @ParameterizedTest
    @EnumSource(MemberModerationAction.class)
    void 인증_버전_오버플로이면_상태와_이력을_변경하지_않는다(
            MemberModerationAction action
    ) {
        Member target = member(
                TARGET_ID,
                action.beforeStatus(),
                MemberRole.USER,
                Long.MAX_VALUE
        );
        stubMembers(admin(), target);

        assertThatThrownBy(() -> invoke(action, "운영 정책 검토"))
                .isInstanceOf(ArithmeticException.class);

        assertThat(target.getStatus()).isEqualTo(action.beforeStatus());
        assertThat(target.getCredentialVersion())
                .isEqualTo(Long.MAX_VALUE);

        verifyNoInteractions(logRepository);
    }

    @Test
    void 이력_저장_실패는_삼키지_않고_호출자에게_전파한다() {
        stubMembers(admin(), activeTarget());

        IllegalStateException failure =
                new IllegalStateException("이력 저장 실패");

        when(logRepository.save(any(MemberModerationLog.class)))
                .thenThrow(failure);

        assertThatThrownBy(() ->
                invoke(MemberModerationAction.SUSPEND, "운영 정책 위반")
        ).isSameAs(failure);

        // 이 테스트는 Spring 트랜잭션 없이 실행되는 단위 테스트다.
        // 실제 DB 롤백 여부는 다음 통합 테스트에서 확인한다.
    }

    private AdminMemberModerationResult invoke(
            MemberModerationAction action,
            String reason
    ) {
        return switch (action) {
            case SUSPEND -> service.suspend(
                    ACTOR_ID,
                    ACTOR_VERSION,
                    TARGET_ID,
                    reason
            );
            case LIFT_SUSPENSION -> service.liftSuspension(
                    ACTOR_ID,
                    ACTOR_VERSION,
                    TARGET_ID,
                    reason
            );
        };
    }

    private void stubMembers(Member actor, Member target) {
        when(memberRepository.findByIdForUpdate(actor.getId()))
                .thenReturn(Optional.of(actor));

        when(memberRepository.findByIdForUpdate(target.getId()))
                .thenReturn(Optional.of(target));
    }

    private Member admin() {
        return member(
                ACTOR_ID,
                MemberStatus.ACTIVE,
                MemberRole.ADMIN,
                ACTOR_VERSION
        );
    }

    private Member activeTarget() {
        return member(
                TARGET_ID,
                MemberStatus.ACTIVE,
                MemberRole.USER,
                0L
        );
    }

    private Member member(
            Long id,
            MemberStatus status,
            MemberRole role,
            long credentialVersion
    ) {
        Member member = Member.createPending(
                "member" + id + "@example.com",
                "test-only-password-hash",
                "회원" + id
        );

        // 다양한 상태를 만드는 테스트 전용 설정이다.
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(member, "role", role);
        ReflectionTestUtils.setField(
                member,
                "credentialVersion",
                credentialVersion
        );

        return member;
    }

    private void assertCode(
            AdminMemberModerationException.Code expected,
            Runnable action
    ) {
        AdminMemberModerationException exception = assertThrows(
                AdminMemberModerationException.class,
                action::run
        );

        assertThat(exception.getCode()).isEqualTo(expected);
    }
}