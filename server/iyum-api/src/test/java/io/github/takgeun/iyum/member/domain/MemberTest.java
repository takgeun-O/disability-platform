package io.github.takgeun.iyum.member.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    void 닉네임을_변경하면서_앞뒤_공백을_제거한다() {
        Member member = Member.createPending(
                "user@example.com",
                TEST_HASH,
                "기존닉네임"
        );
        member.activeAfterEmailVerification();

        member.changeNickname("  새로운_닉네임  ");

        assertThat(member.getNickname())
                .isEqualTo("새로운_닉네임");

        // 닉네임 변경이 다른 회원 정보를 바꾸지 않는다.
        assertThat(member.getEmail())
                .isEqualTo("user@example.com");
        assertThat(member.getPasswordHash())
                .isEqualTo(TEST_HASH);
        assertThat(member.getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getRole())
                .isEqualTo(MemberRole.USER);
    }

    // 현재 닉네임을 그대로 저장하는 요청은 오류로 취급하지 않겠다.
    @Test
    void 현재와_같은_닉네임으로_변경해도_정상_처리한다() {
        Member member = Member.createPending(
                "user@example.com",
                TEST_HASH,
                "타끈"
        );
        member.activeAfterEmailVerification();

        member.changeNickname(" 타끈 ");

        assertThat(member.getNickname()).isEqualTo("타끈");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "가",
            "abcdefghijklmnopqrstu",
            "닉 네임",
            "닉네임!",
            "닉네임😀",
            "관리자",
            "운영자",
            "ADMIN",
            " Moderator "
    })
    void 잘못된_닉네임은_거절하고_기존_닉네임을_유지한다(
            String nickname
    ) {
        Member member = Member.createPending(
                "user@example.com",
                TEST_HASH,
                "기존닉네임"
        );
        member.activeAfterEmailVerification();

        assertThatThrownBy(() -> member.changeNickname(nickname))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(member.getNickname())
                .isEqualTo("기존닉네임");
    }
}
