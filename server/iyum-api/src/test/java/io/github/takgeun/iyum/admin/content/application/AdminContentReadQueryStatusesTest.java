package io.github.takgeun.iyum.admin.content.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminContentReadQueryStatusesTest {

    @Test
    void 다중_상태와_검색어와_페이지_조건을_보존한다() {
        AdminPostReadQuery postQuery =
                AdminPostReadQuery.ofStatuses(
                        List.of(
                                AdminPostStatus.VISIBLE,
                                AdminPostStatus.HIDDEN,
                                AdminPostStatus.VISIBLE
                        ),
                        "  보청기 적응  ",
                        2,
                        50
                );

        assertThat(postQuery.statuses()).containsExactlyInAnyOrder(
                AdminPostStatus.VISIBLE,
                AdminPostStatus.HIDDEN
        );
        assertThat(postQuery.keyword()).isEqualTo("보청기 적응");
        assertThat(postQuery.page()).isEqualTo(2);
        assertThat(postQuery.size()).isEqualTo(50);

        AdminCommentReadQuery commentQuery =
                AdminCommentReadQuery.ofStatuses(
                        List.of(
                                AdminCommentStatus.VISIBLE,
                                AdminCommentStatus.HIDDEN,
                                AdminCommentStatus.VISIBLE
                        ),
                        "  댓글 검색  ",
                        1,
                        20
                );

        assertThat(commentQuery.statuses()).containsExactlyInAnyOrder(
                AdminCommentStatus.VISIBLE,
                AdminCommentStatus.HIDDEN
        );
        assertThat(commentQuery.keyword()).isEqualTo("댓글 검색");
        assertThat(commentQuery.page()).isEqualTo(1);
        assertThat(commentQuery.size()).isEqualTo(20);
    }

    @Test
    void 기존_ALL과_세_상태_선택은_동일한_조회_조건이다() {
        AdminPostReadQuery legacyPostQuery =
                new AdminPostReadQuery(
                        AdminPostStatus.ALL,
                        null,
                        0,
                        20
                );

        AdminPostReadQuery multiplePostQuery =
                AdminPostReadQuery.ofStatuses(
                        List.of(
                                AdminPostStatus.DELETED,
                                AdminPostStatus.VISIBLE,
                                AdminPostStatus.HIDDEN
                        ),
                        null,
                        0,
                        20
                );

        assertThat(multiplePostQuery).isEqualTo(legacyPostQuery);

        AdminCommentReadQuery legacyCommentQuery =
                new AdminCommentReadQuery(
                        AdminCommentStatus.ALL,
                        null,
                        0,
                        20
                );

        AdminCommentReadQuery multipleCommentQuery =
                AdminCommentReadQuery.ofStatuses(
                        List.of(
                                AdminCommentStatus.DELETED,
                                AdminCommentStatus.VISIBLE,
                                AdminCommentStatus.HIDDEN
                        ),
                        null,
                        0,
                        20
                );

        assertThat(multipleCommentQuery).isEqualTo(legacyCommentQuery);
    }

    @Test
    void 명시적인_다중_상태가_비어_있으면_거부한다() {
        assertInvalid(
                () -> AdminPostReadQuery.ofStatuses(
                        List.of(), null, 0, 20
                ),
                AdminContentReadException.Code.INVALID_REQUEST_PARAMETER
        );

        assertInvalid(
                () -> AdminCommentReadQuery.ofStatuses(
                        List.of(), null, 0, 20
                ),
                AdminContentReadException.Code.INVALID_REQUEST_PARAMETER
        );
    }

    @Test
    void 기본_생성자로도_ALL을_상태_집합에_넣을_수_없다() {
        assertInvalid(
                () -> new AdminPostReadQuery(
                        0, 20, null, Set.of(AdminPostStatus.ALL)
                ),
                AdminContentReadException.Code.INVALID_REQUEST_PARAMETER
        );

        assertInvalid(
                () -> new AdminCommentReadQuery(
                        0, 20, null, Set.of(AdminCommentStatus.ALL)
                ),
                AdminContentReadException.Code.INVALID_REQUEST_PARAMETER
        );
    }

    @Test
    void 다중_상태에서도_페이지_검증을_적용한다() {
        assertInvalid(
                () -> AdminPostReadQuery.ofStatuses(
                        List.of(AdminPostStatus.VISIBLE),
                        null,
                        -1,
                        20
                ),
                AdminContentReadException.Code.INVALID_PAGE_REQUEST
        );

        assertInvalid(
                () -> AdminCommentReadQuery.ofStatuses(
                        List.of(AdminCommentStatus.VISIBLE),
                        null,
                        -1,
                        20
                ),
                AdminContentReadException.Code.INVALID_PAGE_REQUEST
        );
    }

    @Test
    void 다중_상태에서도_검색어_길이_검증을_적용한다() {
        String keyword = "😀".repeat(101);

        assertInvalid(
                () -> AdminPostReadQuery.ofStatuses(
                        List.of(AdminPostStatus.VISIBLE),
                        keyword,
                        0,
                        20
                ),
                AdminContentReadException.Code.INVALID_SEARCH_KEYWORD
        );

        assertInvalid(
                () -> AdminCommentReadQuery.ofStatuses(
                        List.of(AdminCommentStatus.VISIBLE),
                        keyword,
                        0,
                        20
                ),
                AdminContentReadException.Code.INVALID_SEARCH_KEYWORD
        );
    }

    private void assertInvalid(
            Runnable action,
            AdminContentReadException.Code expectedCode
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        AdminContentReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
                );
    }
}