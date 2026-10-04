package io.github.takgeun.iyum.member.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

public class MemberTest {

    // 이 테스트는 비밀번호 해시 알고리즘을 검증하지 않음.
    private static final String TEST_HASH = "test-only-password-hash";

    @Test
    void 신규_회원은_인증대기_일반회원으로_생성된다() {
        Member member = Member.createPending(
                " User@Example.com ",
                TEST_HASH,
                " 타끈 "
        );

        assertThat(member.getEmail()).isEqualTo("user@example.com");
        assertThat(member.getNickname()).isEqualTo("타끈");
        assertThat(member.getStatus()).isEqualTo(MemberStatus.PENDING);
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
    }

    @Test
    void 인증대기_회원은_활성화할_수_있다() {
        Member member = Member.createPending(
                "user@example.com",
                TEST_HASH,
                "타끈"
        );

        member.activeAfterEmailVerification();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void 이미_활성화된_회원은_다시_활성화할_수_없다() {
        Member member = Member.createPending(
                "user@example.com",
                TEST_HASH,
                "타끈"
        );

        member.activeAfterEmailVerification();

        assertThatThrownBy(member::activeAfterEmailVerification)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 관리자_사칭_닉네임은_사용할_수_없다() {
        assertThatThrownBy(() ->
                Member.createPending(
                        "user@example.com",
                        TEST_HASH,
                        "ADMIN"
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
