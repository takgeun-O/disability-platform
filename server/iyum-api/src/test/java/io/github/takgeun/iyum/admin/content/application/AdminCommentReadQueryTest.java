package io.github.takgeun.iyum.admin.content.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminCommentReadQueryTest {

    @Test
    void 상태가_없으면_ALL을_적용한다() {
        AdminCommentReadQuery query =
                new AdminCommentReadQuery(null, null, 0, 20);

        assertThat(query.status()).isEqualTo(AdminCommentStatus.ALL);
    }

    @Test
    void 검색어의_앞뒤_공백만_제거하고_나머지_조건은_유지한다() {
        AdminCommentReadQuery query =
                new AdminCommentReadQuery(
                        AdminCommentStatus.HIDDEN,
                        "  보청기 적응  ",
                        2,
                        50
                );

        assertThat(query.status()).isEqualTo(AdminCommentStatus.HIDDEN);
        assertThat(query.keyword()).isEqualTo("보청기 적응");
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(50);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t\n", "\u3000"})
    void 검색어가_없거나_공백뿐이면_검색_조건을_제거한다(
            String keyword
    ) {
        AdminCommentReadQuery query =
                new AdminCommentReadQuery(
                        AdminCommentStatus.ALL,
                        keyword,
                        0,
                        20
                );

        assertThat(query.keyword()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, -1",
            "0, 51",
            "2147483647, 2"
    })
    void 페이지_조건이나_조회_시작_위치가_유효하지_않으면_거부한다(
            int page,
            int size
    ) {
        assertThatThrownBy(() ->
                new AdminCommentReadQuery(
                        AdminCommentStatus.ALL,
                        null,
                        page,
                        size
                )
        ).isInstanceOfSatisfying(
                AdminContentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminContentReadException.Code.INVALID_PAGE_REQUEST
                        )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 검색어는_코드포인트_100자까지_허용한다(
            String character
    ) {
        String keyword = character.repeat(100);

        AdminCommentReadQuery query =
                new AdminCommentReadQuery(
                        AdminCommentStatus.ALL,
                        "  " + keyword + "  ",
                        0,
                        20
                );

        assertThat(query.keyword()).isEqualTo(keyword);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 검색어가_코드포인트_100자를_초과하면_거부한다(
            String character
    ) {
        assertThatThrownBy(() ->
                new AdminCommentReadQuery(
                        AdminCommentStatus.ALL,
                        character.repeat(101),
                        0,
                        20
                )
        ).isInstanceOfSatisfying(
                AdminContentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminContentReadException.Code.INVALID_SEARCH_KEYWORD
                        )
        );
    }

    @Test
    void 특수문자_검색어는_그대로_유지한다() {
        AdminCommentReadQuery query =
                new AdminCommentReadQuery(
                        AdminCommentStatus.ALL,
                        "  50%_할인!  ",
                        0,
                        20
                );

        assertThat(query.keyword()).isEqualTo("50%_할인!");
    }

    @Test
    void 조회_시작_위치가_int_최댓값이면_허용한다() {
        AdminCommentReadQuery query =
                new AdminCommentReadQuery(
                        AdminCommentStatus.ALL,
                        null,
                        Integer.MAX_VALUE,
                        1
                );

        assertThat(query.page()).isEqualTo(Integer.MAX_VALUE);
        assertThat(query.size()).isEqualTo(1);
    }
}