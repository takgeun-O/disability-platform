package io.github.takgeun.iyum.member.security;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.auth.session.security.SessionLockingSecurityContextRepository;
import io.github.takgeun.iyum.member.application.MemberPasswordChangeResult;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.util.Objects;

import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.*;

/**
 * 이 클래스는 다음과 같은 순서로 동작해야 한다.
 * <p>
 * 1. 변경 요청이 시작될 때 세션이 여전히 유효한지 확인 - 목적 : 로그아웃·만료된 세션을 되살리지 않기
 * 2. 세션의 회원 ID와 변경 전 인증 버전 확인 - 목적 : 다른 인증 정보로 바뀐 세션을 덮어쓰지 않기
 * 3. DB에서 ACTIVE와 이번 변경 결과의 버전 확인 - 목적 : 이후 재설정·탈퇴가 발생했으면 로그인 유지 중단
 * 4. MemberPasswordChangeAuthenticationFactory로 새 인증 객체 생성 - 목적 : 기존 인증 객체를 직접 수정하지 않기
 * 5. 세션 ID 교체 및 기존 CSRF 토큰 폐기 - 목적 : 변경 후 세션 보안 정보 갱신
 * 6. 새 SecurityContext를 만들어 명시적으로 저장 - 목적 : 다음 요청에서도 로그인 유지
 * <p>
 * 이 업데이터는 비밀번호 변경 서비스가 Changed를 반환한 뒤에 실행되어야 한다.
 * 참고로 현재 비밀번호 변경 서비스는 내부 트랜잭션의 커밋까지 마친 후 결과를 반환하는 구조임
 */
@Component
public class MemberPasswordChangeSessionUpdater {

    // 비밀번호 변경 서비스에서 사용 중인 인터페이스
    private final MemberRepository memberRepository;
    private final MemberPasswordChangeAuthenticationFactory authenticationFactory;

    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    private final RequestAttributeSecurityContextRepository requestContextRepository =
            new RequestAttributeSecurityContextRepository();

    // 갱신 전용 저장소
    // 역할) 인증 저장 과정에서 새 세션 생성 차단
    private final SecurityContextRepository sessionUpdateRepository =
            createSessionUpdateRepository();

    public MemberPasswordChangeSessionUpdater(
            MemberRepository memberRepository,
            MemberPasswordChangeAuthenticationFactory authenticationFactory,
            // 이 전략에는 세션 ID 교체와 CSRF 처리가 함꼐 들어있음.
            @Qualifier("loginSessionAuthenticationStrategy")
            SessionAuthenticationStrategy sessionAuthenticationStrategy
    ) {
        this.memberRepository = memberRepository;
        this.authenticationFactory = authenticationFactory;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    /**
     * 1. 현재 세션과 인증 정보를 기억하는 capture()
     * 1) 무엇을 기억해야 할까?
     * 비밀번호를 변경하는 동안 다른 요청이 로그아웃하거나 다시 로그인할 수 있으므로, 다음 정보를 기억해둘 것.
     * - 기존 세션 객체 : 같은 세션을 사용하고 있는가?
     * - 공통 잠금 객체 : 확인과 저장에 같은 잠금을 사용하는가?
     * - 기존 세션 ID : 다른 요청이 세션 ID를 교체했는가?
     * - 기존 SecurityContext : 세션의 인증 컨텍스트가 교체됐는가?
     * - 기존 Authentication : 다른 로그인으로 인증이 교체됐는가?
     * <p>
     * 앞으로 이러한 정보를 묶은 객체를 SessionSnapshot이라고 부를 것. (나중에 비교할 참조와 세션 ID를 기억하기 위한 것)
     */
    // 변경 서비스 호출 전에 기존 세션과 인증 정보를 기억한다.
    // 반환값이 null이면 사용할 수 있는 기존 인증 세션을 확보하지 못했다는 의미
    // 새 세션 생성이나 인증 정보 변경은 수행하지 않는다.
    public SessionSnapshot capture(
            HttpServletRequest request,
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                instanceof MemberPrincipal principal)
                || principal.getStatus() != MemberStatus.ACTIVE
                || !principal.isEnabled()
                || principal.getMemberId() == null
                || principal.getMemberId() <= 0
                || principal.getCredentialVersion() < 0) {
            return null;
        }

        try {
            // 기존 세션만 조회한다. 없으면 새로 만들지 않는다.
            HttpSession session = request.getSession(false);

            if (session == null) {
                return null;
            }

            // SessionSecurityConfig의 sessionMutexListener()에서 가져오는 공통 잠금 객체
            Object mutex = WebUtils.getSessionMutex(session);

            synchronized (mutex) {
                Object stored = session.getAttribute(
                        SPRING_SECURITY_CONTEXT_KEY
                );

                // 잠금을 기다리는 동안 세션이나 인증이 달라질 수 있으므로 다시 확인
                if (request.getSession(false) != session
                        || !(stored instanceof SecurityContext context)

                        // 여기서 의도적으로 equals() 사용하지 않음
                        // 확인하려는 것은 내용이 비슷한가가 아니라 요청이 가지고 온 인증 객체가 여전히 세션에 저장된 바로 그 객체인가를 확인하려는 것
                        // 같은 회원이 다시 로그인했다면 회원 ID와 인증 버전이 같아도
                        // 새로운 인증 객체가 만들어질 수 있다.
                        // 즉, 이전 요청이 그 새 로그인을 자기 인증으로 취급하면 안되므로 객체의 동일성을 확인하고자 하는 것
                        || context.getAuthentication() != authentication
                ) {
                    return null;
                }

                // SessionSnapshot을 업데이터의 필드로 보관하지 않는다. (중요)
                // @Component인 업데이터는 여러 요청이 함꼐 사용한다.
                // 따라서 요청마다 다른 세션 정보는 새 객체로 반환하고, 이후 Controller의 지역변수로 보관할 것
                return new SessionSnapshot(
                        session,
                        mutex,
                        session.getId(),
                        context,
                        authentication
                );
            }
        } catch (IllegalStateException expiredSession) {
            // 세션을 읽는 도중 로그아웃·만료로 무효화된 경우
            return null;
        }

        // capture()가 성공했다고 이후까지 세션이 계속 유효하다는 뜻은 아님.
        // 아래 update()에서 반드시 다시 확인해야 한다.
    }

    /**
     * 2. 왜 다시 확인해야 할까?
     * capture()와 실제 세션 갱신 사이에는 비밀번호 변경 서비스가 실행된다.
     * 그동안 다른 요청이 로그아웃하거나 다시 로그인할 수 있기 때문에
     * capture()가 성공했더라도 이후 상태가 달라질 수 있다.
     * <p>
     * 이번 메서드는 다음 조건을 확인한다.
     * 비교 대상
     * - 세션 객체 : 처음 기억한 세션과 같은 객체인지?
     * - 세션 ID : 처음 기억한 ID와 동일한지?
     * - SecurityContext : 처음 기억한 컨텍스트와 같은 객체인지?
     * - Authentication : 처음 기억한 인증과 같은 객체인지?
     * <p>
     * 하나라도 달라졌다면 이전 요청이 그 세션에 새 인증을 덮어쓰면 안된다.
     */
    // capture()로 기억한 세션과 인증 정보가 지금도 그대로 유지되고 있는지 확인한다.
    // 이 결과는 확인 시점에만 유효하다.
    // 실제 갱신에서는 같은 잠금을 유지한 채 확인과 저장을 이어가야 한다.
    boolean isOriginalSession(
            SessionSnapshot snapshot,
            HttpServletRequest request
    ) {
        // 이 조건을 통과하면 currentSession은 null이 아님.
        // SessionSnapshot은 capture()에서 실제 세션이 있을 때만 생성했기 때문
        if (snapshot == null) {
            return false;
        }

        try {
            synchronized (snapshot.mutex) {
                HttpSession currentSession = request.getSession(false);

                if (currentSession != snapshot.session) {
                    return false;
                }

                if (!snapshot.sessionId.equals(currentSession.getId())) {
                    return false;
                }

                Object stored = currentSession.getAttribute(
                        SPRING_SECURITY_CONTEXT_KEY
                );

                if (stored != snapshot.context) {
                    return false;
                }

                return snapshot.context.getAuthentication()
                        == snapshot.authentication;
            }
        } catch (IllegalStateException expiredSession) {
            // 확인하는 도중 세션이 무효화된 경우
            return false;
        }

        // 이렇게 확인을 끝내고 다음처럼 잠금을 놓은 뒤 저장하면, 그 사이에 다른 요청이 끼어들 수 있다.
        // if (isOriginalSession(snapshot, request) { // 이후에 별도 저장 };
        // 따라서 아래 update()에서 더 바깥에서 같은 잠금을 잡고, 재확인부터 저장까지 이어갈 것.
        // Java의 synchronized는 같은 스레드가 이미 가진 잠금에 다시 들어갈 수 있다.
    }

    /**
     * 3. DB의 회원 상태가 ACTIVE이고, 인증 버전이 이번 비밀번호 변경 결과의 버전인지 확인한다.
     * 4. 조건을 만족하면 MemberPasswordChangeAuthenticationFactory로 새 인증 객체를 만든다.
     * <p>
     * 커밋된 비밀번호 변경 결과를 확인하고 새 인증 객체를 준비한다.
     * <p>
     * 반환값이 null이면 현재 요청에서 로그인 유지 절차를 중단해야 한다.
     * 새 인증 객체를 반환하더라도 아직 세션에 저장된 것은 아니다.
     * <p>
     * 최종 갱신에서는 동일한 세션 잠금을 유지한 채
     * 이 메서드 호출부터 세션 갱신과 저장까지 수행해야 한다.
     */
    // 이 메서드에서 반환하는 null은 세션이나 회원 상태가 유지 조건을 충족하지 못했다는 의미
    Authentication prepareAuthentication(
            SessionSnapshot snapshot,
            MemberPasswordChangeResult.Changed changed,
            HttpServletRequest request
    ) {
        if (snapshot == null) {
            return null;
        }

        Objects.requireNonNull(changed, "changed");

        synchronized (snapshot.mutex) {
            if (!isOriginalSession(snapshot, request)) {
                return null;
            }

            boolean currentChangeStillValid =
                    memberRepository.existsByIdAndStatusAndCredentialVersion(
                            changed.memberId(),
                            MemberStatus.ACTIVE,
                            changed.credentialVersion()
                    );

            if (!currentChangeStillValid) {
                // 이번 비밀번호 변경으로 버전이 3 -> 4가 됐는데
                // 그 직후 비밀번호 재설정이 발생해 DB 버전이 5가 됐다면 이번 로그인 유지 절차 중단
                // 그리고 DB의 최신 버전인 5를 가져와서 현재 세션에 넣는 것도 안된다.
                return null;
            }

            // DB 조회 도중 세션이 만료되거나 무효화됐을 수도 있으므로 재확인한다.
            if (!isOriginalSession(snapshot, request)) {
                return null;
            }

            return authenticationFactory.create(
                    snapshot.authentication,
                    changed
            );
        }
    }

    /**
     * 비밀번호 변경 트랜잭션이 커밋된 뒤 호출한다.
     *
     * 이 update()는 비밀번호 변경이 이미 DB에 커밋된 뒤, 현재 요청의 로그인을 안전하게 유지할 수 있는자 확인하고
     * 세션을 갱신하는 메서드
     *
     * 여기서 반환하는 두 값
     * SESSION_RETAINED (비밀번호 변경 완료 + 현재 로그인 유지 성공)
     * RELOGIN_REQUIRED (비밀번호 변경 완료 + 현재 요청에서 로그인 유지 불가)
     * 는 모두 비밀번호 변경 자체는 성공한 상태다.
     */
    public MemberPasswordChangeSessionUpdateResult update(
            SessionSnapshot snapshot,
            MemberPasswordChangeResult.Changed changed,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        /**
         * 아래 상황으로 가정
         *  - DB 인증 버전 -> 변경 전 3 / 변경 서비스 완료 후 4
         *  - 현재 세션 인증 버전 -> 변경 전 3 / 변경 서비스 완료 후 아직 3
         *  - 세션 ID -> 변경 전 AAA / 변경 서비스 완료 후 아직 AAA
         *
         * 서비스 호출 전에 capture()로 기존 세션·Context·Authentication을 snapshot에 기억해준 상태.
         * -> 따라서 changed에는 이번 변경 결과인 이전 버전 3, 새 버전 4가 들어있다.
         */

        /**
         * 1. 필수 결과와 기존 세션 정보 확인
         *  변경 성공 결과가 없으면 잘못된 호출이므로 즉시 예외를 발생시킨다.
         *
         *  snapshot이 없다는 건, 변경 전에 갱신 대상으로 사용할 인증 세션을 확보하지 못했다는 의미
         *  이 때는 현재 요청의 인증 정보를 정리하고 재로그인이 필요하다고 반환한다.
         */
        Objects.requireNonNull(changed, "changed"); // changed가 null인지 확인하고, null이면 즉시 예외 발생 시키기

        if (snapshot == null) {
            // SecurityContextHolder에 있는 현재 실행 흐름의 인증
            // 현재 HTTP 요청 속성에 보관한 Context
            // 위 두 곳을 정리한다. (다만 세션 전체를 무효화하는 것은 아님)
            clearRequestAuthentication(request, response);
            return MemberPasswordChangeSessionUpdateResult.RELOGIN_REQUIRED;
        }

        /**
         * 2. 공통 잠금을 잡고 갱신 준비한다.
         * 여기서부터 메서드의 잠금 구간을 빠져나올 때까지, 같은 mutex를 사용하는
         * 다른 요청의 저장 작업이 끼어들지 못하게 한다.
         */
        synchronized (snapshot.mutex) {
            // 비밀번호 변경 후 세션 갱신에서만 사용하는 요청 래퍼를 만든다.
            // 이 래퍼는 이후 전략과 저장소가 세션을 사용할 때 처음 기억한 기존 세션만 사용하게 하고
            // 새 세션 생성을 차단한다.
            MemberPasswordChangeSessionRequest sessionRequest =
                    new MemberPasswordChangeSessionRequest(
                            request,
                            snapshot.session
                    );

            Authentication nextAuthentication = null;   // 새 버전 4를 담을 인증 객체
            SecurityContext nextContext = null; // 새 인증 객체를 담을 Context
            boolean retained = false;   // 모든 단계가 끝나 로그인 유지에 성공했는지 표시 (finally에서 사용)

            /**
             * 3. 세션과 DB를 확인한 뒤 새 인증 객체 생성
             */
            try {
                // prepareAuthentication() : 다음과 같은 순서로 수행한다.
                // 1. 세션 객체·ID·Context·Authentication이 capture() 때와 같은지 확인
                // 2. DB 회원이 ACTIVE이고 인증 버전이 이번 변경 결광니 4인지 확인
                // 3. DB 조회 중 세션이 달라졌을 수 있으므로 다시 확인
                // 4. Factory로 버전 4의 새 인증 객체 생성
                nextAuthentication =
                        prepareAuthentication(snapshot, changed, request);

                if (nextAuthentication == null  // 로그인 유지 조건을 충족하지 못함
                        || response.isCommitted()   // 응답 헤더가 이미 확정돼 새 세션 쿠키 등을 안전하게 전달하기 어려움
                        || !isOriginalSession(snapshot, request)    // 원래 세션이 아님(다른 요청이 바꾼 세션·인증을 덮어쓰면 안됨)
                ) {
                    return MemberPasswordChangeSessionUpdateResult.RELOGIN_REQUIRED;
                }

                /**
                 * 4. 새 SecurityContext 생성
                 * 기존 Context를 직접 수정하지 않고, 새 인증 객체를 담을 새 Context를 만든다.
                 *
                 * 세션에 저장된 기존 Context : 인증 정보 버전 3
                 * 지역변수 nextContext : 인증 정보 버전 4
                 */
                nextContext = SecurityContextHolder.createEmptyContext();
                nextContext.setAuthentication(nextAuthentication);
                // 이제 새 Context를 세션에 저장할 차례다.

                /**
                 * 5. 세션 ID 교체와 CSRF 처리
                 */
                try {
                    // 여기서 주입된 전략은 아래 두 전략의 조합이다.
                    // 1. 기존 세션 ID 교체
                    // 2. 기존 CSRF 토큰 폐기 및 새 토큰 처리 준비
                    // 여기서 세션 ID가 AAA -> BBB로 바뀐다.
                    sessionAuthenticationStrategy.onAuthentication(
                            nextAuthentication,

                            // 원래 request 대신 sessionRequest 래퍼를 전달했다는 점이 중요.
                            // 전략이 세션을 얻거나 ID를 바꿀 때 래퍼의 검사를 거치도록 하는 것.
                            sessionRequest,
                            response
                    );

                    // 래퍼가 실제 ID 교체 과정에서 기록한 새 ID BBB를 가져온다.
                    String changedSessionId =
                            sessionRequest.getChangedSessionId();

                    // 전략이 실제로 세션 ID를 교체했는지 확인
                    // 새 ID가 없거나 여전히 AAA라면, 의도한 교체가 이루어지지 않았으므로 로그인 유지 중단.
                    if (changedSessionId == null
                            || changedSessionId.equals(snapshot.sessionId)) {
                        return MemberPasswordChangeSessionUpdateResult.RELOGIN_REQUIRED;
                    }

                    /**
                     * 6. ID만 바뀌었고, 기존 인증은 그대로인지 확인
                     * 여기서 기대하는 상태는 다음과 같다.
                     *  - 세션 객체: 처음 기억한 객체
                     *  - 세션 ID: 새 ID BBB
                     *  - Context: 기존 Context
                     *  - Authentication: 기존 인증 객체
                     *
                     * 아직 새 인증을 저장하지 않았으므로, 기존 인증이 그대로 있어야 정상이다.
                     * 여기서는 isOriginalSession() 대신 hasSessionContext()를 사용한다.
                     * isOriginalSession()은 ID가 예전 AAA인지 검사하므로,
                     * 우리가 정상적으로 BBB로 교체한 뒤에는 사용할 수 없기 때문.
                     */
                    // ID 교체 이후에도 원래 인증이 그대로 있는지 확인한다.
                    if (!hasSessionContext(
                            snapshot,
                            request,
                            changedSessionId,
                            snapshot.context,
                            snapshot.authentication
                    )) {
                        return MemberPasswordChangeSessionUpdateResult.RELOGIN_REQUIRED;
                    }

                    /**
                     * 7. 새 Context 저장 후 실제 저장 여부 확인
                     * 이제 새 버전 4의 인증 정보를 요청 속성과 세션에 저장한다.
                     *
                     * 여기도 요청 래퍼를 전달하므로, 도중에 기존 세션이 사라졌다면
                     * 새 세션을 만들어 인증을 되살리는 것을 막는다.
                     *
                     * 저장 후에는 실제 세션 내용을 다시 확인한다.
                     */
                    sessionUpdateRepository.saveContext(
                            nextContext,
                            sessionRequest,
                            response
                    );

                    // saveContext()가 예외 없이 저장을 건너뛸 수도 있으므로
                    // 실제 세션의 저장 결과를 확인한다.
                    if (!hasSessionContext(
                            snapshot,
                            request,
                            changedSessionId,
                            nextContext,
                            nextAuthentication
                    )) {
                        return MemberPasswordChangeSessionUpdateResult.RELOGIN_REQUIRED;
                    }

                    /**
                     * 8. 현재 요청에도 새 인증을 적용하고 성공을 반환한다.
                     *
                     * 세션 저장은 다음 요청을 위한 것
                     * SecurityContextHolder 설정은 현재 실행 중인 요청을 위한 것이다.
                     */
                    SecurityContextHolder.setContext(nextContext);

                    retained = true;
                    return MemberPasswordChangeSessionUpdateResult.SESSION_RETAINED;

                    /**
                     * 이로써 정상 완료 상태는 다음과 같다.
                     * - DB 인증 버전 : 4
                     * - 현재 세션 인증 버전 : 4
                     * - 현재 요청 인증 버전 : 4
                     * - 세션 ID : BBB
                     * - 기존 CSRF 토큰 : 폐기
                     */

                } catch (SessionAuthenticationException rejected) {
                    // 세션 인증 전략이 거절하면 재로그인 필요 결과로 처리
                    return MemberPasswordChangeSessionUpdateResult.RELOGIN_REQUIRED;

                } catch (IllegalStateException failure) {
                    // IllegalStateException은 이유를 구분한다.
                    // - 기대한 세션이 없어졌다면 : 재로그인 필요로 처리
                    // - 세션은 여전히 있는데 다른 문제가 발생했다면: 예외를 호출자에게 전달
                    // 즉, 모든 오류를 재로그인 안내로 감추지 않는다.
                    if (sessionRequest.getSession(false) == null) {
                        return MemberPasswordChangeSessionUpdateResult.RELOGIN_REQUIRED;
                    }

                    throw failure;
                }

            } finally {
                /**
                 * 9. 마지막 finally는 실패한 갱신의 흔적을 정리한다.
                 *
                 * 앞선 과정이 성공한 경우 정리를 건너뛴다.
                 *
                 * 성공하지 못한 경우에는 이번 요청이 사용하던 인증 또는
                 * 이번 요청이 저장한 인증만 세션에서 제거하고, 현재 요청의 인증도 정리한다.
                 *
                 * 예를 들어 변경 처리 중 다른 탭이 다른 계정으로 로그인했다면
                 * 1) 세션의 Context가 snapshot과 달라짐
                 * 2) 갱신을 중단하고 RELOGIN_REQUIRED 반환 시도
                 * 3) finally 실행
                 * 4) 새 로그인 인증은 이 요청의 것이 아니므로 유지
                 * 5) 이 비밀번호 변경 요청의 인증만 정리
                 *
                 * 또는 새 Context를 저장하다가 후속 검증에서 실패했다면,
                 * 세션이 남은 이번 요청의 새 인증을 정리한다.
                 *
                 * 다만 위 두 가지 케이스 모두 이미 커밋한 비밀번호 변경은 되돌리지 않는다.
                 */
                if (!retained) {
                    removeOwnedAuthentication(
                            snapshot,
                            sessionRequest,
                            request,
                            nextContext,
                            nextAuthentication
                    );

                    clearRequestAuthentication(request, response);
                }
            }
        }
    }

    /**
     * 한번의 비밀번호 변경 요청에서만 사용하는 비교 정보.
     * 세션이나 DB에 따로 저장하지 않는다.
     */
    public static final class SessionSnapshot {
        /**
         * - 기존 세션 객체 : 같은 세션을 사용하고 있는가?
         * - 공통 잠금 객체 : 확인과 저장에 같은 잠금을 사용하는가?
         * - 기존 세션 ID : 다른 요청이 세션 ID를 교체했는가?
         * - 기존 SecurityContext : 세션의 인증 컨텍스트가 교체됐는가?
         * - 기존 Authentication : 다른 로그인으로 인증이 교체됐는가?
         */
        private final HttpSession session;
        private final Object mutex;
        private final String sessionId;
        private final SecurityContext context;
        private final Authentication authentication;

        private SessionSnapshot(
                HttpSession session,
                Object mutex,
                String sessionId,
                SecurityContext context,
                Authentication authentication
        ) {
            this.session = session;
            this.mutex = mutex;
            this.sessionId = sessionId;
            this.context = context;
            this.authentication = authentication;
        }

        @Override
        public String toString() {
            return "SessionSnapshot[REDACTED]";
        }
    }

    private static SecurityContextRepository createSessionUpdateRepository() {
        HttpSessionSecurityContextRepository sessionRepository =
                new HttpSessionSecurityContextRepository();

        // 요청 래퍼에서도 새 세션 생성을 막고 있지만, 저장소 자체에도 세션 생성 막기
        sessionRepository.setAllowSessionCreation(false);

        SecurityContextRepository delegate =
                new DelegatingSecurityContextRepository(
                        new RequestAttributeSecurityContextRepository(),
                        sessionRepository
                );

        return new SessionLockingSecurityContextRepository(delegate);
    }

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

    private boolean hasSessionContext(
            SessionSnapshot snapshot,
            HttpServletRequest request,
            String expectedSessionId,
            SecurityContext expectedContext,
            Authentication expectedAuthentication
    ) {
        try {
            HttpSession currentSession = request.getSession(false);

            if (currentSession != snapshot.session) {
                return false;
            }

            if (!expectedSessionId.equals(currentSession.getId())) {
                return false;
            }

            Object stored =
                    currentSession.getAttribute(SPRING_SECURITY_CONTEXT_KEY);

            return stored == expectedContext
                    && expectedContext.getAuthentication() == expectedAuthentication;
        } catch (IllegalStateException expiredSession) {
            return false;
        }
    }

    /**
     * snapshot.mutex 잠금을 잡은 상태에서 호출한다.
     * <p>
     * 이번 요청이 사용하던 인증 또는 직접 저장한 인증만 제거한다.
     * 다른 로그인으로 교체된 인증은 유지한다.
     */
    private void removeOwnedAuthentication(
            SessionSnapshot snapshot,
            MemberPasswordChangeSessionRequest sessionRequest,
            HttpServletRequest request,
            SecurityContext nextContext,
            Authentication nextAuthentication
    ) {
        String ownedSessionId = sessionRequest.getChangedSessionId();

        if (ownedSessionId == null) {
            ownedSessionId = snapshot.sessionId;
        }

        try {
            boolean ownsOriginalContext = hasSessionContext(
                    snapshot,
                    request,
                    ownedSessionId,
                    snapshot.context,
                    snapshot.authentication
            );

            boolean ownsNextContext = nextContext != null
                    && hasSessionContext(
                    snapshot,
                    request,
                    ownedSessionId,
                    nextContext,
                    nextAuthentication
            );

            if (ownsOriginalContext || ownsNextContext) {
                snapshot.session.removeAttribute(SPRING_SECURITY_CONTEXT_KEY);
            }

        } catch (IllegalStateException expiredSession) {
            // 정리 도중 이미 무효화된 세션은 추가로 처리하지 않는다.
        }
    }
}
