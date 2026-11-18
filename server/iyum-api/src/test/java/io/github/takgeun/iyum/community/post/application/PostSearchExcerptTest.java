package io.github.takgeun.iyum.community.post.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostSearchExcerptTest {

    @Test
    void 짧은_본문은_줄바꿈과_대소문자를_포함해_원문을_그대로_반환한다() {
        String content = "첫 방문 후기입니다.\nHello라고 인사했어요.";
        String match = "Hello";
        int start = content.indexOf(match);

        String excerpt = PostSearchExcerpt.aroundMatch(
                content,
                start,
                start + match.length()
        );

        assertThat(excerpt).isEqualTo(content);
    }

    @Test
    void 긴_본문_중간에서는_일치한_원문과_앞뒤_40코드포인트를_반환한다() {
        String content = "가".repeat(50) + "보청기" + "나".repeat(60);
        int start = content.indexOf("보청기");

        String excerpt = PostSearchExcerpt.aroundMatch(
                content,
                start,
                start + "보청기".length()
        );

        assertThat(excerpt).isEqualTo(
                "…" + "가".repeat(40) + "보청기" + "나".repeat(40) + "…"
        );
    }

    @Test
    void 본문_처음에서_일치하면_생략된_뒤쪽에만_말줄임표를_붙인다() {
        String content = "안녕" + "뒤".repeat(41);

        String excerpt = PostSearchExcerpt.aroundMatch(
                content,
                0,
                "안녕".length()
        );

        assertThat(excerpt).isEqualTo("안녕" + "뒤".repeat(40) + "…");
    }

    @Test
    void 본문_끝에서_일치하면_앞쪽_이모지_40개를_온전히_보존한다() {
        String content = "😀".repeat(41) + "안녕";
        int start = content.indexOf("안녕");

        String excerpt = PostSearchExcerpt.aroundMatch(
                content,
                start,
                content.length()
        );

        assertThat(excerpt).isEqualTo("…" + "😀".repeat(40) + "안녕");
    }

    @Test
    void 양쪽_문맥이_정확히_40자이면_말줄임표를_붙이지_않는다() {
        String match = "😀".repeat(100);
        String content = "앞".repeat(40) + match + "뒤".repeat(40);
        int start = content.indexOf(match);

        String excerpt = PostSearchExcerpt.aroundMatch(
                content,
                start,
                start + match.length()
        );

        assertThat(excerpt).isEqualTo(content);
    }

    @Test
    void 본문_일치_위치와_길이를_코드포인트로_받아_원문을_발췌한다() {
        String content = "😀".repeat(50)
                + "A🦻B"
                + "뒤".repeat(50);

        // 앞에 이모지 50개가 있으므로 DB 기준 첫 일치 위치는 51이다.
        // A🦻B는 코드포인트 3개다.
        String excerpt = PostSearchExcerpt.fromBodyMatchPosition(
                content,
                51,
                3
        );

        assertThat(excerpt).isEqualTo(
                "…" + "😀".repeat(40) + "A🦻B" + "뒤".repeat(40) + "…"
        );
    }

    @Test
    void 본문_일치_위치가_0이면_발췌문은_null이다() {
        String excerpt = PostSearchExcerpt.fromBodyMatchPosition(
                "센터에 다녀왔어요.",
                0,
                3
        );

        assertThat(excerpt).isNull();
    }
}