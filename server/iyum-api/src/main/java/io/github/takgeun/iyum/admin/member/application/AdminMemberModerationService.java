package io.github.takgeun.iyum.admin.member.application;

import io.github.takgeun.iyum.admin.member.domain.MemberModerationAction;
import io.github.takgeun.iyum.admin.member.domain.MemberModerationInputPolicy;
import io.github.takgeun.iyum.admin.member.domain.MemberModerationLog;
import io.github.takgeun.iyum.admin.member.infrastructure.MemberModerationLogRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 조치 서비스 처리 순서)
 * 1. 로그인 정보의 기본 형식을 검증합니다.
 * 2. 관리자와 대상 회원을 회원 ID 오름차순으로 잠금 조회합니다.
 * 3. 관리자의 현재 상태·인증 버전·권한을 검증합니다.
 * 4. 처리 사유를 검증합니다.
 * 5. 자기 자신, 관리자 계정, 변경할 수 없는 회원 상태를 거부합니다.
 * 6. 정지 또는 해제를 실행합니다.
 * 7. 실제 상태가 변경됐을 때만 이력을 저장합니다.
 */
@Service
@RequiredArgsConstructor
public class AdminMemberModerationService {

    private final MemberRepository memberRepository;
    private final MemberModerationLogRepository logRepository;
    private final Clock clock;


    // actorMemberId와 credentialVersion은 로그인한 MemberPrincipal에서
    // 전달받아야 한다. 요청 본문의 값을 사용하지 않는다.
    @Transactional
    public AdminMemberModerationResult suspend(
            Long actorMemberId,
            long credentialVersion,
            Long targetMemberId,
            String reason
    ) {
        return changeState(
                actorMemberId,
                credentialVersion,
                targetMemberId,
                reason,
                MemberModerationAction.SUSPEND
        );
    }

    @Transactional
    public AdminMemberModerationResult liftSuspension(
            Long actorMemberId,
            long credentialVersion,
            Long targetMemberId,
            String reason
    ) {
        return changeState(
                actorMemberId,
                credentialVersion,
                targetMemberId,
                reason,
                MemberModerationAction.LIFT_SUSPENSION
        );
    }

    private AdminMemberModerationResult changeState(
            Long actorMemberId,
            long credentialVersion,
            Long targetMemberId,
            String reason,
            MemberModerationAction action
    ) {
        validateAuthenticationInput(actorMemberId, credentialVersion);

        // 관리자와 대상 회원을 회원 ID 오름차순으로 잠금 조회
        LockedMembers locked = lockMembers(
                actorMemberId,
                targetMemberId
        );

        // 대상 회원에 관한 오류를 반환하기 전에 관리자 자격을 검증한다.
        validateAdmin(locked.actor(), credentialVersion);

        // 이미 요청한 상태인 경우에도 동일하게 사유를 검증한다.
        String normalizedReason = validateReason(reason);

        if (targetMemberId == null || targetMemberId <= 0) {
            throw new AdminMemberModerationException(
                    AdminMemberModerationException.Code.MEMBER_NOT_FOUND
            );
        }

        if (actorMemberId.equals(targetMemberId)) {
            throw new AdminMemberModerationException(
                    AdminMemberModerationException.Code.SELF_MODERATION_NOT_ALLOWED
            );
        }

        Member target = locked.target();

        if (target == null) {
            throw new AdminMemberModerationException(
                    AdminMemberModerationException.Code.MEMBER_NOT_FOUND
            );
        }

        if (target.getRole() != MemberRole.USER) {
            throw new AdminMemberModerationException(
                    AdminMemberModerationException.Code.ADMIN_TARGET_NOT_ALLOWED
            );
        }

        if (target.getStatus() != MemberStatus.ACTIVE
                && target.getStatus() != MemberStatus.SUSPENDED) {
            throw new AdminMemberModerationException(
                    AdminMemberModerationException.Code.MEMBER_STATUS_NOT_ALLOWED
            );
        }

        boolean changed = switch (action) {
            case SUSPEND -> target.suspend();
            case LIFT_SUSPENSION -> target.liftSuspension();
        };

        if(changed) {
            logRepository.save(
                    MemberModerationLog.create(
                            locked.actor.getId(),
                            target.getId(),
                            action,
                            normalizedReason,
                            clock.instant()
                    )
            );
        }

        // target은 JPA 관리 상태이므로 변경 감지로 저장
        // 이력 저장과 회원 상태 변경은 같은 트랜잭션에 포함
        return new AdminMemberModerationResult(
                target.getId(),
                target.getStatus(),
                changed
        );
    }

    private void validateAuthenticationInput(
            Long actorMemberId,
            long credentialVersion
    ) {
        if (actorMemberId == null
                || actorMemberId <= 0
                || credentialVersion < 0) {
            throw new AdminMemberModerationException(
                    AdminMemberModerationException.Code.AUTHENTICATION_REQUIRED
            );
        }
    }

    private LockedMembers lockMembers(
            Long actorMemberId,
            Long targetMemberId
    ) {
        // 대상 ID가 잘못됐거나 자기 자신이면 관리자 행만 잠근다.
        // 이후 관리자 자격을 먼저 검증하고 대상 관련 오류를 반환한다.
        if (targetMemberId == null
                || targetMemberId <= 0
                || actorMemberId.equals(targetMemberId)) {
            Member actor = memberRepository
                    .findByIdForUpdate(actorMemberId)
                    .orElse(null);

            return new LockedMembers(actor, null);
        }

        // 두 회원 행을 잠글 때는 항상 ID 오름차순을 지킨다.
        long firstId = Math.min(actorMemberId, targetMemberId);
        long secondId = Math.max(actorMemberId, targetMemberId);

        Member first = memberRepository
                .findByIdForUpdate(firstId)
                .orElse(null);

        Member second = memberRepository
                .findByIdForUpdate(secondId)
                .orElse(null);

        if (actorMemberId.longValue() == firstId) {
            return new LockedMembers(first, second);
        }

        return new LockedMembers(second, first);
    }

    private void validateAdmin(
            Member actor,
            long credentialVersion
    ) {
        if (actor == null
                || actor.getStatus() != MemberStatus.ACTIVE
                || actor.getCredentialVersion() != credentialVersion) {
            throw new AdminMemberModerationException(
                    AdminMemberModerationException.Code.AUTHENTICATION_REQUIRED
            );
        }

        if (actor.getRole() != MemberRole.ADMIN) {
            throw new AdminMemberModerationException(
                    AdminMemberModerationException.Code.ADMIN_REQUIRED
            );
        }
    }

    private String validateReason(String reason) {
        try {
            return MemberModerationInputPolicy.normalizeReason(reason);
        } catch (IllegalArgumentException exception) {
            throw new AdminMemberModerationException(
                    AdminMemberModerationException.Code.INVALID_REASON
            );
        }
    }

    private record LockedMembers(
            Member actor,
            Member target
    ) {
    }
}
