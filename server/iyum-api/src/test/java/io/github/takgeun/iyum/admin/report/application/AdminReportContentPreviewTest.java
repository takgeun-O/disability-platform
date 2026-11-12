package io.github.takgeun.iyum.admin.report.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AdminReportContentPreviewTest {

    @Test
    void 유니코드_공백을_한_칸으로_정리한다() {
        String content = "\u3000첫 줄\n\t둘째\u00A0\u2003줄  ";

        assertThat(AdminReportContentPreview.from(content))
                .isEqualTo("첫 줄 둘째 줄");
    }

    @Test
    void 정확히_120_코드포인트이면_말줄임표를_붙이지_않는다() {
        String content = "가".repeat(119) + "😀";

        assertThat(AdminReportContentPreview.from(content))
                .isEqualTo(content);
    }

    @Test
    void 제한을_넘으면_이모지를_분리하지_않고_말줄임표를_붙인다() {
        String content = "가".repeat(119) + "😀나";

        String result = AdminReportContentPreview.from(content);

        assertThat(result)
                .isEqualTo("가".repeat(119) + "😀…");

        assertThat(result.codePointCount(0, result.length()))
                .isEqualTo(121);
    }

    @Test
    void 공백_정규화_후의_길이로_제한을_적용한다() {
        String content = " ".repeat(200)
                + "짧은 본문"
                + "\n".repeat(200);

        assertThat(AdminReportContentPreview.from(content))
                .isEqualTo("짧은 본문");
    }

    @Test
    void 공백만_있으면_빈_문자열을_반환한다() {
        assertThat(AdminReportContentPreview.from("\n\t\u3000"))
                .isEmpty();
    }

    @Test
    void null은_허용하지_않는다() {
        assertThatNullPointerException()
                .isThrownBy(() -> AdminReportContentPreview.from(null));
    }
}