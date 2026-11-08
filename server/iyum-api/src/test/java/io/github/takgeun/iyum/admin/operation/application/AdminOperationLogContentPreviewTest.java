package io.github.takgeun.iyum.admin.operation.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminOperationLogContentPreviewTest {

    @Test
    void 짧은_일반_본문을_유지한다() {
        assertThat(AdminOperationLogContentPreview.from("보청기 적응 경험을 나눕니다."))
                .isEqualTo("보청기 적응 경험을 나눕니다.");
    }

    @Test
    void 줄바꿈과_탭과_연속_공백을_한_칸으로_정리한다() {
        assertThat(AdminOperationLogContentPreview.from("  첫째\n\r\n둘째\t\t 셋째   넷째  "))
                .isEqualTo("첫째 둘째 셋째 넷째");
    }

    @Test
    void NBSP와_전각_공백도_정리한다() {
        assertThat(AdminOperationLogContentPreview.from("\u00A0\u3000첫째\u00A0\u00A0둘째\u3000\t셋째\u3000"))
                .isEqualTo("첫째 둘째 셋째");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\r\t", "\u00A0\u3000\t  \n"})
    void 빈_문자열과_공백만_있는_본문은_빈_문자열이다(String content) {
        assertThat(AdminOperationLogContentPreview.from(content)).isEmpty();
    }

    @Test
    void 정확히_120_코드포인트에는_말줄임표가_없다() {
        String content = "가".repeat(119) + "😀";
        assertThat(AdminOperationLogContentPreview.from(content)).isEqualTo(content);
    }

    @ParameterizedTest
    @ValueSource(ints = {121, 200})
    void 초과한_경우에만_120_코드포인트_뒤에_말줄임표를_붙인다(int length) {
        assertThat(AdminOperationLogContentPreview.from("가".repeat(length)))
                .isEqualTo("가".repeat(120) + "…");
    }

    @Test
    void 공백_정리_이후의_길이가_120이면_자르지_않는다() {
        String content = "  " + "가".repeat(59) + "\n\t\u00A0\u3000" + "나".repeat(60) + "   ";
        assertThat(AdminOperationLogContentPreview.from(content))
                .isEqualTo("가".repeat(59) + " " + "나".repeat(60));
    }

    @Test
    void 공백_정리_이후에도_초과하면_정리된_본문을_제한한다() {
        String content = "\u3000" + "가".repeat(59) + "\n\t  " + "나".repeat(61) + "\u00A0";
        assertThat(AdminOperationLogContentPreview.from(content))
                .isEqualTo("가".repeat(59) + " " + "나".repeat(60) + "…");
    }

    @Test
    void 경계의_보조_평면_문자를_중간에서_자르지_않는다() {
        assertThat(AdminOperationLogContentPreview.from("가".repeat(119) + "😀" + "나"))
                .isEqualTo("가".repeat(119) + "😀…");
    }

    @Test
    void null_본문은_거부한다() {
        assertThatThrownBy(() -> AdminOperationLogContentPreview.from(null))
                .isInstanceOf(NullPointerException.class);
    }
}
