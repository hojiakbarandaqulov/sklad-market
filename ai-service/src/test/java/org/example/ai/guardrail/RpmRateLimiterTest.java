package org.example.ai.guardrail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RpmRateLimiterTest {

    @Test
    void allowsUpToCapacityThenDeniesWithinTheSameWindow() {
        RpmRateLimiter limiter = new RpmRateLimiter(2);

        assertThat(limiter.tryConsume("user-1")).isTrue();
        assertThat(limiter.tryConsume("user-1")).isTrue();
        assertThat(limiter.tryConsume("user-1")).isFalse();
    }

    @Test
    void tracksEachUserIndependently() {
        RpmRateLimiter limiter = new RpmRateLimiter(1);

        assertThat(limiter.tryConsume("user-a")).isTrue();
        assertThat(limiter.tryConsume("user-a")).isFalse();
        assertThat(limiter.tryConsume("user-b")).isTrue();
    }

    @Test
    void zeroRpmIsTheKillSwitchAndBlocksEveryRequest() {
        RpmRateLimiter limiter = new RpmRateLimiter(0);

        assertThat(limiter.tryConsume("user-1")).isFalse();
        assertThat(limiter.tryConsume("user-2")).isFalse();
    }

    @Test
    void negativeRpmIsAlsoTreatedAsTheKillSwitch() {
        RpmRateLimiter limiter = new RpmRateLimiter(-5);

        assertThat(limiter.tryConsume("user-1")).isFalse();
    }

    @Test
    void operationSpecificCapacityDoesNotChangeTheDefaultBucket() {
        RpmRateLimiter limiter = new RpmRateLimiter(1);

        assertThat(limiter.tryConsume("dashboard:user-1", 2)).isTrue();
        assertThat(limiter.tryConsume("dashboard:user-1", 2)).isTrue();
        assertThat(limiter.tryConsume("dashboard:user-1", 2)).isFalse();
        assertThat(limiter.tryConsume("chat:user-1")).isTrue();
        assertThat(limiter.tryConsume("chat:user-1")).isFalse();
    }
}
