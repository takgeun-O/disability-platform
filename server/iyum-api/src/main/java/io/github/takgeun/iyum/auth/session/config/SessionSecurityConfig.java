package io.github.takgeun.iyum.auth.session.config;

import io.github.takgeun.iyum.auth.session.security.SessionLockingSecurityContextRepository;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
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
import org.springframework.web.util.HttpSessionMutexListener;

import java.util.List;

/**
 * 로그인 흐름
 * 1. 프론트엔드가 CSRF 토큰을 받아 로그인 요청을 보낸다.
 * 2. 서버가 CSRF 토큰과 이메일·비밀번호를 검증한다.
 * 3. 인증 성공 후 이 전략을 실행해 세션 ID를 바꾸고 기존 CSRF 토큰을 폐기한다.
 * 4. 로그인한 사용자의 SecurityContext를 저장한다.
 * 5. 로그인 성공 응답을 반환한다.
 * 6. 프론트엔드가 CSRF 토큰을 다시 받아 이후 요청에 사용한다.
 * 로그인 컨트롤러에서 이런 세션 처리를 우리가 직접 호출해야 한다.
 *
 * 이 클래스는 로그인에 필요한 세션·인증 정보·CSRF 처리 도구를 만들어 Spring에 등록하는 설정 클래스임.
 * 이 클래스 자체가 로그인 요청을 처리하는 것이 아니라 로그인 컨트롤러와 보안 설정이 여기서 등록한 객체를 사용해서 실제 처리 수행
 */
@Configuration(proxyBeanMethods = false)
public class SessionSecurityConfig {

    // SecurityContextRepository : 인증 정보를 요청 속성과 세션에 저장
    // LoginController 쪽에서 주입 받아 사용한다.
    // 저장 흐름 : 기존 세션의 잠금 획득 -> 요청 속성에 저장 -> 세션에 저장 -> 잠금 해제
    // 기존 세션이 없다면 잠금 없이 실제 저장소에 위임
    @Bean
    public SecurityContextRepository securityContextRepository() {

        // 1. 인증 정보 저장소를 조합한다.
        SecurityContextRepository delegate =
                new DelegatingSecurityContextRepository(
                        new RequestAttributeSecurityContextRepository(),    // 현재 HTTP 요청의 속성에 Context 보관
                        new HttpSessionSecurityContextRepository()  // 다음 요청에서도 사용할 수 있도록 세션에 Context 보관
                );

        // 기존 세션에 저장할 때 공통 잠금 사용
        return new SessionLockingSecurityContextRepository(delegate);
    }

    // 세션마다 공통 잠금 객체를 준비
    // 이 리스너는 특정 사건이 발생했을 때 호출되는 객체. (이 리스너가 관심 갖는 사건은 세션 생성과 소멸임)
    //  - 세션이 만들어지면 -> 세션 속성에 mutex 객체를 넣는다.
    //  - 세션이 소멸하면 -> 해당 속성을 정리한다.
    @Bean
    public ServletListenerRegistrationBean<HttpSessionMutexListener> sessionMutexListener() {
        // ServletListenerRegistrationBean: 이 리스너를 서블릿 컨테이너에 등록해서 실제로 호출되게 해주는 연결 장치
        // Object mutex = WebUtils.getSessionMutex(session); 이런 코드가 가능하게 해줌. (같은 세션에 마련된 공통 객체를 얻을 수 있게 해줌)
        return new ServletListenerRegistrationBean<>(
                new HttpSessionMutexListener()
        );
    }

    // CSRF 토큰을 세션에 저장·조회
    // SecurityContext: 누가 로그인했는지와 권한을 확인
    // CSRF 토큰: 상태 변경 요청의 CSRF 검증에 사용
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        // CsrfTokenRepository : CSRF 토큰을 생성·저장·조회·삭제하는 역할
        // 여기서는 HttpSessionCsrfTokenRepository를 사용하므로 서버의 HTTP 세션에 요청 검증에 사용할 CSRF 토큰 저장
        return new HttpSessionCsrfTokenRepository();
    }

    // 로그인 성공 후 실행할 두 가지 작업을 묶는다.
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
                        // 세션 고정 공격을 방지하기 위해 하는 작업임. 공격자가 로그인 전에 알아낸 세션 ID를 로그인 후에도 이용하지 못하게 하는 게 목적
                        new ChangeSessionIdAuthenticationStrategy(),
                        csrfStrategy
                )
        );
    }
}
