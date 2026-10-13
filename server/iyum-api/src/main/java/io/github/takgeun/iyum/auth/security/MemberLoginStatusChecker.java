package io.github.takgeun.iyum.auth.security;

import io.github.takgeun.iyum.member.domain.MemberStatus;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;

public class MemberLoginStatusChecker implements UserDetailsChecker {

    private final AccountStatusUserDetailsChecker accountStatusChecker =
            new AccountStatusUserDetailsChecker();

    @Override
    public void check(UserDetails userDetails) {
        // 1. 우리가 만든 MemberPrincipal인지 확인한다.
        if(!(userDetails instanceof MemberPrincipal principal)) {
            throw new InternalAuthenticationServiceException(
                    "지원하지 않는 회원 인증 정보입니다."
            );
        }

        // 2. PENDING이면 이메일 인증 필요 예외를 발생시킨다.
        if(principal.getStatus() == MemberStatus.PENDING) {
            throw new EmailVerificationRequiredException();
        }

        // 3. 공통적인 계정 상태 검사를 Spring Security의 계정 상태 검사에 맡긴다.
        // 이 검사기는 principal의 다음 4가지 값을 확인한다.
        // - isAccountNonLocked() : 계정이 잠기지 않았는가?
        // - isEnabled() : 사용 가능한 계정인가?
        // - isAccountNonExpired() : 계정이 만료되지 않았는가?
        // - isCredentialsNonExpired() : 비밀번호 등 인증정보가 만료되지 않았는가?
        // 위 조건을 만족 못하면 각각 적절한 예외를 발생시킨다.
        accountStatusChecker.check(principal);
    }
}
