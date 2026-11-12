package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminReportReadQueryTest {

    @Test
    void 대상과_정렬을_생략하면_전체_대상과_최신순을_사용한다() {
        AdminReportReadQuery query = new AdminReportReadQuery(
                null,
                null,
                null,
                null,
                null,
                AdminReportReadQuery.DEFAULT_PAGE,
                AdminReportReadQuery.DEFAULT_SIZE,
                null
        );

        assertThat(query.targetType())
                .isEqualTo(AdminReportTargetType.ALL);

        assertThat(query.sort())
                .isEqualTo(AdminReportSort.LATEST);

        // null은 전체 조회 조건이다.
        assertThat(query.status()).isNull();
        assertThat(query.reasonType()).isNull();

        assertThat(query.fromInclusive()).isNull();
        assertThat(query.toExclusive()).isNull();
    }

    @Test
    void 지정한_검색_조건은_기본값으로_덮어쓰지_않는다() {
        LocalDate startDate = LocalDate.of(2026, 11, 1);
        LocalDate endDate = LocalDate.of(2026, 11, 12);

        AdminReportReadQuery query = new AdminReportReadQuery(
                AdminReportTargetType.COMMENT,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.PERSONAL_INFORMATION,
                startDate,
                endDate,
                2,
                50,
                AdminReportSort.OLDEST
        );

        assertThat(query.targetType())
                .isEqualTo(AdminReportTargetType.COMMENT);
        assertThat(query.status())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(query.reasonType())
                .isEqualTo(ContentReportReasonType.PERSONAL_INFORMATION);
        assertThat(query.startDate()).isEqualTo(startDate);
        assertThat(query.endDate()).isEqualTo(endDate);
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(50);
        assertThat(query.sort()).isEqualTo(AdminReportSort.OLDEST);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    void 음수_페이지는_거부한다(int page) {
        assertPageFailure(page, 20);
    }

    @ParameterizedTest
    @ValueSource(ints = {
            Integer.MIN_VALUE,
            -1,
            0,
            51,
            Integer.MAX_VALUE
    })
    void 페이지_크기가_1부터_50_범위가_아니면_거부한다(
            int size
    ) {
        assertPageFailure(0, size);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 50})
    void 허용된_페이지_크기를_사용할_수_있다(int size) {
        AdminReportReadQuery query = query(
                null,
                null,
                0,
                size
        );

        assertThat(query.size()).isEqualTo(size);
    }

    @Test
    void offset이_Integer_MAX_VALUE이면_허용한다() {
        AdminReportReadQuery query = query(
                null,
                null,
                Integer.MAX_VALUE,
                1
        );

        assertThat((long) query.page() * query.size())
                .isEqualTo((long) Integer.MAX_VALUE);
    }

    @Test
    void 페이지_크기_50에서_허용되는_마지막_offset을_처리한다() {
        int lastAllowedPage = Integer.MAX_VALUE / 50;

        AdminReportReadQuery query = query(
                null,
                null,
                lastAllowedPage,
                50
        );

        assertThat((long) query.page() * query.size())
                .isLessThanOrEqualTo((long) Integer.MAX_VALUE);
    }

    @Test
    void offset이_Integer_MAX_VALUE를_초과하면_거부한다() {
        int firstRejectedPage = Integer.MAX_VALUE / 50 + 1;

        assertPageFailure(firstRejectedPage, 50);
    }

    @Test
    void int_곱셈_오버플로로_잘못된_offset이_통과하지_않는다() {
        assertPageFailure(Integer.MAX_VALUE, 50);
    }

    @Test
    void 시작일이_종료일보다_늦으면_거부한다() {
        assertDateFailure(
                LocalDate.of(2026, 11, 13),
                LocalDate.of(2026, 11, 12)
        );
    }

    @Test
    void 같은_날짜는_한국_시간_하루_전체를_조회한다() {
        LocalDate date = LocalDate.of(2026, 11, 12);

        AdminReportReadQuery query = query(
                date,
                date,
                0,
                20
        );

        assertThat(query.fromInclusive())
                .isEqualTo(
                        Instant.parse("2026-11-11T15:00:00Z")
                );

        assertThat(query.toExclusive())
                .isEqualTo(
                        Instant.parse("2026-11-12T15:00:00Z")
                );
    }

    @Test
    void 시작일만_지정하면_시작_시각만_계산한다() {
        AdminReportReadQuery query = query(
                LocalDate.of(2026, 11, 12),
                null,
                0,
                20
        );

        assertThat(query.fromInclusive())
                .isEqualTo(
                        Instant.parse("2026-11-11T15:00:00Z")
                );

        assertThat(query.toExclusive()).isNull();
    }

    @Test
    void 종료일만_지정하면_다음_날_시작_시각만_계산한다() {
        AdminReportReadQuery query = query(
                null,
                LocalDate.of(2026, 11, 12),
                0,
                20
        );

        assertThat(query.fromInclusive()).isNull();

        assertThat(query.toExclusive())
                .isEqualTo(
                        Instant.parse("2026-11-12T15:00:00Z")
                );
    }

    @Test
    void 연말_종료일은_다음_연도_첫날을_상한으로_계산한다() {
        AdminReportReadQuery query = query(
                null,
                LocalDate.of(2026, 12, 31),
                0,
                20
        );

        assertThat(query.toExclusive())
                .isEqualTo(
                        Instant.parse("2026-12-31T15:00:00Z")
                );
    }

    @Test
    void 윤년의_2월_29일도_하루_전체를_조회한다() {
        LocalDate date = LocalDate.of(2028, 2, 29);

        AdminReportReadQuery query = query(
                date,
                date,
                0,
                20
        );

        assertThat(query.fromInclusive())
                .isEqualTo(
                        Instant.parse("2028-02-28T15:00:00Z")
                );

        assertThat(query.toExclusive())
                .isEqualTo(
                        Instant.parse("2028-02-29T15:00:00Z")
                );
    }

    @ParameterizedTest
    @CsvSource({
            "START, -1",
            "START, 0",
            "START, 10000",
            "END, -1",
            "END, 0",
            "END, 10000"
    })
    void 허용_연도_범위를_벗어난_날짜는_거부한다(
            String position,
            int year
    ) {
        LocalDate invalidDate = LocalDate.of(year, 1, 1);

        if ("START".equals(position)) {
            assertDateFailure(invalidDate, null);
        } else {
            assertDateFailure(null, invalidDate);
        }
    }

    @Test
    void LocalDate의_극단적인_값도_범위_오류로_거부한다() {
        assertDateFailure(LocalDate.MIN, null);
        assertDateFailure(null, LocalDate.MAX);
    }

    @Test
    void 최소_허용_날짜를_한국_시간_기준으로_변환한다() {
        LocalDate minimumDate = LocalDate.of(1, 1, 1);
        ZoneId zone = ZoneId.of("Asia/Seoul");

        AdminReportReadQuery query = query(
                minimumDate,
                minimumDate,
                0,
                20
        );

        /*
         * 과거 날짜의 Asia/Seoul 오프셋은 현대의 +09:00과
         * 다를 수 있으므로 ZoneId 정책에 따라 비교한다.
         */
        assertThat(query.fromInclusive())
                .isEqualTo(
                        minimumDate.atStartOfDay(zone).toInstant()
                );

        assertThat(query.toExclusive())
                .isEqualTo(
                        minimumDate.plusDays(1)
                                .atStartOfDay(zone)
                                .toInstant()
                );
    }

    @Test
    void 최대_허용_종료일의_다음_날도_계산할_수_있다() {
        LocalDate maximumDate = LocalDate.of(9999, 12, 31);
        ZoneId zone = ZoneId.of("Asia/Seoul");

        AdminReportReadQuery query = query(
                maximumDate,
                maximumDate,
                0,
                20
        );

        assertThat(query.fromInclusive())
                .isEqualTo(
                        maximumDate.atStartOfDay(zone).toInstant()
                );

        /*
         * 입력 날짜는 9999-12-31까지 허용한다.
         * 조회용 상한은 그 다음 날인 10000-01-01이다.
         */
        assertThat(query.toExclusive())
                .isEqualTo(
                        LocalDate.of(10000, 1, 1)
                                .atStartOfDay(zone)
                                .toInstant()
                );
    }

    private AdminReportReadQuery query(
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        return new AdminReportReadQuery(
                AdminReportTargetType.ALL,
                null,
                null,
                startDate,
                endDate,
                page,
                size,
                AdminReportSort.LATEST
        );
    }

    private void assertPageFailure(
            int page,
            int size
    ) {
        assertThatThrownBy(() ->
                query(null, null, page, size)
        )
                .isInstanceOfSatisfying(
                        AdminReportReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        AdminReportReadException.Code
                                                .INVALID_PAGE_REQUEST
                                )
                );
    }

    private void assertDateFailure(
            LocalDate startDate,
            LocalDate endDate
    ) {
        assertThatThrownBy(() ->
                query(startDate, endDate, 0, 20)
        )
                .isInstanceOfSatisfying(
                        AdminReportReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        AdminReportReadException.Code
                                                .INVALID_DATE_RANGE
                                )
                );
    }
}