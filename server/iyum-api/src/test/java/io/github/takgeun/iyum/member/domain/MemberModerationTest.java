package io.github.takgeun.iyum.member.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberModerationTest {

    private static final String EMAIL = "moderation@example.com";
    private static final String PASSWORD_HASH = "password-hash";
    private static final String NICKNAME = "운영대상회원";

    @Test
    void 활성_일반_회원을_정지하고_인증_버전을_증가시킨다() {
        Member member = activeMember();
        ReflectionTestUtils.setField(member, "credentialVersion", 5L);

        boolean changed = member.suspend();

        assertThat(changed).isTrue();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getCredentialVersion()).isEqualTo(6L);
        assertAccountInformationPreserved(member);
    }

    @Test
    void 정지된_일반_회원을_해제하고_인증_버전을_증가시킨다() {
        Member member = activeMember();
        member.suspend();

        long versionBefore = member.getCredentialVersion();

        boolean changed = member.liftSuspension();

        assertThat(changed).isTrue();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getCredentialVersion())
                .isEqualTo(versionBefore + 1L);
        assertAccountInformationPreserved(member);
    }

    @Test
    void 이미_정지된_회원에게_정지를_반복하면_변경하지_않는다() {
        Member member = activeMember();
        member.suspend();

        long versionBefore = member.getCredentialVersion();

        boolean changed = member.suspend();

        assertThat(changed).isFalse();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getCredentialVersion()).isEqualTo(versionBefore);
        assertAccountInformationPreserved(member);
    }

    @Test
    void 이미_활성인_회원에게_해제를_요청하면_변경하지_않는다() {
        Member member = activeMember();
        ReflectionTestUtils.setField(member, "credentialVersion", 5L);

        boolean changed = member.liftSuspension();

        assertThat(changed).isFalse();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getCredentialVersion()).isEqualTo(5L);
        assertAccountInformationPreserved(member);
    }

    @Test
    void 정지와_해제를_반복해도_인증_버전은_이전_값으로_돌아가지_않는다() {
        Member member = activeMember();
        ReflectionTestUtils.setField(member, "credentialVersion", 10L);

        assertThat(member.suspend()).isTrue();
        assertThat(member.getCredentialVersion()).isEqualTo(11L);

        assertThat(member.liftSuspension()).isTrue();
        assertThat(member.getCredentialVersion()).isEqualTo(12L);

        assertThat(member.suspend()).isTrue();
        assertThat(member.getCredentialVersion()).isEqualTo(13L);

        assertThat(member.liftSuspension()).isTrue();
        assertThat(member.getCredentialVersion()).isEqualTo(14L);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertAccountInformationPreserved(member);
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = {"ACTIVE", "SUSPENDED"},
            mode = EnumSource.Mode.EXCLUDE
    )
    void 허용하지_않은_상태에서는_정지와_해제가_모두_실패한다(
            MemberStatus status
    ) {
        Member member = activeMember();
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(member, "credentialVersion", 5L);

        assertThatThrownBy(() -> member.suspend())
                .isInstanceOf(IllegalStateException.class);

        assertThat(member.getStatus()).isEqualTo(status);
        assertThat(member.getCredentialVersion()).isEqualTo(5L);

        assertThatThrownBy(() -> member.liftSuspension())
                .isInstanceOf(IllegalStateException.class);

        assertThat(member.getStatus()).isEqualTo(status);
        assertThat(member.getCredentialVersion()).isEqualTo(5L);
        assertAccountInformationPreserved(member);
    }

    @ParameterizedTest
    @EnumSource(MemberStatus.class)
    void 관리자_계정은_상태에_관계없이_정지와_해제_대상에서_제외한다(
            MemberStatus status
    ) {
        Member member = activeMember();
        ReflectionTestUtils.setField(member, "role", MemberRole.ADMIN);
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(member, "credentialVersion", 5L);

        assertThatThrownBy(() -> member.suspend())
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> member.liftSuspension())
                .isInstanceOf(IllegalStateException.class);

        assertThat(member.getStatus()).isEqualTo(status);
        assertThat(member.getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(member.getCredentialVersion()).isEqualTo(5L);
        assertThat(member.getEmail()).isEqualTo(EMAIL);
        assertThat(member.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(member.getNickname()).isEqualTo(NICKNAME);
        assertThat(member.getWithdrawnAt()).isNull();
    }

    @Test
    void 탈퇴_완료_회원의_계정_정보와_탈퇴_시각을_변경하지_않는다() {
        Member member = activeMember();
        Instant withdrawnAt = Instant.parse("2026-11-10T10:00:00Z");
        member.withdraw(withdrawnAt);

        long versionBefore = member.getCredentialVersion();

        assertThatThrownBy(() -> member.suspend())
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> member.liftSuspension())
                .isInstanceOf(IllegalStateException.class);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(member.getCredentialVersion()).isEqualTo(versionBefore);
        assertThat(member.getWithdrawnAt()).isEqualTo(withdrawnAt);
        assertThat(member.getEmail()).isNull();
        assertThat(member.getPasswordHash()).isNull();
        assertThat(member.getNickname()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 인증_버전이_범위를_초과하면_상태도_변경하지_않는다(
            boolean suspend
    ) {
        Member member = activeMember();

        if (!suspend) {
            member.suspend();
        }

        MemberStatus statusBefore = member.getStatus();
        ReflectionTestUtils.setField(
                member,
                "credentialVersion",
                Long.MAX_VALUE
        );

        assertThatThrownBy(() -> {
            if (suspend) {
                member.suspend();
            } else {
                member.liftSuspension();
            }
        }).isInstanceOf(ArithmeticException.class);

        assertThat(member.getStatus()).isEqualTo(statusBefore);
        assertThat(member.getCredentialVersion())
                .isEqualTo(Long.MAX_VALUE);
        assertAccountInformationPreserved(member);
    }

    private Member activeMember() {
        Member member = Member.createPending(
                EMAIL,
                PASSWORD_HASH,
                NICKNAME
        );

        member.activeAfterEmailVerification();

        return member;
    }

    private void assertAccountInformationPreserved(Member member) {
        assertThat(member.getEmail()).isEqualTo(EMAIL);
        assertThat(member.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(member.getNickname()).isEqualTo(NICKNAME);
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getWithdrawnAt()).isNull();
    }
}