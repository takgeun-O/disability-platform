package io.github.takgeun.iyum.auth.session.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import java.util.List;

/**
 * 로그인 흐름
 * 1. 프론트엔드가 CSRF 토큰을 받아 로그인 요청을 보낸다.
 * 2. 서버가 CSRF 토큰과 이메일·비밀번호를 검증한다.
 * 3. 인증 성공 후 이 전략을 실행해 세션 ID를 바꾸고 기존 CSRF 토큰을 폐기한다.
 * 4. 로그인한 사용자의 SecurityContext를 저장한다.
 * 5. 로그인 성공 응답을 반환한다.
 * 6. 프론트엔드가 CSRF 토큰을 다시 받아 이후 요청에 사용한다.
 *
 * 로그인 컨트롤러에서 이런 세션 처리를 우리가 직접 호출해야 한다.
 */
@Configuration(proxyBeanMethods = false)
public class SessionSecurityConfig {

    // SecurityContextRepository : 인증 정보를 저장하는 역할
    @Bean
    public SecurityContextRepository securityContextRepository() {

        // 현재 요청과 HTTP 세션, 두 곳을 활용해 인증 정보를 유지하도록 구성
        // SecurityContext: 현재 사용자의 인증 정보(그러니까 Authentication)를 담는 객체. 안에 있는 Authentication에는 MemberPrincipal과 권한 등이 들어감.
        // SecurityContextRepository: SecurityContext를 저장하고 불러오는 역할 (이름에 Repository가 있지만 JPA로 DB테이블에 저장하는 리포지토리는 아님.)
        // 등록된 아래 저장소에 저장 임무를 위임한다고 생각하기.
        return new DelegatingSecurityContextRepository(
                // RequestAttributeSecurityCOntextRepository(): 현재 요청 안에서 보관
                // SecurityContext를 현재 HTTP 요청의 속성에 저장한다.
                // 주요 목적 : 하나의 요청이 오류 처리 등으로 다시 전달될 때도 인증 정보를 참조할 수 있게 하는 것.
                // -> 다만 이 저장소만으로는 다음 HTTP 요청까지 로그인 상태를 유지할 수 없다.
                new RequestAttributeSecurityContextRepository(),

                // HttpSessionSecurityContextRepository : 다음 요청에서도 로그인 유지
                // SecurityContext를 서버의 HTTP 세션에 저장하고 불러오는 역할
                new HttpSessionSecurityContextRepository()
        );
    }

    // CSRF 토큰을 세션에 저장
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        // CsrfTokenRepository : CSRF 토큰을 생성·저장·조회·삭제하는 역할
        // 여기서는 HttpSessionCsrfTokenRepository를 사용하므로 서버의 HTTP 세션에 요청 검증에 사용할 CSRF 토큰 저장
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    public SessionAuthenticationStrategy loginSessionAuthenticationStrategy(
            CsrfTokenRepository csrfTokenRepository
    ) {
        // 로그인 성공 시 기존 CSRF 토큰 폐기
        // 이 객체는 인증 성공 시 기존 CSRF 토큰을 제거하는 처리를 담당한다.
        // 폐기 후 새 토큰은 GET /api/v1/auth/csrf 를 다시 호출하는 방식으로 가져올 수 있음.
        // 여기서 받은 새 토큰을 이후 POST 등의 요청에 활용하면 된다.
        CsrfAuthenticationStrategy csrfStrategy =
                new CsrfAuthenticationStrategy(csrfTokenRepository);

        // XOR 방식으로 CSRF 토큰을 노출하도록 설정
        // 핸들러가 요청에서 사용할 CSRF 토큰을 준비할 때 난수와 XOR 연산을 이용해 마스킹된 형태로 제공 (보안을 위해)
        csrfStrategy.setRequestHandler(
                new XorCsrfTokenRequestAttributeHandler()
        );

        // Composite : 여러 전략을 하나로 묶는다는 의미
        // 1. 기존 세션 ID 변경
        // 2. 기존 CSRF 토큰 폐기 및 요청의 토큰 처리 준비
        return new CompositeSessionAuthenticationStrategy(
                List.of(
                        // 로그인 성공 시 세션 ID 변경
                        // 기존 세션이 있다면 세션에 담긴 속성을 유지하면서 세션 ID를 변경한다.
                        // 세션 고정 공격을 방지하기 위해 하는 작업임. 공격자가 로그인 전에 알아낸 세션 ID를 로그인 후에도 이요하지 못하게 하는 게 목적
                        new ChangeSessionIdAuthenticationStrategy(),
                        csrfStrategy
                )
        );
    }
}
