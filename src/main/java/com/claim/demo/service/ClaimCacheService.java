package com.claim.demo.service;

import com.claim.demo.dto.ClaimDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Best-effort cache facade. Redis is never authoritative: failures are treated as misses,
 * and database mutations invalidate cached values only after their transaction commits.
 */
@Service
public class ClaimCacheService {

    private static final Logger logger = LogManager.getLogger(ClaimCacheService.class);
    private static final String DETAIL_PREFIX = "claims:detail:";
    private static final String STATUS_PREFIX = "claims:status:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final Duration detailTtl;
    private final Duration statusTtl;

    public ClaimCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${claims.cache.detail-ttl:PT10M}") Duration detailTtl,
            @Value("${claims.cache.status-ttl:PT5M}") Duration statusTtl) {
        this.redisTemplate = redisTemplate;
        this.detailTtl = detailTtl;
        this.statusTtl = statusTtl;
    }

    public Optional<ClaimDTO> getClaim(Long claimId) {
        try {
            Object value = redisTemplate.opsForValue().get(detailKey(claimId));
            return value instanceof ClaimDTO claim ? Optional.of(claim) : Optional.empty();
        } catch (RuntimeException exception) {
            logger.warn("Redis claim lookup failed for claim {}; using PostgreSQL: {}",
                    claimId, exception.getMessage());
            return Optional.empty();
        }
    }

    public void putClaim(ClaimDTO claim) {
        try {
            redisTemplate.opsForValue().set(detailKey(claim.getClaimId()), claim, detailTtl);
        } catch (RuntimeException exception) {
            logger.warn("Redis claim cache write failed for claim {}: {}",
                    claim.getClaimId(), exception.getMessage());
        }
    }

    public Optional<String> getStatus(Long claimId) {
        try {
            Object value = redisTemplate.opsForValue().get(statusKey(claimId));
            return value instanceof String status ? Optional.of(status) : Optional.empty();
        } catch (RuntimeException exception) {
            logger.warn("Redis claim-status lookup failed for claim {}; using PostgreSQL: {}",
                    claimId, exception.getMessage());
            return Optional.empty();
        }
    }

    public void putStatus(Long claimId, String status) {
        try {
            redisTemplate.opsForValue().set(statusKey(claimId), status, statusTtl);
        } catch (RuntimeException exception) {
            logger.warn("Redis claim-status cache write failed for claim {}: {}",
                    claimId, exception.getMessage());
        }
    }

    public void evictClaimAfterCommit(Long claimId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictClaim(claimId);
                }
            });
            return;
        }
        evictClaim(claimId);
    }

    void evictClaim(Long claimId) {
        try {
            redisTemplate.delete(List.of(detailKey(claimId), statusKey(claimId)));
        } catch (RuntimeException exception) {
            logger.warn("Redis cache eviction failed for claim {}; cached values will expire: {}",
                    claimId, exception.getMessage());
        }
    }

    static String detailKey(Long claimId) {
        return DETAIL_PREFIX + claimId;
    }

    static String statusKey(Long claimId) {
        return STATUS_PREFIX + claimId;
    }
}
