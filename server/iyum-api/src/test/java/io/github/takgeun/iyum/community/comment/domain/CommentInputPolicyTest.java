package io.github.takgeun.iyum.community.comment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentInputPolicyTest {

    @Test
    void 정상_내용의_공백과_줄바꿈을_보존한다() {
        String content = "  첫 번째 줄\n\n두 번째 줄  ";

        String result =
                CommentInputPolicy.validateContent(content);

        assertThat(result).isEqualTo(content);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "   ",
            "\t",
            "\n",
            " \t\n "
    })
    void 내용이_없거나_공백뿐이면_거부한다(String content) {
        assertThatThrownBy(() ->
                CommentInputPolicy.validateContent(content)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("댓글 내용은 필수입니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 이천_코드포인트까지_허용한다(String character) {
        String content = character.repeat(2_000);

        String result =
                CommentInputPolicy.validateContent(content);

        assertThat(result).isEqualTo(content);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 이천_코드포인트를_초과하면_거부한다(String character) {
        String content = character.repeat(2_001);

        assertThatThrownBy(() ->
                CommentInputPolicy.validateContent(content)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("댓글 내용은 2,000자 이하여야 합니다.");
    }

    @Test
    void 공백과_줄바꿈도_길이에_포함한다() {
        String content = "가".repeat(1_999) + " \n";

        assertThatThrownBy(() ->
                CommentInputPolicy.validateContent(content)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("댓글 내용은 2,000자 이하여야 합니다.");
    }
}