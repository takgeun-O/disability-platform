package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberInputPolicy;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberNicknameUpdateService {

    private final MemberRepository memberRepository;

    @Transactional
    public MemberProfileResult updateNickname(
            Long memberId,
            String nickname
    ) {
        if (memberId == null) {
            throw new MemberNicknameUpdateException(
                    MemberNicknameUpdateException.Code.MEMBER_NOT_FOUND
            );
        }

        String normalizeNickname = normalizeNickname(nickname);

        // 동일 회원에 대한 변경 작업을 순서대로 처리한다.
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new MemberNicknameUpdateException(
                                MemberNicknameUpdateException.Code.MEMBER_NOT_FOUND
                        )
                );

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new MemberNicknameUpdateException(
                    MemberNicknameUpdateException.Code.MEMBER_NOT_ACTIVE
            );
        }

        // 앞뒤 공백 제거 후 완전히 같은 값이라면 변경 없이 성공한다.
        if (member.getNickname().equals(normalizeNickname)) {
            return MemberProfileResult.from(member);
        }

        // 조회한 시점에 중복이 없는지 확인
        // 다만 실제로 저장하는 순간까지 중복이 없음을 보장하지 못함.
        // 이미 존재하는 중복을 미리 확인하고 이해하기 쉬운 서비스 예외 발생
        if (memberRepository.existsByNicknameIgnoreCaseExcludingMemberId(
                normalizeNickname,
                memberId
        )) {
            throw new MemberNicknameUpdateException(
                    MemberNicknameUpdateException.Code.NICKNAME_ALREADY_EXISTS
            );
        }

        member.changeNickname(normalizeNickname);

        // 동시 요청까지 포함하여 중복 저장을 최종적으로 차단
        // 사전 중복 검사만으로 충분하지 않음. 두 회원이 거의 동시에 같은 닉네임 요청 시
        // 둘 다 "아직 사용 중인 회원이 없다"는 결과를 받을 수 있음
        // 따라서 findByIdUpdate() 와 uk_members_nickname_lower 두 장치를 같이 사용
        // findByIdForUpdate(): 같은 회원의 변경 요청이 동시에 실행되는 것 제어
        // uk_members_nickname_lower 고유 인덱스: 서로 다른 회원이 같은 닉네임을 저장하는 것을 최종 차단
        try {
            // 변경 SQL을 실행해 DB의 고유 제약 위반을 여기서 확인
            // 오해하기 쉬운 게 flush()는 중복 조회를 한번 더 하는 코드가 아님. (또한 커밋도 아님)
            // changeNickname()은 자바 객체의 값을 바꾸고
            // flush()가 변경 SQL을 DB로 보낸다.
            // 이 때 보낸 SQL을 실행할 때 DB가 고유 제약 검사
            // 만약 명시적인 flush()가 없으면 SQL이 나중에 커밋 과정에서 실행되고
            // 그러면 이 메서드 안의 try-catch에서 예외를 잡기 어려워서 여기서 미리 실행하는 것.
            memberRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraintViolation(exception);
        }

        return MemberProfileResult.from(member);
    }

    private String normalizeNickname(String nickname) {
        try {
            return MemberInputPolicy.normalizedNickname(nickname);
        } catch (IllegalArgumentException exception) {
            throw new MemberNicknameUpdateException(
                    MemberNicknameUpdateException.Code.INVALID_NICKNAME,
                    exception
            );
        }
    }

    private RuntimeException translateConstraintViolation(
            DataIntegrityViolationException exception
    ) {
        for (
                Throwable cause = exception;
                cause != null;
                cause = cause.getCause()
        ) {
            if (cause instanceof ConstraintViolationException violation
                    && "uk_members_nickname_lower".equals(
                    violation.getConstraintName()
            )) {
                return new MemberNicknameUpdateException(
                        MemberNicknameUpdateException.Code.NICKNAME_ALREADY_EXISTS,
                        exception
                );
            }
        }

        // 닉네임 중복이 아닌 DB 오류를 중복 오류로 잘못 처리하지 않는다.
        return exception;
    }
}
