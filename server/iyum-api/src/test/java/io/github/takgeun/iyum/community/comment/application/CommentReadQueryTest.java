package io.github.takgeun.iyum.community.comment.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentReadQueryTest {

    private static final Instant TIME =
            Instant.parse("2026-10-28T03:00:00Z");

    @Test
    void 정렬을_생략하면_등록순_첫_조회가_된다() {
        CommentReadQuery query =
                new CommentReadQuery(null, null, 20);

        assertThat(query.sort()).isEqualTo(CommentSort.OLDEST);
        assertThat(query.cursor()).isNull();
        assertThat(query.size()).isEqualTo(20);
    }

    @Test
    void 기존_두_인자_커서는_등록순으로_유지된다() {
        CommentCursor cursor = new CommentCursor(TIME, 10L);

        assertThat(cursor.sort()).isEqualTo(CommentSort.OLDEST);
        assertThat(cursor.likeCount()).isNull();

        CommentReadQuery query =
                new CommentReadQuery(CommentSort.OLDEST, cursor, 20);

        assertThat(query.cursor()).isEqualTo(cursor);
    }

    @ParameterizedTest
    @EnumSource(CommentSort.class)
    void 각_정렬의_첫_조회와_유효한_후속_조회를_허용한다(
            CommentSort sort
    ) {
        CommentReadQuery first =
                new CommentReadQuery(sort, null, 1);

        Long likeCount =
                sort == CommentSort.POPULAR ? 0L : null;

        CommentCursor cursor =
                new CommentCursor(TIME, 10L, sort, likeCount);

        CommentReadQuery next =
                new CommentReadQuery(sort, cursor, 50);

        assertThat(first.sort()).isEqualTo(sort);
        assertThat(next.cursor()).isEqualTo(cursor);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 51})
    void 허용_범위를_벗어난_조회_개수는_거부한다(int size) {
        assertThatThrownBy(
                () -> new CommentReadQuery(
                        CommentSort.OLDEST,
                        null,
                        size
                )
        )
                .isInstanceOfSatisfying(
                        CommentReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        CommentReadException.Code.INVALID_PAGE_REQUEST
                                )
                );
    }

    @ParameterizedTest
    @EnumSource(
            value = CommentSort.class,
            names = {"LATEST", "POPULAR"}
    )
    void 등록순_커서를_다른_정렬에_재사용하면_거부한다(
            CommentSort sort
    ) {
        CommentCursor cursor = new CommentCursor(TIME, 10L);

        assertInvalidCursor(sort, cursor);
    }

    @ParameterizedTest
    @MethodSource("invalidPopularityCursors")
    void 잘못된_인기순_커서는_거부한다(CommentCursor cursor) {
        assertInvalidCursor(CommentSort.POPULAR, cursor);
    }

    static Stream<CommentCursor> invalidPopularityCursors() {
        return Stream.of(
                new CommentCursor(
                        null, 10L, CommentSort.POPULAR, 0L
                ),
                new CommentCursor(
                        TIME, null, CommentSort.POPULAR, 0L
                ),
                new CommentCursor(
                        TIME, 0L, CommentSort.POPULAR, 0L
                ),
                new CommentCursor(
                        TIME, -1L, CommentSort.POPULAR, 0L
                ),
                new CommentCursor(
                        TIME, 10L, CommentSort.POPULAR, null
                ),
                new CommentCursor(
                        TIME, 10L, CommentSort.POPULAR, -1L
                ),
                new CommentCursor(
                        TIME, 10L, null, 0L
                )
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = CommentSort.class,
            names = {"OLDEST", "LATEST"}
    )
    void 시간순_커서에_좋아요_수가_있으면_거부한다(
            CommentSort sort
    ) {
        CommentCursor cursor =
                new CommentCursor(TIME, 10L, sort, 0L);

        assertInvalidCursor(sort, cursor);
    }

    private void assertInvalidCursor(
            CommentSort sort,
            CommentCursor cursor
    ) {
        assertThatThrownBy(
                () -> new CommentReadQuery(sort, cursor, 20)
        )
                .isInstanceOfSatisfying(
                        CommentReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        CommentReadException.Code.INVALID_CURSOR
                                )
                );
    }
}