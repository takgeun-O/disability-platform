package io.github.takgeun.iyum.admin.operation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminOperationLogReadQueryTest {

    @Test
    void 조건을_생략하면_전체_대상과_전체_기간을_조회한다() {
        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        null, null, null, null, 0, 20
                );

        assertThat(query.targetType())
                .isEqualTo(AdminOperationLogTargetType.ALL);
        assertThat(query.action()).isNull();
        assertThat(query.fromInclusive()).isNull();
        assertThat(query.toExclusive()).isNull();
    }

    @Test
    void 지정한_대상과_조치와_페이지_조건을_유지한다() {
        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        AdminOperationLogTargetType.COMMENT,
                        ModerationAction.RESTORE,
                        null,
                        null,
                        2,
                        50
                );

        assertThat(query.targetType())
                .isEqualTo(AdminOperationLogTargetType.COMMENT);
        assertThat(query.action())
                .isEqualTo(ModerationAction.RESTORE);
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(50);
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, -1",
            "0, 51",
            "2147483647, 2"
    })
    void 잘못된_페이지_조건은_거부한다(int page, int size) {
        assertThatThrownBy(() ->
                new AdminOperationLogReadQuery(
                        null, null, null, null, page, size
                )
        ).isInstanceOfSatisfying(
                AdminOperationLogReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminOperationLogReadException.Code
                                        .INVALID_PAGE_REQUEST
                        )
        );
    }

    @Test
    void 조회_시작_위치가_int_최댓값이면_허용한다() {
        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        null,
                        null,
                        null,
                        null,
                        Integer.MAX_VALUE,
                        1
                );

        assertThat(query.page()).isEqualTo(Integer.MAX_VALUE);
        assertThat(query.size()).isEqualTo(1);
    }

    @Test
    void 같은_날짜는_한국_시간_하루_범위로_변환한다() {
        LocalDate date = LocalDate.of(2026, 11, 4);

        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        null, null, date, date, 0, 20
                );

        assertThat(query.fromInclusive())
                .isEqualTo(Instant.parse("2026-11-03T15:00:00Z"));
        assertThat(query.toExclusive())
                .isEqualTo(Instant.parse("2026-11-04T15:00:00Z"));
    }

    @Test
    void 시작_날짜만_지정할_수_있다() {
        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        null,
                        null,
                        LocalDate.of(2026, 11, 4),
                        null,
                        0,
                        20
                );

        assertThat(query.fromInclusive())
                .isEqualTo(Instant.parse("2026-11-03T15:00:00Z"));
        assertThat(query.toExclusive()).isNull();
    }

    @Test
    void 종료_날짜만_지정할_수_있으며_연도_경계를_처리한다() {
        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        null,
                        null,
                        null,
                        LocalDate.of(2026, 12, 31),
                        0,
                        20
                );

        assertThat(query.fromInclusive()).isNull();
        assertThat(query.toExclusive())
                .isEqualTo(Instant.parse("2026-12-31T15:00:00Z"));
    }

    @Test
    void 시작_날짜가_종료_날짜보다_늦으면_거부한다() {
        assertThatThrownBy(() ->
                new AdminOperationLogReadQuery(
                        null,
                        null,
                        LocalDate.of(2026, 11, 5),
                        LocalDate.of(2026, 11, 4),
                        0,
                        20
                )
        ).isInstanceOfSatisfying(
                AdminOperationLogReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminOperationLogReadException.Code
                                        .INVALID_DATE_RANGE
                        )
        );
    }

    @Test
    void LocalDate_최댓값을_종료일로_사용하면_거부한다() {
        assertThatThrownBy(() ->
                new AdminOperationLogReadQuery(
                        null, null, null, LocalDate.MAX, 0, 20
                )
        ).isInstanceOfSatisfying(
                AdminOperationLogReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminOperationLogReadException.Code
                                        .INVALID_DATE_RANGE
                        )
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {
            -999999999,
            -1,
            0,
            10000,
            999999999
    })
    void 허용_연도를_벗어난_시작일과_종료일은_거부한다(int year) {
        LocalDate invalidDate = LocalDate.of(year, 1, 1);

        assertThatThrownBy(() ->
                new AdminOperationLogReadQuery(
                        null, null, invalidDate, null, 0, 20
                )
        ).isInstanceOfSatisfying(
                AdminOperationLogReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminOperationLogReadException.Code.INVALID_DATE_RANGE
                        )
        );

        assertThatThrownBy(() ->
                new AdminOperationLogReadQuery(
                        null, null, null, invalidDate, 0, 20
                )
        ).isInstanceOfSatisfying(
                AdminOperationLogReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminOperationLogReadException.Code.INVALID_DATE_RANGE
                        )
        );
    }

    @Test
    void 허용_날짜_범위의_최솟값과_최댓값은_변환할_수_있다() {
        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        null,
                        null,
                        LocalDate.of(1, 1, 1),
                        LocalDate.of(9999, 12, 31),
                        0,
                        20
                );

        assertThat(query.fromInclusive()).isNotNull();

        assertThat(query.toExclusive())
                .isEqualTo(Instant.parse("9999-12-31T15:00:00Z"));
    }

    @Test
    void 정렬을_지정하지_않는_기존_호출은_최신순을_적용한다() {
        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        null, null, null, null, 0, 20
                );

        assertThat(query.sort())
                .isEqualTo(AdminOperationLogSort.LATEST);
    }

    @Test
    void 정렬이_null이면_최신순을_적용한다() {
        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        null, null, null, null, 0, 20, null
                );

        assertThat(query.sort())
                .isEqualTo(AdminOperationLogSort.LATEST);
    }

    @ParameterizedTest
    @EnumSource(AdminOperationLogSort.class)
    void 지정한_정렬과_기존_조회_조건을_유지한다(
            AdminOperationLogSort sort
    ) {
        LocalDate startDate = LocalDate.of(2026, 11, 1);
        LocalDate endDate = LocalDate.of(2026, 11, 6);

        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        AdminOperationLogTargetType.COMMENT,
                        ModerationAction.HIDE,
                        startDate,
                        endDate,
                        2,
                        50,
                        sort
                );

        assertThat(query.sort()).isEqualTo(sort);
        assertThat(query.targetType())
                .isEqualTo(AdminOperationLogTargetType.COMMENT);
        assertThat(query.action()).isEqualTo(ModerationAction.HIDE);
        assertThat(query.startDate()).isEqualTo(startDate);
        assertThat(query.endDate()).isEqualTo(endDate);
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(50);
    }

    @Test
    void 기존_6개_인자_호출은_최신순과_사유_전체를_적용한다() {
        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        AdminOperationLogTargetType.ALL,
                        null,
                        null,
                        null,
                        0,
                        20
                );

        assertThat(query.sort()).isEqualTo(AdminOperationLogSort.LATEST);
        assertThat(query.reasonType()).isNull();
    }

    @Test
    void 기존_7개_인자_호출은_정렬을_유지하고_사유_전체를_적용한다() {
        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        AdminOperationLogTargetType.ALL,
                        null,
                        null,
                        null,
                        0,
                        20,
                        AdminOperationLogSort.OLDEST
                );

        assertThat(query.sort()).isEqualTo(AdminOperationLogSort.OLDEST);
        assertThat(query.reasonType()).isNull();
    }

    @ParameterizedTest
    @EnumSource(ModerationReasonType.class)
    void 모든_사유_유형을_조회_조건으로_허용한다(
            ModerationReasonType reasonType
    ) {
        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        AdminOperationLogTargetType.ALL,
                        null,
                        null,
                        null,
                        0,
                        20,
                        AdminOperationLogSort.LATEST,
                        reasonType
                );

        // 신규 조치에서는 금지된 LEGACY도 조회에는 사용할 수 있다.
        assertThat(query.reasonType()).isEqualTo(reasonType);
    }

    @Test
    void 사유_유형이_null이면_전체_조회_조건으로_유지한다() {
        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        AdminOperationLogTargetType.ALL,
                        null,
                        null,
                        null,
                        0,
                        20,
                        AdminOperationLogSort.LATEST,
                        null
                );

        assertThat(query.reasonType()).isNull();
    }

    @Test
    void 신규_조치에서_허용하지_않는_조합도_조회_조건으로는_허용한다() {
        AdminOperationLogReadQuery query =
                new AdminOperationLogReadQuery(
                        AdminOperationLogTargetType.ALL,
                        ModerationAction.RESTORE,
                        null,
                        null,
                        0,
                        20,
                        AdminOperationLogSort.LATEST,
                        ModerationReasonType.SPAM_ADVERTISING
                );

        // 조회 조건은 그대로 보존한다.
        // 일치하는 기록이 없다면 저장소에서 빈 목록을 반환한다.
        assertThat(query.action()).isEqualTo(ModerationAction.RESTORE);
        assertThat(query.reasonType())
                .isEqualTo(ModerationReasonType.SPAM_ADVERTISING);
    }
}