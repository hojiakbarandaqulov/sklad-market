package org.example.ai.guardrail;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Per-user token bucket (PLAN.md Phase 1: "per-`sub` RPM limiter (Caffeine token bucket)").
 * Capacity refills continuously at {@code rpm} tokens/minute; each chat turn consumes one token.
 * {@code rpm <= 0} is the documented kill switch (HANDOFF.md §4: "AI_RATE_LIMIT_RPM=0 + restart"
 * hard-disables chat) and blocks every request rather than being floored to a capacity of 1.
 */
@Component
public class RpmRateLimiter {

    private final double capacity;
    private final Cache<BucketKey, Bucket> buckets;

    public RpmRateLimiter(@Value("${ai.limits.rate-limit-rpm:10}") int rpm) {
        this.capacity = Math.max(rpm, 0);
        this.buckets = Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(2)).build();
    }

    public boolean tryConsume(String userSub) {
        return tryConsume(userSub, capacity);
    }

    /** Uses an operation-specific capacity while retaining the same bounded token-bucket store. */
    public boolean tryConsume(String userSub, int rpm) {
        return tryConsume(userSub, (double) Math.max(rpm, 0));
    }

    private boolean tryConsume(String userSub, double requestedCapacity) {
        if (requestedCapacity <= 0) {
            return false;
        }
        BucketKey key = new BucketKey(userSub, requestedCapacity);
        Bucket bucket = buckets.get(key, ignored -> new Bucket(requestedCapacity));
        return bucket.tryConsume(requestedCapacity);
    }

    private record BucketKey(String key, double capacity) { }

    private static final class Bucket {
        private double tokens;
        private long lastRefillNanos;

        private Bucket(double capacity) {
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume(double capacity) {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            lastRefillNanos = now;
            tokens = Math.min(capacity, tokens + elapsedSeconds * (capacity / 60.0));
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
