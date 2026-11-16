package io.github.takgeun.iyum.community.report.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentReportInputPolicyTest {

    @Test
    void 신고_사유_유형은_필수다() {
        assertThatThrownBy(() ->
                ContentReportInputPolicy.normalizeReasonDetail(
                        null,
                        "신고 상세 설명"
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("신고 사유 유형은 필수입니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "   ",
            "\t",
            "\n",
            " \t\r\n ",
            "\u2003"
    })
    void 기타_사유는_비어있는_상세_설명을_거부한다(
            String detail
    ) {
        assertThatThrownBy(() ->
                ContentReportInputPolicy.normalizeReasonDetail(
                        ContentReportReasonType.OTHER,
                        detail
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("기타 사유는 상세 설명이 필요합니다.");
    }

    @ParameterizedTest
    @EnumSource(
            value = ContentReportReasonType.class,
            names = "OTHER",
            mode = EnumSource.Mode.EXCLUDE
    )
    void 기타_외_사유의_빈_상세_설명은_null로_정규화한다(
            ContentReportReasonType reasonType
    ) {
        String[] emptyDetails = {
                null,
                "",
                " ",
                "\t\r\n",
                "\u2003"
        };

        for (String detail : emptyDetails) {
            assertThat(
                    ContentReportInputPolicy.normalizeReasonDetail(
                            reasonType,
                            detail
                    )
            ).as("사유=%s, 상세=%s", reasonType, detail)
                    .isNull();
        }
    }

    @ParameterizedTest
    @EnumSource(ContentReportReasonType.class)
    void 모든_사유에_상세_설명을_입력할_수_있다(
            ContentReportReasonType reasonType
    ) {
        String result =
                ContentReportInputPolicy.normalizeReasonDetail(
                        reasonType,
                        "구체적인 신고 내용"
                );

        assertThat(result).isEqualTo("구체적인 신고 내용");
    }

    @Test
    void 양끝_공백은_제거하고_내부_공백과_줄바꿈은_유지한다() {
        String result =
                ContentReportInputPolicy.normalizeReasonDetail(
                        ContentReportReasonType.OTHER,
                        " \t\u2003첫 번째 내용\n두  번째 내용\u2003\r\n "
                );

        assertThat(result)
                .isEqualTo("첫 번째 내용\n두  번째 내용");
    }

    @ParameterizedTest
    @EnumSource(ContentReportReasonType.class)
    void 정규화_후_500자는_허용한다(
            ContentReportReasonType reasonType
    ) {
        String detail = "가".repeat(500);

        String result =
                ContentReportInputPolicy.normalizeReasonDetail(
                        reasonType,
                        " \n" + detail + "\t "
                );

        assertThat(result).isEqualTo(detail);
    }

    @ParameterizedTest
    @EnumSource(ContentReportReasonType.class)
    void 정규화_후_501자는_거부한다(
            ContentReportReasonType reasonType
    ) {
        String detail = "가".repeat(501);

        assertThatThrownBy(() ->
                ContentReportInputPolicy.normalizeReasonDetail(
                        reasonType,
                        detail
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("신고 상세 설명은 500자 이하여야 합니다.");
    }

    @Test
    void 보조_평면_문자도_코드_포인트_기준_500자까지_허용한다() {
        String detail = "\uD83D\uDE00".repeat(500);

        // UTF-16 코드 단위는 1,000개지만 코드 포인트는 500개다.
        assertThat(detail.length()).isEqualTo(1000);
        assertThat(
                detail.codePointCount(0, detail.length())
        ).isEqualTo(500);

        String result =
                ContentReportInputPolicy.normalizeReasonDetail(
                        ContentReportReasonType.OTHER,
                        detail
                );

        assertThat(result).isEqualTo(detail);
    }

    @Test
    void 보조_평면_문자도_코드_포인트_기준_501자는_거부한다() {
        String detail = "\uD83D\uDE00".repeat(501);

        assertThatThrownBy(() ->
                ContentReportInputPolicy.normalizeReasonDetail(
                        ContentReportReasonType.OTHER,
                        detail
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("신고 상세 설명은 500자 이하여야 합니다.");
    }
}