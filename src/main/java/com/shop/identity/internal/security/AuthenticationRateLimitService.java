package com.shop.identity.internal.security;

import com.shop.identity.internal.configuration.AuthenticationRateLimitProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class AuthenticationRateLimitService {

    AuthenticationRateLimitProperties properties;
    Map<String, BucketState> buckets = new ConcurrentHashMap<>();

    RateLimitDecision tryConsume(String path, String clientAddress) {
        if (!properties.isEnabled()) {
            return RateLimitDecision.permitted();
        }

        AuthenticationRateLimitProperties.Limit limit = properties.limitFor(path);
        if (limit == null) {
            return RateLimitDecision.permitted();
        }

        String key = path + ':' + clientAddress;
        BucketState bucketState = buckets.get(key);
        if (bucketState == null) {
            bucketState = createBucketState(key, limit);
            if (bucketState == null) {
                return RateLimitDecision.denied(limit.getRefillPeriod().toSeconds());
            }
        }

        bucketState.touch(System.nanoTime());
        ConsumptionProbe probe = bucketState.bucket().tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return RateLimitDecision.permitted();
        }

        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        return RateLimitDecision.denied(retryAfterSeconds);
    }

    private synchronized BucketState createBucketState(String key, AuthenticationRateLimitProperties.Limit limit) {
        BucketState existingState = buckets.get(key);
        if (existingState != null) {
            return existingState;
        }
        if (buckets.size() >= properties.getMaxBuckets()) {
            removeIdleBuckets();
            if (buckets.size() >= properties.getMaxBuckets()) {
                return null;
            }
        }

        BucketState bucketState = new BucketState(createBucket(limit), System.nanoTime());
        buckets.put(key, bucketState);
        return bucketState;
    }

    private Bucket createBucket(AuthenticationRateLimitProperties.Limit limit) {
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth
                        .capacity(limit.getCapacity())
                        .refillIntervally(limit.getCapacity(), limit.getRefillPeriod()))
                .build();
    }

    @Scheduled(fixedDelayString = "${app.security.rate-limit.cleanup-interval:60000}")
    void removeIdleBuckets() {
        long now = System.nanoTime();
        long idleNanos = properties.getBucketIdleTime().toNanos();
        buckets.entrySet().removeIf(entry -> now - entry.getValue().lastAccessNanos() >= idleNanos);
    }

    record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

        static RateLimitDecision permitted() {
            return new RateLimitDecision(true, 0);
        }

        static RateLimitDecision denied(long retryAfterSeconds) {
            return new RateLimitDecision(false, Math.max(1, retryAfterSeconds));
        }
    }

    private static final class BucketState {

        private final Bucket bucket;
        private volatile long lastAccessNanos;

        private BucketState(Bucket bucket, long lastAccessNanos) {
            this.bucket = bucket;
            this.lastAccessNanos = lastAccessNanos;
        }

        private Bucket bucket() {
            return bucket;
        }

        private long lastAccessNanos() {
            return lastAccessNanos;
        }

        private void touch(long accessNanos) {
            lastAccessNanos = accessNanos;
        }
    }
}
