package io.github.takgeun.iyum.community.post.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PostContentPreviewTest {

    @Test
    void 줄바꿈과_연속_공백을_한_칸으로_정리한다() {
        String content =
                " \t첫 번째 줄\r\n\n두 번째\u00A0\u3000줄  ";

        String result = PostContentPreview.from(content);

        assertThat(result)
                .isEqualTo("첫 번째 줄 두 번째 줄");
    }

    @ParameterizedTest
    @ValueSource(ints = {199, 200, 201})
    void 미리보기는_최대_200_코드_포인트까지_반환한다(int length) {
        String content = "가".repeat(length);

        String result = PostContentPreview.from(content);

        assertThat(result)
                .isEqualTo("가".repeat(Math.min(length, 200)));
    }

    @Test
    void 경계에_있는_이모지의_UTF16_문자쌍을_쪼개지_않는다() {
        String content = "가".repeat(199) + "😀" + "나";

        String result = PostContentPreview.from(content);

        assertThat(result)
                .isEqualTo("가".repeat(199) + "😀");

        assertThat(result.codePointCount(0, result.length()))
                .isEqualTo(200);
    }

    @Test
    void 잘라낸_결과의_끝에_남은_공백을_제거한다() {
        String content = "가".repeat(199) + " " + "나";

        String result = PostContentPreview.from(content);

        assertThat(result)
                .isEqualTo("가".repeat(199));
    }
}