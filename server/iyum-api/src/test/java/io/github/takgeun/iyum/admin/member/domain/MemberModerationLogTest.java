package io.github.takgeun.iyum.admin.member.domain;

import io.github.takgeun.iyum.member.domain.MemberStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberModerationLogTest {

    private static final Instant NOW =
            Instant.parse("2026-11-10T10:00:00Z");

    @ParameterizedTest
    @EnumSource(MemberModerationAction.class)
    void 조치에_맞는_상태_전이와_정규화한_사유를_기록한다(
            MemberModerationAction action
    ) {
        MemberModerationLog log = MemberModerationLog.create(
                1L,
                2L,
                action,
                " \n 운영 정책 검토 결과 \t ",
                NOW
        );

        MemberStatus expectedBefore =
                action == MemberModerationAction.SUSPEND
                        ? MemberStatus.ACTIVE
                        : MemberStatus.SUSPENDED;

        MemberStatus expectedAfter =
                action == MemberModerationAction.SUSPEND
                        ? MemberStatus.SUSPENDED
                        : MemberStatus.ACTIVE;

        assertThat(log.getActorMemberId()).isEqualTo(1L);
        assertThat(log.getTargetMemberId()).isEqualTo(2L);
        assertThat(log.getAction()).isEqualTo(action);
        assertThat(log.getBeforeStatus()).isEqualTo(expectedBefore);
        assertThat(log.getAfterStatus()).isEqualTo(expectedAfter);
        assertThat(log.getReason()).isEqualTo("운영 정책 검토 결과");
        assertThat(log.getCreatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 처리_관리자_ID는_양수여야_한다(Long actorId) {
        assertThatThrownBy(() -> MemberModerationLog.create(
                actorId,
                2L,
                MemberModerationAction.SUSPEND,
                "운영 정책 위반",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 대상_회원_ID는_양수여야_한다(Long targetId) {
        assertThatThrownBy(() -> MemberModerationLog.create(
                1L,
                targetId,
                MemberModerationAction.SUSPEND,
                "운영 정책 위반",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 자기_자신에_대한_조치는_기록할_수_없다() {
        assertThatThrownBy(() -> MemberModerationLog.create(
                1L,
                1L,
                MemberModerationAction.SUSPEND,
                "운영 정책 위반",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 처리_종류는_필수다() {
        assertThatThrownBy(() -> MemberModerationLog.create(
                1L,
                2L,
                null,
                "운영 정책 위반",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 처리_시각은_필수다() {
        assertThatThrownBy(() -> MemberModerationLog.create(
                1L,
                2L,
                MemberModerationAction.SUSPEND,
                "운영 정책 위반",
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n", " \t\n "})
    void 비어_있는_사유는_허용하지_않는다(String reason) {
        assertThatThrownBy(() -> createLog(reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("처리 사유는 필수입니다.");
    }

    @Test
    void 정규화한_사유가_500_코드포인트이면_저장할_수_있다() {
        // 이 이모지는 UTF-16에서는 두 char지만 코드 포인트는 하나다.
        String reason = "😀".repeat(500);

        MemberModerationLog log = createLog("  " + reason + "  ");

        assertThat(log.getReason()).isEqualTo(reason);
        assertThat(log.getReason().codePointCount(
                0,
                log.getReason().length()
        )).isEqualTo(500);
    }

    @Test
    void 사유가_500_코드포인트를_초과하면_거부한다() {
        assertThatThrownBy(() -> createLog("😀".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("처리 사유는 500자 이하여야 합니다.");
    }

    private MemberModerationLog createLog(String reason) {
        return MemberModerationLog.create(
                1L,
                2L,
                MemberModerationAction.SUSPEND,
                reason,
                NOW
        );
    }
}