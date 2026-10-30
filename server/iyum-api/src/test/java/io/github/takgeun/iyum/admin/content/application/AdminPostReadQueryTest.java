package io.github.takgeun.iyum.admin.content.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminPostReadQueryTest {

    @Test
    void 상태가_없으면_ALL을_적용한다() {
        AdminPostReadQuery query =
                new AdminPostReadQuery(null, null, 0, 20);

        assertThat(query.status()).isEqualTo(AdminPostStatus.ALL);
    }

    @Test
    void 검색어의_앞뒤_공백만_제거한다() {
        AdminPostReadQuery query =
                new AdminPostReadQuery(
                        AdminPostStatus.HIDDEN,
                        "  보청기 적응  ",
                        2,
                        50
                );

        assertThat(query.status()).isEqualTo(AdminPostStatus.HIDDEN);
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
        AdminPostReadQuery query =
                new AdminPostReadQuery(
                        AdminPostStatus.ALL,
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
            "0, 51"
    })
    void 잘못된_페이지_조건은_거부한다(int page, int size) {
        assertThatThrownBy(() ->
                new AdminPostReadQuery(
                        AdminPostStatus.ALL,
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
    void 검색어는_코드_포인트_기준_100자까지_허용한다(
            String character
    ) {
        String keyword = character.repeat(100);

        AdminPostReadQuery query =
                new AdminPostReadQuery(
                        AdminPostStatus.ALL,
                        "  " + keyword + "  ",
                        0,
                        20
                );

        assertThat(query.keyword()).isEqualTo(keyword);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 검색어가_코드_포인트_기준_100자를_초과하면_거부한다(
            String character
    ) {
        assertThatThrownBy(() ->
                new AdminPostReadQuery(
                        AdminPostStatus.ALL,
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
    void 조회_시작_위치가_int_범위를_초과하면_거부한다() {
        assertThatThrownBy(() ->
                new AdminPostReadQuery(
                        AdminPostStatus.ALL,
                        null,
                        Integer.MAX_VALUE,
                        2
                )
        ).isInstanceOfSatisfying(
                AdminContentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminContentReadException.Code.INVALID_PAGE_REQUEST
                        )
        );
    }

    @Test
    void 조회_시작_위치가_int_최댓값이면_허용한다() {
        AdminPostReadQuery query = new AdminPostReadQuery(
                AdminPostStatus.ALL,
                null,
                Integer.MAX_VALUE,
                1
        );

        assertThat(query.page()).isEqualTo(Integer.MAX_VALUE);
        assertThat(query.size()).isEqualTo(1);
    }
}