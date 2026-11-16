package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

// 검색 조건 객체
public record AdminReportReadQuery(

        AdminReportTargetType targetType,

        @Nullable
        ContentReportStatus status,

        @Nullable
        ContentReportReasonType reasonType,

        @Nullable
        LocalDate startDate,

        @Nullable
        LocalDate endDate,

        int page,

        int size,

        AdminReportSort sort
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    private static final ZoneId SEARCH_ZONE =
            ZoneId.of("Asia/Seoul");

    public AdminReportReadQuery {
        validatePage(page, size);

        validateDate(startDate);
        validateDate(endDate);

        if (startDate != null
                && endDate != null
                && startDate.isAfter(endDate)) {
            throw new AdminReportReadException(
                    AdminReportReadException.Code.INVALID_DATE_RANGE
            );
        }

        if(targetType == null) {
            targetType = AdminReportTargetType.ALL;
        }

        if(sort == null) {
            sort = AdminReportSort.LATEST;
        }
    }

    /**
     * 시작 날짜의 한국 시간 00:00
     *
     * 저장소에서 createdAt >= 반환값으로 조회
     * 시작 날짜를 지정하지 않으면 하한 조건을 적용하지 않는다.
     */
    @Nullable
    public Instant fromInclusive() {
        if(startDate == null) {
            return null;
        }

        return startDate
                .atStartOfDay(SEARCH_ZONE)
                .toInstant();
    }

    /**
     * 종료 날짜 다음 날의 한국 시간 00:00.
     *
     * 저장소에서 createdAt < 반환값으로 조회한다.
     * 종료 날짜 전체를 포함하기 위해 다음 날 시작 시각을 사용한다.
     */
    @Nullable
    public Instant toExclusive() {
        if (endDate == null) {
            return null;
        }

        return endDate
                .plusDays(1)
                .atStartOfDay(SEARCH_ZONE)
                .toInstant();
    }

    private static void validatePage(
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new AdminReportReadException(
                    AdminReportReadException.Code.INVALID_PAGE_REQUEST
            );
        }

        // int 곱연산 오버플로 방지
        long offset = (long) page * size;

        if (offset > Integer.MAX_VALUE) {
            throw new AdminReportReadException(
                    AdminReportReadException.Code.INVALID_PAGE_REQUEST
            );
        }
    }

    private static void validateDate(
            @Nullable LocalDate date
    ) {
        if (date == null) {
            return;
        }

        // 요청 날짜는 0001-01-01 ~ 9999-12-31만 허용
        if (date.getYear() < 1 || date.getYear() > 9999) {
            throw new AdminReportReadException(
                    AdminReportReadException.Code.INVALID_DATE_RANGE
            );
        }
    }
}
