package io.github.takgeun.iyum.member.security;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.member.application.MemberPasswordChangeResult;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.*;

class MemberPasswordChangeAuthenticationFactoryTest {

    private final MemberPasswordChangeAuthenticationFactory factory =
            new MemberPasswordChangeAuthenticationFactory();

    @Test
    void 이번_변경의_버전으로_새_인증을_만들고_기존_인증은_유지한다() {
        var originalPrincipal = principal(
                10L,
                3L,
                MemberStatus.ACTIVE
        );
        var originalAuthentication = authentication(originalPrincipal);

        var changed = changed(10L, 3L, 4L);

        var refreshed = factory.create(
                originalAuthentication,
                changed
        );

        assertThat(refreshed).isNotSameAs(originalAuthentication);
        assertThat(refreshed.isAuthenticated()).isTrue();

        assertThat(refreshed.getPrincipal()).isInstanceOfSatisfying(
                MemberPrincipal.class,
                refreshedPrincipal -> {
                    assertThat(refreshedPrincipal)
                            .isNotSameAs(originalPrincipal);
                    assertThat(refreshedPrincipal.getMemberId())
                            .isEqualTo(10L);
                    assertThat(refreshedPrincipal.getUsername())
                            .isEqualTo("member@example.com");
                    assertThat(refreshedPrincipal.getNickname())
                            .isEqualTo("변경시점닉네임");
                    assertThat(refreshedPrincipal.getStatus())
                            .isEqualTo(MemberStatus.ACTIVE);
                    assertThat(refreshedPrincipal.getRole())
                            .isEqualTo(MemberRole.USER);
                    assertThat(refreshedPrincipal.getCredentialVersion())
                            .isEqualTo(4L);

                    // 세션에 넣을 Principal에 비밀번호를 보관하지 않는다.
                    assertThat(refreshedPrincipal.getPassword()).isNull();
                }
        );

        assertThat(refreshed.getCredentials()).isNull();
        assertThat(refreshed.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");

        // 기존 인증 객체와 Principal을 직접 갱신하지 않았는지 확인한다.
        assertThat(originalAuthentication.getPrincipal())
                .isSameAs(originalPrincipal);
        assertThat(originalPrincipal.getCredentialVersion()).isEqualTo(3L);
        assertThat(originalPrincipal.getNickname()).isEqualTo("기존닉네임");
    }

    @Test
    void 다른_회원의_변경_결과로는_인증을_만들지_않는다() {
        var current = authentication(
                principal(10L, 3L, MemberStatus.ACTIVE)
        );

        assertThatThrownBy(() -> factory.create(
                current,
                changed(20L, 3L, 4L)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 요청_버전과_변경_이전_버전이_다르면_거절한다() {
        var current = authentication(
                principal(10L, 2L, MemberStatus.ACTIVE)
        );

        // 버전 2의 인증을 버전 3에서 시작한 변경 결과로 갱신하면 안 된다.
        assertThatThrownBy(() -> factory.create(
                current,
                changed(10L, 3L, 4L)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 바로_다음_버전이_아닌_변경_결과는_거절한다() {
        var current = authentication(
                principal(10L, 3L, MemberStatus.ACTIVE)
        );

        // 버전이 증가하지 않은 결과
        assertThatThrownBy(() -> factory.create(
                current,
                changed(10L, 3L, 3L)
        )).isInstanceOf(IllegalArgumentException.class);

        // 중간 버전을 건너뛴 결과
        assertThatThrownBy(() -> factory.create(
                current,
                changed(10L, 3L, 5L)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 인증되지_않은_요청이나_회원이_아닌_인증은_거절한다() {
        var changed = changed(10L, 3L, 4L);

        assertThatThrownBy(() -> factory.create(
                null,
                changed
        )).isInstanceOf(IllegalArgumentException.class);

        var unauthenticated =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        principal(10L, 3L, MemberStatus.ACTIVE),
                        null
                );

        assertThatThrownBy(() -> factory.create(
                unauthenticated,
                changed
        )).isInstanceOf(IllegalArgumentException.class);

        var unsupported =
                UsernamePasswordAuthenticationToken.authenticated(
                        "member@example.com",
                        null,
                        java.util.List.of()
                );

        assertThatThrownBy(() -> factory.create(
                unsupported,
                changed
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ACTIVE가_아닌_회원의_인증은_거절한다() {
        var current = authentication(
                principal(10L, 3L, MemberStatus.DELETED)
        );

        assertThatThrownBy(() -> factory.create(
                current,
                changed(10L, 3L, 4L)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private MemberPrincipal principal(
            Long memberId,
            long credentialVersion,
            MemberStatus status
    ) {
        return new MemberPrincipal(
                memberId,
                "member@example.com",
                "",
                "기존닉네임",
                status,
                MemberRole.USER,
                credentialVersion
        );
    }

    private UsernamePasswordAuthenticationToken authentication(
            MemberPrincipal principal
    ) {
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        authentication.eraseCredentials();
        return authentication;
    }

    private MemberPasswordChangeResult.Changed changed(
            Long memberId,
            long previousVersion,
            long nextVersion
    ) {
        return new MemberPasswordChangeResult.Changed(
                memberId,
                "member@example.com",
                "변경시점닉네임",
                MemberRole.USER,
                previousVersion,
                nextVersion
        );
    }
}