package io.github.takgeun.iyum.auth.security;

import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;

import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

/**
 * 세션에 로그인 정보가 남아 있더라도, DB에서 더 이상 ACTIVE 회원이 아니라면 로그아웃 시키는 필터
 *
 * @Component 없이 Spring Security 필터 체인에 직접 등록할 것 (SpringConfig에서 등록)
 */
public class MemberSessionStatusFilter extends OncePerRequestFilter {

    private final MemberRepository memberRepository;
    private final SecurityContextLogoutHandler securityContextLogoutHandler;
    private final CookieClearingLogoutHandler cookieClearingLogoutHandler;

    // 비밀번호 변경 요청 전용 저장소 추가
    private final RequestAttributeSecurityContextRepository requestContextRepository =
            new RequestAttributeSecurityContextRepository();

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

        SecurityContext requestContext = SecurityContextHolder.getContext();
        Authentication authentication = requestContext.getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal()
                instanceof MemberPrincipal principal) {

            // 반드시 DB 조회 전에 세션과 컨텍스트를 기록한다.
            SessionObservation observation = observeSession(request);

            // 2. 세션에 남아있는 정보인 이전 로그인 정보를 불러온다.
            Long memberId = principal.getMemberId();

            // 로그인 당시 세션에 저장된 인증 버전과 권한
            long credentialVersion = principal.getCredentialVersion();
            MemberRole role = principal.getRole();

            boolean validSession =
                    memberId != null
                            && memberId > 0
                            && credentialVersion >= 0
                            && role != null
                            && memberRepository
                            .existsByIdAndStatusAndCredentialVersionAndRole(
                                    memberId,
                                    MemberStatus.ACTIVE,
                                    credentialVersion,
                                    role
                            );

            if (!validSession) {
                /**
                 * DB에서 무효로 확인된 인증을 정리한다.
                 *
                 * 세션·세션 ID·컨텍스트·인증이 검사 당시와 동일할 때만
                 * 세션을 무효화하고 세션 쿠키를 삭제한다.
                 * 다른 요청이 저장한 새 인증은 보존한다.
                 *
                 * 세션 로그아웃 여부와 관계없이 현재 요청의 인증은 항상 제거.
                 */
                clearInvalidAuthentication(
                        observation,
                        requestContext,
                        authentication,
                        request,
                        response
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

    private SessionObservation observeSession(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);

            if (session == null) {
                return null;
            }

            return new SessionObservation(
                    session,
                    WebUtils.getSessionMutex(session),
                    session.getId()
            );
        } catch (IllegalStateException expiredSession) {
            return null;
        }
    }

    private void clearInvalidAuthentication(
            SessionObservation observation,
            SecurityContext requestContext,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            if (observation == null) {
                return;
            }

            synchronized (observation.mutex()) {
                HttpSession currentSession = request.getSession(false);

                // DB 조회 도중 세션이 사라지거나 다른 세션으로 바뀌었다.
                if (currentSession != observation.session()) {
                    return;
                }

                // 같은 세션 객체라도 다른 작업이 ID를 교체했을 수 있다.
                if (!observation.sessionId().equals(currentSession.getId())) {
                    return;
                }

                Object stored =
                        currentSession.getAttribute(SPRING_SECURITY_CONTEXT_KEY);

                // 다른 작업이 새 컨텍스트나 인증을 저장했다면 보존한다.
                if (stored != requestContext
                        || requestContext.getAuthentication() != authentication) {
                    return;
                }

                // DB에서 무효하다고 판단한 바로 그 인증이
                // 아직 세션에 남아 있을 때만 세션 로그아웃을 수행한다.
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

        } catch (IllegalStateException failure) {
            // 확인과 로그아웃 사이에 세션이 사라진 경우는 정리만 마친다.
            // 같은 세션이 여전히 유효하다면 다른 오류일 수 있으므로 전파한다.
            if (observation != null
                    && request.getSession(false) == observation.session()) {
                throw failure;
            }

        } finally {
            clearRequestAuthentication(request, response);
        }
    }

    // 현재 요청의 인증을 비우는 메서드
    private void clearRequestAuthentication(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        SecurityContextHolder.clearContext();

        SecurityContext emptyContext =
                SecurityContextHolder.createEmptyContext();

        requestContextRepository.saveContext(
                emptyContext,
                request,
                response
        );
    }

    private record SessionObservation(
            HttpSession session,
            Object mutex,
            String sessionId
    ) {
        @Override
        public String toString() {
            return "SessionObservation[REDACTED]";
        }
    }
}
