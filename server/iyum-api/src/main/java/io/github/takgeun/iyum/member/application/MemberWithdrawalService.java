package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.auth.emailverification.infrastructure.EmailVerificationTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberAgreementRepository;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    private final MemberRepository memberRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final MemberAgreementRepository agreementRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public void withdraw(
            Long memberId,
            String currentPassword
    ) {
        if (memberId == null || memberId <= 0) {
            throw new MemberWithdrawalException(
                    MemberWithdrawalException.Code.MEMBER_NOT_FOUND
            );
        }

        validatePasswordInput(currentPassword);

        // 기존 닉네임 변경·이메일 인증 처리와 동일하게
        // 회원 행을 먼저 잠근다. (같은 회원 행에 여러 후속 작업이 들어왔을 때 순차적으로 처리할 수 있게 하기 위함)
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new MemberWithdrawalException(
                                MemberWithdrawalException.Code.MEMBER_NOT_FOUND
                        )
                );

        // 개인정보가 제거된 회원의 null 비밀번호 해시를
        // PasswordEncoder에 전달하지 않도록 상태부터 확인
        // 비밀번호 검증 전 회원 상태 확인
        // 이미 탈퇴해서 개인정보가 제거된 회원이면 다음 값이 null일 수 있다.
        // member.getPasswordHash() -> null
        if(member.getStatus() != MemberStatus.ACTIVE) {
            throw new MemberWithdrawalException(
                    MemberWithdrawalException.Code.MEMBER_NOT_ACTIVE
            );
        }

        if(!passwordEncoder.matches(
                currentPassword,
                member.getPasswordHash()
        )) {
            throw new MemberWithdrawalException(
                    MemberWithdrawalException.Code.PASSWORD_MISMATCH
            );
        }

        // 비밀번호 검증이 성공한 뒤에만 데이터를 변경한다.
        tokenRepository.deleteAllByMemberId(memberId);
        agreementRepository.deleteAllByMemberId(memberId);

        member.withdraw(clock.instant());

        // 관리 중인 Member의 변경 SQL을 실행한다.
        // flush는 커밋이 아니므로 이후 실패하면 함께 롤백
        memberRepository.flush();
    }

    private void validatePasswordInput(String password) {
        if (password == null
                || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new MemberWithdrawalException(
                    MemberWithdrawalException.Code.INVALID_PASSWORD
            );
        }
    }
}
