package io.github.takgeun.iyum.auth.security.config;

import io.github.takgeun.iyum.auth.security.MemberLoginStatusChecker;
import io.github.takgeun.iyum.auth.security.MemberUserDetailsService;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)    // 이 설정 클래스의 @Bean 메서드 호출을 가로채는 프록시를 만들지 않겠다. 파라미터 주입 방식 선호
public class AuthenticationConfig {

    // 이게 바로 MemberUserDetailsService에 @Service를 추가하지 않아도 되는 이유.
    // 스프링 실행 시 이 설정파일을 통해 곧바로 @Bean으로 등록하기 떄문
    // 이러한 설정 클래스에서의 @Bean 방식은 관련 객체의 생성·연결을 한 곳에서 확인하고 환경·조건별 등록을 관리하기 쉬움
    @Bean
    public MemberUserDetailsService memberUserDetailsService(
            MemberRepository memberRepository
    ) {
        // 이메일로 회원 정보 조회하는 역할
        return new MemberUserDetailsService(memberRepository);
    }

    @Bean
    public DaoAuthenticationProvider memberAuthenticationProvider(
            MemberUserDetailsService memberUserDetailsService,
            PasswordEncoder passwordEncoder // 입력한 비밀번호와 저장된 해시 비교
    ) {
        // 회원 조회와 비밀번호 검증을 수행
        // DaoAuthenticationProvider는 UserDetailsService와 PasswordEncoder를 사용해
        // 인증하는 Spring Security 구현체 -> 따라서 비밀번호 비교 로직을 직접 작성할 필요 없음.
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(memberUserDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        // 없는 이메일과 틀린 비밀번호를 같은 인증 실패로 처리한다.
        provider.setHideUserNotFoundExceptions(true);

        // 회원 상태 검사는 비밀번호가 일치한 뒤 수행한다.
        // Spring Security에는 비밀번호 검증 전에 계정 상태를 검사하는 단계가 있다.
        // 이러한 단계를 비밀번호 검증 후로 옮기겠다는 것.
        // -> 즉 비밀번호가 틀려도 해당 회원의 상태가 PENDING이구나~ 하고 알아채는 걸 방지하는 것.
        provider.setPreAuthenticationChecks(userDetails -> {
        });

        provider.setPostAuthenticationChecks(
                // 비밀번호가 맞은 회원의 상태 검사
                new MemberLoginStatusChecker()
        );

        return provider;
    }

    // 인증을 요청받는 진입점
    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider memberAuthenticationProvider
    ) {
        return new ProviderManager(memberAuthenticationProvider);
    }
}
