package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private final MemberRepository memberRepository;

    // 읽기 전용 트랜잭션 안에서 회원을 조회하고 결과 객체로 변환
    @Transactional(readOnly = true)
    public MemberProfileResult getProfile(Long memberId) {

        if(memberId == null) {
            throw new MemberProfileException(
                    MemberProfileException.Code.MEMBER_NOT_FOUND
            );
        }

        // findById(): 단순 조회이므로 쓰기 잠금도 사용하지 않음
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberProfileException(
                        MemberProfileException.Code.MEMBER_NOT_FOUND
                ));

        if(member.getStatus() != MemberStatus.ACTIVE) {
            throw new MemberProfileException(
                    MemberProfileException.Code.MEMBER_NOT_ACTIVE
            );
        }

        return MemberProfileResult.from(member);
    }
}
