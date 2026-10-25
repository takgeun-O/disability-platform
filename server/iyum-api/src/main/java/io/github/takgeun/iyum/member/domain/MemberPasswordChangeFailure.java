package io.github.takgeun.iyum.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

// 실패 기록 엔티티
@Entity
@Table(name = "member_password_change_failures")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberPasswordChangeFailure {

    public static final int MAX_FAILURES = 5;
    public static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    public static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "failed_at", nullable = false, updatable = false)
    private Instant failedAt;

    @Column(name = "blocked_until", updatable = false)
    private Instant blockedUntil;

    private MemberPasswordChangeFailure(
            Long memberId,
            Instant failedAt,
            long previousFailures
    ) {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException(
                    "유효한 회원 ID가 필요합니다."
            );
        }

        if(failedAt == null) {
            throw new IllegalArgumentException(
                    "실패 시각이 필요합니다."
            );
        }

        // 이미 5회 실패했다면 서비스에서 차단해야 하므로
        // 새 실패 기록을 생성하는 경로로 들어오면 안된다.
        // 즉 횟수를 클라이언트에게서 받는 행동을 하면 안된다.
        if(previousFailures < 0 || previousFailures >= MAX_FAILURES) {
            throw new IllegalArgumentException(
                    "기준 실패 횟수는 0~4회여야 합니다."
            );
        }

        this.memberId = memberId;
        this.failedAt = failedAt;

        // 기존 4회 + 이번 1회 = 5회인 시점부터 15분 차단한다.
        this.blockedUntil = previousFailures == MAX_FAILURES -1
                ? failedAt.plus(BLOCK_DURATION)
                : null;
    }

    /**
     * 호출 전에 서비스쪽에서 회원 행 잠금을 획득하고,
     * 차단 여부와 최근 실패 횟수를 확인할 것.
     *
     * 즉, 이 객체 자체가 동시 요청을 제어하거나 DB를 조회하지는 않는다.
     */
    public static MemberPasswordChangeFailure record(
            Long memberId,
            Instant failedAt,
            long previousFailures
    ) {
        return new MemberPasswordChangeFailure(
                memberId,
                failedAt,
                previousFailures
        );
    }
}
