package io.github.takgeun.iyum.auth.security;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MemberUserDetailsServiceTest {

    private static final String EMAIL = "user@example.com";

    // 이번 테스트에서는 비밀번호 비교를 하지 않는다.
    private static final String TEST_HASH = "test-only-password-hash";

    private MemberRepository memberRepository;
    private MemberUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);

        userDetailsService = new MemberUserDetailsService(
                memberRepository
        );
    }

    @Test
    void 이메일을_정규화하여_조회하고_인증정보를_반환한다() {
        Member member = storedMember(
                MemberStatus.ACTIVE,
                MemberRole.ADMIN
        );

        // Repository가 반환할 결과를 미리 지정
        // "findByEmail("user@example.com")이 호출되면 준비한 member 반환하라"
        // 이 방식을 stubbing이라고 함. 실제 DB를 조회하는 대신 조회 결과를 정해 놓는 것.
        when(memberRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(member));

        MemberPrincipal principal =
                userDetailsService.loadUserByUsername(
                        " User@Example.com "
                );

        // 정규화된 이메일로 조회했는지 확인
        // verify(...) : 실제로 해당 인자로 호출됐는지 사후 확인
        // 즉, findByEmail("user@example.com")이 정확히 한 번 호출됐는지 확인
        verify(memberRepository).findByEmail(EMAIL);

        assertThat(principal.getMemberId()).isEqualTo(1L);
        assertThat(principal.getUsername()).isEqualTo(EMAIL);
        assertThat(principal.getPassword()).isEqualTo(TEST_HASH);   // 원본 비밀번호가 아닌 저장된 비밀번호 해시 반환
        assertThat(principal.getNickname()).isEqualTo("타끈");
        assertThat(principal.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(principal.getRole()).isEqualTo(MemberRole.ADMIN);

        // ADMIN 역할이 인증 권한으로 변환됐는지 확인
        assertThat(principal.getAuthorities())      // Principal이 가진 권한 객체 목록 가져오기
                .extracting(GrantedAuthority::getAuthority) // 각 권한 객체의 getAuthority()를 호출해서 권한 문자열만 추출
                .containsExactly("ROLE_ADMIN");     // 추출된 결과에 "ROLE_ADMIN" 하나만 있는지 확인
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, false",
            "ACTIVE, true",
            "RESTRICTED, false",
            "SUSPENDED, false",
            "WITHDRAWN, false",
            "DELETED, false"
    })
    void 회원_상태에_따라_활성_여부를_전달한다(
            MemberStatus status,
            boolean expectedEnabled
    ) {
        Member member = storedMember(
                status,
                MemberRole.USER
        );

        when(memberRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(member));

        MemberPrincipal principal =
                userDetailsService.loadUserByUsername(EMAIL);

        assertThat(principal.isEnabled())
                .isEqualTo(expectedEnabled);

        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void 존재하지_않는_회원은_조회_실패로_처리한다() {
        when(memberRepository.findByEmail(EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userDetailsService.loadUserByUsername(EMAIL)
        ).isInstanceOf(UsernameNotFoundException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 이메일이_없으면_DB를_조회하지_않는다(String email) {
        assertThatThrownBy(() ->
                userDetailsService.loadUserByUsername(email)
        ).isInstanceOf(UsernameNotFoundException.class);

        verifyNoInteractions(memberRepository);
    }

    @Test
    void DB의_인증_버전을_로그인_정보에_복사한다() {
        Member member = storedMember(
                MemberStatus.ACTIVE,
                MemberRole.USER
        );

        // 기본값 0이 아닌 값으로 검증해서
        // 실제로 DB 값을 전달하는지 확인한다.
        when(member.getCredentialVersion()).thenReturn(7L);

        when(memberRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(member));

        MemberPrincipal principal =
                userDetailsService.loadUserByUsername(EMAIL);

        assertThat(principal.getCredentialVersion()).isEqualTo(7L);

        // DB 회원 값이 바뀌어도 이미 만든 로그인 정보는 유지되어야 한다.
        when(member.getCredentialVersion()).thenReturn(8L);

        assertThat(member.getCredentialVersion()).isEqualTo(8L);
        assertThat(principal.getCredentialVersion()).isEqualTo(7L);
    }

    @Test
    void 재설정_후_새_로그인만_최신_버전을_받는다() {
        // Mock long의 기본값에 의존하지 않고 실제 엔티티의 재설정을 사용한다.
        Member member = Member.createPending(EMAIL, TEST_HASH, "재설정회원");
        member.activeAfterEmailVerification();
        for (int i = 0; i < 7; i++) {
            member.resetPassword("test-only-reset-hash-" + i);
        }
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));

        MemberPrincipal previousLogin = userDetailsService.loadUserByUsername(EMAIL);
        assertThat(previousLogin.getCredentialVersion()).isEqualTo(7L);

        member.resetPassword("test-only-latest-hash");
        MemberPrincipal newLogin = userDetailsService.loadUserByUsername(EMAIL);

        assertThat(member.getCredentialVersion()).isEqualTo(8L);
        assertThat(previousLogin.getCredentialVersion()).isEqualTo(7L);
        assertThat(previousLogin.getPassword()).isEqualTo("test-only-reset-hash-6");
        assertThat(newLogin.getCredentialVersion()).isEqualTo(8L);
        assertThat(newLogin.getPassword()).isEqualTo("test-only-latest-hash");
        assertThat(newLogin).isNotSameAs(previousLogin);
        verify(memberRepository, times(2)).findByEmail(EMAIL);
    }

    private Member storedMember(
            MemberStatus status,
            MemberRole role
    ) {
        Member member = mock(Member.class);

        when(member.getId()).thenReturn(1L);
        when(member.getEmail()).thenReturn(EMAIL);
        when(member.getPasswordHash()).thenReturn(TEST_HASH);
        when(member.getNickname()).thenReturn("타끈");
        when(member.getStatus()).thenReturn(status);
        when(member.getRole()).thenReturn(role);

        return member;
    }
}