package io.github.takgeun.iyum.auth.passwordreset.infrastructure;

import io.github.takgeun.iyum.auth.passwordreset.config.PasswordResetProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 같은 IP에서 최근 1시간동안 허용한 요청 수를 기록하고, 한도를 넘으면 거절한다.
 */
public final class PasswordResetIpRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private static final int DEFAULT_MAX_TRACKED_IPS = 10_000;

    private final Clock clock;
    private final int maxRequests;
    private final int maxTrackedIps;

    // 마지막으로 허용한 요청이 오래된 IP부터 유지한다.
    private final Map<String, Deque<Instant>> requests =
            new LinkedHashMap<>();

    private Instant lastObservedAt;

    public PasswordResetIpRateLimiter(
            PasswordResetProperties properties,
            Clock clock
    ) {
        this(properties, clock, DEFAULT_MAX_TRACKED_IPS);
    }

    // 같은 패키지의 테스트에서 작은 용량으로 검증하기 위한 생성자.
    PasswordResetIpRateLimiter(
            PasswordResetProperties properties,
            Clock clock,
            int maxTrackedIps
    ) {
        Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");

        if (maxTrackedIps < 1) {
            throw new IllegalArgumentException(
                    "기록할 IP 개수는 1 이상이어야 합니다."
            );
        }

        this.maxRequests = properties.requestMaxPerIpPerHour();
        this.maxTrackedIps = maxTrackedIps;
    }

    // true : 요청을 허용하고, 허용 시각을 기록한다.
    // false : 요청을 거절한다. 겆러한 요청의 시각은 기록하지 않는다.
    public synchronized boolean tryAcquire(String clientIp) {
        if (clientIp == null
                || clientIp.isBlank()
                || clientIp.length() > 128) {
            throw new IllegalArgumentException(
                    "유효한 클라이언트 IP가 필요합니다."
            );
        }

        Instant now = currentTime();
        // 예를 들어 maxRequests = 3 이고 현재가 10:30이면 cutoff는 09:30이다.
        // 따라서 09:30보다 늦고, 10:30 이하인 기록만 집계할 것
        // 기존 IP 요청 기록에서 [10:00, 10:10, 10:20] 이라면 10:30 요청은 거절한다. (모두 범위 내에 있고 3개로 가득 찼으니까)
        // 11시 정각에 다시 요청하면 cutoff는 10:00
        // 아래에 while (!history.isEmpty() && ~~) 코드가 10:00 이하의 오래된 기록을 제거한다.
        // 이 경우 11:00 추가 가능
        Instant cutoff = now.minus(WINDOW);

        removeExpiredBuckets(cutoff);

        Deque<Instant> history = requests.get(clientIp);

        if(history == null) {
            // 제한 기록을 임의로 지우면 제한을 우회할 수 있으므로
            // 공간이 없을 때는 새로운 IP의 요청을 허용하지 않는다.
            if(requests.size() >= maxTrackedIps) {
                return false;
            }

            history = new ArrayDeque<>();
        }

        // 집계 구간: (현재 시각 - 1시간, 현재 시각]
        while (!history.isEmpty()
            && !history.peekFirst().isAfter(cutoff)) {
            history.removeFirst();
        }

        if(history.size() == maxRequests) {
            return false;
        }

        history.addLast(now);

        // 마지막 허용 시각 순으로 다시 배치
        requests.remove(clientIp);
        requests.put(clientIp, history);

        return true;
    }

    private Instant currentTime() {
        Instant observed = clock.instant();

        // 시계가 뒤로 조정되어도 기록 시각의 순서가 뒤집히지 않게 한다.
        if(lastObservedAt == null || observed.isAfter(lastObservedAt)) {
            lastObservedAt = observed;
        }

        return lastObservedAt;
    }

    private void removeExpiredBuckets(Instant cutoff) {
        var iterator = requests.entrySet().iterator();

        while (iterator.hasNext()) {
            Deque<Instant> history = iterator.next().getValue();

            // 마지막 요청도 1시간이 지났으면 전체 기록을 삭제한다.
            if(!history.peekLast().isAfter(cutoff)) {
                iterator.remove();
            } else {
                // 이후 항목들은 더 최근에 허용된 IP이므로 중단한다.
                break;
            }
        }
    }
}
