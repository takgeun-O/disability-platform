package io.github.takgeun.iyum.admin.member.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminMemberReadQueryTest {

    @Test
    void 검색어가_없으면_검색_조건을_적용하지_않는다() {
        AdminMemberReadQuery query = query(
                AdminMemberSearchType.EMAIL,
                "   "
        );

        assertThat(query.keyword()).isNull();
        assertThat(query.searchType()).isNull();
        assertThat(query.sort()).isEqualTo(AdminMemberSort.LATEST);
    }

    @Test
    void 검색어가_있으면_검색_유형이_필요하다() {
        assertThatThrownBy(() -> query(null, "이움"))
                .isInstanceOfSatisfying(
                        AdminMemberReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        AdminMemberReadException.Code
                                                .INVALID_REQUEST_PARAMETER
                                )
                );
    }

    @Test
    void 이메일은_기존_회원_정규화_규칙을_따른다() {
        AdminMemberReadQuery query = query(
                AdminMemberSearchType.EMAIL,
                "  Member@Example.COM  "
        );

        assertThat(query.keyword())
                .isEqualTo("member@example.com");
    }

    @Test
    void 회원_ID를_검증하고_숫자로_제공한다() {
        AdminMemberReadQuery query = query(
                AdminMemberSearchType.MEMBER_ID,
                " 00104 "
        );

        assertThat(query.keyword()).isEqualTo("104");
        assertThat(query.memberIdKeyword()).isEqualTo(104L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "-1",
            "+1",
            "1.5",
            "abc",
            "9223372036854775808"
    })
    void 잘못된_회원_ID는_거절한다(String keyword) {
        assertThatThrownBy(() -> query(
                AdminMemberSearchType.MEMBER_ID,
                keyword
        ))
                .isInstanceOfSatisfying(
                        AdminMemberReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        AdminMemberReadException.Code
                                                .INVALID_SEARCH_KEYWORD
                                )
                );
    }

    @Test
    void 닉네임_검색어의_특수문자는_그대로_유지한다() {
        AdminMemberReadQuery query = query(
                AdminMemberSearchType.NICKNAME,
                "  이움_%  "
        );

        assertThat(query.keyword()).isEqualTo("이움_%");
        assertThat(query.memberIdKeyword()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, 51",
            "2147483647, 2"
    })
    void 잘못된_페이지_조건은_거절한다(int page, int size) {
        assertThatThrownBy(() -> new AdminMemberReadQuery(
                page,
                size,
                null,
                null,
                null,
                null,
                null
        ))
                .isInstanceOfSatisfying(
                        AdminMemberReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        AdminMemberReadException.Code
                                                .INVALID_PAGE_REQUEST
                                )
                );
    }

    private AdminMemberReadQuery query(
            AdminMemberSearchType searchType,
            String keyword
    ) {
        return new AdminMemberReadQuery(
                0,
                20,
                searchType,
                keyword,
                null,
                null,
                null
        );
    }
}