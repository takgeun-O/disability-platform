package io.github.takgeun.iyum.community.post.domain;

import io.github.takgeun.iyum.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PostModerationTest {

    private static final Instant FIRST =
            Instant.parse("2026-10-29T10:00:00Z");

    private static final Instant SECOND =
            Instant.parse("2026-10-29T11:00:00Z");

    private Post target;

    @BeforeEach
    void setUp() {
        target = Post.create(
                mock(Member.class),
                PostType.GENERAL,
                PostTopic.DAILY,
                "테스트 제목",
                "테스트 내용"
        );
    }

    @Test
    void 생성한_콘텐츠는_숨김_상태가_아니다() {
        assertThat(target.getHiddenAt()).isNull();
    }

    @Test
    void 공개_콘텐츠를_숨기면_시각을_기록한다() {
        assertThat(target.hide(FIRST)).isTrue();

        assertThat(target.getHiddenAt()).isEqualTo(FIRST);
        assertThat(target.getDeletedAt()).isNull();
        assertThat(target.getContent()).isEqualTo("테스트 내용");
    }

    @Test
    void 중복_숨김은_최초_숨김_시각을_유지한다() {
        target.hide(FIRST);

        assertThat(target.hide(SECOND)).isFalse();

        assertThat(target.getHiddenAt()).isEqualTo(FIRST);
    }

    @Test
    void 숨김_시각이_없으면_거부한다() {
        assertThatThrownBy(() -> target.hide(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(target.getHiddenAt()).isNull();
    }

    @Test
    void 숨긴_콘텐츠를_복원한다() {
        target.hide(FIRST);

        assertThat(target.restore()).isTrue();

        assertThat(target.getHiddenAt()).isNull();
        assertThat(target.getDeletedAt()).isNull();
        assertThat(target.getContent()).isEqualTo("테스트 내용");
    }

    @Test
    void 공개_콘텐츠의_복원은_변경하지_않는다() {
        assertThat(target.restore()).isFalse();

        assertThat(target.getHiddenAt()).isNull();
    }

    @Test
    void 복원_후_다시_숨기면_새로운_숨김_시각을_기록한다() {
        target.hide(FIRST);
        target.restore();

        assertThat(target.hide(SECOND)).isTrue();

        assertThat(target.getHiddenAt()).isEqualTo(SECOND);
    }

    @Test
    void 삭제된_콘텐츠는_숨길_수_없다() {
        target.delete(FIRST);

        assertThatThrownBy(() -> target.hide(SECOND))
                .isInstanceOf(IllegalStateException.class);

        assertThat(target.getHiddenAt()).isNull();
        assertThat(target.getDeletedAt()).isEqualTo(FIRST);
    }

    @Test
    void 숨기지_않았어도_삭제된_콘텐츠의_복원은_거부한다() {
        target.delete(FIRST);

        assertThatThrownBy(target::restore)
                .isInstanceOf(IllegalStateException.class);

        assertThat(target.getDeletedAt()).isEqualTo(FIRST);
    }

    @Test
    void 숨긴_콘텐츠도_삭제할_수_있고_이후_복원은_거부한다() {
        target.hide(FIRST);
        target.delete(SECOND);

        assertThat(target.getDeletedAt()).isEqualTo(SECOND);
        assertThat(target.getHiddenAt()).isEqualTo(FIRST);

        assertThatThrownBy(target::restore)
                .isInstanceOf(IllegalStateException.class);

        assertThat(target.getDeletedAt()).isEqualTo(SECOND);
        assertThat(target.getHiddenAt()).isEqualTo(FIRST);
    }
}