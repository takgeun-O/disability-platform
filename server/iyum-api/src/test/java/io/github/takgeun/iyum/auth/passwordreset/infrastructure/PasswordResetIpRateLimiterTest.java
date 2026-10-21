package io.github.takgeun.iyum.auth.passwordreset.infrastructure;

import io.github.takgeun.iyum.auth.passwordreset.config.PasswordResetProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PasswordResetIpRateLimiterTest {

    private static final Instant NOW =
            Instant.parse("2026-10-21T05:00:00Z");

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void 같은_IP는_30건까지_허용하고_31번째부터_거절한다() {
        var limiter = limiter(30);

        for (int i = 0; i < 30; i++) {
            assertThat(limiter.tryAcquire("192.0.2.1")).isTrue();
        }

        assertThat(limiter.tryAcquire("192.0.2.1")).isFalse();
        assertThat(limiter.tryAcquire("192.0.2.1")).isFalse();
    }

    @Test
    void 서로_다른_IP는_별도로_집계한다() {
        var limiter = limiter(1);

        assertThat(limiter.tryAcquire("192.0.2.1")).isTrue();
        assertThat(limiter.tryAcquire("192.0.2.1")).isFalse();

        assertThat(limiter.tryAcquire("192.0.2.2")).isTrue();
        assertThat(limiter.tryAcquire("192.0.2.2")).isFalse();
    }

    @Test
    void 정확히_1시간_지난_요청만_집계에서_제외한다() {
        var limiter = limiter(2);

        assertThat(limiter.tryAcquire("192.0.2.1")).isTrue();

        when(clock.instant()).thenReturn(NOW.plusSeconds(10));
        assertThat(limiter.tryAcquire("192.0.2.1")).isTrue();

        when(clock.instant()).thenReturn(NOW.plusSeconds(3599));
        assertThat(limiter.tryAcquire("192.0.2.1")).isFalse();

        // 첫 번째 요청만 정확히 1시간이 지났다.
        when(clock.instant()).thenReturn(NOW.plusSeconds(3600));

        assertThat(limiter.tryAcquire("192.0.2.1")).isTrue();
        assertThat(limiter.tryAcquire("192.0.2.1")).isFalse();

        // 두 번째 요청도 정확히 1시간이 지났다.
        when(clock.instant()).thenReturn(NOW.plusSeconds(3610));

        assertThat(limiter.tryAcquire("192.0.2.1")).isTrue();
        assertThat(limiter.tryAcquire("192.0.2.1")).isFalse();
    }

    @Test
    void 거절된_요청으로_제한_시간을_연장하지_않는다() {
        var limiter = limiter(1);

        assertThat(limiter.tryAcquire("192.0.2.1")).isTrue();

        when(clock.instant()).thenReturn(NOW.plusSeconds(3599));
        assertThat(limiter.tryAcquire("192.0.2.1")).isFalse();

        when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
        assertThat(limiter.tryAcquire("192.0.2.1")).isTrue();
    }

    @Test
    void 용량이_가득_차면_기존_기록을_유지하고_만료_후_공간을_회수한다() {
        var limiter = new PasswordResetIpRateLimiter(
                properties(1),
                clock,
                2
        );

        assertThat(limiter.tryAcquire("192.0.2.1")).isTrue();
        assertThat(limiter.tryAcquire("192.0.2.2")).isTrue();

        // 새로운 IP 때문에 기존 제한 기록을 밀어내면 안 된다.
        assertThat(limiter.tryAcquire("192.0.2.3")).isFalse();
        assertThat(limiter.tryAcquire("192.0.2.1")).isFalse();

        when(clock.instant()).thenReturn(NOW.plusSeconds(3600));

        // 만료된 IP 기록이 정리되어 새로운 IP도 허용된다.
        assertThat(limiter.tryAcquire("192.0.2.3")).isTrue();
    }

    @Test
    void 시계가_뒤로_이동해도_기존_제한을_유지한다() {
        var limiter = limiter(1);

        assertThat(limiter.tryAcquire("192.0.2.1")).isTrue();

        when(clock.instant()).thenReturn(NOW.minusSeconds(60));
        assertThat(limiter.tryAcquire("192.0.2.1")).isFalse();

        when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
        assertThat(limiter.tryAcquire("192.0.2.1")).isTrue();
    }

    @Test
    void 같은_IP의_동시_요청도_30건만_허용한다() throws Exception {
        var limiter = limiter(30);

        ExecutorService workers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 60; i++) {
                futures.add(workers.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "동시 요청 시작 대기 시간 초과"
                        );
                    }

                    return limiter.tryAcquire("192.0.2.1");
                }));
            }

            start.countDown();

            int accepted = 0;

            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }

            assertThat(accepted).isEqualTo(30);
        } finally {
            start.countDown();
            workers.shutdownNow();

            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS))
                    .isTrue();
        }
    }

    private PasswordResetIpRateLimiter limiter(int maxRequests) {
        return new PasswordResetIpRateLimiter(
                properties(maxRequests),
                clock
        );
    }

    private PasswordResetProperties properties(int maxRequests) {
        return new PasswordResetProperties(
                Duration.ofMinutes(30),
                Duration.ofSeconds(60),
                5,
                maxRequests
        );
    }
}