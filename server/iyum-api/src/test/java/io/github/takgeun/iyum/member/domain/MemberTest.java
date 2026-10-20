package io.github.takgeun.iyum.member.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

public class MemberTest {

    // 이 테스트는 비밀번호 해시 알고리즘을 검증하지 않음.
    private static final String TEST_HASH = "test-only-password-hash";

    private static final Instant WITHDRAWN_TIME =
            Instant.parse("2026-10-20T08:00:00Z");

    private Member activeMemberForWithdrawal() {
        Member member = Member.createPending(
                "withdraw@example.com",
                TEST_HASH,
                "탈퇴테스트"
        );
        member.activeAfterEmailVerification();
        return member;
    }

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

    @Test
    void 탈퇴하면_계정정보를_제거하고_탈퇴시각을_기록한다() {
        Member member = activeMemberForWithdrawal();

        member.withdraw(WITHDRAWN_TIME);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(member.getEmail()).isNull();
        assertThat(member.getPasswordHash()).isNull();
        assertThat(member.getNickname()).isNull();
        assertThat(member.getWithdrawnAt()).isEqualTo(WITHDRAWN_TIME);
    }

    @Test
    void 탈퇴시각이_없으면_기존_회원정보를_유지한다() {
        Member member = activeMemberForWithdrawal();

        assertThatThrownBy(() -> member.withdraw(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getEmail()).isEqualTo("withdraw@example.com");
        assertThat(member.getPasswordHash()).isEqualTo(TEST_HASH);
        assertThat(member.getNickname()).isEqualTo("탈퇴테스트");
        assertThat(member.getWithdrawnAt()).isNull();
    }

    @Test
    void 인증대기_회원은_현재_탈퇴_흐름을_사용할_수_없다() {
        Member member = Member.createPending(
                "pending@example.com",
                TEST_HASH,
                "인증대기회원"
        );

        assertThatThrownBy(() -> member.withdraw(WITHDRAWN_TIME))
                .isInstanceOf(IllegalStateException.class);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.PENDING);
        assertThat(member.getEmail()).isEqualTo("pending@example.com");
        assertThat(member.getPasswordHash()).isEqualTo(TEST_HASH);
        assertThat(member.getNickname()).isEqualTo("인증대기회원");
        assertThat(member.getWithdrawnAt()).isNull();
    }

    @Test
    void 탈퇴_회원은_닉네임을_변경할_수_없다() {
        Member member = activeMemberForWithdrawal();
        member.withdraw(WITHDRAWN_TIME);

        assertThatThrownBy(() -> member.changeNickname("새닉네임"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(member.getNickname()).isNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.DELETED);
    }

    @Test
    void 탈퇴_회원은_이메일_인증으로_재활성화할_수_없다() {
        Member member = activeMemberForWithdrawal();
        member.withdraw(WITHDRAWN_TIME);

        assertThatThrownBy(member::activeAfterEmailVerification)
                .isInstanceOf(IllegalStateException.class);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.DELETED);
    }

    // 반복 탈퇴에 대한 HTTP 응답 정책은 API 단계에서 정할 것.
    // 여기서는 엔티티가 이미 완료된 탈퇴 정보를 다시 변경하지 않는지만 검증한다.
    @Test
    void 반복_탈퇴로_최초_탈퇴시각을_덮어쓰지_않는다() {
        Member member = activeMemberForWithdrawal();
        member.withdraw(WITHDRAWN_TIME);

        assertThatThrownBy(() ->
                member.withdraw(WITHDRAWN_TIME.plusSeconds(60))
        ).isInstanceOf(IllegalStateException.class);

        assertThat(member.getWithdrawnAt()).isEqualTo(WITHDRAWN_TIME);
    }
}
