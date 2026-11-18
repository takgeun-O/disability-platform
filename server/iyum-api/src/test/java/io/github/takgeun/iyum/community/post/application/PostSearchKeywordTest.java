package io.github.takgeun.iyum.community.post.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostSearchKeywordTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void 검색어가_없거나_공백뿐이면_null을_반환한다(String keyword) {
        assertThat(PostSearchKeyword.normalize(keyword))
                .isNull();
    }

    @Test
    void 앞뒤_공백만_제거하고_내부_공백과_특수문자는_유지한다() {
        String keyword = "\t  보청기  50%_할인  \n";

        assertThat(PostSearchKeyword.normalize(keyword))
                .isEqualTo("보청기  50%_할인");
    }

    @Test
    void 앞뒤_공백을_제거한_뒤_이모지_100개는_허용한다() {
        String keyword = "😀".repeat(100);

        assertThat(PostSearchKeyword.normalize(" " + keyword + " "))
                .isEqualTo(keyword);
    }

    @Test
    void 검색어가_101코드포인트이면_검색어_오류가_발생한다() {
        String keyword = "😀".repeat(101);

        assertThatThrownBy(() -> PostSearchKeyword.normalize(keyword))
                .isInstanceOfSatisfying(
                        PostReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        PostReadException.Code.INVALID_SEARCH_KEYWORD
                                )
                );
    }
}