package io.github.takgeun.iyum.global.config;

import io.github.takgeun.iyum.auth.security.EmailVerificationRequiredException;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.auth.security.MemberUserDetailsService;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationConfigTest {

    private static final String EMAIL = "member@example.com";
    private static final String RAW_PASSWORD = "Example123!";

    private static final PasswordEncoder PASSWORD_ENCODER =
            new PasswordConfig().passwordEncoder();

    private static final String PASSWORD_HASH =
            PASSWORD_ENCODER.encode(RAW_PASSWORD);

    private MemberRepository memberRepository;
    private AuthenticationManager authenticationManager;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);

        AuthenticationConfig config = new AuthenticationConfig();

        MemberUserDetailsService userDetailsService =
                config.memberUserDetailsService(memberRepository);

        DaoAuthenticationProvider provider =
                config.memberAuthenticationProvider(
                        userDetailsService,
                        PASSWORD_ENCODER
                );

        authenticationManager =
                config.authenticationManager(provider);
    }

    @Test
    void activeMemberWithCorrectPasswordIsAuthenticated() {
        givenMember(MemberStatus.ACTIVE);

        Authentication result = authenticate(
                " Member@Example.com ",
                RAW_PASSWORD
        );

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal())
                .isInstanceOf(MemberPrincipal.class);

        MemberPrincipal principal =
                (MemberPrincipal) result.getPrincipal();

        assertThat(principal.getMemberId()).isEqualTo(1L);
        assertThat(principal.getUsername()).isEqualTo(EMAIL);
        assertThat(principal.getStatus())
                .isEqualTo(MemberStatus.ACTIVE);

        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER");

        // 인증 결과에 비밀번호와 해시를 남기지 않는다.
        assertThat(result.getCredentials()).isNull();
        assertThat(principal.getPassword()).isNull();

        // 다음 인증 요청에서도 새 회원 정보를 조회해 인증할 수 있다.
        Authentication secondResult =
                authenticate(EMAIL, RAW_PASSWORD);

        assertThat(secondResult.isAuthenticated()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(MemberStatus.class)
    void wrongPasswordFailsBeforeMemberStatusIsReported(
            MemberStatus status
    ) {
        givenMember(status);

        assertThatThrownBy(() ->
                authenticate(EMAIL, "WrongPassword123!")
        ).isExactlyInstanceOf(BadCredentialsException.class);
    }

    @Test
    void pendingMemberWithCorrectPasswordRequiresEmailVerification() {
        givenMember(MemberStatus.PENDING);

        assertThatThrownBy(() ->
                authenticate(EMAIL, RAW_PASSWORD)
        ).isExactlyInstanceOf(
                EmailVerificationRequiredException.class
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = {
                    "RESTRICTED",
                    "SUSPENDED",
                    "WITHDRAWN",
                    "DELETED"
            }
    )
    void unavailableMemberWithCorrectPasswordIsRejected(
            MemberStatus status
    ) {
        givenMember(status);

        assertThatThrownBy(() ->
                authenticate(EMAIL, RAW_PASSWORD)
        ).isExactlyInstanceOf(DisabledException.class);
    }

    @Test
    void unknownEmailIsAlsoReportedAsBadCredentials() {
        when(memberRepository.findByEmail(EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authenticate(EMAIL, RAW_PASSWORD)
        ).isExactlyInstanceOf(BadCredentialsException.class);
    }

    private Authentication authenticate(
            String email,
            String password
    ) {
        return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        email,
                        password
                )
        );
    }

    private void givenMember(MemberStatus status) {
        Member member = mock(Member.class);

        when(member.getId()).thenReturn(1L);
        when(member.getEmail()).thenReturn(EMAIL);
        when(member.getPasswordHash()).thenReturn(PASSWORD_HASH);
        when(member.getNickname()).thenReturn("테스트회원");
        when(member.getStatus()).thenReturn(status);
        when(member.getRole()).thenReturn(MemberRole.USER);

        when(memberRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(member));
    }
}