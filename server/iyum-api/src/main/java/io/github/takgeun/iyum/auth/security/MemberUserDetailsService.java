package io.github.takgeun.iyum.auth.security;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberInputPolicy;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class MemberUserDetailsService implements UserDetailsService {

    private static final String USER_NOT_FOUND_MESSAGE =
            "회원 정보를 확인할 수 없습니다.";

    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true) // 회원 조회와 값 복사는 읽기 전용 트랜잭션 안에서 실행하도록
    public MemberPrincipal loadUserByUsername(String username) {

        String email;

        try {
            email = MemberInputPolicy.normalizeEmail(username);
        } catch (IllegalArgumentException exception) {
            throw new UsernameNotFoundException(
                    USER_NOT_FOUND_MESSAGE
            );
        }

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() ->
                        /**
                         * DaoAuthenticationProvider를 사용하는 일반적인 Spring Security 로그인 구성 기준 순서
                         * 1. 로그인 필터가 이메일과 비밀번호를 받아 인증 요청
                         * 2. AuthenticationManager가 인증 처리를 Provider에게 맡긴다.
                         * 3. DaoAuthenticationProvider가 loadUserByUsername()으로 회원 정보 조회
                         * 4. 회원이 없으면 UsernameNotFoundException 발생
                         * 5. Provider가 기본 설정에 따라 이를 BadCredentialsException으로 바꿔 전달
                         */
                        new UsernameNotFoundException(
                                USER_NOT_FOUND_MESSAGE
                        )
                );

        // 인증용 객체에 필요한 값 복사 (엔티티 자체를 담지 않음)
        // 따라서 이후 인증 정보를 읽을 때 영속성 컨텍스트에 의존하지 않게 된다.
        return new MemberPrincipal(
                member.getId(),
                member.getEmail(),
                member.getPasswordHash(),
                member.getNickname(),
                member.getStatus(),
                member.getRole(),
                member.getCredentialVersion()   // 여기서는 반드시 DB에서 읽은 회원의 버전 전달
        );
    }
}
