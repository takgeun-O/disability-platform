package io.github.takgeun.iyum.auth.session.security;

/**
 * 이 클래스 만들게 된 배경 (이 모든 순서를 이 클래스가 하는 건 아님)
 * -> 같은 브라우저의 여러 탭에서 요청이 동시에 실행될 수 있다.
 * 1. 비밀번호 변경 요청이 기존 인증 정보 확인
 * 2. 다른 로그인 요청이 새 인증 정보를 세션에 저장
 * 3. 비밀번호 변경 요청이 뒤늦게 자기 인증 정보를 저장해서 덮어씀.
 *
 * 이를 막기 위해 세션 갱신에서
 * 기존 인증 확인 -> 새 인증 저장을 수행하는 동안
 * 다른 로그인 요청의 저장이 끼어들지 못하게 해야 한다.
 */

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.util.WebUtils;

import java.util.Objects;

/**
 * 기본 인증 정보 저장소를 감싸서, 같은 세션에 저장하는 작업들이 공통 잠금을 사용하게 만드는 클래스
 * 이 클래스는 저장하는 순간에만 잠근다.
 */
public final class SessionLockingSecurityContextRepository
        implements SecurityContextRepository {
    // 클래스 레벨의 final: 상속 금지
    // SecurityCOntextRepository: Spring Security에서 인증 정보를 담은 SecurityContext를 조회하고 저장하는 역할
    // -> 이름에 Repository가 있지만 반드시 DB에 저장하는 것은 아님. (HttpSessionSecurityContextRepository는 HTTP 세션에 저장)

    // 실제 일을 맡길 저장소
    // 필드 레벨의 final: 생성 후 delegate 참조를 다른 객체로 바꿀 수 없게 한다.
    private final SecurityContextRepository delegate;

    public SessionLockingSecurityContextRepository(
            SecurityContextRepository delegate
    ) {
        // 실제 저장소랍시고 null이 전달되면 생성 시점에 바로 예외를 발생시키는 코드
        // 즉, 실제 저장소 없이 이 클래스가 만들어지는 것을 막는다.
        this.delegate = Objects.requireNonNull(delegate);
    }

    // loadContex()는 그냥 단순히 호환을 위해 기존 메서드도 그대로 위임한 것.
    // 이 클래스에서는 saveContext()를 중심으로 보자.
    @Override
    @SuppressWarnings("deprecation")
    public SecurityContext loadContext(
            HttpRequestResponseHolder holder
    ) {
        // 조회는 그대로 기존 저장소에 맡긴다.
        return delegate.loadContext(holder);    // deprecated
    }

    // Deferred: 지연된
    // loadDeferredContext(): 실제 인증 정보가 필요해지는 시점까지 조회를 미룬다. (비동기나 별도 스레드 실행은 아님)
    @Override
    public DeferredSecurityContext loadDeferredContext(
            HttpServletRequest request
    ) {
        // 조회는 그대로 기존 저장소에 맡긴다.
        return delegate.loadDeferredContext(request);
    }

    @Override
    public boolean containsContext(
            // 조회는 그대로 기존 저장소에 맡긴다.
            HttpServletRequest request
    ) {
        return delegate.containsContext(request);
    }

    // 여기가 핵심!
    @Override
    public void saveContext(
            SecurityContext context,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // false: 기존 세션만 가져오고, 그 세션이 없으면 새로 만들지 마라.
        HttpSession session = request.getSession(false);

        if(session == null) {
            // 잠글 기존 세션이 없으므로 바로 저장을 위임한다.
            // 실제 저장소의 설정과 Context 내용에 따라 새 세션이 만들어질 수 있음.
            // 일반 로그인 동작 때 지원
            delegate.saveContext(context, request, response);
            return;
            // 로그아웃된 세션을 되살리지 않아야 하는 비밀번호 변경 후 갱신 작업은
            // 호출자 쪽에서 별도로 기존 세션의 유효성을 확인해야 한다.
        }

        // 세션이 있으면 공통 잠금 객체를 얻는다.
        // mutex는 여러 스레드가 동시에 특정 작업을 하지 못하게 할 때 사용하는 공통 잠금 객체
        // getSessionMutex(): 세션에 등록된 전용 mutex가 있으면 그것을 반환하고
        // 없으면 세션 객체 자체를 반환한다.
        // 같은 세션을 다루는 코드들이 동일한 잠금 객체를 사용해야 한다.
        Object mutex = WebUtils.getSessionMutex(session);

        // "이 mutex를 사용한 잠금 구간에는 한번에 하나의 스레드만 들어올 수 있다."
        // 다른 요청이 같은 mutex를 잡고 있으면 기다렸다가 들어간다.
        synchronized (mutex) {
            // 실제로 덮어쓰기를 막는 과정
            // 비밀번호 변경 요청 A / 로그인 요청 B (A, B 둘 다 이 Repository를 거친다고 가정)
            // 1. 세션 mutex 획득 / 로그인 처리 중
            // 2. 기존 회원·인증 버전 확인 / saveContext() 호출
            // 3. 새 인증 정보를 저장 / 같은 mutex가 잠겨 있으므로 대기
            // 4. 잠금 해제 / 잠금을 얻어 로그인 인증 정보 저장
            delegate.saveContext(context, request, response);
        }
        // 참고로 synchronized는 같은 JVM 안에서만 작동하므로
        // 단일 서버의 메모리 세션 구성을 대상으로 한다.
        // 초기 IYUM은 서버 1대, Spring Boot 프로세스 1개로 운영할 예정이니 당장은 OK.
        // 향후 여러 애플리케이션 인스턴스로 확장하면 재검토 필요
    }
}
