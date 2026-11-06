package io.github.takgeun.iyum.admin.moderation.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModerationReasonPolicyTest {

    @ParameterizedTest
    @CsvSource({
            "HIDE, SPAM_ADVERTISING",
            "HIDE, ABUSE_HATE",
            "HIDE, PERSONAL_INFORMATION",
            "HIDE, DUPLICATE_FLOODING",
            "HIDE, OFF_TOPIC",
            "RESTORE, MODERATION_ERROR",
            "RESTORE, NO_VIOLATION",
            "HIDE, OTHER",
            "RESTORE, OTHER"
    })
    void 허용된_조치와_사유는_상세_내용을_정리한다(
            ModerationAction action,
            ModerationReasonType reasonType
    ) {
        String result = ModerationInputPolicy.normalizeReasonDetail(
                action,
                reasonType,
                "  확인한 내용\n추가 설명  "
        );

        assertThat(result).isEqualTo("확인한 내용\n추가 설명");
    }

    @ParameterizedTest
    @CsvSource({
            "RESTORE, SPAM_ADVERTISING",
            "RESTORE, ABUSE_HATE",
            "RESTORE, PERSONAL_INFORMATION",
            "RESTORE, DUPLICATE_FLOODING",
            "RESTORE, OFF_TOPIC",
            "HIDE, MODERATION_ERROR",
            "HIDE, NO_VIOLATION",
            "HIDE, LEGACY",
            "RESTORE, LEGACY"
    })
    void 조치에_맞지_않거나_기존기록_전용인_사유는_거부한다(
            ModerationAction action,
            ModerationReasonType reasonType
    ) {
        assertThatThrownBy(() ->
                ModerationInputPolicy.normalizeReasonDetail(
                        action,
                        reasonType,
                        "상세 설명"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "해당 조치에 사용할 수 없는 처리 사유 유형입니다."
                );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u3000"})
    void 일반_유형의_상세_내용이_없으면_null로_정리한다(
            String detail
    ) {
        String result = ModerationInputPolicy.normalizeReasonDetail(
                ModerationAction.HIDE,
                ModerationReasonType.SPAM_ADVERTISING,
                detail
        );

        assertThat(result).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u3000"})
    void 기타_유형의_상세_내용이_없으면_거부한다(String detail) {
        for (ModerationAction action : ModerationAction.values()) {
            assertThatThrownBy(() ->
                    ModerationInputPolicy.normalizeReasonDetail(
                            action,
                            ModerationReasonType.OTHER,
                            detail
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("기타 사유는 상세 내용을 입력해야 합니다.");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 상세_내용은_코드포인트_기준_500자까지_허용한다(
            String character
    ) {
        String detail = character.repeat(500);

        String result = ModerationInputPolicy.normalizeReasonDetail(
                ModerationAction.HIDE,
                ModerationReasonType.OTHER,
                "  " + detail + "  "
        );

        assertThat(result).isEqualTo(detail);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 상세_내용이_코드포인트_기준_500자를_초과하면_거부한다(
            String character
    ) {
        assertThatThrownBy(() ->
                ModerationInputPolicy.normalizeReasonDetail(
                        ModerationAction.HIDE,
                        ModerationReasonType.SPAM_ADVERTISING,
                        character.repeat(501)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("처리 사유는 500자 이하여야 합니다.");
    }

    @Test
    void 처리_종류가_없으면_거부한다() {
        assertThatThrownBy(() ->
                ModerationInputPolicy.normalizeReasonDetail(
                        null,
                        ModerationReasonType.OTHER,
                        "상세 설명"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("처리 종류는 필수입니다.");
    }

    @Test
    void 사유_유형이_없으면_거부한다() {
        assertThatThrownBy(() ->
                ModerationInputPolicy.normalizeReasonDetail(
                        ModerationAction.HIDE,
                        null,
                        "상세 설명"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("처리 사유 유형은 필수입니다.");
    }
}