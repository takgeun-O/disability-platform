package io.github.takgeun.iyum.community.post.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostInputPolicyTest {

    @Test
    void 제목은_앞뒤_공백만_제거한다() {
        String result = PostInputPolicy.normalizeTitle(
                "  보청기  질문입니다  "
        );

        assertThat(result).isEqualTo("보청기  질문입니다");
    }

    @ParameterizedTest
    @NullAndEmptySource     // null과 ""를 각각 전달
    @ValueSource(strings = {" ", "\t\n", "　"})
    void 제목이_없거나_공백뿐이면_거부한다(String title) {
        assertThatThrownBy(() ->
                PostInputPolicy.normalizeTitle(title)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("제목은 필수입니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 120})
    void 제목은_공백_제거_후_1자부터_120자까지_허용한다(int length) {
        String title = "가".repeat(length);

        String result = PostInputPolicy.normalizeTitle(
                "  " + title + "  "
        );

        assertThat(result).isEqualTo(title);
    }

    @Test
    void 제목이_121자이면_거부한다() {
        String title = "가".repeat(121);

        assertThatThrownBy(() ->
                PostInputPolicy.normalizeTitle(title)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("제목은 120자 이하여야 합니다.");
    }

    @Test
    void 제목_길이는_유니코드_코드_포인트로_계산한다() {
        String allowed = "😀".repeat(120);
        String rejected = "😀".repeat(121);

        assertThat(
                PostInputPolicy.normalizeTitle(allowed)
        ).isEqualTo(allowed);

        assertThatThrownBy(() ->
                PostInputPolicy.normalizeTitle(rejected)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "　"})
    void 본문이_없거나_공백뿐이면_거부한다(String content) {
        assertThatThrownBy(() ->
                PostInputPolicy.validateContent(content)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("본문은 필수입니다.");
    }

    @Test
    void 본문의_공백과_줄바꿈은_보존한다() {
        String content = "\n  첫 번째 줄\n\n두 번째 줄  \n";

        String result = PostInputPolicy.validateContent(content);

        assertThat(result).isEqualTo(content);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10_000})
    void 본문은_1자부터_10000자까지_허용한다(int length) {
        String content = "가".repeat(length);

        assertThat(
                PostInputPolicy.validateContent(content)
        ).isEqualTo(content);
    }

    @Test
    void 본문이_10001자이면_거부한다() {
        String content = "가".repeat(10_001);

        assertThatThrownBy(() ->
                PostInputPolicy.validateContent(content)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("본문은 10,000자 이하여야 합니다.");
    }

    @Test
    void 본문_길이는_유니코드_코드_포인트로_계산한다() {
        String allowed = "😀".repeat(10_000);
        String rejected = "😀".repeat(10_001);

        assertThat(
                PostInputPolicy.validateContent(allowed)
        ).isEqualTo(allowed);

        assertThatThrownBy(() ->
                PostInputPolicy.validateContent(rejected)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}