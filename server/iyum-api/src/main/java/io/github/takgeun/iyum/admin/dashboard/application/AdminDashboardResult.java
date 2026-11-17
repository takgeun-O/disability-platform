package io.github.takgeun.iyum.admin.dashboard.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 대시보드 조회 결과.
 * 조회 기준 시각, 오늘의 날짜, 요약 수치, 신고 목록을 묶는다.
 * <p>
 * queriedAt은 조회 기준 시각이며,
 * baseDate는 해당 시각의 한국 날짜이다.
 */
public record AdminDashboardResult(

        // 서비스가 조회를 시작할 때 정한 기준 시각
        Instant queriedAt,

        // queriedAt을 Asia/Seoul로 변환한 날짜.
        // '오늘' 집계 기간 계산에 사용한다.
        LocalDate baseDate,
        AdminDashboardCountsResult counts,
        List<AdminDashboardPendingReportResult> pendingReports
) {

    public AdminDashboardResult {
        // 전달 받은 목록을 수정 불가능한 목록으로 복사한다.
        // 1, 원본 목록이 나중에 수정돼도 조회 결과를 유지한다.
        // 2. 결과를 받은 쪽에서도 목록을 수정하지 못하게 한다.
        //  컨트롤러 같은 호출자가 result.pendingReports().clear();
        //  를 작성했을 경우 copyOf()로 만든 목록에서는
        //  UnsupportedOperationException이 발생한다.
        //  이처럼 목록을 조회하거나 DTO로 변환하는 것은 가능하지만
        //  추가·삭제·교체는 할 수 없다.
        // record는 필드의 참조를 바꾸지 못하게 하지만,
        // 참조하는 객체 내부까지 보호하지는 않는다.
        // 예시)
        // this.pendingReports = anotherList; (record 필드 자체에 다른 목록을 다시 대입하는 것은 불가)
        // result.pendingReports().clear(); (하지만 수정 가능한 List를 담았다면 내부 변경은 가능)
        pendingReports = List.copyOf(pendingReports);
    }
}
