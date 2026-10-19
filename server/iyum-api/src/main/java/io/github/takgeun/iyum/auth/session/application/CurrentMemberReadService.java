package io.github.takgeun.iyum.auth.session.application;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurrentMemberReadService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public CurrentMemberResult getCurrentMember(Long memberId) {

        if(memberId == null) {
            throw new CurrentMemberReadException(
                    CurrentMemberReadException.Code.MEMBER_NOT_FOUND
            );
        }

        // 단순 조회이므로 findByIdUpdate() 대신 findById() 사용
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CurrentMemberReadException(
                                CurrentMemberReadException.Code.MEMBER_NOT_FOUND
                        )
                );

        if(member.getStatus() != MemberStatus.ACTIVE) {
            throw new CurrentMemberReadException(
                    CurrentMemberReadException.Code.MEMBER_NOT_ACTIVE
            );
        }

        return CurrentMemberResult.from(member);
    }
}
