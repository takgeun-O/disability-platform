package io.github.takgeun.iyum.admin.operation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

// 조회 조건 객체 작성
public record AdminOperationLogReadQuery(
        AdminOperationLogTargetType targetType,
        @Nullable ModerationAction action,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        int page,
        int size
) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    private static final ZoneId SEARCH_ZONE =
            ZoneId.of("Asia/Seoul");

    public AdminOperationLogReadQuery {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new AdminOperationLogReadException(
                    AdminOperationLogReadException.Code.INVALID_PAGE_REQUEST
            );
        }

        // int 곱셈에서 먼저 오버플로가 발생하지 않도록
        // 곱하기 전에 long으로 변환한다.
        long offset = (long) page * size;

        if (offset > Integer.MAX_VALUE) {
            throw new AdminOperationLogReadException(
                    AdminOperationLogReadException.Code.INVALID_PAGE_REQUEST
            );
        }

        if (targetType == null) {
            targetType = AdminOperationLogTargetType.ALL;
        }

        validateDate(startDate);
        validateDate(endDate);

        if (startDate != null
                && endDate != null
                && startDate.isAfter(endDate)) {
            throw new AdminOperationLogReadException(
                    AdminOperationLogReadException.Code.INVALID_DATE_RANGE
            );
        }
    }

    /**
     * 시작 날짜의 한국 시간 00:00을 Instant로 변환한다.
     * 저장소에서는 createdAt >= 반환값 조건으로 사용한다.
     * 날짜만 한국 시간 기준으로 해석하고
     * DB와 비교할 때는 기존처럼 Instant를 사용한다. (DB세션 시간대를 변경할 필요 없음)
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
     * 종료 날짜 다음 날의 한국 시간 00:00을 Instant로 변환한다.
     * 저장소에는 createdAt < 반환값 조건으로 사용한다.
     * 날짜만 한국 시간 기준으로 해석하고
     * DB와 비교할 때는 기존처럼 Instant를 사용한다. (DB세션 시간대를 변경할 필요 없음)
     */
    @Nullable
    public Instant toExclusive() {
        if(endDate == null) {
            return null;
        }

        return endDate
                .plusDays(1)
                .atStartOfDay(SEARCH_ZONE)
                .toInstant();
    }

    /**
     * 요청 날짜는 0001-01-01부터 9999-12-31까지 허용한다.
     * Java에서 표현할 수 있는 극단적인 날짜가 DB까지 전달되지 않게 한다.
     */
    private static void validateDate(@Nullable LocalDate date) {
        if(date == null) {
            return;
        }

        if(date.getYear() < 1 || date.getYear() > 9999) {
            throw new AdminOperationLogReadException(
                    AdminOperationLogReadException.Code.INVALID_DATE_RANGE
            );
        }
    }
}
