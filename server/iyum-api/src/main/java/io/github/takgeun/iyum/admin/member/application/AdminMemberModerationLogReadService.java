package io.github.takgeun.iyum.admin.member.application;

import io.github.takgeun.iyum.admin.member.infrastructure.MemberModerationLogRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서비스 조치 기록 조회 서비스 진행 순서
 * 1. 요청자의 현재 관리자 자격과 인증 버전 확인
 * 2. 페이지 범위 검증
 * 3. 대상 회원 존재 여부 확인
 * 4. 해당 회원의 이력을 최신순으로 조회
 * 5. 엔티티를 조회 결과 객체로 변환
 * <p>
 * 탈퇴한 회원이나 관리자 자신의 이력도 조회 가능
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberModerationLogReadService {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    private static final Sort LATEST_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final MemberRepository memberRepository;
    private final MemberModerationLogRepository logRepository;

    public Page<AdminMemberModerationLogResult> getLogs(
            @Nullable Long actorMemberId,
            long credentialVersion,
            @Nullable Long targetMemberId,
            int page,
            int size
    ) {
        // 대상 회원의 존재 여부 확인 전 요청자부터 검증
        validateAdmin(actorMemberId, credentialVersion);

        validatePage(page, size);

        validateTargetExists(targetMemberId);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                LATEST_SORT
        );

        // 조회에는 findByIdForUpdate()를 사용하지 않는다.
        // 회원 상태를 변경하는 작업이 아니기 때문.
        return logRepository
                .findByTargetMemberId(targetMemberId, pageable)
                .map(AdminMemberModerationLogResult::from);
    }

    private void validateAdmin(
            @Nullable Long actorMemberId,
            long credentialVersion
    ) {
        if (actorMemberId == null
                || actorMemberId <= 0
                || credentialVersion < 0) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        Member actor = memberRepository.findById(actorMemberId)
                .orElseThrow(() ->
                        new AdminMemberReadException(
                                AdminMemberReadException.Code.AUTHENTICATION_REQUIRED
                        )
                );

        if (actor.getStatus() != MemberStatus.ACTIVE
                || actor.getCredentialVersion() != credentialVersion) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        if (actor.getRole() != MemberRole.ADMIN) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.ADMIN_REQUIRED
            );
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.INVALID_PAGE_REQUEST
            );
        }

        // int 곱셈 오버플로우 방지
        long offset = (long) page * size;

        if (offset > Integer.MAX_VALUE) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.INVALID_PAGE_REQUEST
            );
        }
    }

    private void validateTargetExists(
            @Nullable Long targetMemberId
    ) {
        if (targetMemberId == null || targetMemberId <= 0) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.MEMBER_NOT_FOUND
            );
        }

        /**
         * 현재 상태나 역할로 대상을 제한하지 않는다.
         *
         * 탈퇴 완료 회원도 DB행이 존재하면 이력을 조회한다.
         * 관리자 자신의 이력을 조회하는 것도 허용
         */
        if(!memberRepository.existsById(targetMemberId)) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.MEMBER_NOT_FOUND
            );
        }
    }
}
