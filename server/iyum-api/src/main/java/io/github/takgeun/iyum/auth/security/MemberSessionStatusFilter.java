package io.github.takgeun.iyum.auth.security;

import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 세션에 로그인 정보가 남아 있더라도, DB에서 더 이상 ACTIVE 회원이 아니라면 로그아웃 시키는 필터
 *
 * @Component 없이 Spring Security 필터 체인에 직접 등록할 것 (SpringConfig)
 */
public class MemberSessionStatusFilter extends OncePerRequestFilter {

    private final MemberRepository memberRepository;
    private final SecurityContextLogoutHandler securityContextLogoutHandler;
    private final CookieClearingLogoutHandler cookieClearingLogoutHandler;

    /**
     * 생성자에서 로그아웃 처리 도구 준비
     */
    public MemberSessionStatusFilter(
            MemberRepository memberRepository,
            SecurityContextRepository securityContextRepository
    ) {
        this.memberRepository = memberRepository;

        // 서버 쪽 로그인 상태를 정리하는 역할
        this.securityContextLogoutHandler =
                new SecurityContextLogoutHandler();

        this.securityContextLogoutHandler
                .setInvalidateHttpSession(true);    // 현재 요청의 HTTP 세션 무효화

        this.securityContextLogoutHandler
                .setClearAuthentication(true);  // 보안 컨텍스트의 인증 정보를 제거하도록 설정

        this.securityContextLogoutHandler
                .setSecurityContextRepository(
                        securityContextRepository   // 보안 컨텍스트를 저장하는 저장소에도 로그아웃 상태가 반영되도록 연결
                );

        // 브라우저가 갖고 있는 세션 쿠키 JSESSIONID를 삭제하도록 응답에 쿠키 설정 추가
        this.cookieClearingLogoutHandler =
                new CookieClearingLogoutHandler("JSESSIONID");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 요청이 들어오면 현재 인증 정보 확인
        // 이 필터가 정상적으로 동작하려면 세션의 보안 컨텍스트를 불러온 다음에 실행되도록 필터 순서가 설정되어 있어야 한다.
        // 1. (예시) 탈퇴한 회원이 기존 세션으로 마이페이지 요청
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal()
                instanceof MemberPrincipal principal) {

            // 2. 세션에 남아있는 정보인 이전 로그인 정보를 불러온다.
            Long memberId = principal.getMemberId();

            // 로그인 당시 세션에 저장한 버전
            long credentialVersion = principal.getCredentialVersion();

            boolean validSession =
                    memberId != null
                            && memberId > 0
                            && credentialVersion >= 0
                            && memberRepository
                            .existsByIdAndStatusAndCredentialVersion(
                                    memberId,
                                    MemberStatus.ACTIVE,
                                    credentialVersion
                            );

            if(!validSession) {
                securityContextLogoutHandler.logout(
                        request,
                        response,
                        authentication
                );

                cookieClearingLogoutHandler.logout(
                        request,
                        response,
                        authentication
                );
            }
        }

        // 로그아웃했더라도 다음 필터로 요청을 넘긴다.
        // 로그인 상태를 정리한 뒤, 다음 필터들이 비로그인 상태를 기준으로 요청을 처리하도록 넘기는 것.
        filterChain.doFilter(request, response);

        // 이후에는 SecurityConfig 설정에 따라 AnonymousAuthenticationFilter가 실행되는데
        // 인증 정보가 제거됐으므로 익명 사용자로 처리한다.

        // 이후 접속 시도한 마이페이지에서는 .anyRequest().authenticated(); 규칙이 적용된다.
        // 익명 사용자는 이 조건을 충족하지 못하므로, SecurityConfig의 authenticationEntryPoint가 응답한다.
    }
}
