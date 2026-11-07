package io.github.takgeun.iyum.admin.content.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminPostReadQueryDateSortTest {

    @Test
    void 기존_생성자와_팩토리는_날짜_제한_없이_최신순을_사용한다() {
        List<AdminPostReadQuery> queries = List.of(
                new AdminPostReadQuery(
                        AdminPostStatus.ALL, null, 0, 20
                ),
                new AdminPostReadQuery(
                        0, 20, null, Set.of(AdminPostStatus.VISIBLE)
                ),
                AdminPostReadQuery.ofStatuses(
                        List.of(AdminPostStatus.HIDDEN), null, 0, 20
                )
        );

        assertThat(queries).allSatisfy(query -> {
            assertThat(query.startDate()).isNull();
            assertThat(query.endDate()).isNull();
            assertThat(query.fromInclusive()).isNull();
            assertThat(query.toExclusive()).isNull();
            assertThat(query.sort()).isEqualTo(AdminPostSort.LATEST);
        });
    }

    @Test
    void 정렬이_null이면_최신순을_적용한다() {
        AdminPostReadQuery query = new AdminPostReadQuery(
                0,
                20,
                null,
                Set.of(AdminPostStatus.VISIBLE),
                null,
                null,
                null
        );

        assertThat(query.sort()).isEqualTo(AdminPostSort.LATEST);
    }

    @Test
    void 다중_상태와_검색어_날짜_정렬을_함께_보존한다() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 7);

        AdminPostReadQuery query = AdminPostReadQuery.ofStatuses(
                List.of(AdminPostStatus.VISIBLE, AdminPostStatus.HIDDEN),
                "  보청기  ",
                2,
                10,
                start,
                end,
                AdminPostSort.OLDEST
        );

        assertThat(query.statuses()).containsExactlyInAnyOrder(
                AdminPostStatus.VISIBLE,
                AdminPostStatus.HIDDEN
        );
        assertThat(query.keyword()).isEqualTo("보청기");
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(10);
        assertThat(query.startDate()).isEqualTo(start);
        assertThat(query.endDate()).isEqualTo(end);
        assertThat(query.sort()).isEqualTo(AdminPostSort.OLDEST);
    }

    @Test
    void 같은_날짜는_한국_날짜_하루_전체의_경계로_변환한다() {
        LocalDate date = LocalDate.of(2026, 11, 7);

        AdminPostReadQuery query = query(date, date);

        assertThat(query.fromInclusive())
                .isEqualTo(Instant.parse("2026-11-06T15:00:00Z"));

        assertThat(query.toExclusive())
                .isEqualTo(Instant.parse("2026-11-07T15:00:00Z"));
    }

    @Test
    void 시작일만_지정할_수_있다() {
        AdminPostReadQuery query = query(
                LocalDate.of(2026, 11, 7),
                null
        );

        assertThat(query.fromInclusive())
                .isEqualTo(Instant.parse("2026-11-06T15:00:00Z"));
        assertThat(query.toExclusive()).isNull();
    }

    @Test
    void 종료일만_지정할_수_있다() {
        AdminPostReadQuery query = query(
                null,
                LocalDate.of(2026, 11, 7)
        );

        assertThat(query.fromInclusive()).isNull();
        assertThat(query.toExclusive())
                .isEqualTo(Instant.parse("2026-11-07T15:00:00Z"));
    }

    @Test
    void 시작일이_종료일보다_늦으면_거부한다() {
        assertThatThrownBy(() -> query(
                LocalDate.of(2026, 11, 8),
                LocalDate.of(2026, 11, 7)
        )).isInstanceOfSatisfying(
                AdminContentReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminContentReadException.Code.INVALID_DATE_RANGE
                        )
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void 허용_연도_밖의_시작일과_종료일은_거부한다(int year) {
        LocalDate invalidDate = LocalDate.of(year, 1, 1);

        assertThatThrownBy(() -> query(invalidDate, null))
                .isInstanceOfSatisfying(
                        AdminContentReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        AdminContentReadException.Code.INVALID_DATE_RANGE
                                )
                );

        assertThatThrownBy(() -> query(null, invalidDate))
                .isInstanceOfSatisfying(
                        AdminContentReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        AdminContentReadException.Code.INVALID_DATE_RANGE
                                )
                );
    }

    @Test
    void 날짜_최솟값과_최댓값을_허용하고_조회_경계를_변환한다() {
        LocalDate start = LocalDate.of(1, 1, 1);
        LocalDate end = LocalDate.of(9999, 12, 31);

        AdminPostReadQuery query = query(start, end);

        assertThat(query.startDate()).isEqualTo(start);
        assertThat(query.endDate()).isEqualTo(end);
        assertThat(query.fromInclusive()).isNotNull();

        // 종료일 다음 날의 한국 시간 00:00을 UTC로 변환한 값.
        assertThat(query.toExclusive())
                .isEqualTo(Instant.parse("9999-12-31T15:00:00Z"));
    }

    private AdminPostReadQuery query(
            LocalDate startDate,
            LocalDate endDate
    ) {
        return new AdminPostReadQuery(
                0,
                20,
                null,
                Set.of(AdminPostStatus.VISIBLE),
                startDate,
                endDate,
                AdminPostSort.LATEST
        );
    }
}