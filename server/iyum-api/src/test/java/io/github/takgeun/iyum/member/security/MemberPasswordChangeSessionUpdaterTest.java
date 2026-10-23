package io.github.takgeun.iyum.member.security;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.member.application.MemberPasswordChangeResult;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.web.util.HttpSessionMutexListener;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

class MemberPasswordChangeSessionUpdaterTest {

    private final MemberRepository memberRepository =
            mock(MemberRepository.class);

    private final MemberPasswordChangeAuthenticationFactory authenticationFactory =
            mock(MemberPasswordChangeAuthenticationFactory.class);

    private final SessionAuthenticationStrategy sessionAuthenticationStrategy =
            mock(SessionAuthenticationStrategy.class);

    private final MemberPasswordChangeSessionUpdater updater =
            new MemberPasswordChangeSessionUpdater(
                    memberRepository,
                    authenticationFactory,
                    sessionAuthenticationStrategy
            );

    private MockHttpServletRequest request;
    private MockHttpSession session;
    private UsernamePasswordAuthenticationToken authentication;
    private SecurityContext context;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        session = new MockHttpSession();

        // 실제 설정처럼 세션별 공통 잠금 객체를 준비한다.
        new HttpSessionMutexListener().sessionCreated(
                new HttpSessionEvent(session)
        );

        request.setSession(session);

        authentication = authenticatedMember();
        context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        session.setAttribute(
                SPRING_SECURITY_CONTEXT_KEY,
                context
        );
    }

    @Test
    void 기존_세션과_인증을_변경하지_않고_캡처한다() {
        String originalSessionId = session.getId();

        var snapshot = updater.capture(request, authentication);

        assertThat(snapshot).isNotNull();
        assertThat(request.getSession(false)).isSameAs(session);
        assertThat(session.getId()).isEqualTo(originalSessionId);

        assertThat(session.getAttribute(SPRING_SECURITY_CONTEXT_KEY))
                .isSameAs(context);

        assertThat(context.getAuthentication())
                .isSameAs(authentication);

        MemberPrincipal principal =
                (MemberPrincipal) authentication.getPrincipal();

        assertThat(principal.getCredentialVersion()).isEqualTo(3L);
    }

    @Test
    void 세션이_없으면_새로_만들지_않는다() {
        MockHttpServletRequest noSessionRequest =
                new MockHttpServletRequest();

        var snapshot = updater.capture(
                noSessionRequest,
                authentication
        );

        assertThat(snapshot).isNull();
        assertThat(noSessionRequest.getSession(false)).isNull();
    }

    @Test
    void 세션에_인증_컨텍스트가_없으면_거절한다() {
        session.removeAttribute(SPRING_SECURITY_CONTEXT_KEY);

        var snapshot = updater.capture(request, authentication);

        assertThat(snapshot).isNull();
    }

    @Test
    void 같은_회원이라도_세션의_인증_객체가_교체됐으면_거절한다() {
        // 회원 ID와 버전은 같지만 새로 만들어진 인증 객체다.
        var anotherAuthentication = authenticatedMember();

        SecurityContext anotherContext =
                SecurityContextHolder.createEmptyContext();

        anotherContext.setAuthentication(anotherAuthentication);

        session.setAttribute(
                SPRING_SECURITY_CONTEXT_KEY,
                anotherContext
        );

        var snapshot = updater.capture(request, authentication);

        assertThat(snapshot).isNull();

        // 교체된 인증을 지우거나 수정하지 않는다.
        assertThat(session.getAttribute(SPRING_SECURITY_CONTEXT_KEY))
                .isSameAs(anotherContext);

        assertThat(anotherContext.getAuthentication())
                .isSameAs(anotherAuthentication);
    }

    @Test
    void 인증이_없거나_미인증_상태이면_거절한다() {
        assertThat(updater.capture(request, null)).isNull();

        var unauthenticated =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        authentication.getPrincipal(),
                        null
                );

        assertThat(updater.capture(request, unauthenticated))
                .isNull();
    }

    @Test
    void 세션을_읽는_도중_무효화되면_거절한다() {
        MockHttpSession expiringSession = spy(session);
        request.setSession(expiringSession);

        doAnswer(invocation -> {
            expiringSession.invalidate();
            throw new IllegalStateException("테스트용 세션 무효화");
        }).when(expiringSession)
                .getAttribute(SPRING_SECURITY_CONTEXT_KEY);

        var snapshot = updater.capture(request, authentication);

        assertThat(snapshot).isNull();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void 캡처한_세션과_인증이_그대로이면_재확인을_통과한다() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        assertThat(updater.isOriginalSession(snapshot, request))
                .isTrue();
    }

    @Test
    void 스냅샷이_없으면_재확인을_거절한다() {
        assertThat(updater.isOriginalSession(null, request))
                .isFalse();
    }

    @Test
    void 캡처_후_세션이_무효화되면_거절하고_새로_만들지_않는다() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        session.invalidate();

        assertThat(updater.isOriginalSession(snapshot, request))
                .isFalse();

        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void 캡처_후_다른_세션으로_바뀌면_거절한다() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        MockHttpSession replacementSession = new MockHttpSession();

        // 인증 내용이 그대로여도 세션 자체가 다르면 거절해야 한다.
        replacementSession.setAttribute(
                SPRING_SECURITY_CONTEXT_KEY,
                context
        );

        request.setSession(replacementSession);

        assertThat(updater.isOriginalSession(snapshot, request))
                .isFalse();

        // 새로 연결된 세션을 무효화하거나 수정하지 않는다.
        assertThat(replacementSession.isInvalid()).isFalse();

        assertThat(replacementSession.getAttribute(
                SPRING_SECURITY_CONTEXT_KEY
        )).isSameAs(context);
    }

    @Test
    void 캡처_후_세션_ID가_바뀌면_거절한다() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        String previousSessionId = session.getId();

        request.changeSessionId();

        assertThat(session.getId()).isNotEqualTo(previousSessionId);

        // 같은 세션 객체여도 ID가 변경됐으면 거절한다.
        assertThat(request.getSession(false)).isSameAs(session);

        assertThat(updater.isOriginalSession(snapshot, request))
                .isFalse();
    }

    @Test
    void 캡처_후_컨텍스트가_교체되면_거절한다() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        SecurityContext replacementContext =
                SecurityContextHolder.createEmptyContext();

        // 인증 객체가 같아도 컨텍스트 자체가 교체됐다면 거절한다.
        replacementContext.setAuthentication(authentication);

        session.setAttribute(
                SPRING_SECURITY_CONTEXT_KEY,
                replacementContext
        );

        assertThat(updater.isOriginalSession(snapshot, request))
                .isFalse();

        assertThat(session.getAttribute(SPRING_SECURITY_CONTEXT_KEY))
                .isSameAs(replacementContext);
    }

    @Test
    void 캡처_후_같은_컨텍스트의_인증만_교체돼도_거절한다() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        var replacementAuthentication = authenticatedMember();

        context.setAuthentication(replacementAuthentication);

        assertThat(updater.isOriginalSession(snapshot, request))
                .isFalse();

        assertThat(context.getAuthentication())
                .isSameAs(replacementAuthentication);
    }

    @Test
    void prepareAuthentication_returnsNewAuthentication_withoutChangingSession() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        var changed = changedResult();
        var nextAuthentication =
                mock(UsernamePasswordAuthenticationToken.class);

        String originalSessionId = session.getId();

        when(memberRepository.existsByIdAndStatusAndCredentialVersion(
                10L, MemberStatus.ACTIVE, 4L
        )).thenReturn(true);

        when(authenticationFactory.create(authentication, changed))
                .thenReturn(nextAuthentication);

        Authentication prepared =
                updater.prepareAuthentication(snapshot, changed, request);

        assertThat(prepared).isSameAs(nextAuthentication);

        verify(memberRepository)
                .existsByIdAndStatusAndCredentialVersion(
                        10L, MemberStatus.ACTIVE, 4L
                );

        verify(authenticationFactory).create(authentication, changed);

        assertThat(request.getSession(false)).isSameAs(session);
        assertThat(session.getId()).isEqualTo(originalSessionId);
        assertThat(session.getAttribute(SPRING_SECURITY_CONTEXT_KEY))
                .isSameAs(context);
        assertThat(context.getAuthentication()).isSameAs(authentication);
    }

    @Test
    void prepareAuthentication_returnsNull_whenDatabaseStateDoesNotMatch() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        var changed = changedResult();

        when(memberRepository.existsByIdAndStatusAndCredentialVersion(
                10L, MemberStatus.ACTIVE, 4L
        )).thenReturn(false);

        Authentication prepared =
                updater.prepareAuthentication(snapshot, changed, request);

        assertThat(prepared).isNull();
        verifyNoInteractions(authenticationFactory);

        assertThat(session.getAttribute(SPRING_SECURITY_CONTEXT_KEY))
                .isSameAs(context);
        assertThat(context.getAuthentication()).isSameAs(authentication);
    }

    @Test
    void prepareAuthentication_returnsNull_whenAuthenticationWasReplaced() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        var replacementAuthentication = authenticatedMember();
        context.setAuthentication(replacementAuthentication);

        Authentication prepared =
                updater.prepareAuthentication(
                        snapshot,
                        changedResult(),
                        request
                );

        assertThat(prepared).isNull();
        verifyNoInteractions(memberRepository, authenticationFactory);

        assertThat(context.getAuthentication())
                .isSameAs(replacementAuthentication);
    }

    @Test
    void prepareAuthentication_returnsNull_whenSessionExpiresDuringDatabaseCheck() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        when(memberRepository.existsByIdAndStatusAndCredentialVersion(
                10L, MemberStatus.ACTIVE, 4L
        )).thenAnswer(invocation -> {
            session.invalidate();
            return true;
        });

        Authentication prepared =
                updater.prepareAuthentication(
                        snapshot,
                        changedResult(),
                        request
                );

        assertThat(prepared).isNull();
        assertThat(request.getSession(false)).isNull();
        verifyNoInteractions(authenticationFactory);
    }

    @Test
    void prepareAuthentication_returnsNull_whenSnapshotIsMissing() {
        Authentication prepared =
                updater.prepareAuthentication(
                        null,
                        changedResult(),
                        request
                );

        assertThat(prepared).isNull();
        verifyNoInteractions(memberRepository, authenticationFactory);
    }

    private UsernamePasswordAuthenticationToken authenticatedMember() {
        MemberPrincipal principal = new MemberPrincipal(
                10L,
                "member@example.com",
                "",
                "테스트회원",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                3L
        );

        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        authentication.eraseCredentials();

        return authentication;
    }

    private MemberPasswordChangeResult.Changed changedResult() {
        return new MemberPasswordChangeResult.Changed(
                10L,
                "member@example.com",
                "테스트회원",
                MemberRole.USER,
                3L,
                4L
        );
    }
}