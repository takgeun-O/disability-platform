package io.github.takgeun.iyum.member.security;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.member.application.MemberPasswordChangeResult;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.servlet.http.HttpSessionEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import org.springframework.web.util.HttpSessionMutexListener;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

class MemberPasswordChangeSessionUpdaterUpdateTest {

    private MemberRepository memberRepository;
    private MemberPasswordChangeSessionUpdater updater;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockHttpSession session;

    private UsernamePasswordAuthenticationToken authentication;
    private SecurityContext originalContext;

    private HttpSessionCsrfTokenRepository csrfTokenRepository;
    private CsrfToken originalCsrfToken;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        session = spy(new MockHttpSession());

        new HttpSessionMutexListener()
                .sessionCreated(new HttpSessionEvent(session));

        request.setSession(session);
        request.setRequestedSessionId(session.getId());
        request.setRequestedSessionIdValid(true);

        authentication = authenticatedMember();

        originalContext = SecurityContextHolder.createEmptyContext();
        originalContext.setAuthentication(authentication);

        session.setAttribute(
                SPRING_SECURITY_CONTEXT_KEY,
                originalContext
        );

        SecurityContextHolder.setContext(originalContext);

        csrfTokenRepository = new HttpSessionCsrfTokenRepository();
        originalCsrfToken = csrfTokenRepository.generateToken(request);

        csrfTokenRepository.saveToken(
                originalCsrfToken,
                request,
                response
        );

        CsrfAuthenticationStrategy csrfStrategy =
                new CsrfAuthenticationStrategy(csrfTokenRepository);

        csrfStrategy.setRequestHandler(
                new XorCsrfTokenRequestAttributeHandler()
        );

        SessionAuthenticationStrategy strategy =
                new CompositeSessionAuthenticationStrategy(
                        List.of(
                                new ChangeSessionIdAuthenticationStrategy(),
                                csrfStrategy
                        )
                );

        updater = updaterWith(strategy);

        when(memberRepository.existsByIdAndStatusAndCredentialVersion(
                10L,
                MemberStatus.ACTIVE,
                4L
        )).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void retainsSession_withNewAuthenticationAndSecurityInformation() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        String originalSessionId = session.getId();

        var result = updater.update(
                snapshot,
                changedResult(),
                request,
                response
        );

        assertThat(result)
                .isEqualTo(MemberPasswordChangeSessionUpdateResult.SESSION_RETAINED);

        assertThat(request.getSession(false)).isSameAs(session);
        assertThat(session.getId()).isNotEqualTo(originalSessionId);

        SecurityContext savedContext =
                (SecurityContext) session.getAttribute(
                        SPRING_SECURITY_CONTEXT_KEY
                );

        assertThat(savedContext).isNotNull();
        assertThat(savedContext).isNotSameAs(originalContext);
        assertThat(savedContext.getAuthentication())
                .isNotSameAs(authentication);

        MemberPrincipal savedPrincipal =
                (MemberPrincipal) savedContext
                        .getAuthentication()
                        .getPrincipal();

        assertThat(savedPrincipal.getMemberId()).isEqualTo(10L);
        assertThat(savedPrincipal.getCredentialVersion()).isEqualTo(4L);

        assertThat(originalContext.getAuthentication())
                .isSameAs(authentication);

        assertThat(
                ((MemberPrincipal) authentication.getPrincipal())
                        .getCredentialVersion()
        ).isEqualTo(3L);

        assertThat(SecurityContextHolder.getContext())
                .isSameAs(savedContext);

        CsrfToken nextCsrfToken = csrfTokenRepository
                .loadDeferredToken(request, response)
                .get();

        assertThat(nextCsrfToken.getToken())
                .isNotEqualTo(originalCsrfToken.getToken());

        MockHttpServletRequest nextRequest = new MockHttpServletRequest();
        nextRequest.setSession(session);

        SecurityContext loadedContext =
                new HttpSessionSecurityContextRepository()
                        .loadDeferredContext(nextRequest)
                        .get();

        assertThat(loadedContext).isSameAs(savedContext);
    }

    @Test
    void requiresRelogin_whenDatabaseStateNoLongerMatches() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        String originalSessionId = session.getId();

        when(memberRepository.existsByIdAndStatusAndCredentialVersion(
                10L,
                MemberStatus.ACTIVE,
                4L
        )).thenReturn(false);

        var result = updater.update(
                snapshot,
                changedResult(),
                request,
                response
        );

        assertThat(result)
                .isEqualTo(MemberPasswordChangeSessionUpdateResult.RELOGIN_REQUIRED);

        assertThat(session.getId()).isEqualTo(originalSessionId);
        assertThat(session.getAttribute(SPRING_SECURITY_CONTEXT_KEY))
                .isNull();

        assertRequestAuthenticationCleared();
    }

    @Test
    void requiresRelogin_whenStrategyDoesNotRotateSessionId() {
        updater = updaterWith((auth, req, res) -> {
            // ID 교체가 수행되지 않는 상황을 재현한다.
        });

        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        var result = updater.update(
                snapshot,
                changedResult(),
                request,
                response
        );

        assertThat(result)
                .isEqualTo(MemberPasswordChangeSessionUpdateResult.RELOGIN_REQUIRED);

        assertThat(session.getAttribute(SPRING_SECURITY_CONTEXT_KEY))
                .isNull();

        assertRequestAuthenticationCleared();
    }

    @Test
    void preservesReplacementAuthentication_duringSessionUpdate() {
        var replacementAuthentication = authenticatedMember();

        SecurityContext replacementContext =
                SecurityContextHolder.createEmptyContext();

        replacementContext.setAuthentication(replacementAuthentication);

        updater = updaterWith((auth, req, res) -> {
            req.changeSessionId();

            session.setAttribute(
                    SPRING_SECURITY_CONTEXT_KEY,
                    replacementContext
            );
        });

        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        var result = updater.update(
                snapshot,
                changedResult(),
                request,
                response
        );

        assertThat(result)
                .isEqualTo(MemberPasswordChangeSessionUpdateResult.RELOGIN_REQUIRED);

        assertThat(request.getSession(false)).isSameAs(session);

        assertThat(session.getAttribute(SPRING_SECURITY_CONTEXT_KEY))
                .isSameAs(replacementContext);

        assertThat(replacementContext.getAuthentication())
                .isSameAs(replacementAuthentication);

        assertRequestAuthenticationCleared();
    }

    @Test
    void requiresRelogin_whenContextSaveIsSilentlySkipped() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        doNothing().when(session).setAttribute(
                eq(SPRING_SECURITY_CONTEXT_KEY),
                any()
        );

        var result = updater.update(
                snapshot,
                changedResult(),
                request,
                response
        );

        assertThat(result)
                .isEqualTo(MemberPasswordChangeSessionUpdateResult.RELOGIN_REQUIRED);

        assertThat(session.getAttribute(SPRING_SECURITY_CONTEXT_KEY))
                .isNull();

        assertRequestAuthenticationCleared();
    }

    @Test
    void doesNotRecreateSession_whenItExpiresDuringSave() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        doAnswer(invocation -> {
            session.invalidate();
            return null;
        }).when(session).setAttribute(
                eq(SPRING_SECURITY_CONTEXT_KEY),
                any()
        );

        var result = updater.update(
                snapshot,
                changedResult(),
                request,
                response
        );

        assertThat(result)
                .isEqualTo(MemberPasswordChangeSessionUpdateResult.RELOGIN_REQUIRED);

        assertThat(request.getSession(false)).isNull();

        assertRequestAuthenticationCleared();
    }

    @Test
    void propagatesDatabaseFailure_insteadOfTreatingItAsNormalRejection() {
        var snapshot = updater.capture(request, authentication);
        assertThat(snapshot).isNotNull();

        IllegalStateException databaseFailure =
                new IllegalStateException("테스트용 DB 조회 실패");

        when(memberRepository.existsByIdAndStatusAndCredentialVersion(
                10L,
                MemberStatus.ACTIVE,
                4L
        )).thenThrow(databaseFailure);

        assertThatThrownBy(() -> updater.update(
                snapshot,
                changedResult(),
                request,
                response
        )).isSameAs(databaseFailure);

        assertRequestAuthenticationCleared();
    }

    private MemberPasswordChangeSessionUpdater updaterWith(
            SessionAuthenticationStrategy strategy
    ) {
        return new MemberPasswordChangeSessionUpdater(
                memberRepository,
                new MemberPasswordChangeAuthenticationFactory(),
                strategy
        );
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

        var token = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );

        token.eraseCredentials();
        return token;
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

    private void assertRequestAuthenticationCleared() {
        assertThat(
                SecurityContextHolder.getContext().getAuthentication()
        ).isNull();

        SecurityContext requestContext =
                new RequestAttributeSecurityContextRepository()
                        .loadDeferredContext(request)
                        .get();

        assertThat(requestContext.getAuthentication()).isNull();
    }
}