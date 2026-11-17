package io.github.takgeun.iyum.admin.member.infrastructure;

import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.takgeun.iyum.admin.member.application.AdminMemberSummaryCountsResult;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static io.github.takgeun.iyum.member.domain.QMember.member;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberSummaryReadRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 목록 검색·필터·정렬·페이지 조건과 무관한 회원 요약을 조회한다.
     * 관리자 계정을 포함한 전체 계정을 기준으로 집계
     *
     * fromInclusive: 한국 날짜 기준 오늘 시작 시각,
     * toExclusive: 다음날 시작 시각
     *
     * 오늘 가입 수에만 날짜 조건 적용
     * 현재 회원·인증 대기·이용 정지 수는 전체 기간을 대상으로 한다.
     *
     * 집계 쿼리 4개 실행하며,
     * 여기서도 모든 조회가 동일한 DB 스냅샷임을 보장하지 않는다. (단순 집계용으로 쓸꺼니까)
     */
    public AdminMemberSummaryCountsResult findCounts(
            Instant fromInclusive,
            Instant toExclusive
    ) {
        // 탈퇴 상태인 WITHDRAWN, DELETED를 제외한다.
        // 인증 대기·활성·이용 제한·이용 정지는 현재 회원에 포함한다.
        Long currentMemberCount = queryFactory
                .select(member.count())
                .from(member)
                .where(
                        member.status.notIn(
                                MemberStatus.WITHDRAWN,
                                MemberStatus.DELETED
                        )
                )
                .fetchOne();

        // 오늘 생성된 계정을 센다.
        // 이후 인증·정지·탈퇴 여부와 무관하게 포함한다.
        Long todayMemberCount = queryFactory
                .select(member.count())
                .from(member)
                .where(
                        member.createdAt.goe(fromInclusive),
                        member.createdAt.lt(toExclusive)
                )
                .fetchOne();

        // 가입 날짜와 무관하게 현재 인증 대기 상태를 센다.
        Long pendingMemberCount = queryFactory
                .select(member.count())
                .from(member)
                .where(
                        member.status.eq(
                                MemberStatus.PENDING
                        )
                )
                .fetchOne();

        // 가입 날짜와 무관하게 현재 이용 정지 상태를 센다.
        // 과거 정지 이력이나 조치 횟수는 집계하지 않는다.
        Long suspendedMemberCount = queryFactory
                .select(member.count())
                .from(member)
                .where(
                        member.status.eq(
                                MemberStatus.SUSPENDED
                        )
                )
                .fetchOne();

        return new AdminMemberSummaryCountsResult(
                currentMemberCount == null
                        ? 0L
                        : currentMemberCount,
                todayMemberCount == null
                        ? 0L
                        : todayMemberCount,
                pendingMemberCount == null
                        ? 0L
                        : pendingMemberCount,
                suspendedMemberCount == null
                        ? 0L
                        : suspendedMemberCount
        );
    }
}
