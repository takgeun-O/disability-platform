package io.github.takgeun.iyum.admin.content.application;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Set;

/**
 * 관리자 게시글 목록 조회 조건.
 * <p>
 * 선택 상태, 날짜 범위, 정렬과 페이지 정보를 검증한다.
 * 내부 상태 집합에는 ALL 대신 실제 상태만 보관한다.
 * <p>
 * 이 객체를 만든 이유는 컨트롤러에서 들어온 값을 조회 쿼리에 넘기기 전에
 * 기본값·검색어·페이지 범위를 한곳에서 정리하기 위함
 */
public record AdminPostReadQuery(
        int page,
        int size,
        @Nullable String keyword,
        Set<AdminPostStatus> statuses,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        AdminPostSort sort
) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    public static final int MAX_KEYWORD_CODE_POINTS = 100;

    private static final ZoneId SEARCH_ZONE =
            ZoneId.of("Asia/Seoul");

    public AdminPostReadQuery {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.INVALID_PAGE_REQUEST
            );
        }

        // int 곱셈에서 먼저 오버플로가 발생하지 않도록 변환
        long offset = (long) page * size;

        if (offset > Integer.MAX_VALUE) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.INVALID_PAGE_REQUEST
            );
        }

        // 기본 생성자를 직접 호출하더라도 상태 검증을 거치게 한다.
        // 원본 집합과 분리된 불변 집합으로 보관한다.
        statuses = AdminPostStatus.normalizeStatuses(statuses);

        if (keyword != null) {
            keyword = keyword.strip();

            if (keyword.isEmpty()) {
                keyword = null;
            } else if (
                    keyword.codePointCount(0, keyword.length())
                            > MAX_KEYWORD_CODE_POINTS
            ) {
                throw new AdminContentReadException(
                        AdminContentReadException.Code.INVALID_SEARCH_KEYWORD
                );
            }
        }

        validateDate(startDate);
        validateDate(endDate);

        if (startDate != null
                && endDate != null
                && startDate.isAfter(endDate)) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.INVALID_DATE_RANGE
            );
        }

        // 정렬을 지정하지 않으면 기존 조회 순서를 유지한다.
        if(sort == null) {
            sort = AdminPostSort.LATEST;
        }
    }

    /**
     * 기존 상태 집합 생성자와의 호환성 유지 목적
     * 날짜 제한 없이 최신순으로 조회한다.
     */
    public AdminPostReadQuery(
            int page,
            int size,
            @Nullable String keyword,
            Set<AdminPostStatus> statuses
    ) {
        this(
                page,
                size,
                keyword,
                statuses,
                null,
                null,
                AdminPostSort.LATEST
        );
    }

    /**
     * 기존 단일 상태 생성자
     * null 또는 ALL은 실제 상태 세 개로 변환한다.
     */
    public AdminPostReadQuery(
            @Nullable AdminPostStatus status,
            @Nullable String keyword,
            int page,
            int size
    ) {
        this(
                page,
                size,
                keyword,
                AdminPostStatus.fromSingleStatus(status)
        );
    }

    /**
     * 기존 다중 상태 조회 호출을 유지한다.
     */
    public static AdminPostReadQuery ofStatuses(
            Collection<AdminPostStatus> statuses,
            @Nullable String keyword,
            int page,
            int size
    ) {
        return ofStatuses(
                statuses,
                keyword,
                page,
                size,
                null,
                null,
                AdminPostSort.LATEST
        );
    }

    /**
     * 날짜 범위와 정렬을 포함하는 다중 상태 조건
     * 상태 목록의 null, 빈 목록, null 원소, ALL은 허용하지 않는다.
     */
    public static AdminPostReadQuery ofStatuses(
            Collection<AdminPostStatus> statuses,
            @Nullable String keyword,
            int page,
            int size,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable AdminPostSort sort
    ) {
        return new AdminPostReadQuery(
                page,
                size,
                keyword,
                AdminPostStatus.normalizeStatuses(statuses),
                startDate,
                endDate,
                sort
        );
    }

    /**
     * 시작 날짜의 한국 시간 하루 시작을 Instant로 변환한다.
     * 저장소에서는 createdAt >= 반환값 기준으로 사용한다.
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
     * 종료 날짜 다음 날의 한국 시간 하루 시작을 반환한다.
     * 저장소에서는 createdAt < 반환값 조건으로 사용한다.
     *
     * 종료일의 마지막 순간을 직접 계산하지 않으므로
     * DB 시간 정밀도에 관계없이 해당 날짜 전체를 포함한다.
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

    private static void validateDate(@Nullable LocalDate date) {
        if (date == null) {
            return;
        }

        if (date.getYear() < 1 || date.getYear() > 9999) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.INVALID_DATE_RANGE
            );
        }
    }
}
