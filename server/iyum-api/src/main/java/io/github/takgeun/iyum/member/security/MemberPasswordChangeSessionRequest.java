package io.github.takgeun.iyum.member.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpSession;

import java.util.Objects;

/**
 * 비밀번호 변경 후 세션 갱신에서만 사용하는 요청 래퍼
 * 역할) 이 요청을 전달받은 코드가 새 세션을 생성하거나 다른 세션을 사용하는 것을 차단
 *
 * 여기서는 세션 객체를 비교하고 ID를 고정하지 않는다.
 * 이번 갱신 작업 자체가 세션 ID를 변경해야 하는 작업이기 때문.
 * 변경 시작 전 ID 검사는
 * MemberPasswordChangeSessionUpdater의 isOriginalSession()이 담당한다.
 */
public class MemberPasswordChangeSessionRequest
        extends HttpServletRequestWrapper {

    private final HttpSession expectedSession;
    private String changedSessionId;

    MemberPasswordChangeSessionRequest(
            HttpServletRequest request,
            HttpSession expectedSession
    ) {
        super(request);
        this.expectedSession =
                Objects.requireNonNull(expectedSession, "expectedSession");
    }

    @Override
    public HttpSession getSession(boolean create) {
        HttpSession session = findExpectedSession();

        if (session != null) {
            return session;
        }

        if (create) {
            throw new IllegalStateException(
                    "비밀번호 변경 후 갱신할 기존 세션이 없습니다."
            );
        }

        return null;
    }

    @Override
    public HttpSession getSession() {
        // getSession(true): 이 래퍼 안에서 "기존 세션이 반드시 필요하다"로 제한
        // 기존 세션이 없다면 생성하는 대신 예외 발생시킴
        return getSession(true);
    }

    @Override
    public String changeSessionId() {
        // 기존 세션이 유효한 경우에만 ID 교체를 시작한다. (자식의 검사)
        getSession(true);

        // 실제 ID 교체는 부모에게 맡긴다. (부모가 원래 요청에 위임: 실제 세션 ID 교체)
        // 즉, 부모를 통해 실제 요청의 세션 ID를 교체한다는 것.
        this.changedSessionId = super.changeSessionId();

        // 교체 이후에도 동일한 세션 객체인지 확인한다. (자식의 검사)
        getSession(true);

        return this.changedSessionId;
    }

    private HttpSession findExpectedSession() {
        try {
            HttpSession currentSession = super.getSession(false);

            if (currentSession != expectedSession) {
                return null;
            }

            // 무효화된 세션이라면 IllegalStateException이 발생한다.
            // 생성 시각을 사용하려는 목적이 아니라
            // 무효화된 세션인지 확인하기 위해서 호출
            // 해당 메서드는 무효화된 세션에서 IllegalStateException 발생시킨다.
            currentSession.getCreationTime();

            return currentSession;
        } catch (IllegalStateException expiredSession) {
            return null;
        }
    }

    String getChangedSessionId() {
        return this.changedSessionId;
    }
}
