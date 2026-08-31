package com.tongkey.openapi;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 Client 维度的令牌桶限流（规格文档 7.3），防止单一第三方压垮系统。
 */
@Component
public class RateLimiterRegistry {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(String clientId, int qpsLimit) {
        int qps = Math.max(1, qpsLimit);
        return buckets.compute(clientId, (k, old) ->
                old != null && old.qps == qps ? old : new TokenBucket(qps)).tryAcquire();
    }

    /** 配置变更后重建桶。 */
    public void evict(String clientId) {
        buckets.remove(clientId);
    }

    private static final class TokenBucket {
        final int qps;
        private double tokens;
        private long lastNanos;

        TokenBucket(int qps) {
            this.qps = qps;
            this.tokens = qps;
            this.lastNanos = System.nanoTime();
        }

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastNanos) / 1_000_000_000.0;
            lastNanos = now;
            tokens = Math.min(qps, tokens + elapsedSeconds * qps);
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }
    }
}
