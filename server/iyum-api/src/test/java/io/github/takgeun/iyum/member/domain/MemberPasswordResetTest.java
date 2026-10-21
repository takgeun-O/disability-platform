package io.github.takgeun.iyum.member.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class MemberPasswordResetTest {

    private static final String OLD_HASH = "old-password-hash";
    private static final String NEW_HASH = "new-password-hash";

    @Test
    void 활성_회원의_비밀번호_해시를_변경하고_인증_버전을_증가시킨다() {
        Member member = activeMember();

        assertThat(member.getCredentialVersion()).isZero();

        member.resetPassword(NEW_HASH);

        assertThat(member.getPasswordHash()).isEqualTo(NEW_HASH);
        assertThat(member.getCredentialVersion()).isEqualTo(1L);

        // 비밀번호 재설정으로 다른 회원 정보가 바뀌지는 않는다.
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getEmail()).isEqualTo("reset@example.com");
        assertThat(member.getNickname()).isEqualTo("재설정회원");
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
    }

    @Test
    void 재설정할_때마다_인증_버전이_증가한다() {
        Member member = activeMember();

        member.resetPassword(NEW_HASH);
        member.resetPassword("another-password-hash");

        assertThat(member.getPasswordHash())
                .isEqualTo("another-password-hash");
        assertThat(member.getCredentialVersion()).isEqualTo(2L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void 비어_있는_해시이면_기존_값을_유지한다(String invalidHash) {
        Member member = activeMember();

        assertThatThrownBy(() -> member.resetPassword(invalidHash))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효한 비밀번호 해시가 필요합니다.");

        assertThat(member.getPasswordHash()).isEqualTo(OLD_HASH);
        assertThat(member.getCredentialVersion()).isZero();
    }

    @Test
    void 해시가_255자를_초과하면_기존_값을_유지한다() {
        Member member = activeMember();

        assertThatThrownBy(() ->
                member.resetPassword("a".repeat(256))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효한 비밀번호 해시가 필요합니다.");

        assertThat(member.getPasswordHash()).isEqualTo(OLD_HASH);
        assertThat(member.getCredentialVersion()).isZero();
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = "ACTIVE",
            mode = EnumSource.Mode.EXCLUDE
    )
    void 활성_회원이_아니면_재설정할_수_없다(MemberStatus status) {
        Member member = activeMember();

        // 다양한 회원 상태를 만드는 테스트 전용 설정이다.
        ReflectionTestUtils.setField(member, "status", status);

        assertThatThrownBy(() -> member.resetPassword(NEW_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("활성 회원만 비밀번호를 재설정할 수 있습니다.");

        assertThat(member.getPasswordHash()).isEqualTo(OLD_HASH);
        assertThat(member.getCredentialVersion()).isZero();
        assertThat(member.getStatus()).isEqualTo(status);
    }

    @Test
    void 탈퇴한_회원의_비밀번호를_다시_저장하지_않는다() {
        Member member = activeMember();

        Instant withdrawnAt =
                Instant.parse("2026-10-21T05:00:00Z");

        member.withdraw(withdrawnAt);

        long versionBefore = member.getCredentialVersion();

        assertThatThrownBy(() -> member.resetPassword(NEW_HASH))
                .isInstanceOf(IllegalStateException.class);

        assertThat(member.getPasswordHash()).isNull();
        assertThat(member.getEmail()).isNull();
        assertThat(member.getNickname()).isNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(member.getWithdrawnAt()).isEqualTo(withdrawnAt);
        assertThat(member.getCredentialVersion())
                .isEqualTo(versionBefore);
    }

    private Member activeMember() {
        Member member = Member.createPending(
                "reset@example.com",
                OLD_HASH,
                "재설정회원"
        );

        member.activeAfterEmailVerification();

        return member;
    }
}