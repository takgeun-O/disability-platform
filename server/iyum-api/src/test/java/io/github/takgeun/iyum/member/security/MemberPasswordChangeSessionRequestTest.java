package io.github.takgeun.iyum.member.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberPasswordChangeSessionRequestTest {

    private MockHttpServletRequest request;
    private MockHttpSession session;
    private MemberPasswordChangeSessionRequest wrappedRequest;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        session = new MockHttpSession();
        request.setSession(session);

        wrappedRequest =
                new MemberPasswordChangeSessionRequest(request, session);
    }

    @Test
    void returnsExistingSession_forAllSessionAccessMethods() {
        assertThat(wrappedRequest.getSession(false)).isSameAs(session);
        assertThat(wrappedRequest.getSession(true)).isSameAs(session);
        assertThat(wrappedRequest.getSession()).isSameAs(session);
    }

    @Test
    void doesNotCreateSession_whenRequestHasNoSession() {
        request.setSession(null);

        assertThat(wrappedRequest.getSession(false)).isNull();

        assertThatThrownBy(() -> wrappedRequest.getSession(true))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> wrappedRequest.getSession())
                .isInstanceOf(IllegalStateException.class);

        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void doesNotRecreateSession_whenOriginalSessionWasInvalidated() {
        session.invalidate();

        assertThat(wrappedRequest.getSession(false)).isNull();

        assertThatThrownBy(() -> wrappedRequest.getSession(true))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> wrappedRequest.getSession())
                .isInstanceOf(IllegalStateException.class);

        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void rejectsReplacementSession_withoutChangingIt() {
        MockHttpSession replacementSession = new MockHttpSession();
        replacementSession.setAttribute("marker", "replacement");

        String replacementSessionId = replacementSession.getId();
        request.setSession(replacementSession);

        assertThat(wrappedRequest.getSession(false)).isNull();

        assertThatThrownBy(() -> wrappedRequest.getSession(true))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> wrappedRequest.getSession())
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> wrappedRequest.changeSessionId())
                .isInstanceOf(IllegalStateException.class);

        assertThat(request.getSession(false))
                .isSameAs(replacementSession);

        assertThat(replacementSession.getId())
                .isEqualTo(replacementSessionId);

        assertThat(replacementSession.getAttribute("marker"))
                .isEqualTo("replacement");
    }

    @Test
    void changesSessionId_whileKeepingOriginalSessionAndAttributes() {
        String originalSessionId = session.getId();
        Object marker = new Object();
        session.setAttribute("marker", marker);

        String changedSessionId = wrappedRequest.changeSessionId();

        assertThat(changedSessionId).isNotEqualTo(originalSessionId);
        assertThat(session.getId()).isEqualTo(changedSessionId);

        assertThat(request.getSession(false)).isSameAs(session);
        assertThat(wrappedRequest.getSession(false)).isSameAs(session);
        assertThat(session.getAttribute("marker")).isSameAs(marker);
    }

    @Test
    void rejectsSessionIdChange_whenOriginalSessionWasInvalidated() {
        session.invalidate();

        assertThatThrownBy(() -> wrappedRequest.changeSessionId())
                .isInstanceOf(IllegalStateException.class);

        assertThat(request.getSession(false)).isNull();
    }
}