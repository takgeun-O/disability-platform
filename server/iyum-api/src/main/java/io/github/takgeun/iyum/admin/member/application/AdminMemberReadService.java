package io.github.takgeun.iyum.admin.member.application;

import io.github.takgeun.iyum.admin.member.infrastructure.AdminMemberReadRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1. 조회 요청자의 현재 관리자 자격 검증
 * 2. 회원 목록·상세를 Repository에서 조회
 * 3/ 회원 엔티티를 Result객체로 변환
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberReadService {

    private final MemberRepository memberRepository;
    private final AdminMemberReadRepository memberReadRepository;

    public Page<AdminMemberItemResult> getMembers(
            @Nullable Long actorMemberId,
            long credentialVersion,
            AdminMemberReadQuery query
    ) {
        validateAdmin(actorMemberId, credentialVersion);

        return memberReadRepository.findMembers(query)
                .map(AdminMemberItemResult::from);
    }

    public AdminMemberDetailResult getMember(
            @Nullable Long actorMemberId,
            long credentialVersion,
            @Nullable Long targetMemberId
    ) {
        // 대상 회원의 존재 여부를 조회하기 전에
        // 요청자의 관리자 자격부터 검증
        validateAdmin(actorMemberId, credentialVersion);

        if(targetMemberId == null || targetMemberId <= 0) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.MEMBER_NOT_FOUND
            );
        }

        Member target = memberReadRepository
                .findMemberById(targetMemberId)
                .orElseThrow(() ->
                        new AdminMemberReadException(
                                AdminMemberReadException.Code.MEMBER_NOT_FOUND
                        )
                );

        return AdminMemberDetailResult.from(target);
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

        // 로그인 당시 정보가 현재도 유효한지 확인한다.
        if (actor.getStatus() != MemberStatus.ACTIVE
                || actor.getCredentialVersion() != credentialVersion) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        // 유효한 인증 정보지만 현재 관리자가 아닌 경우
        if (actor.getRole() != MemberRole.ADMIN) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.ADMIN_REQUIRED
            );
        }
    }
}
